# Android Companion App - Sync Documentation

**Date:** March 9, 2026
**Repository:** my-smart-homes/android
**Upstream:** home-assistant/android
**Sync Type:** Upstream rebase/merge from home-assistant/android

## 1. Repository Status

### Current State

| Field | Value |
|-------|-------|
| Current Version | 2026.3.3 |
| Upstream Version | 2026.3.3 |
| Branch | main |
| Commits Ahead | 5 (custom patches) |
| Commits Behind | 0 (fully synced) |
| Custom Files Changed | 89 |

### Fork-Specific Commits

The following custom commits exist in the fork (not in upstream):

| Commit | Message | Date | Files |
|--------|---------|------|-------|
| `7780aa51b` | add: firebase login | Mar 9, 2026 | 27 |
| `7b9092740` | fix: rebase missing string after app_name | Mar 2, 2026 | 1 |
| `40f78d1bf` | patch: name change | Feb 14, 2026 | 3 |
| `5c4b08698` | patch: icon update | Feb 14, 2026 | 55 |
| `801680a64` | patch: release only on github | Jan 6, 2026 | 3+ |

### Upstream Remote
- **URL:** `https://github.com/home-assistant/android.git`
- **Status:** Added and fetched
- **Latest upstream commit:** `89600a769` (Update github/codeql-action action to v4.32.5)

---

## 2. Custom Modifications Summary

### Application Identity
- **App ID:** `com.yildiz.MySmartHomes` (was `io.homeassistant.companion.android`)
- **App Name:** "My Smart Homes" (was "Home Assistant")
- **Short Name:** "MSH" (was "HA")

