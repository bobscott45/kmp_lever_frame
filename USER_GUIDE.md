# Lever Frame User Guide

Welcome to the Lever Frame application. This guide will walk you through operating the application and using the native configuration screens to build your custom virtual railway interlocking.

## The Main Screen

The main screen is your operational view of the virtual interlocking frame.

<screenshot of the main application screen showing levers and the block shelf>

The interface is divided into several key sections:
*   **Frame Tabs**: Located at the top left, allowing you to switch between multiple interlocking frames (e.g., North Junction, South Box).
*   **Block Occupancy Shelf**: Positioned above the levers, displaying real-time track occupancy status.
*   **Track Schematic (Optional)**: A collapsible shelf above the blocks that provides a live visual representation of your track layout, signals, and point positions.
*   **Lever Row**: The primary interactive area containing your mechanical levers.
*   **Hamburger Menu (⋮)**: Located at the top right, providing access to system tools and configuration.

---

## Getting Started & Recommended Workflow

Before diving into the configuration menus, it's helpful to understand the two ways you can use this application:

1. **Standalone Simulator Mode**: You do **not** need a physical model railway, a network, or any hardware to use this app. You can simply define your blocks, levers, and schematic, and then tap the digital blocks on your screen to manually simulate train movements. The app's internal mechanical interlocking engine will lock and release levers exactly as it would in real life. *(Note: When using the app as a standalone simulator, it is highly recommended to turn **LCC Enabled** OFF in the System Settings to prevent unnecessary network transmission attempts).*
2. **Hardware Mode**: If you have a physical layout with LCC hardware (via a JMRI hub), the app acts as a live control panel. You configure the network settings, and the app will respond to physical trains triggering LCC sensors on your layout.

### Scalable Complexity

Lever Frame is designed as a sandbox. It supports highly complex prototypical operation but **does not enforce any of it**. You can scale the complexity exactly to your liking:

* **Simple "Point Motor Switchboard"**: You can define a frame with just a few `POINTS` levers. You don't need to define any signals, blocks, or interlocking rules. You don't even need to draw a schematic—you can just use the app as a row of switches.
* **Gradual Expansion**: You might start with standalone point levers, then later add a few signals and basic interlocking rules (e.g., ensuring a signal cannot be cleared if the points are set against it).
* **Full Prototypical Operation**: You can wire up physical track occupancy sensors, add facing point locks, complex conditional "OR" logic, and cross-interlock mechanical levers to digital blocks.

### Recommended Configuration Sequence

Because the application is built as an inter-dependent system, we highly recommend following this specific sequence when building a new setup from scratch:

1. **Hardware / Network (Optional)**: If using real hardware, go to **System Settings** and define your Node IDs, JMRI IP, and Wi-Fi configuration.
2. **Define Blocks**: Go to **Frame Configuration > Blocks**. Define all your track occupancy sections first (and their LCC Event IDs if using hardware). 
3. **Define Levers**: Go to **Frame Configuration > Levers**. Create your signals and points. Because you already created your blocks in Step 2, you can now easily assign interlocking rules that cross-reference those blocks.
4. **Draw the Schematic**: Go to the **Fullscreen Schematic Editor**. Because your blocks and levers already exist, you can immediately link track cells to live inputs as you draw them.

---

## The Hamburger Menu

Tapping the **Hamburger Menu (⋮)** in the top right corner opens a dropdown with five options:

<screenshot of the hamburger menu dropdown>

1.  **System Status**: Opens a read-only overlay to check your network connection and current configuration values.
2.  **System Settings**: Enters the configuration mode to edit top-level application and network settings.
3.  **Frame Configuration**: Enters the configuration mode to edit the frames, levers, and interlocking rules.
4.  **Import**: Loads a JSON configuration, overwriting the current setup.
5.  **Export**: Exports your entire frame, blocks, and interlocking configuration as standard JSON.


### System Status

<screenshot of the System Status screen>

