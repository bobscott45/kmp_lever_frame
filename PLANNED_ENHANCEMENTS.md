# Planned Enhancements

This document tracks mid-to-long term architectural goals and planned features for the project that are outside the scope of immediate bug fixes or minor UI improvements.

## 1. Standalone LCC Module Extraction

### The Goal
Extract the OpenLCB/LCC networking layer from `LeverFrame` into a generic, standalone Kotlin Multiplatform (KMP) library (e.g., `kmp-openlcb`). This allows the networking logic to be reused across other model railway or IoT projects.

### The Challenge: Configuration Description Information (CDI)
To be a truly generic and useful OpenLCB library, the standalone module must support standard node configuration protocols, specifically **CDI (Configuration Description Information)** and **SNIP (Simple Node Information Protocol)**.

Currently, LeverFrame only speaks the CAN "Event" protocol (MTIs like Producer Identified). CDI configuration, however, relies on:
1. **Datagrams**: Multi-frame messages requiring assembly and Ack/Nak responses.
2. **Memory Configuration Protocol**: Read/Write commands targeting specific 24-bit memory address spaces.

### Implementation Strategy

Because LeverFrame uses a dynamic JSON-based configuration with a custom visual UI (Schematic Editor, interlocking rules), it is impractical to map its complex state into a fixed LCC memory map for JMRI's generic CDI tool to manipulate. 

Therefore, the separation of concerns between the new `kmp-openlcb` library and the `LeverFrame` application should look like this:

#### The Generic Library (`kmp-openlcb`)
Provides the low-level plumbing:
* **Datagram Transport Engine**: Handles CAN fragmentation, reassembly, and acknowledgments.
* **Memory Config Engine**: Parses standard Memory Space Read/Write commands and routes them to registered handlers.
* **SNIP Engine**: Automatically responds to MTI `0x0DE8` requests.

#### The Host Application (`LeverFrame`)
Plugs into the library's plumbing:
* Provides simple strings (`Edranor`, `LeverFrame`, Version) to the library's SNIP engine.
* Registers a virtual handler for Memory Space `0xFF` (the CDI space).
* When the library receives a read request for `0xFF`, LeverFrame returns a minimal, static XML string. This XML simply identifies the node and informs JMRI: *"This node's complex interlocking rules must be configured via its native UI."*

### Proposed Roadmap

To achieve this extraction without breaking the current application, the work should be done incrementally:

1. **Phase 1: SNIP Implementation**
   * *Effort: Low*
   * Implement responses to Simple Node Information requests directly in the existing `GridConnectNetwork` class. This immediately makes LeverFrame a "good citizen" on the network by reporting its Name and Manufacturer to JMRI.
2. **Phase 2: Datagram Transport Layer**
   * *Effort: Medium-High*
   * Build the framing logic to receive and assemble incoming Datagrams and transmit proper Datagram Acknowledgments.
3. **Phase 3: Memory Config & CDI Stub**
   * *Effort: Medium*
   * Implement the Memory Configuration protocol atop Datagrams. Serve a static XML stub for LeverFrame's CDI space.
4. **Phase 4: Library Extraction**
   * *Effort: Medium*
   * Decouple the networking code from LeverFrame's business logic and move it into a separate Gradle module (`:openlcb` or similar).
