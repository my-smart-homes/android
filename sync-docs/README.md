# Sync Documentation - Android

This directory contains all documentation related to the sync of the Android companion app repository with upstream Home Assistant Android.

## Documentation Files

### 1. SYNC_DOCUMENTATION.md (~8KB)
Comprehensive sync analysis and details

- Base upstream version: 2026.3.3
- 5 custom commits on top of upstream
- 89 files changed across custom patches
- Major customizations: app ID, branding, release mechanism, Firebase login
- Conflict resolution details
- Testing requirements

### 2. CUSTOM_CHANGES.md (~9KB)
Detailed custom modifications analysis

- All 5 custom commits documented
- Application ID change (`com.yildiz.MySmartHomes`)
- Full rebranding (191 strings updated)
- Custom icons across all DPI variants
- CI/CD changes (GitHub releases only)
- Upstream sync workflow addition
- Rebase fix for missing string
- Firebase login feature (27 new files, 1356 insertions)

### 3. SYNC_PRIORITY.md (~6KB)
Workflow planning and priorities

- Repository dependencies explained
- Step-by-step action plan
- Testing strategy
- Decision checklist

### 4. SYNC_QUICKREF.md (~4KB)
Quick reference guide

- Fast command reference
- Critical change summary
- Conflict resolution tips
- Pre-merge checklist
- Post-merge verification

## Quick Access

For quick start:
```bash
# Quick overview
less SYNC_QUICKREF.md

# Full details
less SYNC_DOCUMENTATION.md
```

For understanding custom changes:
```bash
# What was preserved
less CUSTOM_CHANGES.md

# Sync order
less SYNC_PRIORITY.md
```

## Sync Summary

| Field | Value |
|-------|-------|
| Repository | my-smart-homes/android |
| Branch | main |
| Upstream | home-assistant/android |
| Base Version | 2026.3.3 |
| Custom Commits | 5 |
| Files Changed | 89 |
| Status | In sync with upstream (5 patches ahead) |

## Key Custom Changes

All preserved during sync:

- **App ID**: `io.homeassistant.companion.android` -> `com.yildiz.MySmartHomes`
- **App Name**: Home Assistant -> My Smart Homes
- **Icons**: Custom branding icons (55 files)
- **Strings**: 191 rebranded strings
- **CI/CD**: GitHub releases only (Play Store, Firebase, Amazon disabled)
- **Translations**: Lokalise sync removed
- **Sync Workflow**: `.github/workflows/sync_upstream.yml` added
- **String Fix**: Missing string after `app_name` restored post-rebase
- **Firebase Login**: Custom MSH login flow with encrypted credentials, auto WiFi, session management, version checker, update dialog (27 new files)

## Related Documentation

- Supervisor docs: See supervisor repository `sync-docs/`
- OS docs: See operating-system repository `sync-docs/`

---
Last Updated: March 9, 2026
Sync Date: March 9, 2026 (latest custom patches)