The System Status overlay provides crucial read-only information at a glance:
*   **Network & Connection Status**: Verify your IP address, TCP Port, and current LCC connection state.
*   **Current Settings**: View the active External Event Policy, LCC network status, and Master behavior.

### System Settings

This controls the top-level behavior of the application and its connection to the OpenLCB / LCC network.

<screenshot of the System Settings screen>

*   **Node ID & Node Name**: Every device on an LCC network needs a unique Node ID. Enter your device's ID and a friendly name here.
*   **JMRI Hub IP**: If you are using JMRI to bridge Wi-Fi LCC events to a physical CAN bus, enter the IP address of the JMRI computer here. The app connects to port `12021` automatically.
*   **Wi-Fi Settings (SSID & Passwords)**: Used by the application to manage network connectivity.
*   **Conflict Policy**: Determines how the application resolves situations where the internal lever state differs from a received network event.
*   **Restore Last State**: When enabled, the application saves the position of all levers when closed and restores them upon restart.
*   **LCC Enabled**: Toggles whether the application connects to the OpenLCB/LCC network at all. When enabled, it performs normal LCC startup and broadcasts current states. When disabled, the app is completely isolated from the network.
*   **LCC Master**: Determines if the LeverFrame acts as the authoritative brain for the interlocking. 
    *   **ON (Checked)**: The frame listens to track occupancy events to dynamically lock levers, enforces interlocking on remote commands from other panels, and broadcasts its saved states on startup to force layout alignment.
    *   **OFF (Unchecked)**: The frame acts as a "dumb panel". It ignores external events (block sensors or remote lever commands) and relies purely on your manual clicks and its internal static state.
*   **Enable Sound**: Toggles auditory feedback.
*   **Default Rule Display Mode**: Controls what view is shown immediately when opening the Rules tab for a lever. Options are `Locking Table` (a read-only standard interlocking table), `Clause Builder` (a visual UI for interlocking clauses), or `Text Formula` (raw text syntax).
*   **Default Rule Editor**: When the Display Mode is set to `Locking Table`, this determines which editor mode opens when you click the "Edit Rules" button (`Clause Builder` or `Text Formula`). If the Display Mode is already set to an editor mode, this setting is ignored as you will edit directly in that view.

If changes are pending, the Save button at the top right of the panel will become active.

## Frame

Allows adding, editing and deleting frames. Select an existing frame to edit its configuration, use the **✕ Delete** button to remove the currently selected frame, or press +Add to create a new frame.

<screenshot of the Tab list screen>

When editing a specific Tab, you can configure display options (Name, Label Lines, Block Layout). From the Tab edit screen, you can delve deeper into configuring the **Blocks** and **Levers** for that specific frame.

The settings for each frame are divided into three tabs: Settings, Levers and Blocks. Additionally, you can launch the **Fullscreen Schematic Editor** from the top of the Frame configuration screen to visually draw and link your track layout.

If changes are pending, the Save button at the top right of the panel will become active.

### Settings

* **Frame Title**: The name of the frame, displayed on its tab at the top of the screen.
* **Lever Label Lines**: The number of lines allocated for the brass nameplate text on each lever. Increase this if you have long, multi-line labels.
* **Lever Line Height**: The height of each text line on the brass nameplate.
* **Block Font Size**: The size of the text used for the Digital Block Shelf labels.
* **Block Layout**: Choose between **Horizontal** (blocks side-by-side) or **Vertical** (blocks stacked) for the Digital Block Shelf.


### Levers

Levers can be added, edited or deleted.

Existing levers are listed with their lever number and description. You can reorder levers using the **↑** and **↓** arrows next to each lever; the application will automatically update all your schematic links and interlocking rules to track the lever to its new position. Levers can be added by pressing the **＋ Add Lever** button at the end of the list.

<screenshot of the Lever configuration screen>

Pressing on a lever description will open a dedicated **Lever Detail Screen** to configure the settings for that lever. The settings are organized into three tabs: **Basic**, **LCC**, and **Rules**.

To return to the main list of levers, tap the **← Back** button in the top app bar.

#### Basic (Tab)

