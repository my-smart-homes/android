# Quick Reference: Android Companion Sync Guide

**Status:** Synced (5 custom patches ahead of upstream)
**Date:** March 9, 2026
**Current Version:** 2026.3.3

## Quick Status

| Field | Value |
|-------|-------|
| Repository | android (my-smart-homes fork) |
| Upstream | home-assistant/android |
| Commits Ahead | 5 (custom patches) |
| Commits Behind | 0 |
| Custom Changes | App ID, branding, icons, CI/CD, Firebase login |

## Quick Sync Commands

```bash
# Fetch upstream
git fetch upstream

# Rebase custom patches onto latest upstream
git checkout main
git rebase upstream/main

# If conflicts, resolve manually keeping:
# - com.yildiz.MySmartHomes app ID
# - "My Smart Homes" branding in strings
# - Custom icons
# - GitHub release workflow

# Push (force needed after rebase)
git push origin main --force-with-lease
```

## Critical Custom Changes to Preserve

| File | Change | Why Critical |
|------|--------|-------------|
| `build-logic/.../AndroidApplicationConventionPlugin.kt` | App ID -> `com.yildiz.MySmartHomes` | App identity |
| `common/src/main/res/values/strings.xml` | 191 strings rebranded | User-facing name |
| `app/src/*/res/mipmap-*/ic_launcher*.webp` | Custom icons | Visual branding |
| `.github/workflows/onPush.yml` | GitHub releases only | Distribution |
| `.github/workflows/sync_upstream.yml` | Auto sync | Maintenance |
| `app/src/*/kotlin/.../onboarding/login/*` | Firebase login flow | MSH feature |
| `app/src/main/kotlin/.../update/*` | Version checker + update dialog | MSH feature |
| `gradle/libs.versions.toml` | Firebase Auth & Firestore deps | Login deps |

## Conflict Resolution Quick Guide

### If you see conflicts in:

**`common/src/main/res/values/strings.xml`**
```xml
<!-- KEEP custom branding, accept new upstream strings -->
<!-- Replace "Home Assistant" with "My Smart Homes" in any new strings -->
<string name="app_name">My Smart Homes</string>
```

**`build-logic/.../AndroidApplicationConventionPlugin.kt`**
```kotlin
// KEEP THIS:
private const val APPLICATION_ID = "com.yildiz.MySmartHomes"
// Accept other upstream changes (SDK versions, etc.)
```

**`.github/workflows/onPush.yml`**
```yaml
# KEEP: GitHub release mechanism
# KEEP: Play Store sections commented out
# UPDATE: Action versions from upstream
```

**Icon files (`mipmap-*/ic_launcher*.webp`)**
```
# KEEP: Custom icons (ours)
# If upstream adds NEW icon sizes, create custom versions for those too
```

## Post-Sync Verification

```bash
# Build check
./gradlew assembleDebug

# Test check
./gradlew test

# Style check
./gradlew ktlintCheck :build-logic:convention:ktlintCheck --continue

# Verify branding (check strings file)
grep "My Smart Homes" common/src/main/res/values/strings.xml | head -5

# Verify app ID
grep "APPLICATION_ID" build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt
```

## Rollback if Needed

```bash
# Option 1: Use reflog to find pre-sync state
git reflog
git reset --hard <pre-sync-commit>

# Option 2: Re-fetch and start over
git fetch upstream
git reset --hard upstream/main
# Then re-apply patches manually:
git cherry-pick 801680a64  # CI/CD changes
git cherry-pick 5c4b08698  # Icon update
git cherry-pick 40f78d1bf  # Name change
git cherry-pick 7b9092740  # String fix
git cherry-pick 7780aa51b  # Firebase login
```

## Detailed Documentation

- Full sync details: [SYNC_DOCUMENTATION.md](SYNC_DOCUMENTATION.md)
- Custom changes analysis: [CUSTOM_CHANGES.md](CUSTOM_CHANGES.md)
- Priority & status: [SYNC_PRIORITY.md](SYNC_PRIORITY.md)

## TODO After Each Sync

- [ ] Verify build compiles for all variants
- [ ] Run test suite
- [ ] Check no "Home Assistant" leaks in user-facing strings
- [ ] Verify icons are correct
- [ ] Test on device if releasing
- [ ] Update version tag if needed

## Important URLs

| Resource | URL |
|----------|-----|
| Fork | https://github.com/my-smart-homes/android |
| Upstream | https://github.com/home-assistant/android |
| Container Registry | ghcr.io/my-smart-homes/* |

**Remember: Always keep app ID as `com.yildiz.MySmartHomes` and branding as "My Smart Homes"!**

---
**Last Updated:** March 9, 2026
