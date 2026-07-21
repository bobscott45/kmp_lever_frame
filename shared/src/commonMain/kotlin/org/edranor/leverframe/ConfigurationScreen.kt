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
package org.edranor.leverframe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ConfigurationScreen(
    initialConfig: JsonConfig,
    initialMode: ConfigMode,
    initialSelectedFrameIndex: Int = 0,
    initialEditingLeverIndex: Int? = null,
    onUpdateSystemConfig: (JsonConfig, Boolean) -> Unit,
    onClose: () -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }
    val coroutineScope = rememberCoroutineScope()
    
    // Main navigation is now controlled by initialMode
    
    // Sub-navigation for the selected Frame
    var selectedFrameIndex by rememberSaveable { mutableStateOf(initialSelectedFrameIndex) }
    if (selectedFrameIndex >= config.tabs.size && config.tabs.isNotEmpty()) {
        selectedFrameIndex = config.tabs.size - 1
    }
    var selectedFrameConfigTab by rememberSaveable { mutableStateOf(0) }
    var editingLeverIndex by rememberSaveable { mutableStateOf(initialEditingLeverIndex) }
    var editingBlockIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    var showSaveWarning by remember { mutableStateOf(false) }
    var showSystemResetWarning by remember { mutableStateOf(false) }
    var showFramesResetWarning by remember { mutableStateOf(false) }
    var showFrameDeleteWarning by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (editingLeverIndex != null) {
                        val frameName = config.tabs.getOrNull(selectedFrameIndex)?.name ?: "Frame"
                        Text("$frameName - Lever ${editingLeverIndex!! + 1}", color = LeverFrameTheme.Colors.Brass)
                    } else if (editingBlockIndex != null) {
                        val frameName = config.tabs.getOrNull(selectedFrameIndex)?.name ?: "Frame"
                        Text("$frameName - Block ${editingBlockIndex!! + 1}", color = LeverFrameTheme.Colors.Brass)
                    } else {
                        Text(if (initialMode == ConfigMode.SYSTEM) "System Settings" else "Frames", color = LeverFrameTheme.Colors.Brass) 
                    }
                },
                navigationIcon = {
                    if (editingLeverIndex != null) {
                        TextButton(onClick = { editingLeverIndex = null }) {
                            Text("←", style = MaterialTheme.typography.titleLarge, color = LeverFrameTheme.Colors.Brass)
                        }
                    } else if (editingBlockIndex != null) {
                        TextButton(onClick = { editingBlockIndex = null }) {
                            Text("←", style = MaterialTheme.typography.titleLarge, color = LeverFrameTheme.Colors.Brass)
                        }
                    } else {
                        TextButton(onClick = onClose) {
                            Text("✕", style = MaterialTheme.typography.titleLarge, color = LeverFrameTheme.Colors.Brass)
                        }
                    }
                },
                actions = {
                    val hasChanges = config != initialConfig
                    val onlyRulesChanged = hasChanges && config.withoutRules() == initialConfig.withoutRules()
                    TextButton(
                        onClick = { 
                            if (onlyRulesChanged) {
                                onUpdateSystemConfig(config, true)
                                onClose()
                            } else {
                                showSaveWarning = true 
                            }
                        },
                        enabled = hasChanges
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (initialMode == ConfigMode.SYSTEM) {
                // SYSTEM SETTINGS VIEW
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SystemSettingsSection(config) { config = it }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showSystemResetWarning = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reset System Settings to Factory Defaults", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            } else {
                // FRAMES & LEVERS VIEW
                if (editingLeverIndex != null) {
                    val tab = config.tabs[selectedFrameIndex]
                    val lever = tab.levers[editingLeverIndex!!]
                    LeverDetailScreen(
                        nodeId = config.node_id,
                        leverIndex = editingLeverIndex!!,
                        lever = lever,
                        allLevers = tab.levers,
                        allBlocks = tab.blocks,
                        onLeverChange = { newLever ->
                            val newTabs = config.tabs.toMutableList()
                            val newLevers = newTabs[selectedFrameIndex].levers.toMutableList()
                            newLevers[editingLeverIndex!!] = newLever
                            newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(levers = newLevers)
                            config = config.copy(tabs = newTabs)
                        },
                        onDelete = {
                            val newTabs = config.tabs.toMutableList()
                            val newLevers = newTabs[selectedFrameIndex].levers.toMutableList()
                            newLevers.removeAt(editingLeverIndex!!)
                            newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(levers = newLevers)
                            config = config.copy(tabs = newTabs)
                            editingLeverIndex = null
                        }
                    )
                } else if (editingBlockIndex != null) {
                    val tab = config.tabs[selectedFrameIndex]
                    val block = tab.blocks[editingBlockIndex!!]
                    BlockDetailScreen(
                        nodeId = config.node_id,
                        blockIndex = editingBlockIndex!!,
                        block = block,
                        onBlockChange = { newBlock ->
                            val newTabs = config.tabs.toMutableList()
                            val newBlocks = newTabs[selectedFrameIndex].blocks.toMutableList()
                            newBlocks[editingBlockIndex!!] = newBlock
                            newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(blocks = newBlocks)
                            config = config.copy(tabs = newTabs)
                        },
                        onDelete = {
                            val newTabs = config.tabs.toMutableList()
                            val newBlocks = newTabs[selectedFrameIndex].blocks.toMutableList()
                            newBlocks.removeAt(editingBlockIndex!!)
                            newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(blocks = newBlocks)
                            config = config.copy(tabs = newTabs)
                            editingBlockIndex = null
                        }
                    )
                } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Frame Selection Dropdown
                    var frameSelectorExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = frameSelectorExpanded,
                            onExpandedChange = { frameSelectorExpanded = !frameSelectorExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = config.tabs.getOrNull(selectedFrameIndex)?.name ?: "No Frames",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Selected Frame") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frameSelectorExpanded) },
                                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                colors = brassTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = frameSelectorExpanded,
                                onDismissRequest = { frameSelectorExpanded = false }
                            ) {
                                config.tabs.forEachIndexed { index, tab ->
                                    DropdownMenuItem(
                                        text = { Text(tab.name) },
                                        onClick = {
                                            selectedFrameIndex = index
                                            frameSelectorExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        TextButton(onClick = {
                            val newTabs = config.tabs.toMutableList()
                            newTabs.add(JsonTab(name = "New Frame"))
                            config = config.copy(tabs = newTabs)
                            selectedFrameIndex = newTabs.size - 1
                        }) {
                            Text("＋ Add")
                        }
                    }

                    if (config.tabs.isNotEmpty()) {
                        TabRow(
                            selectedTabIndex = selectedFrameConfigTab,
                            containerColor = Color.Transparent,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Tab(selected = selectedFrameConfigTab == 0, onClick = { selectedFrameConfigTab = 0 }, text = { Text("Settings") })
                            Tab(selected = selectedFrameConfigTab == 1, onClick = { selectedFrameConfigTab = 1 }, text = { Text("Levers") })
                            Tab(selected = selectedFrameConfigTab == 2, onClick = { selectedFrameConfigTab = 2 }, text = { Text("Blocks") })
                            Tab(selected = selectedFrameConfigTab == 3, onClick = { selectedFrameConfigTab = 3 }, text = { Text("Schematic") })
                        }

                        // Content for the selected frame
                        if (selectedFrameConfigTab == 3) {
                            SchematicEditorScreen(
                                tabDef = config.tabs[selectedFrameIndex],
                                onTabDefChange = { newTab ->
                                    val newTabs = config.tabs.toMutableList()
                                    newTabs[selectedFrameIndex] = newTab
                                    config = config.copy(tabs = newTabs)
                                },
                                modifier = Modifier.fillMaxSize().weight(1f)
                            )
                        } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val tab = config.tabs[selectedFrameIndex]

                            if (selectedFrameConfigTab == 0) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = tab.name,
                                            onValueChange = { newName ->
                                                val newTabs = config.tabs.toMutableList()
                                                newTabs[selectedFrameIndex] = tab.copy(name = newName)
                                                config = config.copy(tabs = newTabs)
                                            },
                                            label = { Text("Frame Title") },
                                            modifier = Modifier.weight(1f).padding(end = 16.dp)
                                        )
                                        TextButton(onClick = { showFrameDeleteWarning = true }) {
                                            Text("✕ Delete", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        IntTextField(
                                            value = tab.label_lines,
                                            onValueChange = { 
                                                val newTabs = config.tabs.toMutableList()
                                                newTabs[selectedFrameIndex] = tab.copy(label_lines = it)
                                                config = config.copy(tabs = newTabs)
                                            },
                                            label = "Lever Label Lines",
                                            modifier = Modifier.weight(1f)
                                        )
                                        IntTextField(
                                            value = tab.label_line_height,
                                            onValueChange = { 
                                                val newTabs = config.tabs.toMutableList()
                                                newTabs[selectedFrameIndex] = tab.copy(label_line_height = it)
                                                config = config.copy(tabs = newTabs)
                                            },
                                            label = "Lever Line Height",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        IntTextField(
                                            value = tab.block_label_size,
                                            onValueChange = { 
                                                val newTabs = config.tabs.toMutableList()
                                                newTabs[selectedFrameIndex] = tab.copy(block_label_size = it)
                                                config = config.copy(tabs = newTabs)
                                            },
                                            label = "Block Font Size",
                                            modifier = Modifier.weight(1f)
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Block Layout", style = MaterialTheme.typography.bodySmall, color = LeverFrameTheme.Colors.Brass)
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
                                                    RadioButton(
                                                        selected = tab.block_layout == "HORIZONTAL",
                                                        onClick = {
                                                            val newTabs = config.tabs.toMutableList()
                                                            newTabs[selectedFrameIndex] = tab.copy(block_layout = "HORIZONTAL")
                                                            config = config.copy(tabs = newTabs)
                                                        }
                                                    )
                                                    Text("Horizontal", style = MaterialTheme.typography.bodyMedium)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
                                                    RadioButton(
                                                        selected = tab.block_layout == "VERTICAL",
                                                        onClick = {
                                                            val newTabs = config.tabs.toMutableList()
                                                            newTabs[selectedFrameIndex] = tab.copy(block_layout = "VERTICAL")
                                                            config = config.copy(tabs = newTabs)
                                                        }
                                                    )
                                                    Text("Vertical", style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f).clickable {
                                                val newTabs = config.tabs.toMutableList()
                                                newTabs[selectedFrameIndex] = tab.copy(show_lever_numbers = !tab.show_lever_numbers)
                                                config = config.copy(tabs = newTabs)
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = tab.show_lever_numbers,
                                                onCheckedChange = { 
                                                    val newTabs = config.tabs.toMutableList()
                                                    newTabs[selectedFrameIndex] = tab.copy(show_lever_numbers = it)
                                                    config = config.copy(tabs = newTabs)
                                                }
                                            )
                                            Text("Show Lever Numbers", modifier = Modifier.padding(start = 8.dp))
                                        }
                                        Row(
                                            modifier = Modifier.weight(1f).clickable {
                                                val newTabs = config.tabs.toMutableList()
                                                newTabs[selectedFrameIndex] = tab.copy(show_block_numbers = !tab.show_block_numbers)
                                                config = config.copy(tabs = newTabs)
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = tab.show_block_numbers,
                                                onCheckedChange = { 
                                                    val newTabs = config.tabs.toMutableList()
                                                    newTabs[selectedFrameIndex] = tab.copy(show_block_numbers = it)
                                                    config = config.copy(tabs = newTabs)
                                                }
                                            )
                                            Text("Show Block Numbers", modifier = Modifier.padding(start = 8.dp))
                                        }
                                    }
                                }
                            }

                            if (selectedFrameConfigTab == 2) {

                            // Blocks and Levers item headers are no longer needed
                            // as they are split into sub-tabs

                            itemsIndexed(tab.blocks) { blockIndex, block ->
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth().clickable { editingBlockIndex = blockIndex }
                                ) {
                                    ListItem(
                                        headlineContent = { Text(block.label.replace("\n", " ").takeIf { it.isNotBlank() } ?: "Unnamed Block", style = MaterialTheme.typography.bodyMedium) },
                                        leadingContent = {
                                            Box(
                                                modifier = Modifier.size(32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("${blockIndex + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        trailingContent = {
                                            Text("→", style = MaterialTheme.typography.titleMedium)
                                        }
                                    )
                                }
                            }

                            item {
                                Button(
                                    onClick = {
                                        val newTabs = config.tabs.toMutableList()
                                        val newBlocks = newTabs[selectedFrameIndex].blocks.toMutableList()
                                        newBlocks.add(JsonBlock(label = "New Block"))
                                        newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(blocks = newBlocks)
                                        config = config.copy(tabs = newTabs)
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                ) {
                                    Text("＋ Add Block")
                                }
                            }

                            }

                            if (selectedFrameConfigTab == 1) {
                                itemsIndexed(tab.levers) { leverIndex, lever ->
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth().clickable { editingLeverIndex = leverIndex }
                                    ) {
                                        ListItem(
                                            headlineContent = { Text(lever.label.replace("\n", " ").takeIf { it.isNotBlank() } ?: "Unnamed Lever", style = MaterialTheme.typography.bodyMedium) },
                                            supportingContent = { Text(lever.type) },
                                            leadingContent = {
                                                Box(
                                                    modifier = Modifier.size(32.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("${leverIndex + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                                }
                                            },
                                            trailingContent = {
                                                Text("→", style = MaterialTheme.typography.titleMedium)
                                            }
                                        )
                                    }
                                }

                                item {
                                    Button(
                                        onClick = {
                                            val newTabs = config.tabs.toMutableList()
                                            val newLevers = newTabs[selectedFrameIndex].levers.toMutableList()
                                            newLevers.add(JsonLever(label = "SPARE", type = "SPARE"))
                                            newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(levers = newLevers)
                                            config = config.copy(tabs = newTabs)
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                                    ) {
                                        Text("＋ Add Lever")
                                    }
                                }
                            }
                        }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No Frames configured.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    OutlinedButton(
                        onClick = { showFramesResetWarning = true },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset ALL Frames to Factory Defaults", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                }
            }
        }
    }

    if (showSaveWarning) {
        AlertDialog(
            onDismissRequest = { showSaveWarning = false },
            title = { Text("Save Configuration") },
            text = { Text("Saving configuration changes will reset the lever frame state to its default. Proceed?") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveWarning = false
                    onUpdateSystemConfig(config, false)
                    onClose()
                }) {
                    Text("Save & Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSystemResetWarning) {
        AlertDialog(
            onDismissRequest = { showSystemResetWarning = false },
            title = { Text("Reset System Settings", color = MaterialTheme.colorScheme.error) },
            text = { Text("WARNING: This will erase all System Settings and replace them with the factory defaults. Frame configurations will NOT be affected.\n\nThis cannot be undone. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    showSystemResetWarning = false
                    try {
                        val default = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(ConfigManager.defaultPrototypicalConfigJson)
                        config = config.copy(
                            node_id = default.node_id,
                            node_name = default.node_name,
                            jmri_hub_ip = default.jmri_hub_ip,
                            wifi_ssid = default.wifi_ssid,
                            wifi_password = default.wifi_password,
                            wifi_station_password = default.wifi_station_password,
                            conflict_policy = default.conflict_policy,
                            display_sleep_timeout_ms = default.display_sleep_timeout_ms,
                            restore_last_state = default.restore_last_state,
                            lcc_master = default.lcc_master,
                            enable_sound = default.enable_sound
                        )
                    } catch (e: Exception) {
                        println("Failed to reset system settings: ${e.message}")
                    }
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSystemResetWarning = false }) { Text("Cancel") }
            }
        )
    }

    if (showFramesResetWarning) {
        AlertDialog(
            onDismissRequest = { showFramesResetWarning = false },
            title = { Text("Reset Frames", color = MaterialTheme.colorScheme.error) },
            text = { Text("WARNING: This will completely erase ALL Frame configurations (including levers and blocks) and replace them with the factory default North Junction frame.\n\nThis cannot be undone. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    showFramesResetWarning = false
                    try {
                        val default = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(ConfigManager.defaultPrototypicalConfigJson)
                        config = config.copy(tabs = default.tabs)
                        selectedFrameIndex = 0
                        selectedFrameConfigTab = 0
                    } catch (e: Exception) {
                        println("Failed to reset frames: ${e.message}")
                    }
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFramesResetWarning = false }) { Text("Cancel") }
            }
        )
    }

    if (showFrameDeleteWarning) {
        AlertDialog(
            onDismissRequest = { showFrameDeleteWarning = false },
            title = { Text("Delete Frame") },
            text = { Text("Are you sure you want to delete this entire frame and all its configuration?") },
            confirmButton = {
                TextButton(onClick = {
                    showFrameDeleteWarning = false
                    val newTabs = config.tabs.toMutableList()
                    newTabs.removeAt(selectedFrameIndex)
                    config = config.copy(tabs = newTabs)
                    if (selectedFrameIndex >= newTabs.size && newTabs.isNotEmpty()) {
                        selectedFrameIndex = newTabs.size - 1
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFrameDeleteWarning = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun brassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LeverFrameTheme.Colors.Brass,
    unfocusedBorderColor = LeverFrameTheme.Colors.Brass.copy(alpha = 0.5f),
    focusedLabelColor = LeverFrameTheme.Colors.Brass,
    unfocusedLabelColor = LeverFrameTheme.Colors.Brass.copy(alpha = 0.8f),
    cursorColor = LeverFrameTheme.Colors.Brass
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsSection(config: JsonConfig, onConfigChange: (JsonConfig) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = config.node_name,
                onValueChange = { onConfigChange(config.copy(node_name = it)) },
                label = { Text("Node Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = brassTextFieldColors()
            )
            OutlinedTextField(
                value = config.node_id,
                onValueChange = { onConfigChange(config.copy(node_id = it)) },
                label = { Text("Node ID") },
                modifier = Modifier.fillMaxWidth(),
                colors = brassTextFieldColors()
            )
            OutlinedTextField(
                value = config.jmri_hub_ip,
                onValueChange = { onConfigChange(config.copy(jmri_hub_ip = it)) },
                label = { Text("JMRI OPENLCB/LCC HUB IP ADDRESS (optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = brassTextFieldColors()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Restore Last State", style = MaterialTheme.typography.bodyLarge, color = LeverFrameTheme.Colors.Brass)
                Switch(
                    checked = config.restore_last_state,
                    onCheckedChange = { onConfigChange(config.copy(restore_last_state = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LeverFrameTheme.Colors.PaleBlue
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LCC Master", style = MaterialTheme.typography.bodyLarge, color = LeverFrameTheme.Colors.Brass)
                Switch(
                    checked = config.lcc_master,
                    onCheckedChange = { onConfigChange(config.copy(lcc_master = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LeverFrameTheme.Colors.PaleBlue
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Sound", style = MaterialTheme.typography.bodyLarge, color = LeverFrameTheme.Colors.Brass)
                Switch(
                    checked = config.enable_sound,
                    onCheckedChange = { onConfigChange(config.copy(enable_sound = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LeverFrameTheme.Colors.PaleBlue
                    )
                )
            }
            
            var policyExpanded by remember { mutableStateOf(false) }
            val policies = mapOf(1 to "Strict Local", 2 to "Override Allowed", 3 to "Accept & Warn")
            val currentPolicyName = policies[config.conflict_policy] ?: "Override Allowed"
            
            ExposedDropdownMenuBox(
                expanded = policyExpanded,
                onExpandedChange = { policyExpanded = !policyExpanded }
            ) {
                OutlinedTextField(
                    value = currentPolicyName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("External Event Policy") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = policyExpanded) },
                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    colors = brassTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = policyExpanded,
                    onDismissRequest = { policyExpanded = false }
                ) {
                    policies.forEach { (key, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onConfigChange(config.copy(conflict_policy = key))
                                policyExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeverDetailScreen(
    nodeId: String,
    leverIndex: Int,
    lever: JsonLever,
    allLevers: List<JsonLever>,
    allBlocks: List<JsonBlock>,
    onLeverChange: (JsonLever) -> Unit,
    onDelete: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Basic") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("LCC") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Rules") })
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedTab == 0) {
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
                                    title = { Text("Delete Lever") },
                                    text = { Text("Are you sure you want to delete this lever?") },
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

                            OutlinedTextField(
                                value = lever.label,
                                onValueChange = { onLeverChange(lever.copy(label = it)) },
                                label = { Text("Label") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = brassTextFieldColors()
                            )

                            var typeExpanded by remember { mutableStateOf(false) }
                            val types = listOf("HOME_SIGNAL", "DISTANT_SIGNAL", "POINTS", "FACING_POINTS", "BROWN", "GREEN", "SPARE")
                            ExposedDropdownMenuBox(
                                expanded = typeExpanded,
                                onExpandedChange = { typeExpanded = !typeExpanded }
                            ) {
                                OutlinedTextField(
                                    value = lever.type,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Lever Type") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                    colors = brassTextFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded = typeExpanded,
                                    onDismissRequest = { typeExpanded = false }
                                ) {
                                    types.forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text(t) },
                                            onClick = {
                                                onLeverChange(lever.copy(type = t))
                                                typeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                        }
                    }
                }
            }
            
            if (selectedTab == 1) {
                item {
                    // LCC Events Group
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("LCC Configuration", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("LCC Enabled", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Switch(
                                    checked = lever.lcc_enabled,
                                    onCheckedChange = { onLeverChange(lever.copy(lcc_enabled = it)) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LeverFrameTheme.Colors.PaleBlue)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("LCC Events (Optional)", style = MaterialTheme.typography.bodyMedium, color = LeverFrameTheme.Colors.Brass)
                            
                            val prefix = if (nodeId.isNotBlank()) "$nodeId." else ""
                            
                            val normalSuffix = lever.lcc_event_normal
                            val normalFull = if (normalSuffix.isBlank()) "" else prefix + normalSuffix
                            val isNormalValid = normalFull.isBlank() || LccNode.parseEventId(normalFull).length == 16
                            OutlinedTextField(
                                value = normalSuffix,
                                onValueChange = { onLeverChange(lever.copy(lcc_event_normal = it)) },
                                label = { Text("Event ID (Normal)") },
                                prefix = { Text(prefix, color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                isError = !isNormalValid,
                                supportingText = if (!isNormalValid) { { Text("Invalid event format") } } else { { Text("Parsed: ${LccNode.parseEventId(normalFull)}") } },
                                colors = brassTextFieldColors()
                            )
                            
                            val reversedSuffix = lever.lcc_event_reversed
                            val reversedFull = if (reversedSuffix.isBlank()) "" else prefix + reversedSuffix
                            val isReversedValid = reversedFull.isBlank() || LccNode.parseEventId(reversedFull).length == 16
                            OutlinedTextField(
                                value = reversedSuffix,
                                onValueChange = { onLeverChange(lever.copy(lcc_event_reversed = it)) },
                                label = { Text("Event ID (Reversed)") },
                                prefix = { Text(prefix, color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                isError = !isReversedValid,
                                supportingText = if (!isReversedValid) { { Text("Invalid event format") } } else { { Text("Parsed: ${LccNode.parseEventId(reversedFull)}") } },
                                colors = brassTextFieldColors()
                            )
                        }
                    }
                }
            }
            
            if (selectedTab == 2) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Reverser", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text("(Return to Normal if rules fail)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Switch(
                                checked = lever.auto_reverser,
                                onCheckedChange = { onLeverChange(lever.copy(auto_reverser = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LeverFrameTheme.Colors.PaleBlue)
                            )
                        }
                    }
                    
                    // Interlocking Rules Group Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Interlocking Rules", style = MaterialTheme.typography.titleMedium, color = LeverFrameTheme.Colors.Brass)
                        TextButton(onClick = {
                            val newRules = lever.interlocking.toMutableList()
                            newRules.add(JsonInterlocking(target = 0, state = "NORMAL"))
                            onLeverChange(lever.copy(interlocking = newRules))
                        }) {
                            Text("＋ Add Rule")
                        }
                    }
                }
                
                itemsIndexed(lever.interlocking) { rIndex, rule ->
                    MobileRuleCard(
                        ruleIndex = rIndex,
                        rule = rule,
                        allLevers = allLevers,
                        allBlocks = allBlocks,
                        onRuleChange = { newRule ->
                            val newRules = lever.interlocking.toMutableList()
                            newRules[rIndex] = newRule
                            onLeverChange(lever.copy(interlocking = newRules))
                        },
                        onDelete = {
                            val newRules = lever.interlocking.toMutableList()
                            newRules.removeAt(rIndex)
                            onLeverChange(lever.copy(interlocking = newRules))
                        }
                    )
                }
                
                if (lever.interlocking.isEmpty()) {
                    item {
                        Text("No rules defined for this lever.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileRuleCard(
    ruleIndex: Int,
    rule: JsonInterlocking,
    allLevers: List<JsonLever>,
    allBlocks: List<JsonBlock>,
    onRuleChange: (JsonInterlocking) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Rule ${ruleIndex + 1}", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onDelete, modifier = Modifier.offset(x = 8.dp)) {
                    Text("✕ Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            
            Text("Primary Condition", style = MaterialTheme.typography.labelMedium, color = LeverFrameTheme.Colors.Brass)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }, modifier = Modifier.weight(1.5f)) {
                    OutlinedTextField(
                        value = rule.target_type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        colors = brassTextFieldColors()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf("LEVER", "BLOCK").forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { 
                                val newState = if (s == "BLOCK") "OCCUPIED" else "NORMAL"
                                onRuleChange(rule.copy(target_type = s, state = newState)); typeExpanded = false 
                            })
                        }
                    }
                }
                
                var targetExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = targetExpanded, onExpandedChange = { targetExpanded = !targetExpanded }, modifier = Modifier.weight(1.5f)) {
                    val labelText = if (rule.target_type == "BLOCK") {
                        allBlocks.getOrNull(rule.target)?.label?.replace("\n", " ") ?: "Block ${rule.target + 1}"
                    } else {
                        allLevers.getOrNull(rule.target)?.label?.replace("\n", " ") ?: "Lever ${rule.target + 1}"
                    }
                    
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        colors = brassTextFieldColors()
                    )
                    ExposedDropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
                        if (rule.target_type == "BLOCK") {
                            allBlocks.forEachIndexed { idx, blk ->
                                DropdownMenuItem(text = { Text("[${idx + 1}] ${blk.label.replace("\n", " ")}") }, onClick = { onRuleChange(rule.copy(target = idx)); targetExpanded = false })
                            }
                        } else {
                            allLevers.forEachIndexed { idx, lvr ->
                                DropdownMenuItem(text = { Text("[${idx + 1}] ${lvr.label.replace("\n", " ")}") }, onClick = { onRuleChange(rule.copy(target = idx)); targetExpanded = false })
                            }
                        }
                    }
                }
            }
            
            var stateExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = stateExpanded, onExpandedChange = { stateExpanded = !stateExpanded }, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = rule.state,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Required State") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    colors = brassTextFieldColors()
                )
                ExposedDropdownMenu(expanded = stateExpanded, onDismissRequest = { stateExpanded = false }) {
                    val options = if (rule.target_type == "BLOCK") listOf("OCCUPIED", "EMPTY") else listOf("NORMAL", "REVERSED")
                    options.forEach { s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = { onRuleChange(rule.copy(state = s)); stateExpanded = false })
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("OR Alternate Condition (Optional)", style = MaterialTheme.typography.labelMedium, color = LeverFrameTheme.Colors.Brass)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var altTypeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = altTypeExpanded, onExpandedChange = { altTypeExpanded = !altTypeExpanded }, modifier = Modifier.weight(1.5f)) {
                    OutlinedTextField(
                        value = rule.alt_target_type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alt Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = altTypeExpanded) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        colors = brassTextFieldColors()
                    )
                    ExposedDropdownMenu(expanded = altTypeExpanded, onDismissRequest = { altTypeExpanded = false }) {
                        listOf("LEVER", "BLOCK").forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { 
                                val newState = if (s == "BLOCK") "OCCUPIED" else "NORMAL"
                                onRuleChange(rule.copy(alt_target_type = s, alt_state = newState)); altTypeExpanded = false 
                            })
                        }
                    }
                }

                var altTargetExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = altTargetExpanded, onExpandedChange = { altTargetExpanded = !altTargetExpanded }, modifier = Modifier.weight(1.5f)) {
                    val labelText = if (rule.alt_target == -1) {
                        "None"
                    } else if (rule.alt_target_type == "BLOCK") {
                        allBlocks.getOrNull(rule.alt_target)?.label?.replace("\n", " ") ?: "Block ${rule.alt_target + 1}"
                    } else {
                        allLevers.getOrNull(rule.alt_target)?.label?.replace("\n", " ") ?: "Lever ${rule.alt_target + 1}"
                    }
                    
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alt Target") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = altTargetExpanded) },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        colors = brassTextFieldColors()
                    )
                    ExposedDropdownMenu(expanded = altTargetExpanded, onDismissRequest = { altTargetExpanded = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { onRuleChange(rule.copy(alt_target = -1)); altTargetExpanded = false })
                        if (rule.alt_target_type == "BLOCK") {
                            allBlocks.forEachIndexed { idx, blk ->
                                DropdownMenuItem(text = { Text("[${idx + 1}] ${blk.label.replace("\n", " ")}") }, onClick = { onRuleChange(rule.copy(alt_target = idx)); altTargetExpanded = false })
                            }
                        } else {
                            allLevers.forEachIndexed { idx, lvr ->
                                DropdownMenuItem(text = { Text("[${idx + 1}] ${lvr.label.replace("\n", " ")}") }, onClick = { onRuleChange(rule.copy(alt_target = idx)); altTargetExpanded = false })
                            }
                        }
                    }
                }
            }
            
            var altStateExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = altStateExpanded, onExpandedChange = { altStateExpanded = !altStateExpanded }, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = rule.alt_state,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Alt Required State") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = altStateExpanded) },
                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    colors = brassTextFieldColors()
                )
                ExposedDropdownMenu(expanded = altStateExpanded, onDismissRequest = { altStateExpanded = false }) {
                    val options = if (rule.alt_target_type == "BLOCK") listOf("OCCUPIED", "EMPTY") else listOf("NORMAL", "REVERSED")
                    options.forEach { s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = { onRuleChange(rule.copy(alt_state = s)); altStateExpanded = false })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDetailScreen(
    nodeId: String,
    blockIndex: Int,
    block: JsonBlock,
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

                        OutlinedTextField(
                            value = block.label,
                            onValueChange = { onBlockChange(block.copy(label = it)) },
                            label = { Text("Label") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = brassTextFieldColors()
                        )
                    }
                }
            }
            
            item {
                // LCC Events Group
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("LCC Events", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
                        
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

private fun JsonConfig.withoutRules(): JsonConfig {
    return this.copy(tabs = this.tabs.map { tab ->
        tab.copy(levers = tab.levers.map { lever ->
            lever.copy(interlocking = emptyList())
        })
    })
}
