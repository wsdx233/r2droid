# Implementation Plan - Settings Screen & Customization

This plan outlines the changes made to implement the Settings Screen enhancements, specifically focusing on .radare2rc editing, custom font support, language switching, and project directory management.

## completed Steps

### 1. Settings Manager Updates
- **File**: `app/src/main/java/top/wsdx233/r2droid/data/SettingsManager.kt`
- **Changes**:
    - Added `getR2rcFile`, `getR2rcContent`, `setR2rcContent` to manage `.radare2rc` content directly in `filesDir/radare2/bin/.radare2rc`.
    - Added `getCustomFont()` method to load `FontFamily` from the selected font file path.
    - Updated property setters to use correct Kotlin syntax (`set(value) { ... }`).
    - Added `projectHome` property for custom project directory.

### 2. Settings Screen UI
- **File**: `app/src/main/java/top/wsdx233/r2droid/screen/settings/SettingsScreen.kt`
- **Changes**:
    - Implemented `SettingsViewModel` to bridge UI and `SettingsManager`.
    - Added `AlertDialog` for editing `.radare2rc` content directly.
    - Added UI for selecting Project Home and Font file using system file pickers.
    - Added Language selection dialog.

### 3. Application-Wide Settings Application
- **File**: `app/src/main/java/top/wsdx233/r2droid/activity/MainActivity.kt`
- **Changes**:
    - Overrode `attachBaseContext` to apply language settings before Context creation.
    - Wrapped `MainAppContent` with `CompositionLocalProvider` to provide `LocalAppFont` with the custom font loaded from settings.
    - Initialized `SettingsManager` in `onCreate`.

### 4. Custom Font Integration
- **File**: `app/src/main/java/top/wsdx233/r2droid/ui/theme/Type.kt`
    - Defined `LocalAppFont` composition local.
- **Files**: `ProjectViewers.kt`, `ProjectComponents.kt`
    - Replaced all usages of `FontFamily.Monospace` with `LocalAppFont.current` to ensure the custom font is used in Hex, Disassembly, Decompiler views, and various project components.

### 5. Project Directory Usage
- **File**: `app/src/main/java/top/wsdx233/r2droid/screen/home/HomeViewModel.kt`
    - Updated `copyContentUriToCache` to use the custom project home directory if set.

### 6. R2 Configuration
- **File**: `app/src/main/java/top/wsdx233/r2droid/util/R2Runtime.kt`
    - Verified the direct-launch environment sets `HOME=filesDir/radare2/bin`, ensuring `radare2` loads the custom `.radare2rc` file.

## Verification
- **.radare2rc**: Creating/editing via Settings -> applied to `filesDir/radare2/bin/.radare2rc` -> loaded by r2pipe (via `HOME` env var).
- **Font**: Selected via Settings -> applied via `LocalAppFont` -> visible in all viewers.
- **Language**: Selected via Settings -> applied via `attachBaseContext` -> visible on app restart (or activity recreation).
- **Project Dir**: Selected via Settings -> used for new projects.

## Next Steps
- User might need to restart the app for Language settings to take full effect (Activity recreation handles most, but `attachBaseContext` requires fresh context usually).
- Verify permissions for external project directories if chosen (Android scoped storage limits might require specific handling if user picks a restricted folder). Currently using generic `OpenDocumentTree`.
