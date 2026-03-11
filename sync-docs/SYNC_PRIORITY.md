# Repository Sync Priority & Status - Android

**Date:** March 9, 2026
**Project:** My Smart Homes - Home Assistant Custom Fork

## Current Status Overview

| Repository | Status | Current Version | Action Needed |
|-----------|--------|----------------|---------------|
| core-updated | Complete | Updated | - |
| frontend-updated | Complete | Updated | - |
| operating-system | Pending | 13.7.8 | Sync first |
| supervisor | Ready | 2024.10.04 | Sync second |
| **android** | **Synced** | **2026.3.3** | **Maintain patches** |

## Android Position in Sync Order

### Why Android Can Be Synced Independently

Unlike the supervisor which depends on the OS, the Android app is largely independent:

1. **No hard dependency on other repos** - The app connects to any Home Assistant server via REST/WebSocket
2. **Server version checks are built-in** - The app already handles different server versions gracefully
3. **Focused custom changes** - 5 patch commits (branding + CI/CD + Firebase login)
4. **Automated sync workflow** - `sync_upstream.yml` handles routine syncing

### When to Sync Android

- **Routinely:** Can sync with upstream at any time (weekly/monthly recommended)
- **Before releases:** Always sync before creating a new release
- **After major upstream releases:** Check for breaking changes

## Current Repository State

### Android (my-smart-homes/android)
- **Branch:** main
- **Base Version:** 2026.3.3 (matches upstream)
- **Commits Ahead:** 5 (custom patches)
- **Commits Behind:** 0

**Custom Patches:**
1. `801680a64` - CI/CD: GitHub releases only
2. `5c4b08698` - Icon update (custom branding)
3. `40f78d1bf` - Name change (My Smart Homes)
4. `7b9092740` - Fix: rebase missing string after app_name
5. `7780aa51b` - Add: Firebase login (27 files, 1356 insertions)

**Documentation:**
- SYNC_DOCUMENTATION.md (comprehensive)
- CUSTOM_CHANGES.md (detailed analysis)
- SYNC_QUICKREF.md (quick reference)
- This file (priority & status)

## Step-by-Step Sync Action Plan

### Phase 1: Routine Sync (Automated)
The `sync_upstream.yml` workflow handles this automatically:
```bash
# Or manually:
git fetch upstream
git rebase upstream/main
# Resolve conflicts if any
git push origin main --force-with-lease
```

### Phase 2: Post-Sync Verification
```bash
# 1. Build check
./gradlew assembleDebug

# 2. Test check
./gradlew test

# 3. Style check
./gradlew ktlintCheck :build-logic:convention:ktlintCheck --continue

# 4. Branding check (manual)
# - Install debug APK
# - Verify app name, icons, strings
```

### Phase 3: Release (When Ready)
```bash
# Tag a release to trigger GitHub Actions
git tag -a v2026.X.X -m "Release 2026.X.X"
git push origin v2026.X.X
# GitHub Actions will build and create release with APKs
```

## Testing Strategy

### After Each Sync
1. **Build test** - All variants compile
2. **Unit tests** - `./gradlew test` passes
3. **Lint check** - No new lint errors
4. **Branding smoke test** - Install and verify visually

### Before Release
1. All of the above
2. **Manual testing on device** - Core functionality works
3. **WebView test** - Home Assistant frontend loads
4. **Sensor test** - Background sensors report correctly
5. **Notification test** - Push notifications work
6. **Widget test** - Widgets display correctly

## Decision Checklist

### Before Syncing
- [ ] Is there a new upstream release worth syncing?
- [ ] Have upstream changes been reviewed for breaking changes?
- [ ] Is the current fork in a stable state?
- [ ] Are all tests passing on current main?

### During Sync
- [ ] Are conflicts resolved correctly (branding preserved)?
- [ ] Did the build succeed after sync?
- [ ] Do tests pass?
- [ ] Are custom patches intact?

### After Sync
- [ ] App installs and runs correctly?
- [ ] Branding is correct throughout?
- [ ] CI/CD pipeline works?
- [ ] No regression in core functionality?

## Dependencies Between Repositories

```
operating-system (base layer)
      |
      v
supervisor (orchestrator)
      |
      v
core + frontend (HA server)
      |
      v  (connects via network)
android (client app) <-- independent, connects to any HA server
```

### Key Insight
The Android app is a **client** that connects to the Home Assistant server over the network. It does not have build-time dependencies on the other repositories. This means:

- Android can be synced at any time
- Android version does not need to match server version
- The app handles version compatibility at runtime

## Important URLs

| Resource | URL |
|----------|-----|
| Fork | https://github.com/my-smart-homes/android |
| Upstream | https://github.com/home-assistant/android |
| Supervisor Fork | https://github.com/my-smart-homes/supervisor |
| OS Fork | https://github.com/my-smart-homes/operating-system |

---

**Last Updated:** March 9, 2026
