# Schematic UI Design Proposal

This document outlines the high-level design and architectural integration for adding a track schematic (panel diagram) to the LeverFrame application.

## 1. Architectural Integration & UI Placement

The schematic will integrate cleanly into the existing Unidirectional Data Flow (UDF) architecture driven by `AppViewModel`.

### Visual Placement
In a prototypical signal box, the track diagram sits directly above the lever frame so the signaller can maintain situational awareness. 
*   **Split Screen (Tablet/Desktop):** The UI could be split horizontally, with the top 40% displaying a horizontally scrollable track schematic, and the bottom 60% containing the `LeverFrameScreen`.
*   **Toggle/Tab (Mobile):** On smaller screens, a toggle button or Bottom Navigation bar would allow the user to switch between the "Lever View" and the "Schematic View".

### Rendering Engine
The schematic will be rendered using Compose Multiplatform's native **`Canvas` API**.
*   **Performance:** `Canvas` is heavily optimized, hardware-accelerated, and works identically across Android, iOS, and Desktop.
*   **Drawing:** The API easily supports drawing primitives required for schematics (e.g., thick lines for tracks, colored circles for signals, polygons for turnouts).
*   **Reactivity:** Because Compose is reactive, the `Canvas` will automatically redraw itself with zero boilerplate whenever the underlying state changes.

### Data Binding & Interactivity
The schematic will act as an observer of the existing `AppUiState`.
*   **Block Occupancy:** It will read `blockStates` to dynamically color track segments (e.g., Grey for empty, Red for occupied).
*   **Lever States:** It will read `leverStates` to visually update turnout positions (Normal vs. Diverging) and signal aspects.
*   **Touch Input (Future):** By adding `pointerInput` to the Canvas, the schematic can become interactive. Tapping a signal on the panel could dispatch an intent to `AppViewModel` to request the corresponding lever to be pulled.

---

## 2. Configuration & Updates

To ensure the schematic is dynamic and customizable without recompiling, its layout will be driven by the existing JSON configuration layer.

