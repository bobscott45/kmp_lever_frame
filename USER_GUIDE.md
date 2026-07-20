# Lever Frame User Guide

Welcome to the Lever Frame application. This guide will walk you through operating the application and using the native configuration screens to build your custom virtual railway interlocking.

## The Main Screen

The main screen is your operational view of the virtual interlocking frame.

<screenshot of the main application screen showing levers and the block shelf>

The interface is divided into several key sections:
*   **Frame Tabs**: Located at the top left, allowing you to switch between multiple interlocking frames (e.g., North Junction, South Box).
*   **Block Occupancy Shelf**: Positioned above the levers, displaying real-time track occupancy status.
*   **Lever Row**: The primary interactive area containing your mechanical levers.
*   **Hamburger Menu (⋮)**: Located at the top right, providing access to system tools and configuration.

---

## The Hamburger Menu

Tapping the **Hamburger Menu (⋮)** in the top right corner opens a dropdown with three primary options:

<screenshot of the hamburger menu dropdown>

1.  **System Status**: Opens an overlay to check your network connection and adjust quick settings.
2.  **System Settings**: Enters the configuration mode to edit top-level application and network settings.
3.  **Frame Configuration**: Enters the configuration mode to edit the frames, levers, and interlocking rules.

### System Status

<screenshot of the System Status screen>

The System Status overlay provides crucial information at a glance:
*   **Network & Connection Status**: Verify your IP address, TCP Port, and current LCC connection state.
*   **External Event Policy**: Quickly change how the app responds to conflicting LCC events (Strict Local, Override Allowed, or Accept & Warn).
*   **Quick Toggles**: Conveniently toggle LCC Master behavior and "Restore Last State" directly from this screen without entering full configuration mode.

### System Settings

This controls the top-level behavior of the application and its connection to the OpenLCB / LCC network.

<screenshot of the System Settings screen>

*   **Node ID & Node Name**: Every device on an LCC network needs a unique Node ID. Enter your device's ID and a friendly name here.
*   **JMRI Hub IP**: If you are using JMRI to bridge Wi-Fi LCC events to a physical CAN bus, enter the IP address of the JMRI computer here. The app connects to port `12021` automatically.
*   **Wi-Fi Settings (SSID & Passwords)**: Used by the application to manage network connectivity.
*   **Conflict Policy**: Determines how the application resolves situations where the internal lever state differs from a received network event.
*   **Restore Last State**: When enabled, the application saves the position of all levers when closed and restores them upon restart.
*   **LCC Master**: Toggle whether this application acts as the master authority for the lever states on the network.
*   **Enable Sound**: Toggles auditory feedback.

**Importing, Exporting and Saving**
At the top of the System Settings screen, you'll find options to **Save**, **Export**, and **Import** your configuration.
<screenshot of the Export and Import buttons/dialog>
*   **Export**: Exports your entire frame, blocks, and interlocking configuration as standard JSON. *Note: On the Desktop app, this opens a native save file dialog. On Android/iOS, this displays the JSON in a popup for you to copy to your system clipboard.*
*   **Import**: Loads a JSON configuration, overwriting the current setup. *Note: On the Desktop app, this opens a native open file dialog. On Android/iOS, this opens a popup where you can paste the JSON from your system clipboard.*
*   **Save**: Saves your current frame configuration to the device's storage. If no changes have been made, the option is disabled. 

## Frame

Allows adding, editing and deleting frames. Select an existing frame to edit its configuration, use the **✕ Delete** button to remove the currently selected frame, or press +Add to create a new frame.

<screenshot of the Tab list screen>

When editing a specific Tab, you can configure display options (Name, Label Lines, Block Layout). From the Tab edit screen, you can delve deeper into configuring the **Blocks** and **Levers** for that specific frame.

The settings for each frame are divided into three tabs: Settings, Levers and Blocks.

### Settings

* **Frame Title**: The name of the frame, displayed on its tab at the top of the screen.
* **Lever Label Lines**: The number of lines allocated for the brass nameplate text on each lever. Increase this if you have long, multi-line labels.
* **Lever Line Height**: The height of each text line on the brass nameplate.
* **Block Font Size**: The size of the text used for the Digital Block Shelf labels.
* **Block Layout**: Choose between **Horizontal** (blocks side-by-side) or **Vertical** (blocks stacked) for the Digital Block Shelf.

### Levers

Levers can be added, edited or deleted.

Existing levers are listed with their lever number and description. Levers can be added by pressing the +Add Lever
button at the end of the list.

<screenshot of the Lever configuration screen>

Pressing on a lever description will open a dedicated **Lever Detail Screen** to configure the settings for that lever. The settings are organized into three tabs: **Basic**, **LCC**, and **Rules**.

To return to the main list of levers, tap the **← Back** button in the top app bar.

#### Basic (Tab)

To delete a lever, click on the **✕ Delete** button at the top right of the Basic Info section.

* **Label**: The text displayed on the brass nameplate (e.g., "UP MAIN HOME"). Use multiple lines or spaces to format as desired.
* **Lever Type**: The functional type and color of the lever (e.g., Home Signal is Red, Distant Signal is Yellow, Points are Black).

#### LCC (Tab)

