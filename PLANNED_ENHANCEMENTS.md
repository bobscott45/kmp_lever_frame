# Planned Enhancements

This document tracks mid-to-long term architectural goals and planned features for the project that are outside the scope of immediate bug fixes or minor UI improvements.

## 1. Route Setting (NX) Integration

### The Goal
The system currently operates purely as a mechanical lever frame, requiring operators to manually sequence points and signals in the correct order to clear a route. The goal is to add an "eNtrance to eXit" (NX) mode on top of the existing mechanical interlocking.

### Implementation Strategy
Since the application already parses the track schematics and understands digital blocks and point configurations, it has all the data required to build a routing graph. 
* **User Interaction**: A user would tap a start block (Entrance) and a destination block (Exit) on the schematic.
* **Pathfinding**: The application would calculate the shortest valid route through the track schematic.
* **Execution**: It would automatically sequence the required points and clear the appropriate signals by programmatically pulling the levers, provided the mechanical interlocking rules allow it.
