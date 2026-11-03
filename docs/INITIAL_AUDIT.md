# Initial Audit

Date: 2026-11-02
Repository: `https://github.com/bharti1233/Hmx-esport`
Target branch: `feature/hmx-arena-android-production`

## Starting point

The repository contained only a Gradle scaffold, launcher resources and a CI
workflow. There was no application code, no database schema and no design
system. The audit therefore treated this as a greenfield build inside an
existing empty shell.

## Reference design

The supplied reference image (`file_0000000030208211a128454e9b6c4809.png`)
shows an iOS-style "Liquid Glass" interface:

- pure white background,
- frosted translucent white cards and bars,
- hairline grey borders,
- monochrome ink typography,
- soft, wide shadows,
- pill-shaped controls,
- no colour accents, gradients or dark surfaces.

That direction became a hard requirement: the app ships a single light theme.
There is no dark scheme because the reference has none.

## Constraints

| Constraint | Consequence |
| --- | --- |
| Builds must run only on GitHub Actions | No local Gradle/APK build was attempted. Every compile, test, lint and packaging result comes from CI. |
| Low-RAM Android device used for review | A single `:app` module was chosen over multi-module Clean Architecture. Package layering keeps the same boundaries without the extra Gradle graph. |
| Real backend only | Supabase project `ahsvxxhaowwhqermruhz` is the only data source. No mock repositories, sample lists or hard-coded balances exist in the UI. |
| Payments not approved | Paid entry, wallet funding, refunds and payouts are gated by server-side flags that default to `false`. |
| No fake OTP / SMS | Email + password authentication only. Phone OTP is not offered. |

## Assets and gaps found

- No launcher icon beyond the generated default: replaced with HMX vector icons.
- No legal copy: added Terms, Privacy, Fair Play and Refund documents in-app.
- No Supabase migrations: added `0001`-`0008`.
- No RLS tests: added a 29-assertion SQL regression suite.
- No CI artifact: the workflow now uploads the debug APK.

## Risks recorded before implementation

1. **Email confirmation links** depend on Supabase dashboard configuration.
   The Android manifest accepts `hmxarena://auth/reset`; the project's Site URL
   and redirect allowlist must include it. This is a deployment step, not app code.
2. **No push notifications.** FCM requires a Firebase project and server keys.
   The app states in Settings that notifications are in-app only.
3. **No realtime.** Tournament and match screens use refresh-on-open plus an
   explicit pull-to-refresh action. Realtime WebSocket support was not added.
4. **No local builds.** Compile feedback latency is one CI run (~5-8 minutes).
5. **Paid flows are incomplete by design.** Tables, flags and ledger schema exist,
   but no gateway is integrated and the UI never claims otherwise.

## Decision log

- Kotlin + Compose + Material 3 + Hilt + Room + Retrofit/OkHttp.
- Hand-written Retrofit interfaces for Supabase Auth, PostgREST and Storage;
  the `supabase-kt`/Ktor stack was removed to cut build memory and dependency risk.
- Room is used only as an offline cache for the tournament catalogue.
- Session access tokens stay in memory; refresh tokens in app-private DataStore.
- Screens are grouped by feature package and share one navigation graph.