To delete a lever, click on the **✕ Delete** button at the top right of the Basic Info section.

* **Label**: The text displayed on the brass nameplate (e.g., "UP MAIN HOME"). Use multiple lines or spaces to format as desired. Note that lever names must be unique within the frame.
* **Lever Type**: The functional type and color of the lever (e.g., Home Signal is Red, Distant Signal is Yellow, Points are Black).

#### LCC (Tab)

* **LCC Enabled**: Toggles whether this lever transmits and responds to LCC network events.
* **Event ID (Normal)**: The LCC Event ID broadcast when the lever is pushed to the Normal (up) position.
* **Event ID (Reversed)**: The LCC Event ID broadcast when the lever is pulled to the Reversed (down) position.

#### Rules (Tab)

* **Auto-Reverser**: When enabled, the lever will automatically snap back to its Normal position if any of its interlocking rules fail (e.g., when a train enters an interlocked block).

A lever **cannot be pulled** unless **all** of its interlocking conditions are met. You can require another lever to be in a specific position, lock levers based on the live state of your Digital Block Shelf, or use conditional 'OR' logic.

The Rules tab displays the interlocking rules based on your **Default Rule Display Mode** preference set in the System Settings:

1. **Locking Table Mode**: Displays a traditional, read-only mechanical interlocking table layout showing required lever numbers, required block states, and alternate 'OR' conditions. To make changes, click the **Edit Rules** button at the top of the tab, which will flip the view into your preferred Editor Mode. When finished, click **Done** to return to the Locking Table.
2. **Clause Builder Mode**: A visual, card-based interface for building rules. If this is your default display mode, the tab opens directly into the editor and you can edit immediately. 
   * Click **+ Add Rule** to create a new interlocking rule.
   * For each rule, select a **Primary Condition** (the target Lever or Block and its required state).
   * Check the **OR Alternate Condition** box to add a secondary target. If the Primary Condition fails, the lever can still be pulled if this Alternate Condition is met.
3. **Text Formula Mode**: An advanced, raw text-based syntax editor for writing complex boolean logic (e.g., `(L4_R | B1_O) & L2_N & !(L3_R)`). Like the Clause Builder, if set as your default display mode, it opens directly ready for editing.

<screenshot of adding/editing an interlocking condition dialog>

### Hot-Reloading Interlocking Rules

When you edit *only* the **Interlocking Rules** of a lever (adding, removing, or changing targets) and click **Save**, the application will perform a **Hot-Reload**. This silently applies the new interlocking logic in the background without dropping your LCC connection, without triggering a warning dialog, and without resetting the current positions of your levers. 

If you modify anything else—such as adding/deleting a lever, changing a label, or editing LCC event IDs—the app will fall back to a standard **Save & Reset**, returning all levers to NORMAL and restarting the network connection to ensure system consistency.

### Blocks

Similar to levers, existing blocks are listed on the main tab and can be reordered using the **↑** and **↓** arrows. Pressing on a block description will open a dedicated **Block Detail Screen**. The block configuration options are displayed sequentially on this screen:

* **Basic Info**: Configure the block's **Label**, which determines the text displayed on the Digital Block Shelf, and its **Short Code** for the Schematic Editor. Both the label and short code must be unique within the frame. You can delete the block by clicking the **✕ Delete** button in the top right corner.
* **LCC Events**: Define the LCC Event IDs that will trigger this block to show as "Occupied" or "Empty".

### Schematic Editor

The application features a built-in visual editor for designing the track schematic that sits above your frame. Launch the **Fullscreen Schematic Editor** button from the top of the Frame configuration screen.

<screenshot of the Schematic Editor>

* **The Grid**: The editor is based on a standard graph-paper grid. Each piece of track or signal occupies exactly one square on this grid.
* **Editing a Cell**: To place or modify an element, simply tap the desired square on the grid. This will open the **Edit Cell Panel**.
* **Element Type**: In the edit cell panel, select the type of track piece or signal you want to place. To remove a piece, select "EMPTY". Available element types include:
    * **Straights & Corners**: Standard track segments (Horizontal, Vertical, Corners).
    * **Turnouts / Points**: Track switches that split into two directions (e.g., Turnout Right, Turnout Left).
    * **Signals**: Stop signals facing either left or right.
    * **Bracket Signals**: Complex signals with both a main arm and a diverging arm mounted on a single post (available in Left or Right diverging variants).
