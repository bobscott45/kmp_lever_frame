package org.edranor.leverframe

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchematicElementEditorDialog(
    tabDef: JsonTab,
    cx: Int,
    cy: Int,
    initialEditType: String,
    initialLinkedLever: Int,
    initialLinkedLever2: Int,
    initialLinkedBlock: Int,
    initialNxButton: String,
    initialNxPlacement: String,
    initialNxColor: String,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int, String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var editType by remember { mutableStateOf(initialEditType) }
    var editLinkedLever by remember { mutableStateOf(initialLinkedLever) }
    var editLinkedLever2 by remember { mutableStateOf(initialLinkedLever2) }
    var editLinkedBlock by remember { mutableStateOf(initialLinkedBlock) }
    var editNxButton by remember { mutableStateOf(initialNxButton) }
    var editNxPlacement by remember { mutableStateOf(initialNxPlacement) }
    var editNxColor by remember { mutableStateOf(initialNxColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Cell ($cx, $cy)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val types = listOf("STRAIGHT_H", "STRAIGHT_V", "TURNOUT_LEFT", "TURNOUT_RIGHT", "SIGNAL_LEFT", "SIGNAL_RIGHT", "BRACKET_SIGNAL_LEFT", "BRACKET_SIGNAL_RIGHT")
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                    OutlinedTextField(
                        value = editType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Component Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        types.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { editType = t; typeExpanded = false })
                        }
                    }
                }

                // Linked Lever
                var leverExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = leverExpanded, onExpandedChange = { leverExpanded = !leverExpanded }) {
                    OutlinedTextField(
                        value = if (editLinkedLever >= 0) "${editLinkedLever + 1}" else "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Linked Lever (Main)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = leverExpanded) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = leverExpanded, onDismissRequest = { leverExpanded = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { editLinkedLever = -1; leverExpanded = false })
                        tabDef.levers.forEachIndexed { i, l ->
                            DropdownMenuItem(text = { Text("${i + 1}: ${l.label.replace("\n", " ")}") }, onClick = { editLinkedLever = i; leverExpanded = false })
                        }
                    }
                }

                // Linked Lever 2
                if (editType.startsWith("BRACKET_SIGNAL")) {
                    var lever2Expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = lever2Expanded, onExpandedChange = { lever2Expanded = !lever2Expanded }) {
                        OutlinedTextField(
                            value = if (editLinkedLever2 >= 0) "${editLinkedLever2 + 1}" else "None",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Linked Lever (Branch)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lever2Expanded) },
                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = lever2Expanded, onDismissRequest = { lever2Expanded = false }) {
                            DropdownMenuItem(text = { Text("None") }, onClick = { editLinkedLever2 = -1; lever2Expanded = false })
                            tabDef.levers.forEachIndexed { i, l ->
                                DropdownMenuItem(text = { Text("${i + 1}: ${l.label.replace("\n", " ")}") }, onClick = { editLinkedLever2 = i; lever2Expanded = false })
                            }
                        }
                    }
                }

                // Linked Block
                var blockExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = blockExpanded, onExpandedChange = { blockExpanded = !blockExpanded }) {
                    val blockLabel = if (editLinkedBlock >= 0 && editLinkedBlock < tabDef.blocks.size) {
                        tabDef.blocks[editLinkedBlock].label.ifBlank { "Block ${editLinkedBlock + 1}" }
                    } else { "None" }
                    OutlinedTextField(
                        value = blockLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Linked Block") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = blockExpanded) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = blockExpanded, onDismissRequest = { blockExpanded = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { editLinkedBlock = -1; blockExpanded = false })
                        tabDef.blocks.forEachIndexed { index, b ->
                            val displayLabel = b.label.ifBlank { "Block ${index + 1}" }.replace("\n", " ")
                            DropdownMenuItem(text = { Text(displayLabel) }, onClick = { editLinkedBlock = index; blockExpanded = false })
                        }
                    }
                }
                
                // NX Button Type
                var nxExpanded by remember { mutableStateOf(false) }
                val nxLabels = mapOf(
                    "NONE" to "None",
                    "ENTRANCE_ONLY" to "Entrance Only",
                    "EXIT_ONLY" to "Exit Only",
                    "BOTH" to "Entry & Exit"
                )
                ExposedDropdownMenuBox(expanded = nxExpanded, onExpandedChange = { nxExpanded = !nxExpanded }) {
                    OutlinedTextField(
                        value = nxLabels[editNxButton] ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("NX Route Button") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nxExpanded) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = nxExpanded, onDismissRequest = { nxExpanded = false }) {
                        nxLabels.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) }, 
                                onClick = { editNxButton = key; nxExpanded = false }
                            )
                        }
                    }
                }

                // NX Button Placement
                if (editNxButton != "NONE") {
                    var placementExpanded by remember { mutableStateOf(false) }
                    val placementLabels = mapOf(
                        "DEFAULT" to "Default (Top-Left)",
                        "LEFT" to "Left Edge",
                        "RIGHT" to "Right Edge",
                        "TOP" to "Top Edge",
                        "BOTTOM" to "Bottom Edge"
                    )
                    ExposedDropdownMenuBox(expanded = placementExpanded, onExpandedChange = { placementExpanded = !placementExpanded }) {
                        OutlinedTextField(
                            value = placementLabels[editNxPlacement] ?: "Default",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("NX Button Placement") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placementExpanded) },
                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = placementExpanded, onDismissRequest = { placementExpanded = false }) {
                            placementLabels.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) }, 
                                    onClick = { editNxPlacement = key; placementExpanded = false }
                                )
                            }
                        }
                    }
                }

                // NX Button Color
                if (editNxButton != "NONE") {
                    var colorExpanded by remember { mutableStateOf(false) }
                    val colorLabels = mapOf(
                        "BLACK" to "Black",
                        "WHITE" to "White",
                        "RED" to "Red (Main Line)",
                        "YELLOW" to "Yellow (Call-On/Shunt)",
                        "GREEN" to "Green",
                        "BLUE" to "Blue"
                    )
                    ExposedDropdownMenuBox(expanded = colorExpanded, onExpandedChange = { colorExpanded = !colorExpanded }) {
                        OutlinedTextField(
                            value = colorLabels[editNxColor] ?: "Black",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("NX Button Color") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorExpanded) },
                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = colorExpanded, onDismissRequest = { colorExpanded = false }) {
                            colorLabels.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) }, 
                                    onClick = { editNxColor = key; colorExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(editType, editLinkedLever, editLinkedLever2, editLinkedBlock, editNxButton, editNxPlacement, editNxColor) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
