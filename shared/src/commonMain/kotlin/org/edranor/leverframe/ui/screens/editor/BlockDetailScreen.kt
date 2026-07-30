/*
 * Copyright (C) 2026 Robert Scott
 *
 * This file is part of LeverFrame.
 *
 * This project is dual-licensed to balance open-source collaboration with 
 * ecosystem compatibility:
 *
 * * Source Code: The source code in this repository is licensed under the 
 *   GNU General Public License v3 (GPLv3). You are free to copy, modify, 
 *   and self-compile the code, provided any distributions remain open-source 
 *   under the same terms.
 * * Compiled Binaries & Storefronts: As the sole copyright owner of this 
 *   codebase, the author reserves the right to distribute compiled binaries 
 *   (such as on the Apple App Store, Google Play, or other platforms) under 
 *   separate, proprietary, or storefront-specific licenses.
 *
 * Note: If you wish to contribute code to this project via a Pull Request, you 
 * agree to grant the author a non-exclusive, perpetual license to distribute 
 * your contributions under both the GPLv3 and our storefront distribution licenses.
 */
/**
 * User interface component for editing individual track blocks and sensors.
 */
package org.edranor.leverframe.ui.screens.editor
import org.edranor.leverframe.*

import org.edranor.leverframe.network.*
import org.edranor.leverframe.services.*
import org.edranor.leverframe.ui.screens.main.*
import org.edranor.leverframe.ui.components.*
import org.edranor.leverframe.ui.theme.*
import org.edranor.leverframe.di.*
import org.edranor.leverframe.ui.screens.editor.*
import org.edranor.leverframe.domain.models.*
import org.edranor.leverframe.config.*
import org.edranor.leverframe.ui.screens.schematic.*
import org.edranor.leverframe.domain.engine.*
import org.edranor.leverframe.domain.parser.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A detailed editor screen that allows users to modify a single [JsonBlock]'s properties,
 * including its short code, LCC events, and operational mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDetailScreen(
    nodeId: String,
    blockIndex: Int,
    block: JsonBlock,
    allBlocks: List<JsonBlock>,
    onBlockChange: (JsonBlock) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Basic Info Group
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Basic Info", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
                            TextButton(onClick = { showDeleteDialog = true }) {
                                Text("✕ Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("Delete Block") },
                                text = { Text("Are you sure you want to delete this block?") },
                                confirmButton = {
                                    TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        val isDuplicate = allBlocks.filterIndexed { index, _ -> index != blockIndex }.any { it.label == block.label }
                        OutlinedTextField(
                            value = block.label,
                            onValueChange = { newLabel -> 
                                if (newLabel.length <= OpenLcbConstants.MAX_LABEL_LENGTH) {
                                    val oldAutoShort = block.label.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { it.take(1).uppercase() }
                                val newAutoShort = newLabel.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { it.take(1).uppercase() }
                                
                                    val newShortCode = if (block.short_code.isBlank() || block.short_code == oldAutoShort) newAutoShort else block.short_code
                                    
                                    onBlockChange(block.copy(label = newLabel, short_code = newShortCode.take(OpenLcbConstants.MAX_SHORT_CODE_LENGTH))) 
                                }
                            },
                            label = { Text("Label") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = isDuplicate,
                            supportingText = if (isDuplicate) { { Text("Duplicate block name") } } else null,
                            colors = brassTextFieldColors()
                        )
                        val isDuplicateShortCode = block.short_code.isNotBlank() && allBlocks.filterIndexed { index, _ -> index != blockIndex }.any { it.short_code == block.short_code }
                        OutlinedTextField(
                            value = block.short_code,
                            onValueChange = { if (it.length <= OpenLcbConstants.MAX_SHORT_CODE_LENGTH) onBlockChange(block.copy(short_code = it.uppercase())) },
                            label = { Text("Short Code (for Schematic)") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = isDuplicateShortCode,
                            supportingText = if (isDuplicateShortCode) { { Text("Duplicate short code") } } else null,
                            colors = brassTextFieldColors()
                        )
                    }
                }
            }
            
            item {
                // LCC Events Group
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        var modeExpanded by remember { mutableStateOf(false) }
                        val modes = mapOf(
                            BlockMode.HARDWARE_SENSOR to "Hardware Sensor (Listens)",
                            BlockMode.VIRTUAL_SENSOR to "Virtual Sensor (Broadcasts)",
                            BlockMode.LOCAL_ONLY to "Local Only (No Broadcast)"
                        )
                        ExposedDropdownMenuBox(expanded = modeExpanded, onExpandedChange = { modeExpanded = !modeExpanded }) {
                            OutlinedTextField(
                                value = modes[block.mode] ?: block.mode.name,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Block Operating Mode") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                colors = brassTextFieldColors()
                            )
                            ExposedDropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                                modes.forEach { (mode, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) }, 
                                        onClick = { onBlockChange(block.copy(mode = mode)); modeExpanded = false }
                                    )
                                }
                            }
                        }
                        
                        val prefix = if (nodeId.isNotBlank()) "$nodeId." else ""
                        
                        val occupiedSuffix = block.lcc_event_occupied
                        val occupiedFull = if (occupiedSuffix.isBlank()) "" else prefix + occupiedSuffix
                        val isOccupiedValid = occupiedFull.isBlank() || LccNode.parseEventId(occupiedFull).length == 16
                        OutlinedTextField(
                            value = occupiedSuffix,
                            onValueChange = { onBlockChange(block.copy(lcc_event_occupied = it)) },
                            label = { Text("Event ID (Occupied)") },
                            prefix = { Text(prefix, color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = !isOccupiedValid,
                            supportingText = if (!isOccupiedValid) { { Text("Invalid event format") } } else { { Text("Parsed: ${LccNode.parseEventId(occupiedFull)}") } },
                            colors = brassTextFieldColors()
                        )
                        
                        val emptySuffix = block.lcc_event_empty
                        val emptyFull = if (emptySuffix.isBlank()) "" else prefix + emptySuffix
                        val isEmptyValid = emptyFull.isBlank() || LccNode.parseEventId(emptyFull).length == 16
                        OutlinedTextField(
                            value = emptySuffix,
                            onValueChange = { onBlockChange(block.copy(lcc_event_empty = it)) },
                            label = { Text("Event ID (Empty)") },
                            prefix = { Text(prefix, color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = !isEmptyValid,
                            supportingText = if (!isEmptyValid) { { Text("Invalid event format") } } else { { Text("Parsed: ${LccNode.parseEventId(emptyFull)}") } },
                            colors = brassTextFieldColors()
                        )
                    }
                }
            }
        }
    }
}

/**
 * A text field tailored for integer inputs. Safely handles empty states, negative signs,
 * and parsing errors while exposing a standardized [Int] value to its caller.
 */
@Composable
fun IntTextField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    var text by remember { mutableStateOf(if (value == -1) "" else value.toString()) }
    
    LaunchedEffect(value) {
        val parsed = text.toIntOrNull() ?: if (text.isBlank() || text == "-") -1 else null
        if (parsed != value && text.isNotBlank() && text != "-") {
            text = value.toString()
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.isEmpty() || newText == "-" || newText.toIntOrNull() != null) {
                text = newText
                val parsed = newText.toIntOrNull()
                if (parsed != null) {
                    onValueChange(parsed)
                } else if (newText.isEmpty() || newText == "-") {
                    onValueChange(-1) // Default to -1 (none) when empty
                }
            }
        },
        label = { Text(label) },
        modifier = modifier,
        colors = colors
    )
}