* **Linking to Blocks**: Below the element type, you can assign a **Linked Block** from a dropdown. When this block becomes "Occupied", the track segment on the schematic will automatically turn red to indicate a train's presence.
* **Linking to Levers**: You can also assign a **Linked Lever**. The schematic will visually update when that lever is pulled:
    * For a **Turnout**, pulling the linked lever will visually switch the track blades to the diverging route.
    * For a standard **Signal**, pulling the linked lever will visually drop the signal arm to "Clear".
    * For a **Bracket Signal**, you can assign *two* separate linked levers (one for the main arm, one for the diverging arm).
* **Saving**: Press the Save icon in the top right to compile your grid back into the configuration file and return to the frame settings.

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
    *   *Tip: From the Status Screen, you can click **Edit Configuration** to instantly jump into configuring that specific lever. When you close the configuration, you will be smoothly dropped right back into the status screen!*
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
## Import

You can **Import** your configuration from the top-right hamburger menu (⋮) on the main screen.

*Note: On the Desktop app, this opens a native open file dialog. On Android/iOS, this opens a popup where you can paste the JSON from your system clipboard.*

## Export
You can **Export** your configuration from the top-right hamburger menu (⋮) on the main screen.

*Note: On the Desktop app, this opens a native save file dialog. On Android/iOS, this displays the JSON in a popup for you to copy to your system clipboard.*

---
## Suggested LCC Event Numbering Scheme

Because OpenLCB/LCC Event IDs are 64 bits (16 hex characters) and the first 48 bits (12 hex characters) are dedicated to your Node ID, you have 16 bits (4 hex characters) entirely at your disposal to define your local events. A good scheme should be **human-readable** so that when you are monitoring the network traffic or configuring JMRI, you can instantly recognize what an event means just by glancing at those last 4 characters.

We highly recommend splitting those remaining 4 characters into two distinct bytes (e.g., `XX YY`):

### 1. The `XX` Byte (Object Type & Frame)
Use the first two characters to define the *type* of object generating the event, and optionally the frame it belongs to.

**For a single frame:**
*   `01` = Levers
*   `02` = Track Blocks

**If you want to distinguish multiple frames (e.g., North Jctn vs. South Box):**
*   `11` = North Junction Levers
*   `12` = North Junction Blocks
*   `21` = South Box Levers
*   `22` = South Box Blocks

### 2. The `YY` Byte (Object Index & State)
Use the last two characters to define the specific lever/block number and its *state*. The cleanest way to do this is to use the actual lever number, and add a hex offset (like `80`) to represent the "active/reversed" state. 

**For Levers (`XX` = 11, 21):**
*   `01` to `7F` = Lever is **Normal**
*   `81` to `FF` = Lever is **Reversed** (simply add 80 hex to the lever number)
    *   *Lever 1 Normal* = `01`
    *   *Lever 1 Reversed* = `81`
    *   *Lever 14 Normal* = `0E` 
    *   *Lever 14 Reversed* = `8E`

**For Blocks (`XX` = 12, 22):**
*   `01` to `7F` = Block is **Empty**
*   `81` to `FF` = Block is **Occupied**
    *   *Block 1 Empty* = `01`
    *   *Block 1 Occupied* = `81`

### Example Full Event IDs
If your Node ID is `02.01.57.11.22.33`:
*   **Lever 2 is pulled (Reversed) on North Junction:** `02.01.57.11.22.33.11.82`
*   **Block 4 becomes Occupied on North Junction:** `02.01.57.11.22.33.12.84`

This structure prevents collisions entirely, scales to hundreds of levers/blocks without changing the pattern, and is incredibly easy to parse visually when debugging your JMRI routing tables!
