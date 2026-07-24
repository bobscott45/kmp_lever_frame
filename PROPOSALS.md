# Architecture & Functionality Proposals for LeverFrame

Based on a review of the current architecture and functionality, the following improvements are proposed, ranked in order of impact and usefulness.

## Architectural Improvements

### 1. Segregate State (Domain vs. UI vs. Configuration)
**Current State:** `LeverFrameUiState` is a massive "God Object" data class. It holds static configuration (`tabs`, `config`), transient UI state (`isStatusMode`, `errorMessage`), and high-frequency domain state (`leverStates`, `blockStates` via `List<BooleanArray>`).
**The Problem:** Every time a single lever is pulled, the entire `LeverFrameUiState` is copied (including mapping the massive `BooleanArray` lists). This triggers widespread recomposition checks and is highly inefficient.
**Improvement:** Split the state. 
*   `ConfigurationStateFlow` (Rarely changes: parsed JSON layout, rules).
*   `DomainStateFlow` (Changes often: lever positions, block occupancies).
*   `TransientUiStateFlow` (Changes based on user interaction: open modals, selected tab).
Compose can then observe only the state slices it actually cares about.

### 2. Decouple `AppViewModel` (Single Responsibility Principle)
**Current State:** `AppViewModel` is doing too much (~500 lines). It initializes configuration, manages a debounced disk save coroutine, runs `do-while` loops for cascading signal logic, translates raw LCC hex events into domain state changes, and manages all UI states.
**The Problem:** It becomes a bottleneck for testing and adding new features. 
**Improvement:** Extract domain logic into dedicated services. 
*   Create an `EventProcessorUseCase` for handling incoming LCC data.
*   Create a `PersistenceService` that simply observes the `DomainStateFlow` with a debounce and saves to disk, removing that responsibility entirely from the ViewModel.

### 3. Replace Primitive Arrays with Domain Models
**Current State:** State is tracked using `List<BooleanArray>`. The interlocking engine and UI rely on passing around `tabIndex` and `leverIndex` to cross-reference primitive arrays.
**The Problem:** Highly error-prone (IndexOutOfBounds risks) and makes the code difficult to read. `newStates[tabIdx][leverIdx]` lacks context.
**Improvement:** Create proper domain models. Instead of primitives, use something like:
```kotlin
data class Lever(val id: Int, val isReversed: Boolean, val def: LeverDef)
data class Frame(val id: Int, val levers: List<Lever>)
```
This makes the Interlocking engine much more expressive.

### 4. Implement Dependency Injection (DI)
**Current State:** The ViewModel defaults to singleton objects (`ConfigManager`, `LccNode`).
**The Problem:** Hard-coded singleton dependencies make unit testing the `AppViewModel` or `Interlocking` logic difficult without spinning up the actual file system or network managers.
**Improvement:** Introduce a lightweight DI framework (like Koin, which works well with KMP) or use manual constructor injection across the board.

### 5. Remove Redundant Auto-Reverser Logic (High Priority)
**Observation:** In `AppViewModel.kt`, there is a complex `do-while` loop that handles the cascading "Auto-Reverser" logic (snapping signals back to Danger when a block is occupied). This exact loop is duplicated in both `handleExternalEvent` and `toggleBlockState`.
**Improvement:** Extract this logic into `Interlocking.kt` (e.g., `Interlocking.applyCascades(...)`). This will DRY up the code and make the interlocking engine responsible for resolving all state conflicts, rather than the ViewModel.

---

## Functionality Suggestions (Additional & Redundant)

### 1. Additional: Expression-based Interlocking Logic (Medium Priority)
**Observation:** The current `InterlockingCondition` struct supports basic `AND/OR` matching (e.g., Target A AND (AltTarget B)). 
**Improvement:** Real-world mechanical locking can be incredibly complex (e.g., conditional locking where Lever 1 locks Lever 2 ONLY IF Lever 3 is reversed). Replacing the flat condition list with a Boolean Expression Tree (AST) would allow the simulator to model 100% accurate real-world mechanical locking tables.

### 2. Additional: Session Logging & Diagnostics (Medium Priority)
**Observation:** The app has a `SystemStatusScreen`, but debugging complex interlocking logic is entirely visual.
**Improvement:** Add an internal logging tape that records events like "User Reversed Lever 4", "LCC Event Received: Block 2 Occupied", etc. This would be invaluable for users trying to debug why their virtual signal box isn't behaving as expected.

### 3. Additional: Undo / Redo Stack (Low Priority)
**Observation:** As a simulator, users might accidentally pull the wrong lever and trigger an auto-reversing cascade they didn't want.
**Improvement:** Implement the Command pattern for lever toggles. Keeping a localized history stack would allow users to "Undo" a move, stepping the state machine backward. 

### 4. Additional: Route Setting (NX) Integration (Low Priority)
**Observation:** The system operates purely as a mechanical lever frame.
**Improvement:** Since the application already knows about the track schematics and digital blocks, you could add an "eNtrance to eXit" (NX) mode. A user taps the start block and the destination block on the schematic, and the app calculates the route and automatically sequences the required points and signals.
