# Architectural Decoupling Proposals

Following the successful extraction of the OpenLCB networking module, a review of the remaining `LeverFrame` codebase reveals several areas of tight coupling. Resolving these will be crucial before implementing complex features like **Route Setting (NX)**.

## 1. Deconstructing the `AppViewModel` God Object

### The Problem
`AppViewModel.kt` currently violates the Single Responsibility Principle by acting as the system's central orchestrator. It manages:
- **Three Separate State Flows:** `DomainState` (physical lever/block positions), `ConfigState` (JSON layout structures), and `TransientUiState` (currently open tabs, error messages).
- **Disk I/O:** Directly orchestrating `ConfigurationRepository` and `StatePersistenceRepository`.
- **Network Bridging:** Directly handling LCC event emissions.
- **Business Logic:** Hardcoded rules for invoking interlocking validation and auto-reverser cascades.

**Impact on NX:** Route Setting will require pathfinding algorithms (like Dijkstra or A*). Stuffing graph traversal into an already bloated ViewModel will make the code unmaintainable and highly prone to regression bugs.

### Proposed Solution
Split `AppViewModel` into distinct, single-purpose components:
1. **`InterlockingService` (Headless Domain Layer):** A pure Kotlin class that takes intents (e.g., "Pull Lever 3") and emits `DomainState`. It will not know anything about the UI or disk.
2. **`ConfigViewModel`:** Dedicated to handling the Schematic Editor and JSON configuration tabs.
3. **`FrameViewModel`:** A lightweight ViewModel that only bridges the `InterlockingService` to the Compose UI for rendering the physical levers.

## 2. Decoupling Interlocking from Serialization Models (COMPLETED)

### The Problem
In `Interlocking.kt`, core evaluation functions like `getConflictingLevers` are tightly coupled to `TabDef`, `LeverDef`, and `BlockDef`. These classes are fundamentally Data Transfer Objects (DTOs) tailored for `kotlinx.serialization` (JSON parsing).

### Proposed Solution
Introduce a strict separation between **Persistence Models** (JSON) and **Domain Models** (Interlocking Graph):
- During application boot, parse the JSON `TabDef` models into an immutable `InterlockingGraph` domain model.
- The `InterlockingEngine` should only ever evaluate against the `InterlockingGraph` and `DomainLever` states.
- This ensures that if the persistence format changes (e.g., migrating to SQLite), the core interlocking engine remains completely untouched.

## 3. Extracting Network Knowledge from Business Logic

### The Problem
The method `Interlocking.applyCascades` enforces the rule that if a train occupies an interlocked block, all protecting signals automatically snap to danger. However, the function mutates an `outgoingEvents: MutableList<String>` list by appending raw OpenLCB hex event IDs (e.g., `195B4`). 

The pure mechanical logic engine should have no concept of LCC network strings.

### Proposed Solution
Implement an **Event Bus** or **Domain Event Flow**:
- `Interlocking.applyCascades` should only mutate the physical lever state to `NORMAL` and emit a generic domain event (e.g., `LeverStateChangedEvent(leverId=3, state=NORMAL)`).
- A separate `NetworkStateObserver` listens to these generic domain events, looks up the configured LCC strings in the configuration, and pushes them to the `OpenLcbEngine`.
- This fully restores the Single Responsibility Principle to the interlocking engine.

## 4. Abstracting Network Transports (COMPLETED)

### The Problem
The newly extracted `:openlcb` module's `OpenLcbEngine` directly invokes `GridConnectNetwork.sendMessage()`, which is a concrete singleton explicitly tied to Ktor TCP sockets.

### Proposed Solution
Create a `NetworkTransport` interface in the `:openlcb` module:
```kotlin
interface NetworkTransport {
    val incomingMessages: Flow<String>
    suspend fun sendMessage(msg: String)
}
```
`OpenLcbEngine` should take a `NetworkTransport` as a dependency. This allows the host application to easily swap out the TCP connection for a Serial USB transport, a CAN-bus hat, or a mocked transport for unit testing.
