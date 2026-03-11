# Custom Fork Changes Documentation

**Repository:** my-smart-homes/android
**Date:** March 9, 2026
**Purpose:** Document custom modifications made to fork on top of upstream

## Overview

This document details the 5 custom commits in the fork that differ from upstream. These changes rebrand the Home Assistant Companion Android app to "My Smart Homes" with custom application ID, icons, distribution through GitHub releases instead of Play Store, and a custom Firebase-based login flow.

## Custom Commits Summary

### 1. Commit 3343f6f86 (January 6, 2026)
**Message:** "patch: release only on github"
**Author:** Custom fork maintainer

**Files Modified:**
- `.github/workflows/onPush.yml` (major changes)
- `.github/workflows/release.yml` (disabled)
- `.github/workflows/sync_upstream.yml` (added)

**Purpose:** Reconfigure CI/CD for GitHub-only releases

**Detailed Changes:**

#### `.github/workflows/onPush.yml`
- **Removed/Commented Out:**
  - Fastlane/Ruby setup for Play Store deployment
  - Lokalise translation downloads (`lokalise2` CLI)
  - Firebase App Distribution integration
  - Amazon Appstore integration
  - Play Store publishing job entirely
- **Enabled:**
  - GitHub Release creation using `softprops/action-gh-release`
- **Release Artifacts (APKs instead of AABs):**
  - `app-full-release.apk`
  - `app-minimal-release.apk`
  - `wear-release.apk`
  - `automotive-full-release.apk`
  - `automotive-minimal-release.apk`

#### `.github/workflows/release.yml`
- Completely disabled (commented out - references old Play Store promotion workflow)

#### `.github/workflows/sync_upstream.yml` (NEW FILE)
- Custom workflow to automatically sync with upstream main branch
- Handles conflict resolution with cherry-pick strategy
- Creates backup tags before syncing
- Supports both fast-forward and rebase operations
- Handles workflow file permission conflicts gracefully

**Preservation:** REQUIRED - Core distribution mechanism

---

### 2. Commit c846f36a9 (February 14, 2026)
**Message:** "patch: icon update"
**Author:** Custom fork maintainer

**Files Modified:** 55 files

**Icon files replaced across all DPI variants:**
- `app/src/debug/res/mipmap-*/ic_launcher.webp`
- `app/src/debug/res/mipmap-*/ic_launcher_round.webp`
- `app/src/main/res/mipmap-*/ic_launcher.webp`
- `app/src/main/res/mipmap-*/ic_launcher_round.webp`
- `app/src/main/res/drawable/ic_stat_ic_notification.xml` (removed)
- `app/src/main/res/drawable/ic_stat_ic_notification_blue.xml` (removed)
- `fastlane/metadata/android/en-US/images/icon.png`

**Compose screen icon references updated:**
- `LinkActivity.kt`
- `LoadingScreen.kt`
- `ConnectionScreen.kt`
- `ServerDiscoveryScreen.kt`
- `WelcomeScreen.kt`

**Purpose:** Replace all app icons with custom "My Smart Homes" branding

**Preservation:** REQUIRED - Visual brand identity

---

### 3. Commit 45bc0a515 (February 14, 2026)
**Message:** "patch: name change"
**Author:** Custom fork maintainer

**Files Modified:**
- `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`
- `common/src/main/res/values/strings.xml` (191 strings changed)
- `lint/src/main/kotlin/io/homeassistant/lint/LintRegistry.kt`

**Purpose:** Full rebranding from "Home Assistant" to "My Smart Homes"

**Detailed Changes:**

#### Application ID Change
**File:** `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`
```kotlin
// Before (upstream)
private const val APPLICATION_ID = "io.homeassistant.companion.android"

// After (fork)
private const val APPLICATION_ID = "com.yildiz.MySmartHomes"
```
**Impact:** App installs as a separate app from official Home Assistant
**Preservation:** REQUIRED - Core identity change

#### String Rebranding
**File:** `common/src/main/res/values/strings.xml`

191 strings updated, examples:
```xml
<!-- Before -->
<string name="app_name">Home Assistant</string>
<string name="app_name_short">HA</string>

<!-- After -->
<string name="app_name">My Smart Homes</string>
<string name="app_name_short">MSH</string>
```

Other rebranded strings include:
- "Home Assistant Cloud" -> "My Smart Homes Cloud"
- Permission/background access messages
- Gesture descriptions
- Notification text
- Settings labels
- Error messages referencing the app name

**Preservation:** REQUIRED - User-facing brand identity

#### Lint Registry
**File:** `lint/src/main/kotlin/io/homeassistant/lint/LintRegistry.kt`
- Minor update to lint vendor information

**Preservation:** REQUIRED - Matches new branding

