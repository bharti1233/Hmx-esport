# Architecture

## 1. Shape

Single Gradle module (`:app`) with package-level layers. This keeps the Gradle
graph small enough for the CI runner while preserving dependency direction:

```
ui  ->  data/repository  ->  data/remote | data/local
             |
          core/model, core/util, core/designsystem
```

Rules:

- `ui` never calls Retrofit or Supabase directly.
- Repositories own server calls, validation that needs server state and mapping.
- `core` has no Android UI dependency except the design system.
- Hilt binds the graph; ViewModels receive repositories via constructor injection.

## 2. Packages

| Package | Responsibility |
| --- | --- |
| `com.hmx.arena` | `Application`, `MainActivity`, `HmxRoot` session/onboarding gate. |
| `core.designsystem` | Colour, type, shapes, glass surfaces, buttons, bars, scaffold. |
| `core.model` | `@Serializable` transport models shared by API and UI. |
| `core.util` | Formatting, validation, registration eligibility rules. |
| `data.remote` | Supabase config, Retrofit APIs, auth interceptor, session manager, `RemoteDataSource`. |
| `data.local` | Room database and tournament cache entity/DAO. |
| `data.repository` | `Auth`, `Player`, `Tournament`, `Team` repositories. |
| `di` | `NetworkModule`, `DatabaseModule`. |
| `ui.*` | Feature screens + ViewModels + navigation. |

## 3. Data flow

```
Composable -> ViewModel function -> Repository suspend fun
           -> RemoteDataSource -> Retrofit -> Supabase
           -> StateFlow<UiState> -> recomposition
```

- UI state is one immutable data class per screen, held in `MutableStateFlow`.
- Every async action has `loading`, `error` and success/message fields.
- Failures are converted to short human messages by `Throwable.hmxMessage()`.

## 4. Session and authentication

`SessionManager` is the only owner of session state.

- Access token: memory only.
- Refresh token, user id, email, expiry: DataStore (`hmx_session`).
- `bootstrap()` runs once from `MainActivity` before Compose reads state.
- An OkHttp `Authenticator` performs a blocking refresh on 401 and retries.
- `state: StateFlow<SessionState>` is `Loading -> SignedOut | SignedIn`.

`HmxRoot` renders exactly one of:

1. loading / deep-link processing,
2. password recovery,
3. `AuthFlow`,
4. signed-in gate (`my_profile()` decides onboarding vs main graph).

Deep links:

- implicit fragment: `hmxarena://auth/reset#access_token=...&refresh_token=...&type=recovery`
- token hash: `hmxarena://auth/reset?token_hash=...&type=recovery`
- handled in `onCreate` and `onNewIntent`.

## 5. Networking

`SupabaseApi.kt` declares three Retrofit interfaces:

- `AuthApi` — `/auth/v1/signup|token|verify|recover|resend|user|logout`
- `RestApi` — dynamic PostgREST paths, RPC calls, storage upload
- `StorageApi` — object upload and public URL

`RemoteDataSource` wraps them with:

- `select`, `selectOne`, `insert`, `update`, `delete`, `rpc`, `rpcList`, `rpcUnit`
- idempotency keys for mutating RPCs
- JSON parsing through Kotlin Serialization
- a single `ApiException` type with a readable message

PostgREST filters are passed as `mapOf("column" to "eq.value", "order" to ...)`.

## 6. Caching

Room stores only the tournament catalogue:

- `TournamentEntity` mirrors the transport model.
- On a successful network fetch the list is replaced.
- On network failure the cache is returned if non-empty; otherwise the error is
  shown with retry.
- No user-specific or security-sensitive data is cached.

## 7. Navigation

- `Routes.kt` is the single source of truth for every destination.
- `MainNavHost` maps each route to a screen; parameters are read from
  `NavBackStackEntry.arguments`.
- Bottom tabs use `openTab()` with `saveState`/`restoreState`.
- The root graph is only entered after the session and onboarding gate, so no
  screen assumes a token exists.

## 8. Design system

`Theme.kt` defines one light scheme: white background, ink text, hairline
borders. `Glass.kt` provides:

- `GlassSurface` — translucent white fill, hairline border, soft shadow.
- `GlassScaffold` / `GlassTopBar` / `GlassBottomBar`.
- `GlassButton`, `GlassIconButton`, `GlassChip`.
- `GlassSectionHeader`, `GlassStatCell`, `GlassActionRow`.

The glass effect is built from alpha, highlight borders and elevation. It does
not use `Modifier.blur` (API 31+) so it renders consistently from API 26.

## 9. Build

- Gradle 8.14.3, AGP 8.13.2, Kotlin 2.1.21, KSP, JDK 17.
- Compose BOM 2025.05.01, Material 3, extended icons.
- Hilt 2.56.2, Room 2.7.1, Navigation Compose 2.9.0.
- `compileSdk`/`targetSdk` 36, `minSdk` 26.
- Supabase URL and publishable key come from `gradle.properties` into
  `BuildConfig`. The publishable key is safe to ship; RLS enforces access.

## 10. Deliberate limitations

- No realtime layer; screens refresh on open and via explicit reload actions.
- No push notifications; only the in-app notification list exists.
- No payment SDK; paid flags are off server-side.
- Single module instead of feature modules, to fit CI memory limits.