### The Data Model (Grid Coordinate System)
The `JsonConfig` model will be extended to include a `schematic_elements` array. A **Grid Coordinate System** (similar to JMRI's PanelPro) is recommended as it is predictable and easier to build an editor for.

Example JSON definition for a track element:
```json
{
  "type": "TURNOUT_RIGHT",
  "x": 4,
  "y": 2,
  "linked_lever": 12,       
  "linked_block": "B_104"   
}
```
*   `linked_lever`: The visual state of the turnout updates when Lever 12 moves.
*   `linked_block`: The track segment turns red when Block `B_104` reports occupancy.

### Creation and Update Workflow

**Phase 1: Manual JSON (MVP)**
Initially, schematics can be built by manually declaring grid coordinates in the configuration file. `ConfigManager.kt` will parse these elements, and the new `Schematic` composable will iterate through them, drawing the appropriate graphics at `(x * gridSize, y * gridSize)`.

**Phase 2: In-App Editor**
Leveraging the existing `ConfigurationScreen.kt`, a "Schematic Editor" tab can be introduced.
*   **Visual Editing:** It will render an interactive grid.
*   **Tool Palette:** Users can select tools (e.g., "Straight Track", "Turnout", "Signal") and tap the grid to place them.
*   **Linking:** Tapping a placed item will open a dialog to assign its `linked_lever` or `linked_block`.
*   **Persistence:** Upon saving, the grid translates back into JSON and is persisted to disk using the established `ConfigManager` logic.

---

## 3. Summary of Architectural Impact
*   **State (`AppViewModel`):** Minimal impact. The schematic is primarily a read-only consumer of existing state.
*   **Config (`ConfigManager`):** Requires introducing new data classes to represent "Grid Cells" or "Drawing Elements" within the JSON schema.
*   **UI (`commonMain`):** Requires creating a new `SchematicScreen` composable using `Canvas`, and eventually an editor view within `ConfigurationScreen`.

---

## Appendix: Understanding the Grid Layout

A grid-based schematic is essentially like drawing on a piece of graph paper. Instead of providing exact pixel coordinates, the entire drawing area is divided into a grid of squares (cells), and the configuration dictates which "puzzle piece" goes into which square.

### How the Grid Works

The grid relies on a standard Cartesian coordinate system:
*   **The Origin `(x: 0, y: 0)`**: This is typically the **top-left** corner of the schematic.
*   **The X-axis**: Represents columns moving left to right. `x: 0` is the far left, `x: 1` is one cell to the right, `x: 2` is two cells right, etc.
*   **The Y-axis**: Represents rows moving top to bottom. `y: 0` is the very top row, `y: 1` is one cell down, `y: 2` is two cells down, etc.

When the `Canvas` renders the schematic, it has a set "cell size" (e.g., 40x40 pixels). When a `TURNOUT_RIGHT` is placed at `x: 4, y: 2`, the app automatically calculates the exact pixel placement (e.g., 160 pixels right, 80 pixels down) and draws the turnout icon neatly inside that 40x40 box.

### Manually Determining Coordinates (The "Graph Paper Method")

Before an in-app editor is built, the most reliable way to determine coordinates for the JSON file is to map the layout physically or visually.

**Step 1: Setup a Grid**
Use physical graph paper, or open a blank Excel/Google Sheets document with narrowed columns to form squares. Label the columns (0, 1, 2...) across the top and rows (0, 1, 2...) down the left side.

**Step 2: Sketch the Track Plan**
Draw the track layout onto the grid, ensuring that every distinct piece of track takes up exactly one cell. A long straight track might take up 5 horizontal cells; a turnout will take up 1 cell.

**Step 3: Identify the Elements**
Look at each filled cell on the grid and determine its standard "type". Examples might include:
*   `STRAIGHT_H` (Horizontal straight line)
*   `STRAIGHT_V` (Vertical straight line)
*   `CORNER_NW` (Curve from North to West)
*   `TURNOUT_RIGHT` (Switch splitting right)
*   `SIGNAL_LEFT` (A signal facing left)

**Step 4: Transcribe into JSON**
Read the elements off the grid and transcribe them into the configuration file. For example:
1.  First straight piece (column 0, row 2): `{"type": "STRAIGHT_H", "x": 0, "y": 2}`
2.  Second straight piece (column 1, row 2): `{"type": "STRAIGHT_H", "x": 1, "y": 2}`
3.  Turnout piece (column 2, row 2): `{"type": "TURNOUT_RIGHT", "x": 2, "y": 2}`

This method ensures that all tracks align perfectly. Because the app knows exactly what type of piece is in which cell, it can automatically connect the visual lines between adjacent cells seamlessly.

---

## Appendix B: Third-Party Desktop Editors

Instead of building a complex in-app editor from scratch immediately, a highly efficient alternative is to design the schematic in an existing desktop application and parse its export file. Given the grid-based requirement, here are recommended tools:

### 1. Tiled (Map Editor) - *Highly Recommended*
[Tiled](https://www.mapeditor.org/) is a free, open-source 2D level editor designed for grid-based tile maps.
*   **Workflow:** Create a "Tileset" of track pieces (straight, turnout, signal). Set the grid size and "paint" the track pieces onto the grid.
*   **Custom Data:** Tiled allows assigning custom properties to any placed tile (e.g., setting a property `linked_lever = 12` on a turnout).
*   **Export/Import:** It natively exports to clean, structured JSON containing exact `x` and `y` coordinates and custom properties. A lightweight parser in the Kotlin app can map this directly into the `JsonConfig` data classes.

### 2. JMRI PanelPro (Layout Editor)
Given LeverFrame's integration with JMRI Hubs and LCC, JMRI's built-in PanelPro is a domain-specific option.
*   **Workflow:** Design the schematic using JMRI's Layout Editor, linking turnouts to their logical LCC addresses.
*   **Export/Import:** JMRI saves its panel configuration as an XML file. Importing requires writing an XML parser in Kotlin to extract `layoutturnout` and `tracksegment` nodes, mapping their coordinates into the app's JSON schema.

Using Tiled is recommended for the simplest import pipeline due to its native JSON support and strict grid coordinate system.