---

### 4. Commit 7b9092740 (March 2, 2026)
**Message:** "fix: rebase missing string after app_name"
**Author:** naimurhasan

**Files Modified:**
- `common/src/main/res/values/strings.xml` (1 line added)

**Purpose:** Fix a missing string entry that was lost during a rebase operation. The string immediately after `app_name` was dropped during conflict resolution.

**Preservation:** REQUIRED - Ensures string resources are complete

---

### 5. Commit 7780aa51b (March 9, 2026) - HEAD
**Message:** "add: firebase login"
**Author:** naimurhasan

**Files Modified:** 27 files (1,356 insertions, 24 deletions)

**Purpose:** Add a custom Firebase-based login flow for My Smart Homes, including encrypted credential handling, auto WiFi configuration, session management, version checking, and an update dialog.

**Detailed Changes:**

#### New Feature Files (Full Flavor)
- `app/src/full/kotlin/.../onboarding/login/FullLoginModule.kt` - Hilt DI module for full flavor login
- `app/src/full/kotlin/.../onboarding/login/FullLoginRepositoryImpl.kt` - Firebase-backed login implementation (165 lines)

#### New Feature Files (Minimal Flavor)
- `app/src/minimal/kotlin/.../onboarding/login/MinimalLoginModule.kt` - Hilt DI module for minimal flavor
- `app/src/minimal/kotlin/.../onboarding/login/MinimalLoginRepositoryImpl.kt` - Stub implementation (no Firebase)

#### New Feature Files (Main Source Set)
- `app/src/main/kotlin/.../onboarding/login/LoginRepository.kt` - Login repository interface
- `app/src/main/kotlin/.../onboarding/login/LoginScreen.kt` - Compose login UI (290 lines)
- `app/src/main/kotlin/.../onboarding/login/LoginUiState.kt` - UI state sealed class
- `app/src/main/kotlin/.../onboarding/login/LoginViewModel.kt` - ViewModel for login flow
- `app/src/main/kotlin/.../onboarding/login/CryptoUtil.kt` - AES encryption utility for credentials
- `app/src/main/kotlin/.../onboarding/login/MshAutoWifiManager.kt` - Auto WiFi configuration manager
- `app/src/main/kotlin/.../onboarding/login/MshSessionHolder.kt` - Session state holder
- `app/src/main/kotlin/.../onboarding/login/ServerTimeFetchService.kt` - Server time sync service
- `app/src/main/kotlin/.../onboarding/login/MshSecrets.kt` - Secrets/constants (referenced from IDE)
- `app/src/main/kotlin/.../onboarding/login/navigation/LoginNavigation.kt` - Navigation graph integration

#### New Update Feature
- `app/src/main/kotlin/.../update/UpdateDialog.kt` - In-app update dialog
- `app/src/main/kotlin/.../update/VersionChecker.kt` - Version checking against remote

#### Modified Files
- `app/src/main/kotlin/.../onboarding/OnboardingNavigation.kt` - Integrated login route
- `app/src/main/kotlin/.../onboarding/connection/ConnectionScreen.kt` - Added login button
- `app/src/main/kotlin/.../onboarding/connection/ConnectionViewModel.kt` - Login state handling (82 lines added)
- `app/src/main/kotlin/.../webview/WebViewActivity.kt` - Session/update integration
- `app/src/main/res/drawable/my_smart_home_icon.xml` - Custom vector icon
- `common/src/main/res/values/strings.xml` - 14 new login-related strings
- `build-logic/.../AndroidApplicationDependenciesConventionPlugin.kt` - Added Firebase dependencies
- `gradle/libs.versions.toml` - Added Firebase Auth & Firestore versions
- `app/gradle.lockfile` - Updated dependency locks
- `automotive/gradle.lockfile` - Updated dependency locks
- `keystore.properties` - Keystore configuration
- `.gitignore` - Added keystore exclusions

**Key Architecture:**
- Uses flavor-based DI: `FullLoginRepositoryImpl` (Firebase) vs `MinimalLoginRepositoryImpl` (stub)
- AES-encrypted credential storage via `CryptoUtil`
- Auto WiFi setup via `MshAutoWifiManager`
- Session management via `MshSessionHolder`
- Server time synchronization for time-sensitive operations
- In-app version checking and update prompts

**Preservation:** REQUIRED - Core MSH feature differentiating from upstream

---

## Infrastructure Dependencies

The custom fork relies on:

### Build Infrastructure
- **GitHub Actions** for CI/CD (no Play Store or Amazon)
- **GitHub Releases** for APK distribution
- **Upstream sync workflow** for staying current
- **Firebase Auth & Firestore** for MSH login (full flavor only)