* **LCC Enabled**: Toggles whether this lever transmits and responds to LCC network events.
* **Event ID (Normal)**: The LCC Event ID broadcast when the lever is pushed to the Normal (up) position.
* **Event ID (Reversed)**: The LCC Event ID broadcast when the lever is pulled to the Reversed (down) position.

#### Rules (Tab)

* **Auto-Reverser**: When enabled, the lever will automatically snap back to its Normal position if any of its interlocking rules fail (e.g., when a train enters an interlocked block).

Click **+ Add Rule** to create a new interlocking rule. For each rule, a settings panel is displayed. Click on the **✕ Delete** button at the top right of a rule settings panel to delete the rule.

Each rule is divided into a **Primary Condition** and an optional **Alternate Condition**.

##### Primary Condition

* **Type**: Choose whether to interlock against another **LEVER** or a track **BLOCK**.
* **Index**: The numerical index of the target lever or block (e.g., enter `4` to lock against Lever 4).
* **Required State**: The state the target must be in for this lever to be pulled (e.g., `NORMAL` or `REVERSED` for levers, `EMPTY` or `OCCUPIED` for blocks).

##### Alternate Condition (Optional)

This acts as a logical 'OR' condition. If the Primary Condition fails, the lever can still be pulled if this Alternate Condition is met.

* **Alt Type**: Choose between **LEVER** or **BLOCK** for the alternate condition.
* **Alt Index**: The numerical index of the alternate target (this label title is displayed within the edit box until you enter a value).
* **Alt Required State**: The required state for the alternate target.

When configuring a lever, you set its **Label**, **Type** (color/purpose), and **LCC Events** (the IDs broadcast when pulled or pushed).

**Mechanical Interlocking Engine**
Scroll down in the Lever Configuration screen to find the **Interlocking** section. A lever **cannot be pulled** unless **all** of its interlocking conditions are met.

<screenshot of adding/editing an interlocking condition dialog>

*   **Basic Lever Locking**: Require another lever to be in a specific position (e.g., Lever 4 locks Lever 3 NORMAL).
*   **Cross-Interlocking (Blocks)**: Lock levers based on the live state of your Digital Block Shelf (e.g., Lever 2 locks the "UP MAIN" block EMPTY).
*   **Conditional 'OR' Logic**: Set an **Alt Target** to allow a lever to be pulled if *either* of two conditions is true (e.g., Lever 1 requires Lever 2 OR Lever 5 to be REVERSED).

### Blocks

Similar to levers, pressing on a block description will open a dedicated **Block Detail Screen**. The block configuration options are displayed sequentially on this screen:

* **Basic Info**: Configure the block's **Label**, which determines the text displayed on the Digital Block Shelf. You can delete the block by clicking the **✕ Delete** button in the top right corner.
* **LCC Events**: Define the LCC Event IDs that will trigger this block to show as "Occupied" or "Empty".
---

## The Block Occupancy Shelf

The Digital Block Shelf is a visual indicator that sits above your levers, showing real-time track occupancy.

<screenshot of the Block Shelf on the main screen showing occupied/empty blocks>

As trains move across your physical layout and trigger LCC sensors, the corresponding blocks on this shelf will light up to indicate an "Occupied" state. You can also manually toggle the occupancy state of any block simply by tapping on it. These blocks act as inputs for your interlocking engine, allowing you to physically lock levers (like Home Signals) when a train is sitting in a specific section of track.

## The Lever Row

The Lever Row contains your mechanical levers. You interact with them by pulling (dragging down) and pushing (dragging up) the lever handles.

If a lever refuses to move and vibrates or clunks, it is mechanically locked by other levers or blocks according to your interlocking rules.

### Individual Lever Breakdown

Every lever is composed of several distinct visual and interactive parts:

<screenshot highlighting parts of an individual lever>

*   **Brass Nameplate (Header)**: Located at the top. Displays the lever's custom label and a colored bar denoting its type (e.g., Red for Home Signal, Black for Points).
    *   *Tip: Clicking or tapping the brass nameplate opens the **Lever Status Screen**—an overlay showing exactly which interlocking rule is failing (❌) or satisfied (✅) for that specific lever.*
*   **State Indicators**: Text such as "ON / OFF" or "NORMAL / THROWN" appears above and below the lever track, showing the current operational state.
*   **Track & Handle (Knob)**: The main interactive component. Slide the handle downwards to pull the lever (`REVERSED`), or push it upwards to return it (`NORMAL`). If the handle displays an "**A**", it means the **Auto-Reverser** feature is active, and the lever will snap back to Normal automatically when a train enters an interlocked block.
*   **Locking Pin**: If the lever is mechanically locked, a physical pin visually obstructs the handle track, preventing movement.
*   **Collar Button (Footer)**: Located at the very bottom. This button displays the current lock status:
    *   `UNLOCKED` (Dark Gray): Free to move.
    *   `INTERLOCK` (Dark Gray): Locked by the mechanical interlocking engine (other levers or blocks).
    *   `LOCKED` (Red): Manually locked by the operator.
    *   `ALARM` (Orange): Indicates a conflict between the physical LCC network and the app's internal state.
    *   *Tip: You can tap the collar button to manually lock (`LOCKED`) a lever, placing a virtual "collar" on it to prevent accidental pulling. Tap again to unlock.*

---

