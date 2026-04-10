# AGENTS.md — AI Developer Rules

Read this before making ANY changes to this project or any Android project in this account.

---

## RULE 1 — APK Signing: never regenerate the keystore

`app/keystore/app.jks` is committed to the repo **intentionally**.

- Both `debug` and `release` buildTypes **must** use `signingConfigs.release`
- This ensures every APK is signed with the same key → Android allows update without uninstall
- If you regenerate or replace the keystore, existing installs will get "App not installed" on update
- When creating a new project from this template: keep the key or generate once and commit

```groovy
signingConfigs {
    release {
        storeFile     file("keystore/app.jks")
        storePassword "AppTemplate2024"
        keyAlias      "appkey"
        keyPassword   "AppTemplate2024"
    }
}
buildTypes {
    debug   { signingConfig signingConfigs.release }
    release { signingConfig signingConfigs.release }
}
```

---

## RULE 2 — Bilingual: every app must support VI and EN

- Default language: Vietnamese (`isVietnamese = true`)
- UI must include a `🇻🇳 VI / 🇬🇧 EN` toggle button
- When toggled, **all visible strings must refresh immediately** — no restart required
- Use the `vi(vi, en)` helper pattern:

```kotlin
private var isVietnamese = true
private fun vi(vi: String, en: String) = if (isVietnamese) vi else en

// Define all strings as computed properties (get()) so they auto-switch
private val sTitle  get() = vi("Tiêu đề", "Title")
private val sError  get() = vi("Lỗi mạng.", "Network error.")
```

- `toggleLanguage()` must update: lang button, ALL text views, date formats, hints, error messages
- Date formatting must respect locale:
  - VI: `SimpleDateFormat("EEE, dd MMM yyyy", Locale("vi", "VN"))`
  - EN: `SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH)`

---

## RULE 3 — GitHub Actions: standard CI workflow

Every project must have `.github/workflows/build.yml` that:

1. Triggers on every push to `main` and on `workflow_dispatch`
2. Builds **debug APK** with `./gradlew assembleDebug --no-daemon`
3. Uploads APK to **GitHub Releases** via `softprops/action-gh-release@v2`
4. Tag format: `v{YYYY.MM.DD}-build{RUN_NUMBER}`
5. APK filename: `{AppName}-{YYYY.MM.DD}-build{N}.apk`

**No secrets needed** — signing is handled by `build.gradle` using the committed keystore.
**No separate release signing step** — one job, one APK output.

---

## RULE 4 — UI: programmatic only, no XML layouts

- All UI is built in Kotlin using `LinearLayout`, `TextView`, etc.
- No `layout/*.xml` files
- No Jetpack Compose (unless the project explicitly opts in)
- Dark theme by default — use the color constants pattern from `MainActivity.kt`

---

## RULE 5 — Geocoding (if applicable)

If the app needs reverse geocoding (address from lat/lng):

- Use **Nominatim (OpenStreetMap)** — free, no API key
- Always set `User-Agent: {AppName}/1.0` header
- Use `zoom=18&addressdetails=1` for street-level precision
- **Parse the `address` JSON object** — never split `display_name` by comma
- Fetch two responses in parallel: local lang + `Accept-Language: en`
- Display: `local  (english)` — omit english if same as local

Field priority:
```
road     → road | pedestrian | footway | path
suburb   → suburb | neighbourhood | quarter | hamlet
district → city_district | district | county | borough
city     → city | town | village | municipality
state    → state | province | region
```

---

## Checklist for every new feature

- [ ] All new UI strings have VI + EN versions via `vi()` helper
- [ ] `toggleLanguage()` includes the new strings
- [ ] Build passes: `./gradlew assembleDebug`
- [ ] APK installs over existing without uninstalling first
- [ ] No new secrets or API keys added (prefer free/no-key APIs)

---

## What NOT to do

- ❌ Regenerate or replace `app/keystore/app.jks`
- ❌ Add XML layout files
- ❌ Hardcode Vietnamese-only or English-only strings
- ❌ Create separate signing configs for debug vs release
- ❌ Add GitHub Actions secrets for signing
- ❌ Split Nominatim `display_name` by comma
- ❌ Use Google Maps SDK (OSMDroid works without API key)
