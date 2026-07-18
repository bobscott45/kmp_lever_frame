# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] - 1.0.3-dev

### Added
- **Documentation**: Added day-to-day Tag and Bump versioning workflow guidelines and Xcode configuration steps (`04a7df3`).

### Changed
- **Security**: Untracked user-specific state and configuration files (`leverframe_config.json`, `leverframe_states.json`) from version control to prevent exposing local network credentials. Added `leverframe_config.template.json` for reference.
- **Build System**: Upgraded Android Gradle Plugin (AGP) from 9.0.0 to 9.0.1 and disabled ProGuard for the Desktop app release build (`075b78d`).
- **Build System**: Automated iOS version syncing by adding a Gradle task (`syncIosVersions`) to extract version from the TOML catalog and write to `Config.xcconfig` (`04a7df3`).

### Fixed
- **Build System**: Stripped `-dev` suffix from desktop app package versions to fix validation errors for MSI and DMG native distribution configurations.

## [1.0.2] - 2026-07-16

### Changed
- **UI Spacing**: Grouped Event IDs and Interlocking Rules in the Lever Status popup with smaller vertical spacing to conserve screen real estate.
- **Scroll Indication**: Added native-style fading edge overlays to the Lever Status popup to clearly indicate scrollable content boundaries.
- **Interlock Feedback**: Removed the popup error message when attempting to throw a locked lever, replaced by a flashing white locking pin animation. The Lever Status dropdown also now displays dynamic '✅' and '❌' status indicators next to interlocking rules.

### Fixed
- **AGP Compatibility**: Fixed a build incompatibility issue by downgrading the Android Gradle Plugin (AGP) version from 9.2.1 to 9.0.0.
- **Landscape UI**: Fixed the Lever Status popup cutting off the bottom of the content in landscape mode by making it scrollable.

## [1.0.1] - 2026-07-03

### Changed
- **Licensing**: Updated source file headers to reflect dual-licensing, maintaining GPLv3 for source code while simplifying proprietary app store publishing.

## [1.0.0] - Initial Public Release Candidates

### Added
- **Native Android & Desktop Apps**: Completely rebuilt the ESP32 Lever Frame concept as a native Kotlin Multiplatform app.
- **In-App Configuration**: Added a fully functional Compose UI for editing LCC events, conflict policies, and networking config directly on the device.
- **Android Display Features**: Implemented automatic `KeepScreenOn` and a touch-debounce mechanism that prevents accidental lever throws immediately after waking the screen from sleep.
- **Adaptive App Icons**: Generated a custom minimalist railway lever app icon with full support for Android 8.0+ Adaptive Icons (squircle, circle, teardrop compatible).

### Changed
- **Architecture**: Refactored the core logic into an Unidirectional Data Flow (UDF) pattern utilizing an `AppViewModel`.
- **Event ID Management**: Simplified the LCC event configuration UI. The system now automatically prefixes the user's base Node ID, so you only need to edit the last two suffix bytes when configuring a lever.
- **Lever Animation**: Replaced static state toggling with high-performance Jetpack Compose spring physics for a more prototypical mechanical feel.
- **Aesthetics**: Enhanced the visual design with polished brass coloring, correct British Railway Distant Signal yellow, and improved overlay opacity.

### Fixed
- **Network Resiliency**: Fixed a critical bug where the internal GridConnect TCP Server socket would crash and stop accepting new connections if the JMRI Hub disconnected.
- **UI Responsiveness**: Fixed text overlap on the System Status screen when used in portrait mode on narrow Android tablets.
- **Annoying Popups**: Suppressed the aggressive "Server Error" banner that triggered during routine JMRI disconnections or background auto-reconnection loops.
- **Save Integrity**: Fixed a bug where LCC Event IDs would be lost on restart if the system Node ID was changed.
- **File I/O**: Batched config file writes using a 500ms debounce to prevent thrashing the internal storage when swiping multiple levers simultaneously.
