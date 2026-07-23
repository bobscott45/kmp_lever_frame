# Architecture Overview

This document outlines the high-level architecture and design patterns used in the **LeverFrame** Kotlin Multiplatform (KMP) project.

## 1. Project Structure
The project is built using the standard Kotlin Multiplatform structure with Compose Multiplatform for the UI.

*   **`shared/`**: Contains ~99% of the application's code. This includes all UI layouts, state management, networking, and business logic.
    *   **`commonMain/`**: Platform-agnostic Kotlin code and Compose UI.
    *   **`androidMain/`**, **`jvmMain/`**, **`iosMain/`**: Platform-specific `expect/actual` implementations (e.g., file system access, network specifics, screen-wake locks).
*   **`androidApp/`**: A thin wrapper containing the `MainActivity` that initializes the shared Compose UI on Android.
*   **`desktopApp/`**: A thin wrapper containing `MainKt` which sets up the JVM `Window` and launches the Compose UI.
*   **`iosApp/`**: An Xcode project containing a SwiftUI wrapper that hosts the shared Compose UI.

## 2. Core Components

The application is built around an Unidirectional Data Flow (UDF) pattern, primarily managed by the `AppViewModel`.

### 2.1 State Management (`AppViewModel.kt`)
The `AppViewModel` acts as the single source of truth for the application's state, which is segregated into distinct flows to optimize UI recomposition:
*   **`DomainState`**: Tracks high-frequency data like lever positions (`leverStates`) and digital blocks (`blockStates`).
*   **`ConfigState`**: Holds the static JSON configuration and track layout definitions.
*   **`TransientUiState`**: Manages view modalities (e.g., active tab, open dialogs, networking status).
*   It exposes these via separate `StateFlow` streams to the Compose UI, ensuring that UI updates are highly reactive and minimize unnecessary recomposition.
*   It handles all user intents (e.g., pulling a lever, toggling a block occupancy) and dispatches them to the appropriate subsystem (`NetworkEventProcessor`, `Interlocking`, or `ConfigManager`).
*   **`NetworkEventProcessor`**: A decoupled service that translates raw incoming LCC events into domain state modifications (calculating lock rules and triggering cascading auto-reversers).
*   **`PersistenceService`**: A dedicated coroutine service that observes `DomainStateFlow` and debounces high-frequency lever toggles before writing changes to disk, removing I/O blocks from the ViewModel.

### 2.2 Configuration & Persistence (`ConfigManager.kt`)
*   **`AppConfigRepository`**: An interface abstracting the persistence layer.
*   **`ConfigManager`**: Implements the repository to serialize and deserialize the core configuration state using `kotlinx.serialization`. 
*   It dynamically converts the user's `JsonConfig` definitions into parsed `LeverDef` structures (used by the interlocking engine) and `SchematicElementDef` structures (representing grid coordinates for track components). 
*   **Platform Specifics**: It relies on `expect/actual` functions (e.g., `saveConfigToFile`) to handle file I/O safely on Android, iOS, and JVM.

### 2.3 Interlocking Engine (`Interlocking.kt` & `AppViewModel.kt`)
The brain of the lever frame.
*   It evaluates the `LeverDef` rules (locks, conditional "OR" logic, Facing Point Locks) against the current state of all levers AND blocks in real-time.
*   **Cross-Interlocking**: Levers can interlock not only against other levers but also against digital block states (e.g., TargetType.BLOCK), allowing for prototypical track-circuit locking.
*   When a user attempts to move a lever, the `AppViewModel` queries this engine to determine if the move is legal based on both lever and block occupancies.
*   The logic aims to exactly replicate physical mechanical tappet locking mechanisms found in prototypical signal boxes, enhanced with electro-mechanical track circuit interactions.

### 2.4 Networking (`GridConnectNetwork.kt` & `LccNode.kt`)
*   **`GridConnectNetwork`**: A robust, Coroutine-based TCP engine utilizing `io.ktor.network`. It manages the raw socket connections (acting as either a TCP Server listening on port 12021, or a TCP Client bridging to a JMRI Hub). It exposes incoming messages and connection statuses via `SharedFlow` and `StateFlow`.
*   **`LccNode`**: Acts as the OpenLCB/LCC protocol translator. It observes the raw GridConnect strings (e.g., `:X195B4000N;`), parses the Event IDs, and dispatches state changes to the `AppViewModel`. Conversely, when a lever is moved, it generates the appropriate GridConnect string and pushes it to the network.

### 2.5 User Interface
Built entirely in Compose Multiplatform.
*   **`App.kt`**: The root composable that observes the `AppViewModel` state and routes between the main views.
*   **`LeverFrameScreen.kt`**: The primary operational UI, rendering the physical levers and their dynamic locking states.
*   **`SchematicScreen.kt`**: Uses the Compose `Canvas` API to render a live, reactive panel diagram based on block/lever states and grid coordinates.
*   **`ConfigurationScreen.kt` & `SchematicEditorScreen.kt`**: In-app editors for modifying the JSON configuration and drawing the track schematic visually, bypassing the need for external tools.
*   **`SystemStatusScreen.kt` & `LeverStatusScreen.kt`**: Diagnostic overlays providing real-time feedback on network health and interlocking state.

## 3. Data Flow Example: Pulling a Lever

1.  **User Action**: The user swipes a lever in `LeverFrameScreen.kt`.
2.  **Intent**: The UI calls `viewModel.toggleLever(frameIndex, leverIndex)`.
3.  **Validation**: `AppViewModel` checks the `InterlockingEngine` to see if the lever is locked. If locked, the action is rejected.
4.  **State Update**: If unlocked, the internal `DomainState` is updated (lever state changes from `NORMAL` to `REVERSED`).
5.  **Network Broadcast**: The `LccNode` is notified, formats an LCC Event ID string, and passes it to `GridConnectNetwork` to transmit over Wi-Fi.
6.  **Persistence**: A debounced coroutine in `AppViewModel` detects the state change and writes the new state to disk via `ConfigManager` so it survives a reboot.
7.  **UI Recomposition**: The Compose UI automatically observes the updated `DomainState` and animates the lever to its new position.
