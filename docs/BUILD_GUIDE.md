# Build Guide

## 1. Hard rule

This project is built **only** through GitHub Actions. Do not run
`./gradlew assembleDebug` or any APK build on the low-RAM development phone.
CI is the source of truth for compilation, tests, lint and packaging.

## 2. CI pipeline

Workflow: `.github/workflows/android-ci.yml`

Triggers: pushes to `main` and `feature/hmx-arena-android-production`, plus
manual `workflow_dispatch`.

Steps:

1. Checkout.
2. JDK 17 (Temurin) with Gradle cache.
3. Install Android SDK platform 36 / build tools.
4. `./gradlew testDebugUnitTest`.
5. `./gradlew lintDebug` (`abortOnError = true`).
6. `./gradlew assembleDebug`.
7. Upload `app-debug.apk` as `hmx-arena-debug-apk`.
8. On failure, upload test reports and build logs.

## 3. Getting the APK

1. Open the repository's **Actions** tab.
2. Select the latest successful **Android CI** run.
3. Download `hmx-arena-debug-apk` from **Artifacts**.
4. Unzip and install `app-debug.apk`
   (`adb install -r app-debug.apk` or a file manager).

The debug build uses application id `com.hmx.arena.debug`, so it can coexist
with a release install.

## 4. Supabase configuration

Project: `ahsvxxhaowwhqermruhz`
URL: `https://ahsvxxhaowwhqermruhz.supabase.co`
Client key: publishable key in `gradle.properties`.

`app/build.gradle.kts` reads `HMX_SUPABASE_URL` and `HMX_SUPABASE_KEY` into
`BuildConfig`. Only the publishable key belongs in the client. Never add the
`service_role` key.

### Required dashboard steps

1. **Auth → URL configuration**
   - Site URL: `https://hmxarena.app` (or the real web property).
   - Redirect allowlist: add `hmxarena://auth/reset`.
2. **Auth → Email templates**
   - Confirmation template must include `{{ .Token }}` (the app asks for a
     6-digit code) or a confirmation link that returns to the app.
   - Recovery template must emit either a token-hash link
     (`{{ .TokenHash }}` with `type=recovery`) or an implicit flow link that
     produces `#access_token=...&refresh_token=...&type=recovery`.
   - The app supports both forms; misconfigured templates will produce an
     "invalid or expired link" dialog.
3. **Auth → Providers** — email enabled; phone disabled.
4. **Storage** — buckets `avatars`, `team-logos`, `tournament-banners` public;
   `result-evidence` private. Migration `0004` creates them; verify in dashboard.
5. **Database** — apply `supabase/migrations/0001`…`0008` in order.
6. **RLS suite** — run `supabase/tests/rls_test.sql` and confirm every row
   returns `pass`.

## 5. Repository layout

```
app/                      Android application
  src/main/java/com/hmx/arena/
    core/                 design system, models, utils
    data/                 remote, local, repositories
    di/                   Hilt modules
    ui/                   feature screens and navigation
  src/test/               JVM unit tests
supabase/migrations/      ordered SQL migrations
supabase/tests/           RLS regression suite
docs/                     this documentation
.github/workflows/        CI
```

## 6. Version stack

| Tool | Version |
| --- | --- |
| Gradle | 8.14.3 |
| AGP | 8.13.2 |
| Kotlin | 2.1.21 |
| KSP | 2.1.21-2.0.2 |
| Compose BOM | 2025.05.01 |
| Hilt | 2.56.2 |
| Room | 2.7.1 |
| Navigation Compose | 2.9.0 |
| JDK | 17 |
| compile/target SDK | 36 |
| min SDK | 26 |

## 7. Local source checks (no APK)

If needed, lightweight static checks can run without Gradle:

```
git status
rg "TODO|FIXME" app/src
rg "service_role" .
```

Do not run Gradle on the phone. If a local build is unavoidable later, use a
machine with at least 8 GB RAM and `-Xmx3g`.

## 8. Release notes

- No signing config is committed. A release build requires a keystore in CI
  secrets and a `signingConfig` block; that is intentionally not present.
- `isMinifyEnabled = true` and `isShrinkResources = true` for release; verify
  R8 rules before shipping.
- Paid features must stay disabled until compliance approval; check
  `app_config` before any release that changes money flows.
