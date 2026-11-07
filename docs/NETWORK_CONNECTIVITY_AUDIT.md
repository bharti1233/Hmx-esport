# HMX Arena — Network Connectivity Audit

Date: 2026-11-07  
Branch: `feature/hmx-arena-android-production`  
Commit: `16bf24e`  
Implementation: `data/connectivity/ConnectivityMonitor.kt`,
`data/connectivity/BackendHealthChecker.kt`,
`ui/connectivity/ConnectivityGateViewModel.kt`,
`ui/connectivity/ConnectionRequiredScreen.kt`, `HmxRoot.kt`,
`di/NetworkModule.kt`, `data/remote/SessionManager.kt`

## 1. Requirement

HMX Arena is an online-only product: tournaments, registrations, wallets and
match credentials all come from Supabase. The app must therefore:

- require a working internet connection **before** it renders the signed-in app;
- distinguish *no network*, *network but no internet*, *backend down* and
  *timeout*;
- not loop, not poll, and not sign the player out because the backend is down;
- retry automatically when connectivity changes or the app resumes;
- preserve auth state across every failure;
- never claim "offline mode" when there is no local content.

## 2. Previous behaviour

Before this change there was no connectivity gate. `HmxRoot` rendered the app
immediately after `SessionManager.bootstrap()`, so:

- screens fired requests with no network and showed generic error text;
- a failed refresh attempt could be mapped to a sign-out;
- the player could see a stale/empty dashboard with no explanation;
- nothing re-checked when Wi-Fi came back.

## 3. Design

### 3.1 OS-level classification (`ConnectivityMonitor`)

`ConnectivityMonitor` reads the **default network** through `ConnectivityManager`
and `NetworkCapabilities`:

| OS signal | State |
| --- | --- |
| no `activeNetwork` | `NoNetwork` |
| network without `NET_CAPABILITY_INTERNET` | `NoNetwork` |
| network with `INTERNET` but without `NET_CAPABILITY_VALIDATED` | `NoInternet` |
| network with `INTERNET` + `VALIDATED` | `Available` |

`VALIDATED` is the important distinction: a captive-portal hotel Wi-Fi or a SIM
with no data path reports `INTERNET` but not `VALIDATED`, which is exactly
"connected, no internet".

`observe()` is a `callbackFlow` over
`registerDefaultNetworkCallback`/`unregisterNetworkCallback`, emitting the
current state first and then on `onAvailable`, `onLost` and
`onCapabilitiesChanged`. The flow is `distinctUntilChanged().conflate()`. There
is **no timer and no polling**.

`ACCESS_NETWORK_STATE` is already declared in `AndroidManifest.xml`.

### 3.2 Backend probe (`BackendHealthChecker`)

One `GET {SUPABASE_URL}/auth/v1/health` with the publishable `apikey` header:

- 5 s connect timeout, 8 s read timeout, 8 s whole-call timeout;
- uses `Http.bareClient()`, so it never triggers the session authenticator and
  never carries a user access token;
- maps a 2xx to `Reachable`, timeout exceptions to `Timeout`, everything else to
  `Unavailable`;
- cancels the call in `finally` so a retry cannot leak a socket.

This endpoint verifies the host and the Auth service, not a user table, so it
does not depend on RLS, a session, or any row existing.

### 3.3 Gate state machine (`ConnectivityGateViewModel`)

```
            monitor.observe()
NoNetwork  ───────────────► NoNetwork        (check job cancelled)
NoInternet ───────────────► NoInternet       (check job cancelled)
Available  ───────────────► Checking ─┬─► Ready
                                      ├─► BackendUnavailable
                                      └─► Timeout

retry() / ON_RESUME (failed state only)
        └──────────────────► Checking ...
```

- `Ready` is sticky across repeated `Available` callbacks: capability flapping
  cannot create duplicate probes.
- A failed state is re-probed on resume and on an explicit **Try again** tap.
- `checkBackend()` re-reads `monitor.current()` first, so a probe is never sent
  when the OS says there is no validated network.
- `checkJob` is cancelled when the network drops, so a late response cannot
  overwrite the offline state.
