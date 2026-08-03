# Lever Frame (Multiplatform)

A Kotlin Multiplatform application targeting **Android, Desktop (JVM), and iOS** to control and manage a wireless virtual railway lever frame. This project features full OpenLCB / LCC (Layout Command Control) integration via Wi-Fi and a prototypical mechanical interlocking engine, making it suitable for model railway control systems on touch devices.

This project is a successor to the [ESP32 Lever Frame](https://github.com/bobscott45/esp32_lever_frame), rebuilt using **Compose Multiplatform**.

> **Project Status:** Fully functional and tested with physical LCC hardware via a JMRI hub. Built and verified on **Android, Desktop (JVM), and iOS** (compiled and tested natively on Apple devices including iPad Pro).

**📖 See the [User Guide](USER_GUIDE.md) for detailed instructions on how to configure and use the application.**
**💻 See the [API Documentation](https://bobscott45.github.io/kmp_lever_frame/) for detailed developer documentation of the source code.**

## Key Features

* **Cross-Platform**: Run the same lever frame logic and UI on an Android tablet, an iPad, or a Desktop PC (Windows/MacOS/Linux).
* **Standalone Simulator Mode**: No physical layout, network, or hardware required. The application functions offline as a virtual signalling simulator. You can tap the digital blocks on screen to manually simulate train movements and test complex interlocking logic.
* **Native Kotlin OpenLCB / LCC Integration**: Native Kotlin Multiplatform support for Layout Command Control protocols. Operates exclusively via **GridConnect TCP over Wi-Fi**, handling two-way event parsing and dynamic lever state synchronization without requiring a physical CAN bus connection or any external C/C++ libraries.
* **In-App Configuration**: No web server needed. Configure LCC events, network settings, and conflict policies natively within the app. Configurations can be exported and imported as JSON (via native file dialogs on Desktop, and via the system clipboard on mobile devices).
* **Remote CDI Configuration**: If bridged to a physical layout, virtually all network settings, Event IDs, and text labels (Lever Names, Block Names, and Short Codes) can be edited directly from JMRI via standard OpenLCB Memory Configuration (CDI).
* **Prototypical Interlocking Engine**: An interlocking engine that bidirectionally models physical mechanical tappet locking, preventing deadlocks and supporting complex route dependencies like Facing Point Locks (FPLs) and conditional "OR" logic.
* **Digital Block Shelf**: Define and monitor track occupancy blocks directly above the lever frame. 
* **Live Track Schematic**: A reactive, grid-based panel diagram that dynamically renders live block occupancies and point positions, complete with a built-in visual layout editor.
* **NX (Entrance-Exit) Routing**: Prototypical relay-style route setting. Tap an Entrance signal and an Exit signal on the live schematic, and the NX Routing Engine will trace the track, calculate the required diverging points, throw the physical levers, and sequentially clear all required signals (and their dependencies like FPLs) with interlocking safety checks.
* **Cross-Interlocking & Auto-Reversers**: Interlock mechanical levers directly to digital block occupancies. Signals can be configured as "Auto-Reversers", automatically snapping back to Danger when a train enters a block, mimicking prototypical track-circuit interlocking.
* **Touch UI**: Built with Compose Multiplatform, featuring dark modes and gesture-based lever pulling.
* **Optional Sounds**: Configurable auditory feedback mimicking the physical clunks of mechanical levers, tappet locking, and block instrument bells.
* **Flexible Complexity**: From a simple set of standalone switches to a rigid prototypical simulator. You can configure the app to control just a few points without any signals, blocks, or interlocking rules, or you can scale it up to a highly complex signalled layout. The app supports prototypical operation but does not enforce it.
* **State Persistence**: Saves and restores lever states and configurations across reboots automatically.

## Tested Platforms

The application has been actively tested and verified to run successfully on the following combinations of operating systems and hardware:

* **Linux**: Desktop PC, and Raspberry Pi (with the official Touch Display 2 running in bare Wayland Kiosk mode)
* **macOS**: Mac Mini (M4)
* **Android**: Google Pixel Pro 7
* **iOS**: iPad 13 Pro

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
*(The generated APK will be located in `androidApp/build/outputs/apk/debug/`)*

### Desktop (JVM)
To run the standalone desktop application directly from the source code during development:
```bash
./gradlew :desktopApp:run
```

#### Native Installers
To package the app for the current operating system (creates `.deb` and `.rpm` on Linux, `.dmg` on macOS, or `.msi` on Windows):
```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```
*(The generated installer packages will be located in `desktopApp/build/compose/binaries/main/` within format-specific subdirectories).*
*(Note for Linux users: The above command attempts to build both DEB and RPM packages. If your system lacks the required tools like `dpkg` or `rpmbuild`, it will fail. You can explicitly build just the package type your system supports by running `./gradlew :desktopApp:packageDeb` or `./gradlew :desktopApp:packageRpm` instead).*

**To run the installed app:** Install the generated `.deb`, `.rpm`, `.msi`, or `.dmg` file using your operating system's standard package manager. Once installed, you can launch "LeverFrame" directly from your system's application menu.

#### Standalone Directory
To build a generic Linux/OS-agnostic distributable (creates a standalone directory containing the binaries and a shell script launcher, which can be run without installing via a package manager):
```bash
./gradlew :desktopApp:createDistributable
```
*(The generated standalone directory will be located at `desktopApp/build/compose/binaries/main/app/LeverFrame/`)*

**To run the standalone app:**
```bash
cd desktopApp/build/compose/binaries/main/app/LeverFrame/bin/
./LeverFrame
```

#### UberJar (Fat JAR)
You can also package the application as a single, portable "UberJar" (Fat JAR). This is useful if you want a single executable file that can be easily copied and run on other machines with the same operating system architecture, without needing to run an installer or deal with multiple directories. 

To build an UberJar for your current desktop operating system:
```bash
./gradlew :desktopApp:packageUberJarForCurrentOS
```
*(The generated UberJar will be located in `desktopApp/build/compose/jars/`)*

**To run the generated UberJar manually from the terminal:**
```bash
java -jar desktopApp/build/compose/jars/LeverFrame-*.jar
```
*(Requires Java 11 or higher).*

### Raspberry Pi (Raspbian / Linux ARM64)
You do **not** need to compile the application on the Raspberry Pi itself, which can be very slow. You can run the following build command directly on your standard desktop machine (Windows, macOS, or Linux x64) to build the app, and then copy the resulting JAR over to the Pi.

To build a fat JAR (UberJar) that includes all necessary native dependencies (such as the `linux-arm64` Skiko libraries) to run natively on a Raspberry Pi:
```bash
./gradlew :desktopApp:packageUberJarForCurrentOS
```
*(The generated UberJar will be located in `desktopApp/build/compose/jars/`)*
*(Note: Because this is an UberJar, it bundles native binaries for multiple architectures including ARM64. Even if it outputs a file named after your desktop's host OS like `desktopApp/build/compose/jars/LeverFrame-linux-x64-X.X.X.jar`, you can simply copy this file to your Raspberry Pi and it will run perfectly).*

**To run the generated JAR manually from the terminal:**
```bash
java -jar desktopApp/build/compose/jars/LeverFrame-*.jar
```
*(Requires Java 11 or higher).*

To run it on the Pi with Labwc (the officially supported Raspberry Pi OS Wayland compositor):
```bash
labwc
```
*(Note: You will need an autostart configuration for Labwc to actually launch the app, see the Kiosk section below).*
*(Tip: High-DPI screens, like a 10.5" 1080p display, may render the interface extremely small on a bare Wayland server. You can scale up the entire UI by passing the `--ui-scale` argument to the jar. You can also configure this persistently in the app's System Settings menu).*

#### Screen Rotation (Landscape Mode)
If your screen is natively portrait (like the official DSI screens) and you want to run the app in landscape mode, do **not** use the legacy `video=` or `display_rotate` parameters in `/boot/firmware/cmdline.txt`, as these often crash hardware acceleration on modern Pi OS.

Instead, configure Labwc to rotate the display and then launch the app.
Create an autostart file (`~/.config/labwc/autostart`):
```bash
# Rotate the display (adjust 90 to 180 or 270 as needed)
wlr-randr --output DSI-1 --transform 90
# Launch the application
java -jar LeverFrame-linux-x64-X.X.X.jar --ui-scale 1.5
```

**Note on Touchscreens:** Rotating the display output via `wlr-randr` does *not* automatically rotate the underlying hardware touchscreen inputs in a bare kiosk environment. You must add a `udev` rule to rotate the input matrix at the system level. 

Create `/etc/udev/rules.d/99-touchscreen.rules` (find your exact device name using `libinput list-devices`). For example, to map a native-portrait screen (like the official Raspberry Pi Touch Display 2) to landscape:

```udev
# 90° / 270° Rotation Matrix (required for the Raspberry Pi Touch Display 2 in landscape)
ENV{ID_INPUT_TOUCHSCREEN}=="1", ATTRS{name}=="Goodix Capacitive TouchScreen", ENV{LIBINPUT_CALIBRATION_MATRIX}="0 -1 1 1 0 0"
```
*(If your touches register on the wrong axis, you may need a different matrix. Common alternatives are `"0 1 0 -1 0 1"` or `"-1 0 1 0 -1 1"`).*

Reload the rules with `sudo udevadm control --reload-rules && sudo udevadm trigger`, or simply reboot the Pi.

#### Dedicated Kiosk Mode (Systemd)
For a permanent physical interlocking frame, it is highly recommended to run LeverFrame on **Raspberry Pi OS Lite (64-bit)** (which has no desktop GUI overhead). 

**Hardware Note:** The application has been verified to run successfully on a basic **Raspberry Pi 4 (2GB RAM)**. However, upgrading to a **Raspberry Pi 5 (with 2GB or more of RAM)** is highly recommended for much faster JVM load times and a noticeably smoother UI due to its superior CPU and GPU architecture. Note that because LeverFrame is lightweight and runs without a heavy desktop environment, 2GB of RAM is perfectly sufficient—purchasing a model with 4GB or 8GB of RAM will not provide any additional performance benefits for this specific application.

You can create a `systemd` service to boot directly into the app using Labwc without requiring a login.

1. Ensure prerequisites are installed: `sudo apt install openjdk-17-jre labwc wlr-randr`
2. Configure your `~/.config/labwc/autostart` script as shown in the Screen Rotation section above to launch the `.jar` file.
3. Create the service file: `sudo nano /etc/systemd/system/leverframe.service`
4. Paste the following configuration. **Important:** You MUST replace `<your_username>` with your actual Linux username (e.g., `pi`) before saving:

```ini
[Unit]
Description=LeverFrame Kiosk
After=network.target systemd-user-sessions.service systemd-logind.service

[Service]
User=<your_username>
Group=<your_username>
WorkingDirectory=/home/<your_username>

# CRITICAL: Force a logind session to grant labwc access to the GPU/DRM
PAMName=login

# CRITICAL: Tell labwc where to place the Wayland socket (usually /run/user/<UID>)
Environment=XDG_RUNTIME_DIR=/run/user/1000

# Prevent wlroots from crashing if no keyboard/mouse is plugged in during boot
Environment=WLR_LIBINPUT_NO_DEVICES=1

TTYPath=/dev/tty7
StandardInput=tty
StandardOutput=journal
StandardError=journal

# Wait a few seconds for graphics hardware to settle, then switch monitor to tty7
ExecStartPre=/bin/sleep 3
ExecStartPre=/usr/bin/chvt 7

# Launch labwc natively (it will automatically read ~/.config/labwc/autostart)
ExecStart=/usr/bin/labwc

# Automatically restart if the app crashes (but not if it exits cleanly via ESC, thanks to on-failure)
Restart=on-failure
RestartSec=3

[Install]
# CRITICAL for Pi OS Lite: Must hook into multi-user.target, not graphical.target
WantedBy=multi-user.target
```

4. Enable and start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable leverframe.service
sudo systemctl start leverframe.service
```

### iOS
Open `iosApp/iosApp.xcodeproj` in Xcode, select your connected iPad or iPhone (or an iOS Simulator), select your free Personal Team under **Signing & Capabilities**, and press **Run (`Cmd + R`)**.

*(Note regarding iOS distribution: Due to Apple's developer program fees and maintenance overhead, pre-compiled iOS binaries are not provided via the App Store. However, because the source code is fully available under the GPLv3 license, iOS users with a Mac can open the project in Xcode and compile/install it directly to their iPads at no cost using a standard free Apple ID.)*

### Running Tests
Tests live under the `shared/` and `:openlcb` modules and run on the host JVM.
- **Run all common and JVM tests**: `./gradlew check`
- **Run Android host tests**: `./gradlew :shared:testAndroidHostTest`
- **Run Desktop (JVM) tests**: `./gradlew :shared:jvmTest`
- **Run a single test class**: `./gradlew :shared:jvmTest --tests "org.edranor.leverframe.AppViewModelTest"`

## Example Interlocking Configuration

The app includes a prototypical demonstration configuration by default, showcasing sequential signaling, mutually locking facing points, and conditional 'OR' route locking.

### North Junction (Main Frame)
This frame protects a junction where a branch line diverges from a main line. It includes a Digital Block Shelf monitoring "UP MAIN", "TO YARD", and "DOWN MAIN". The Digital Block Shelf displays the occupancy status of these track sections based on LCC events. Blocks can be interlocked with levers to prevent clearing signals into occupied sections.
- **Lever 1 (UP DISTANT)**: The approach signal. *Locks Lever 2 REVERSED*.
- **Lever 2 (UP MAIN HOME)**: Clears the train straight ahead. *Locks Lever 5 NORMAL and Lever 4 REVERSED*. Also *locks to UP MAIN BLOCK CLEAR* (Cross-Interlocking) and can be configured to Auto-Reverse.
- **Lever 3 (TO YARD HOME)**: Clears the train to turn off onto the branch line toward the Yard. *Locks Lever 5 REVERSED and Lever 4 REVERSED*. Also *locks to TO YARD BLOCK CLEAR*.
- **Lever 4 (FPL FOR POINTS 5)**: The Facing Point Lock.
- **Lever 5 (JUNCTION POINTS)**: The physical turnout. *Locks Lever 4 NORMAL*, ensuring the points cannot be moved unless the physical bolt (FPL) is withdrawn.
- **Lever 6 (SPARE)**: A spare lever.
- **Lever 7 (DOWN HOME)**: The home signal for the down direction. *Locks to DOWN MAIN BLOCK CLEAR*.

### South Box (Yard Frame)
This frame controls a small yard crossover, serving as the continuation of the "TO YARD" line from North Junction. It includes a Digital Block Shelf monitoring "YARD APPROACH", "THROAT", "YARD", and "SIDING".

*(Note: In a real-world prototypical setup, a small yard like this would generally use ground position light signals or shunting discs rather than full-sized semaphore Home and Distant signals. This configuration is "over-signalled" to demonstrate bracket signal and cascading distant signal capabilities in a compact space).*
- **Lever 1 (YARD DISTANT)**: The distant signal providing advance warning for the Yard Home. *Locks Lever 2 REVERSED*.
- **Lever 2 (YARD HOME)**: The main arm of the bracket signal controlling entry into the yard. *Locks Lever 4 NORMAL*.
- **Lever 3 (SIDING HOME)**: The diverging arm of the bracket signal controlling entry into the siding. *Locks Lever 4 REVERSED*.
- **Lever 4 (YARD CROSSOVER)**: The physical points for the crossover.
- **Lever 5 (SHUNT AHEAD)**: A shunting disc. *Locks Lever 4 NORMAL* and *locks to THROAT BLOCK CLEAR*.
- **Lever 6 (SIDING EXIT)**: A home signal controlling trains exiting the siding onto the yard approach. *Locks Lever 4 REVERSED*.

### Demonstrating the Interlocking
Try the following sequences in the app:
1. Try to pull **Lever 2 (UP MAIN HOME)**. It will be locked because the Facing Point Lock (Lever 4) is not engaged.
2. Try to pull **Lever 5 (JUNCTION POINTS)**. It is free to move because the FPL (Lever 4) is withdrawn.
3. Pull **Lever 4 (FPL)** to lock the points.
4. Try to pull **Lever 5** again. It is now locked by Lever 4.
5. Pull **Lever 2** again. It now clears because Lever 5 is Normal and Lever 4 is Reversed.
6. Pull **Lever 1 (UP DISTANT)**. It clears because Lever 2 is Reversed.
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