### Visual Branding
- 55 icon files replaced with custom branding
- Launcher icons (all DPI: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- Round launcher icons
- Debug variant icons
- Notification icons modified
- Fastlane metadata icon

### String Rebranding
- 191 strings in `common/src/main/res/values/strings.xml` updated
- All user-facing "Home Assistant" references changed to "My Smart Homes"
- Includes permissions, notifications, settings, error messages

### CI/CD Changes
- GitHub Releases enabled (APK distribution)
- Play Store publishing disabled
- Firebase App Distribution disabled
- Amazon Appstore disabled
- Lokalise translation sync disabled
- Custom `sync_upstream.yml` workflow added

### Firebase Login Feature (NEW)
- Custom login flow with Firebase Auth & Firestore (full flavor)
- Stub implementation for minimal flavor (no Firebase)
- 27 files added/modified, 1,356 lines of new code
- Includes: LoginScreen, LoginViewModel, CryptoUtil, MshAutoWifiManager, MshSessionHolder, ServerTimeFetchService
- In-app update dialog and version checker
- Integrated into onboarding navigation and connection screen

---

## 3. Upstream Technology Stack

The upstream Home Assistant Android app includes:

### App Modules
- **`:app`** - Main mobile application (min SDK 23)
- **`:automotive`** - Android Automotive version
- **`:wear`** - Wear OS application
- **`:common`** - Shared code across all apps
- **`:testing-unit`** - Shared test utilities
- **`:lint`** - Custom lint rules

### App Flavors (`:app` and `:automotive` only)
- **`full`** - Includes Google Play Services (location, FCM, Wear OS comms)
- **`minimal`** - FOSS version without Google Play Services

### Key Technologies
- Kotlin (100%)
- Jetpack Compose (UI)
- Hilt (DI)
- Kotlin Coroutines and Flow
- Room (database)
- Retrofit + OkHttp (networking)
- Kotlinx.serialization
- Jetpack Navigation Compose

---

## 4. Potential Conflicts During Future Syncs

### High Risk Areas

#### `common/src/main/res/values/strings.xml`
- **Risk:** HIGH
- **Reason:** Upstream frequently adds/modifies strings
- **Strategy:** Accept new upstream strings, apply "My Smart Homes" branding to any new "Home Assistant" references

#### `.github/workflows/onPush.yml`
- **Risk:** MEDIUM-HIGH
- **Reason:** Upstream updates CI workflow regularly
- **Strategy:** Keep custom GitHub release mechanism, update upstream action versions

#### `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`
- **Risk:** MEDIUM
- **Reason:** Upstream may change build configuration
- **Strategy:** Preserve `APPLICATION_ID`, accept other changes

#### `app/src/main/kotlin/.../onboarding/` (OnboardingNavigation, ConnectionScreen, ConnectionViewModel)
- **Risk:** MEDIUM
- **Reason:** Upstream may refactor onboarding flow
- **Strategy:** Preserve login route integration, adapt to upstream navigation changes

#### `gradle/libs.versions.toml`
- **Risk:** LOW-MEDIUM
- **Reason:** Upstream updates dependency versions frequently
- **Strategy:** Keep Firebase Auth & Firestore entries, accept other version bumps

### Low Risk Areas
- Icon files (upstream rarely changes launcher icons)
- `sync_upstream.yml` (our addition, no upstream equivalent)
- Lint registry (rarely touched)
- All `login/` files (entirely custom, no upstream equivalent)
- `update/` files (entirely custom, no upstream equivalent)

---

## 5. Sync Process

### Automated Sync (Preferred)
The repository includes `.github/workflows/sync_upstream.yml` which:
1. Fetches upstream main branch
2. Creates backup tag
3. Attempts fast-forward or rebase
4. Cherry-picks custom patches
5. Handles workflow file permission conflicts

### Manual Sync Process
```bash
# 1. Fetch upstream
git fetch upstream

# 2. Create backup
git checkout -b backup-pre-sync-$(date +%Y%m%d)
git checkout main

# 3. Check what's new
git log --oneline main...upstream/main

# 4. Rebase custom patches onto upstream
git rebase upstream/main

# 5. Resolve any conflicts (see CUSTOM_CHANGES.md for guidance)

# 6. Verify build
./gradlew assembleDebug
./gradlew test

# 7. Push
git push origin main --force-with-lease
```

---

## 6. Post-Sync Verification

### Build Verification
```bash
# Debug build
./gradlew assembleDebug

# Run tests
./gradlew test

# Code style
./gradlew ktlintCheck :build-logic:convention:ktlintCheck --continue

# Lint
./gradlew lint --continue
```

### Branding Verification
- [ ] App name shows "My Smart Homes" on launcher
- [ ] Custom icons display correctly
- [ ] Settings/About shows correct branding
- [ ] Notifications reference "My Smart Homes"
- [ ] No "Home Assistant" leaks in user-facing UI

### Firebase Login Verification
- [ ] Login screen accessible from onboarding/connection
- [ ] Firebase Auth works (full flavor)
- [ ] Minimal flavor gracefully handles no Firebase
- [ ] Session persistence works
- [ ] Update dialog shows when new version available

### CI/CD Verification
- [ ] GitHub Actions workflow runs successfully
- [ ] APKs are generated for all variants
- [ ] GitHub Release is created with correct artifacts

---

## 7. Rollback Plan

If issues arise after sync:

```bash
# Option 1: Reset to backup branch
git checkout backup-pre-sync-YYYYMMDD
git branch -D main
git checkout -b main
git push origin main --force

# Option 2: Use backup tag (if created by sync workflow)
git reset --hard <backup-tag>
git push origin main --force

# Option 3: Revert to last known good
git reflog  # find the good state
git reset --hard <good-commit>
```

---

## 8. Related Repositories

This sync is part of a larger smart home project:

| Repository | Status | Notes |
|-----------|--------|-------|
| core-updated | Complete | Already synced |
| frontend-updated | Complete | Already synced |
| operating-system | Pending | See OS sync-docs |
| supervisor | Ready | See supervisor sync-docs |
| **android** | **Current** | **This repository** |

### Recommended Sync Order
1. Core
2. Frontend
3. Operating System
4. Supervisor
5. **Android** (current)

---

## 9. Notes

### Version Scheme
The app uses `org.ajoberstar.reckon` for automatic versioning based on git tags. Current version `2026.3.3` is derived from the latest upstream tag.

### Namespace vs App ID
- **App ID (installed):** `com.yildiz.MySmartHomes`
- **Code namespace:** `io.homeassistant.companion.android` (unchanged)
- This allows the fork to install alongside the official app while keeping code changes minimal.

### Distribution
- APKs distributed via GitHub Releases
- No Play Store, Firebase, or Amazon distribution
- Supports: full, minimal, wear, automotive-full, automotive-minimal variants

---

**Document Version:** 1.0
**Last Updated:** March 9, 2026
