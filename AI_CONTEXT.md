# AI_CONTEXT.md — Template Project Context

This is a **GitHub Template Repository** for Android apps.
When a new project is created from this template, this file describes what's already set up.

---

## What's included

| File / Folder | Purpose |
|---|---|
| `app/keystore/app.jks` | Fixed release signing key — committed intentionally |
| `app/build.gradle` | Signing config wired for both debug + release |
| `.github/workflows/build.yml` | CI: push to main → build APK → GitHub Release |
| `app/src/main/java/com/myapp/app/MainActivity.kt` | Boilerplate with VI/EN toggle |
| `AGENTS.md` | Mandatory rules for AI developers |

---

## After creating a project from this template

1. **Rename package** — find/replace `com.myapp.app` → `com.yourcompany.yourapp`
2. **Rename app** — update `app_name` in `strings.xml` and `rootProject.name` in `settings.gradle.kts`
3. **Rename APK** — update `APP_NAME` in `.github/workflows/build.yml`
4. **Update `AndroidManifest.xml`** namespace if needed
5. **Keep the keystore** — do not regenerate `app/keystore/app.jks`

---

## Design decisions (for AI context)

### Signing
Every build uses `app/keystore/app.jks` with password `AppTemplate2024`, alias `appkey`.
Same key for both debug and release. Prevents "App not installed" on updates.

### Bilingual
`MainActivity` ships with `isVietnamese` flag and `vi(vi, en)` helper.
Toggle button `🇻🇳 VI / 🇬🇧 EN` wired to `toggleLanguage()`.
All strings are `get()` properties so they switch immediately.

### CI
One workflow, one job. No secrets. Signing comes from committed keystore.
Every push to `main` = new APK in GitHub Releases.

---

## APIs preferred (no key needed)

| Purpose | Service |
|---|---|
| Map display | OSMDroid + MAPNIK tiles |
| Timezone by coordinates | timeapi.io |
| Reverse geocoding | Nominatim (OpenStreetMap) |
| HTTP client | OkHttp 4.x |
