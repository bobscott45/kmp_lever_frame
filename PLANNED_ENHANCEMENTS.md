# Planned Enhancements

This document tracks mid-to-long term architectural goals and planned features for the project that are outside the scope of immediate bug fixes or minor UI improvements.

## 1. Standalone LCC Module Extraction

### The Goal
Extract the OpenLCB/LCC networking layer from `LeverFrame` into a generic, standalone Kotlin Multiplatform (KMP) library (e.g., `kmp-openlcb`). This allows the networking logic to be reused across other model railway or IoT projects.

### Implementation Strategy

The required foundational network protocols have already been implemented natively (SNIP, Datagrams, Memory Configuration, and a dynamic CDI XML schema generator).

The separation of concerns between the new `kmp-openlcb` library and the `LeverFrame` application should look like this:

#### The Generic Library (`kmp-openlcb`)
Provides the low-level plumbing:
* **Datagram Transport Engine**: Handles CAN fragmentation, reassembly, and acknowledgments.
* **Memory Config Engine**: Parses standard Memory Space Read/Write commands and routes them to registered handlers.
* **SNIP Engine**: Automatically responds to MTI `0x0DE8` requests.

#### The Host Application (`LeverFrame`)
Plugs into the library's plumbing:
* Provides simple strings (`Edranor`, `LeverFrame`, Version) to the library's SNIP engine.
* Registers virtual handlers for Memory Space `0xFF` (the CDI XML generation) and `0xFD` (dynamic data storage).
* Allows the generic library to handle all Datagram fragmentation and network transport.

### Proposed Roadmap

With the networking logic now fully supporting the required protocols, the final step is extraction:

1. **Phase 1: Library Extraction**
   * *Effort: Medium*
   * Decouple the networking code from LeverFrame's business logic and move it into a separate Gradle module (`:openlcb` or similar). This module can then be published for other Kotlin Multiplatform projects.

## 2. Route Setting (NX) Integration

### The Goal
The system currently operates purely as a mechanical lever frame, requiring operators to manually sequence points and signals in the correct order to clear a route. The goal is to add an "eNtrance to eXit" (NX) mode on top of the existing mechanical interlocking.

### Implementation Strategy
Since the application already parses the track schematics and understands digital blocks and point configurations, it has all the data required to build a routing graph. 
* **User Interaction**: A user would tap a start block (Entrance) and a destination block (Exit) on the schematic.
* **Pathfinding**: The application would calculate the shortest valid route through the track schematic.
* **Execution**: It would automatically sequence the required points and clear the appropriate signals by programmatically pulling the levers, provided the mechanical interlocking rules allow it.