### Custom Assets Required
- Custom launcher icons (all DPI variants, debug + release)
- Custom fastlane metadata icon
- Custom notification icons
- Custom vector icon (`my_smart_home_icon.xml`)

### Application Identity
- **Package:** `com.yildiz.MySmartHomes`
- **Namespace:** `io.homeassistant.companion.android` (unchanged for code compatibility)

### Firebase Dependencies
- Firebase Authentication (login flow)
- Firebase Firestore (credential/session storage)
- Configured in `gradle/libs.versions.toml` and `AndroidApplicationDependenciesConventionPlugin.kt`

---

## Merge Strategy Recommendations

### Option 1: Cherry-Pick Patches (RECOMMENDED)
The current approach - maintain 5 clean patches on top of upstream:

```bash
git fetch upstream
git checkout main
git rebase upstream/main
# Resolve any conflicts in the 5 patch commits
```

**Pros:**
- Clean, minimal diff from upstream
- Easy to see what's custom
- Automated via `sync_upstream.yml`

**Cons:**
- Rebase conflicts possible when upstream touches same files

### Option 2: Merge Strategy
```bash
git fetch upstream
git merge upstream/main
```

**Pros:**
- Preserves full history
- No rebase conflicts

**Cons:**
- Messier history over time

---

## Pre-Merge Checklist

Before syncing with upstream, verify:

### Branding Integrity
- [ ] App name still shows "My Smart Homes" everywhere
- [ ] Icons are not overwritten by upstream changes
- [ ] Application ID remains `com.yildiz.MySmartHomes`
- [ ] All 191+ strings still correctly rebranded
- [ ] Login-related strings (14) present

### Firebase Login
- [ ] Firebase Auth dependency resolves
- [ ] Firebase Firestore dependency resolves
- [ ] Login screen renders correctly
- [ ] Full flavor login works with Firebase
- [ ] Minimal flavor gracefully stubs login

### CI/CD
- [ ] GitHub release workflow still functional
- [ ] Play Store workflows remain disabled
- [ ] Sync workflow still works

### Build Verification
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew test` passes
- [ ] `./gradlew ktlintCheck` passes

---

## Conflict Resolution Guidelines

### Expected Conflicts

**`common/src/main/res/values/strings.xml`**
- Most likely conflict area (upstream adds/modifies strings)
- Resolution: Accept upstream string additions, ensure "Home Assistant" -> "My Smart Homes" in new strings

**`build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`**
- Upstream may update SDK versions or build config
- Resolution: Keep `APPLICATION_ID = "com.yildiz.MySmartHomes"`, accept other changes

**`.github/workflows/onPush.yml`**
- Upstream frequently updates CI workflows
- Resolution: Keep GitHub release mechanism, update action versions from upstream

**Icon files**
- Upstream may add new icon variants
- Resolution: Keep custom icons, add any new size variants with custom branding

**`gradle/libs.versions.toml`**
- Upstream may update dependency versions
- Resolution: Keep Firebase Auth & Firestore entries, accept other changes

**`app/src/main/kotlin/.../onboarding/` directory**
- Upstream may modify onboarding flow
- Resolution: Preserve login integration in `OnboardingNavigation.kt` and `ConnectionScreen.kt`

### Conflict Resolution Priority
- HIGH: Application ID - Must remain `com.yildiz.MySmartHomes`
- HIGH: App name strings - Must say "My Smart Homes"
- HIGH: Icons - Must use custom branding
- HIGH: Firebase login flow - All login/* files must be preserved
- MEDIUM: CI/CD workflows - Keep GitHub release, update action versions
- MEDIUM: Onboarding navigation - Keep login route integration
- LOW: Lint registry - Update to match branding

---

## Technical Debt

### Current Issues
1. **Namespace mismatch** - App ID is `com.yildiz.MySmartHomes` but code namespace is `io.homeassistant.companion.android`
   - Not critical but could cause confusion
   - Changing namespace would require massive refactor

2. **Hardcoded strings** - Some compose screens reference icons directly
   - Could break if upstream restructures icon resources

3. **Translation coverage** - Lokalise sync disabled
   - Only English strings are rebranded
   - Other languages may still show "Home Assistant"

4. **Keystore in repo** - `keystore.properties` committed
   - Should be in `.gitignore` for security
   - Consider using GitHub Secrets for CI/CD signing

### Future Improvements
- Consider making brand name configurable via build config
- Add automated checks to ensure no "Home Assistant" leaks in user-facing strings
- Set up alternative translation management
- Add unit tests for login flow (CryptoUtil, LoginViewModel, SessionHolder)
- Consider migrating login credentials to EncryptedSharedPreferences/DataStore

---

**Document Version:** 1.0
**Last Updated:** March 9, 2026
**Status:** Current and complete