- The ViewModel **never touches `SessionManager`**. A backend outage cannot sign
  a player out; it only withholds the app UI.

### 3.4 UI (`ConnectionRequiredScreen`)

A full-screen Liquid Glass state uses the brand mark and an icon per status:

| Status | Title | Hint |
| --- | --- | --- |
| `NoNetwork` | No connection | Turn on Wi-Fi or mobile data. |
| `NoInternet` | Connected, no internet | This network has no internet access. |
| `BackendUnavailable` | HMX Arena is unavailable | The server is not responding right now. |
| `Timeout` | Connection timed out | The server took too long to respond. |

Copy is neutral ("Your session is preserved") because the gate can appear both
before and after sign-in. A single **Try again** button calls `retry()`; it is not
shown while `Checking`.

### 3.5 Session and auth interaction

- Recovery deep links are **queued**, not consumed, while the gate is not
  `Ready`. Verification needs the network, so the link is parsed only after the
  gate opens and the raw intent is handled exactly once.
- A legitimate 400/401 from Supabase Auth still clears the session (invalid
  credentials). A 5xx, 429, timeout or I/O failure no longer clears it.
- The OkHttp interceptor refreshes a missing/expired access token for non-auth
  endpoints; the authenticator retries a 401 once when a refresh token exists.
  Both respect the single `refreshLock`.
- The access token is memory-only; it is no longer persisted (see
  `SECURITY_AUDIT_REPORT.md`).

## 4. Failure matrix

| Scenario | Detected by | UI | Auth state |
| --- | --- | --- | --- |
| Airplane mode | `activeNetwork == null` | No connection | unchanged |
| Wi-Fi connected, router offline | `INTERNET` without `VALIDATED` | Connected, no internet | unchanged |
| Captive portal | `INTERNET` without `VALIDATED` | Connected, no internet | unchanged |
| Supabase 5xx | probe non-2xx | HMX Arena is unavailable | unchanged |
| DNS blackhole / slow host | 8 s call timeout | Connection timed out | unchanged |
| Wrong password | Auth 400/401 | auth form error | cleared (correct) |
| Expired access token, valid refresh | 401 → authenticator | none | refreshed |
| Refresh returns 5xx | refresh path | normal error surface | **preserved** |
| Network drops mid-request | `onLost` | offline gate | unchanged |

## 5. What was deliberately not done

- **No retry storm.** There is no exponential backoff loop; retries are only
  user-initiated or event-initiated. A periodic ping would waste battery and is
  unnecessary because `registerDefaultNetworkCallback` already pushes changes.
- **No offline content mode.** The app has no product requirement to browse
  tournaments offline; the Room cache is only a fallback for an already-loaded
  catalogue and is not advertised as offline mode.
- **No forced sign-out in the gate.** Backend downtime is not an auth failure.
- **No ping to a third-party host.** Only the app's own backend is probed, so the
  gate does not leak usage to an unrelated service.

## 6. Tests

`ConnectivityClassificationTest` (JVM):

- `Reachable → Ready` and `Ready` is the only non-blocking state;
- `Unavailable` and `Timeout` stay distinct;
- default state is `Checking`.

The Android-specific monitor and probe are compiled by CI but not exercised by
an instrumented test in this environment (no emulator in the local toolchain).
A future `androidTest` can fake `ConnectivityManager` and an OkHttp
`MockWebServer` to cover the OS callback path.

## 7. Verification status

| Item | Status |
| --- | --- |
| Compilation | `Android CI` `42363668406` for `16bf24e` |
| Classification unit tests | Pass in CI |
| Airplane-mode parity test | Not automated; manual test plan below |
| Backend-down test | Not automated |
| Retry-on-reconnect | Implemented event-driven; verified by code review |

Manual acceptance script for the next device run:

1. Turn on airplane mode → launch → expect *No connection*, no crash.
2. Disable airplane mode → app re-probes → expect *Ready* without relaunch.
3. Connect to a Wi-Fi portal → expect *Connected, no internet*.
4. Point the base URL at an unreachable host → expect *Connection timed out*
   after ≈ 8 s.
5. Kill the backend → expect *HMX Arena is unavailable*; confirm the DataStore
   refresh token is still present after the test.
