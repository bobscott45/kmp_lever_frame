# Lever Frame (Multiplatform)

A Kotlin Multiplatform application targeting **Android, Desktop (JVM), and iOS** to control and manage a wireless virtual railway lever frame. This project features full OpenLCB / LCC (Layout Command Control) integration via Wi-Fi and a prototypical mechanical interlocking engine, making it suitable for model railway control systems on touch devices.

This project is a successor to the [ESP32 Lever Frame](https://github.com/bobscott45/esp32_lever_frame), rebuilt using **Compose Multiplatform** for a user interface with a brass and steel aesthetic.

> **Project Status:** Fully functional and tested with physical LCC hardware via a JMRI hub. Currently built and tested extensively on **Android and Desktop (JVM)**. While the architecture supports iOS out of the box, it has not yet been compiled or physically tested on Apple devices.

**📖 See the [User Guide](USER_GUIDE.md) for detailed instructions on how to configure and use the application.**

## Key Features

* **Cross-Platform**: Run the same lever frame logic and UI on an Android tablet, an iPad, or a Desktop PC.
* **Native Kotlin OpenLCB / LCC Integration**: Comprehensive, 100% native Kotlin Multiplatform support for Layout Command Control protocols. Operates exclusively via **GridConnect TCP over Wi-Fi**, handling two-way event parsing and dynamic lever state synchronization without requiring a physical CAN bus connection or any external C/C++ libraries.
* **In-App Configuration**: No web server needed! Configure LCC events, network settings, and conflict policies natively within the app. Configurations can be exported and imported as JSON (via native file dialogs on Desktop, and via the system clipboard on mobile devices).
* **Prototypical Interlocking Engine**: A robust interlocking engine that bidirectionally models physical mechanical tappet locking, preventing deadlocks and supporting complex route dependencies like Facing Point Locks (FPLs) and conditional "OR" logic.
* **Digital Block Shelf**: Define and monitor track occupancy blocks directly above the lever frame. 
* **Cross-Interlocking & Auto-Reversers**: Interlock mechanical levers directly to digital block occupancies. Signals can be configured as "Auto-Reversers", automatically snapping back to Danger when a train enters a block, mimicking prototypical track-circuit interlocking.
* **Touch UI**: Built with Compose Multiplatform, featuring dark modes and gesture-based lever pulling.
* **Optional Sounds**: Configurable auditory feedback mimicking the physical clunks of mechanical levers, tappet locking, and block instrument bells.
* **State Persistence**: Saves and restores lever states and configurations across reboots automatically.

## Prerequisites

To bridge the wireless Wi-Fi LCC events from this app to a physical CAN-based layout, a **Wi-Fi to CAN LCC bridge** is required. A common approach is to use JMRI.

### Bridging with JMRI (LCC Hub)
To seamlessly pass events between this app and a physical CAN-based LCC network, you can use JMRI's built-in "Hub" feature.

1. **Hardware Setup:** Ensure the physical CAN network is connected to the computer (e.g., via a USB-to-CAN adapter like a GridConnect CAN-USB or LCC Buffer) and that it is configured and working within JMRI Preferences.
2. **Start the Hub:** In the main JMRI window, click the **LCC** menu (or **OpenLCB**) and select **Start Hub**. This starts a GridConnect TCP server that listens on port `12021`.
3. **Configure Firewall:** You must allow incoming TCP traffic on port `12021` on the computer running JMRI.
4. **Configure App:** Open the Lever Frame app, tap the Settings gear icon, and enter the IP address of the computer running JMRI into the **JMRI Hub IP** field. The app will automatically connect and bridge traffic.

## Building and Running

This project uses Gradle and JetBrains Compose Multiplatform. 

### Android
To install the debug build directly to a connected Android device or emulator:
```bash
./gradlew :androidApp:installDebug
```
To generate an APK for manual installation:
```bash
./gradlew :androidApp:assembleDebug
```

### Desktop (JVM)
To run the standalone desktop application:
```bash
./gradlew :desktopApp:run
```
To package the app for the current operating system (creates `.deb`, `.dmg`, or `.msi` depending on your host OS):
```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```

### iOS (Experimental)
Open the `iosApp/iosApp.xcworkspace` folder in Xcode, select a target device or simulator, and run the project. *(Note: This project's KMP architecture supports iOS, but it has not yet been physically built or tested on Apple devices. Contributions and testing are welcome!)*

## Example Interlocking Configuration

The app includes a prototypical demonstration configuration by default, showcasing sequential signaling, mutually locking facing points, and conditional 'OR' route locking.

### North Junction (Main Frame)
This frame protects a junction where a branch line diverges from a main line. It includes a Digital Block Shelf monitoring "UP MAIN", "UP BRANCH", and "DOWN MAIN". The Digital Block Shelf displays the occupancy status of these track sections based on LCC events. Blocks can be interlocked with levers to prevent clearing signals into occupied sections.
- **Lever 1 (UP DISTANT)**: The approach signal. *Locks Lever 2 REVERSED OR Lever 5 REVERSED*. This demonstrates conditional 'OR' logic.
- **Lever 2 (UP MAIN HOME)**: Clears the train straight ahead. *Locks Lever 4 NORMAL and Lever 3 REVERSED*. Also *locks to UP MAIN BLOCK EMPTY* (Cross-Interlocking) and can be configured to Auto-Reverse.
- **Lever 3 (FPL FOR POINTS 4)**: The Facing Point Lock.
- **Lever 4 (JUNCTION POINTS)**: The physical turnout. *Locks Lever 3 NORMAL*, ensuring the points cannot be moved unless the physical bolt (FPL) is withdrawn.
- **Lever 5 (UP BRANCH HOME)**: Clears the train to turn off onto the branch line. *Locks Lever 4 REVERSED and Lever 3 REVERSED*. Also *locks to UP BRANCH BLOCK EMPTY*.
- **Lever 6 (SPARE)**: A spare lever.
- **Lever 7 (DOWN ADVANCED)**: An advanced starting signal for the down direction.
- **Lever 8 (DOWN HOME)**: The home signal for the down direction. *Locks Lever 7 REVERSED*. Also *locks to DOWN MAIN BLOCK EMPTY*.

### South Box (Yard Frame)
This frame controls a small yard crossover. It includes a Digital Block Shelf monitoring the "YARD" block to ensure safe shunting.
- **Lever 1 (SHUNT AHEAD)**: A shunting disc. *Locks Lever 2 REVERSED*.
- **Lever 2 (YARD CROSSOVER)**: The physical points for the crossover. *Locks Lever 3 REVERSED*. Also *locks to YARD BLOCK EMPTY*.
- **Lever 3 (FRAME RELEASE)**: A ground frame release mechanism.

### Demonstrating the Interlocking
Try the following sequences in the app:
1. Try to pull **Lever 2 (UP MAIN HOME)**. It will be locked because the Facing Point Lock (Lever 3) is not engaged.
2. Try to pull **Lever 4 (JUNCTION POINTS)**. It is free to move because the FPL (Lever 3) is withdrawn.
3. Pull **Lever 3 (FPL)** to lock the points.
4. Try to pull **Lever 4** again. It is now locked by Lever 3.
5. Pull **Lever 2** again. It now clears because Lever 4 is Normal and Lever 3 is Reversed.
6. Pull **Lever 1 (UP DISTANT)**. It clears because Lever 2 satisfies the 'OR' condition.
7. Tap the **UP MAIN** block on the Digital Block Shelf above the levers to manually toggle it to "Occupied".
8. Try to pull **Lever 2 (UP MAIN HOME)** again. It will be locked because it is cross-interlocked to the UP MAIN block. (If the lever was already Reversed, it would snap back to Normal due to the Auto-Reverser feature).

## Project Structure

* `shared/` - Core Kotlin Multiplatform logic. Contains the interlocking engine, OpenLCB networking, Compose UI, and configuration managers.
* `androidApp/` - Android application entry point.
* `desktopApp/` - JVM standalone application entry point.
* `iosApp/` - iOS application entry point.

## License & Distribution

This project is dual-licensed to balance open-source collaboration with ecosystem compatibility:

* **Source Code:** The source code in this repository is licensed under the **GNU General Public License v3 (GPLv3)**. You are free to copy, modify, and self-compile the code, provided any distributions remain open-source under the same terms.
* **Compiled Binaries & Storefronts:** As the sole copyright owner of this codebase, the author reserves the right to distribute compiled binaries (such as on the Apple App Store, Google Play, or other platforms) under separate, proprietary, or storefront-specific licenses. 

*Note: If you wish to contribute code to this project via a Pull Request, you agree to grant the author a non-exclusive, perpetual license to distribute your contributions under both the GPLv3 and our storefront distribution licenses.*