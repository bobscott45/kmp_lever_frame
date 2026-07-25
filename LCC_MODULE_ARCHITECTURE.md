# LCC Module Architecture Discussion

## Overview
This document explores the architectural considerations of extracting the LCC/OpenLCB networking code (`LccNode.kt`, `GridConnectNetwork.kt`, `LccCdi.kt`, `LccNetworkClient`) into its own standalone Kotlin Multiplatform (KMP) module (e.g., `:lcc-kmp`).

## 1. The Pros: Why it should be done
* **Reusability:** OpenLCB/GridConnect is a universal model railroad standard. A robust KMP implementation is incredibly valuable. By separating it, the same module can be dropped into a future Android/iOS Throttle app, a Dispatcher Panel app, or a desktop diagnostic tool, without rewriting the networking layer.
* **Separation of Concerns:** Networking code should not know anything about the application's domain. A pure LCC module is strictly responsible for establishing TCP connections, framing CAN messages, and parsing MTI codes. 
* **Testability:** It is much easier to write isolated unit tests for the OpenLCB protocol (e.g., verifying SNIP parsing, CAN ID generation) when the code is completely decoupled from UI and configuration states.

## 2. The Bottleneck: Current Tight Coupling
Currently, `LccNode.kt` is tightly coupled to the Lever Frame domain. For example, in `sendAllProducerIdentified()`, the node directly reaches into the app's configuration:

```kotlin
private fun sendAllProducerIdentified() {
    ConfigManager.currentConfig.tabs.forEach { tab ->
        tab.levers.forEach { lever ->
            // ... uses lever.lcc_event_normal
        }
    }
}
```

An LCC Node shouldn't know what a "tab" or a "lever" is. To extract this into a separate module, **Dependency Inversion** is required. The new module would provide an interface (e.g., `LccEventProvider`), and the `AppViewModel` or `ConfigManager` would pass a simple list of `String` Event IDs to the node upon initialization.

## 3. The Cons: The Pragmatic View
* **Refactoring Time:** Rewiring how `LccNode.kt` receives configuration data (Node ID, Node Name, Event IDs) and how it passes events back to the main app requires non-trivial effort.
* **YAGNI (You Aren't Gonna Need It):** If Lever Frame is the *only* Kotlin application planned for the layout right now, moving it to a separate module is premature optimization. It adds Gradle configuration overhead without delivering immediate value.

## 4. Recommendation
The best path forward is to keep the code in the `:shared` module for now but **refactor the tight coupling**.
By putting all LCC code in a strict `org.edranor.lcc` package and passing configuration variables into an `initialize()` method rather than directly accessing `ConfigManager`, the code becomes domain-agnostic. Once that decoupling is complete, physically extracting the package into a new Gradle module later will be trivial.
