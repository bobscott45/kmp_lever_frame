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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
    onUpdateSystemConfig: (JsonConfig, Boolean, Boolean) -> Unit,
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
    var isEditingSchematic by rememberSaveable { mutableStateOf(false) }

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
                    } else if (isEditingSchematic) {
                        TextButton(onClick = { isEditingSchematic = false }) {
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
                    val safeToUpdateSilently = hasChanges && config.withoutUiAndRules() == initialConfig.withoutUiAndRules()
                    TextButton(
                        onClick = { 
                            if (safeToUpdateSilently) {
                                onUpdateSystemConfig(config, true, false)
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
                    val parsedTabs = try {
                        ConfigManager.parseConfig(ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), config))
                    } catch (e: Exception) { emptyList() }
                    val currentTabDef = parsedTabs.getOrNull(selectedFrameIndex)?.second

                    LeverDetailScreen(
                        nodeId = config.node_id,
                        leverIndex = editingLeverIndex!!,
                        lever = lever,
                        allLevers = tab.levers,
                        allBlocks = tab.blocks,
                        ruleEditorMode = config.rule_editor_mode,
                        ruleDisplayMode = config.rule_display_mode,
                        currentTabDef = currentTabDef,
                        onLeverChange = { newLever ->
                            val newTabs = config.tabs.toMutableList()
                            val newLevers = newTabs[selectedFrameIndex].levers.toMutableList()
                            newLevers[editingLeverIndex!!] = newLever
                            newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(levers = newLevers)
                            config = config.copy(tabs = newTabs)
                        },
                        onDelete = {
                            val newTabs = config.tabs.toMutableList()
                            newTabs[selectedFrameIndex] = deleteLeverSafe(newTabs[selectedFrameIndex], editingLeverIndex!!)
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
                        allBlocks = tab.blocks,
                        onBlockChange = { newBlock ->
                            val newTabs = config.tabs.toMutableList()
                            val newBlocks = newTabs[selectedFrameIndex].blocks.toMutableList()
                            newBlocks[editingBlockIndex!!] = newBlock
                            newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(blocks = newBlocks)
                            config = config.copy(tabs = newTabs)
                        },
                        onDelete = {
                            val newTabs = config.tabs.toMutableList()
                            newTabs[selectedFrameIndex] = deleteBlockSafe(newTabs[selectedFrameIndex], editingBlockIndex!!)
                            config = config.copy(tabs = newTabs)
                            editingBlockIndex = null
                        }
                    )
                } else if (isEditingSchematic) {
                    SchematicEditorScreen(
                        tabDef = config.tabs[selectedFrameIndex],
                        onTabDefChange = { newTab ->
                            val newTabs = config.tabs.toMutableList()
                            newTabs[selectedFrameIndex] = newTab
                            config = config.copy(tabs = newTabs)
                        }
                    )
                } else {
                    FrameSetupView(
                        config = config,
                        selectedFrameIndex = selectedFrameIndex,
                        onSelectedFrameIndexChange = { selectedFrameIndex = it },
                        selectedFrameConfigTab = selectedFrameConfigTab,
                        onSelectedFrameConfigTabChange = { selectedFrameConfigTab = it },
                        onConfigChange = { config = it },
                        onEditLever = { editingLeverIndex = it },
                        onEditBlock = { editingBlockIndex = it },
                        onEditSchematic = { isEditingSchematic = true },
                        onShowFramesResetWarning = { showFramesResetWarning = true }
                    )
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
                    onUpdateSystemConfig(config, false, true)
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
                        val newConfig = config.copy(
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
                        onUpdateSystemConfig(newConfig, false, false)
                        onClose()
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
                        val newConfig = config.copy(tabs = default.tabs)
                        onUpdateSystemConfig(newConfig, false, true)
                        onClose()
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
            GlobalNetworkSettings(config, onConfigChange)
            BehaviorSettings(config, onConfigChange)
            JmriServerSettings(config, onConfigChange)
            DeveloperSettings(config, onConfigChange)
        }
    }
}

@Composable
private fun GlobalNetworkSettings(config: JsonConfig, onConfigChange: (JsonConfig) -> Unit) {
    Text("Global Network Settings", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BehaviorSettings(config: JsonConfig, onConfigChange: (JsonConfig) -> Unit) {
    Text("Behavior Settings", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
    SettingSwitchRow(
        label = "Simulation Mode", 
        checked = config.sim_mode,
        infoText = "If enabled, forces all block indicators to be manually clickable for interactive testing, overriding physical sensor settings."
    ) { onConfigChange(config.copy(sim_mode = it)) }

    SettingSwitchRow(
        label = "Restore Last State", 
        checked = config.restore_last_state,
        infoText = "If enabled, LeverFrame will remember the physical position of all levers between app sessions and restore them when restarted."
    ) { onConfigChange(config.copy(restore_last_state = it)) }
    
    SettingSwitchRow(
        label = "LCC Enabled", 
        checked = config.lcc_enabled,
        infoText = "If enabled, LeverFrame will broadcast and listen to Layout Command Control (LCC) network events. Requires a configured JMRI Hub or physical LCC connection."
    ) { onConfigChange(config.copy(lcc_enabled = it)) }
    
    SettingSwitchRow(
        label = "LCC Master", 
        checked = config.lcc_master,
        infoText = "If enabled, this instance of LeverFrame acts as the master authority for lever states, resolving conflicting state changes from the network."
    ) { onConfigChange(config.copy(lcc_master = it)) }
    SettingSwitchRow("Enable Sound", config.enable_sound) { onConfigChange(config.copy(enable_sound = it)) }
    
    var uiScaleText by remember { mutableStateOf(if (config.ui_scale <= 0.0f) "" else config.ui_scale.toString()) }
    
    OutlinedTextField(
        value = uiScaleText,
        onValueChange = { 
            uiScaleText = it
            val parsed = it.toFloatOrNull()
            if (parsed != null) {
                onConfigChange(config.copy(ui_scale = parsed))
            } else if (it.isEmpty()) {
                onConfigChange(config.copy(ui_scale = 0.0f))
            }
        },
        label = { Text("UI Scale (0 or empty for Auto/Runtime default)") },
        modifier = Modifier.fillMaxWidth(),
        colors = brassTextFieldColors()
    )
    
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
            policies.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onConfigChange(config.copy(conflict_policy = id))
                        policyExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun JmriServerSettings(config: JsonConfig, onConfigChange: (JsonConfig) -> Unit) {
    Text("JMRI / Server Settings", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
    OutlinedTextField(
        value = config.jmri_hub_ip,
        onValueChange = { onConfigChange(config.copy(jmri_hub_ip = it)) },
        label = { Text("JMRI OPENLCB/LCC HUB IP ADDRESS (optional)") },
        modifier = Modifier.fillMaxWidth(),
        colors = brassTextFieldColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperSettings(config: JsonConfig, onConfigChange: (JsonConfig) -> Unit) {
    Text("Developer Settings", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
    
    var displayModeExpanded by remember { mutableStateOf(false) }
    val displayModes = mapOf("LOCKING_TABLE" to "Locking Table", "CLAUSE_BUILDER" to "Clause Builder", "TEXT_FORMULA" to "Text Formula")
    ExposedDropdownMenuBox(expanded = displayModeExpanded, onExpandedChange = { displayModeExpanded = !displayModeExpanded }) {
        OutlinedTextField(
            value = displayModes[config.rule_display_mode] ?: config.rule_display_mode,
            onValueChange = {},
            readOnly = true,
            label = { Text("Default Rule Display Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = displayModeExpanded) },
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            colors = brassTextFieldColors()
        )
        ExposedDropdownMenu(expanded = displayModeExpanded, onDismissRequest = { displayModeExpanded = false }) {
            displayModes.forEach { (mode, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onConfigChange(config.copy(rule_display_mode = mode)); displayModeExpanded = false })
            }
        }
    }
    
    var editorModeExpanded by remember { mutableStateOf(false) }
    val editorModes = mapOf("CLAUSE_BUILDER" to "Clause Builder", "TEXT_FORMULA" to "Text Formula")
    ExposedDropdownMenuBox(expanded = editorModeExpanded, onExpandedChange = { editorModeExpanded = !editorModeExpanded }) {
        OutlinedTextField(
            value = editorModes[config.rule_editor_mode] ?: config.rule_editor_mode,
            onValueChange = {},
            readOnly = true,
            label = { Text("Default Rule Editor") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editorModeExpanded) },
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            colors = brassTextFieldColors()
        )
        ExposedDropdownMenu(expanded = editorModeExpanded, onDismissRequest = { editorModeExpanded = false }) {
            editorModes.forEach { (mode, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onConfigChange(config.copy(rule_editor_mode = mode)); editorModeExpanded = false })
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String, 
    checked: Boolean, 
    infoText: String? = null,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    textColor: androidx.compose.ui.graphics.Color = LeverFrameTheme.Colors.Brass,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onCheckedChange: (Boolean) -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo && infoText != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(label) },
            text = { Text(infoText) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("OK") }
            }
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = textStyle, color = textColor)
            if (infoText != null) {
                IconButton(onClick = { showInfo = true }) {
                    Text("ℹ️", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = LeverFrameTheme.Colors.PaleBlue
            )
        )
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
    ruleEditorMode: String,
    ruleDisplayMode: String,
    currentTabDef: TabDef?,
    onLeverChange: (JsonLever) -> Unit,
    onDelete: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isEditingRules by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, edgePadding = 0.dp) {
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
                                onValueChange = { if (it.length <= OpenLcbConstants.MAX_LABEL_LENGTH) onLeverChange(lever.copy(label = it)) },
                                label = { Text("Label") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = brassTextFieldColors()
                            )

                            var typeExpanded by remember { mutableStateOf(false) }
                            val types = listOf("HOME_SIGNAL", "DISTANT_SIGNAL", "POINTS", "FACING_POINTS", "BROWN", "GREEN", "SPARE")
                            val formatDisplay = { s: String -> s.lowercase().split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } } }
                            
                            ExposedDropdownMenuBox(
                                expanded = typeExpanded,
                                onExpandedChange = { typeExpanded = !typeExpanded }
                            ) {
                                OutlinedTextField(
                                    value = formatDisplay(lever.type),
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
                                            text = { Text(formatDisplay(t)) },
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
                    if (lever.type == "POINTS" || lever.type == "FACING_POINTS") {
                        Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("NX Route Cancellation Override", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text("Overrides the frame's default behavior for restoring points to Normal.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                var overrideExpanded by remember { mutableStateOf(false) }
                                val overrideOptions = mapOf(
                                    "DEFAULT" to "Follow Frame Default",
                                    "ALWAYS" to "Always Restore to Normal",
                                    "NEVER" to "Never Restore (Leave As-Is)"
                                )
                                val currentOverrideName = overrideOptions[lever.restore_override] ?: "Follow Frame Default"
                                
                                ExposedDropdownMenuBox(
                                    expanded = overrideExpanded,
                                    onExpandedChange = { overrideExpanded = !overrideExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = currentOverrideName,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = overrideExpanded) },
                                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                        colors = brassTextFieldColors()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = overrideExpanded,
                                        onDismissRequest = { overrideExpanded = false }
                                    ) {
                                        overrideOptions.forEach { (id, name) ->
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    onLeverChange(lever.copy(restore_override = id))
                                                    overrideExpanded = false
                                                }
                                            )
                                        }
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
                            
                            SettingSwitchRow(
                                label = "LCC Enabled",
                                checked = lever.lcc_enabled,
                                infoText = "If enabled, toggling this lever will broadcast the corresponding Normal/Reversed events to the LCC network.",
                                textStyle = MaterialTheme.typography.bodyMedium,
                                textColor = Color.White
                            ) { onLeverChange(lever.copy(lcc_enabled = it)) }

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
                        SettingSwitchRow(
                            label = "Auto-Reverser",
                            checked = lever.auto_reverser,
                            infoText = "If enabled, this lever will automatically return to the Normal position when any of its interlocking rules evaluate to false (e.g., if an interlocked block becomes occupied).",
                            textStyle = MaterialTheme.typography.bodyMedium,
                            textColor = Color.White,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        ) { onLeverChange(lever.copy(auto_reverser = it)) }
                    }
                    if (currentTabDef != null) {
                        var validationError by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(currentTabDef) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                val result = RuleValidator.validate(currentTabDef)
                                validationError = result.unreachableLevers[leverIndex]
                            }
                        }
                        
                        if (validationError != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("⚠️ Unreachable State", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(validationError!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                    
                    // Interlocking Rules Group Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Interlocking Rules", style = MaterialTheme.typography.titleMedium, color = LeverFrameTheme.Colors.Brass)
                        val currentMode = if (isEditingRules) ruleEditorMode else ruleDisplayMode
                        
                        if (currentMode == "CLAUSE_BUILDER") {
                            TextButton(onClick = {
                                val newRules = lever.interlocking.toMutableList()
                                newRules.add(JsonInterlocking(target = 0, state = "NORMAL"))
                                onLeverChange(lever.copy(interlocking = newRules))
                            }) {
                                Text("＋ Add Rule")
                            }
                        }
                        
                        if (ruleDisplayMode == "LOCKING_TABLE") {
                            if (isEditingRules) {
                                TextButton(onClick = { isEditingRules = false }) {
                                    Text("Done", color = LeverFrameTheme.Colors.Brass)
                                }
                            } else {
                                TextButton(onClick = { isEditingRules = true }) {
                                    Text("Edit Rules", color = LeverFrameTheme.Colors.Brass)
                                }
                            }
                        }
                    }
                }
                
                val currentMode = if (isEditingRules) ruleEditorMode else ruleDisplayMode
                
                if (currentMode == "LOCKING_TABLE") {
                    item {
                        LockingTableView(lever, allLevers, allBlocks)
                    }
                } else if (currentMode == "TEXT_FORMULA") {
                    item {
                        FormulaTextView(
                            ast = lever.ast_logic ?: migrateJsonInterlockingToAst(lever.interlocking),
                            onAstChange = { newAst -> onLeverChange(lever.copy(ast_logic = newAst, interlocking = emptyList())) }
                        )
                    }
                } else { // CLAUSE_BUILDER
                    itemsIndexed(lever.interlocking) { rIndex, rule ->
                        MobileRuleCard(
                            ruleIndex = rIndex,
                            rule = rule,
                            allLevers = allLevers,
                            allBlocks = allBlocks,
                            onRuleChange = { newRule ->
                                val newRules = lever.interlocking.toMutableList()
                                newRules[rIndex] = newRule
                                onLeverChange(lever.copy(interlocking = newRules, ast_logic = migrateJsonInterlockingToAst(newRules)))
                            },
                            onDelete = {
                                val newRules = lever.interlocking.toMutableList()
                                newRules.removeAt(rIndex)
                                onLeverChange(lever.copy(interlocking = newRules, ast_logic = migrateJsonInterlockingToAst(newRules)))
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Rule ${ruleIndex + 1}", color = LeverFrameTheme.Colors.Brass, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete) {
                    Text("✕", color = MaterialTheme.colorScheme.error)
                }
            }
            
            RuleTargetDropdown(
                label = "Target",
                targetType = rule.target_type,
                targetIndex = rule.target,
                allLevers = allLevers,
                allBlocks = allBlocks,
                onTargetSelected = { type, idx -> onRuleChange(rule.copy(target_type = type, target = idx)) }
            )
            
            RuleStateDropdown(
                label = "Required State",
                targetType = rule.target_type,
                state = rule.state,
                onStateSelected = { onRuleChange(rule.copy(state = it)) }
            )
            
            val hasAlt = rule.alt_target != -1
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = hasAlt,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onRuleChange(rule.copy(alt_target = 0, alt_target_type = "LEVER", alt_state = "NORMAL"))
                        } else {
                            onRuleChange(rule.copy(alt_target = -1, alt_target_type = "LEVER", alt_state = "NORMAL"))
                        }
                    },
                    colors = CheckboxDefaults.colors(checkedColor = LeverFrameTheme.Colors.Brass)
                )
                Text("OR Alternate Condition")
            }
            
            if (hasAlt) {
                RuleTargetDropdown(
                    label = "Alt Target",
                    targetType = rule.alt_target_type ?: "LEVER",
                    targetIndex = rule.alt_target ?: 0,
                    allLevers = allLevers,
                    allBlocks = allBlocks,
                    onTargetSelected = { type, idx -> onRuleChange(rule.copy(alt_target_type = type, alt_target = idx)) }
                )
                
                RuleStateDropdown(
                    label = "Alt Required State",
                    targetType = rule.alt_target_type ?: "LEVER",
                    state = rule.alt_state ?: "NORMAL",
                    onStateSelected = { onRuleChange(rule.copy(alt_state = it)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleTargetDropdown(
    label: String,
    targetType: String,
    targetIndex: Int,
    allLevers: List<JsonLever>,
    allBlocks: List<JsonBlock>,
    onTargetSelected: (String, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = if (targetType == "BLOCK") {
        "Block ${targetIndex + 1}" + (allBlocks.getOrNull(targetIndex)?.label?.let { " ($it)" } ?: "")
    } else {
        "Lever ${targetIndex + 1}" + (allLevers.getOrNull(targetIndex)?.label?.let { " ($it)" } ?: "")
    }
    
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            colors = brassTextFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            allLevers.forEachIndexed { i, l ->
                DropdownMenuItem(
                    text = { Text("Lever ${i + 1} (${l.label})") },
                    onClick = {
                        onTargetSelected("LEVER", i)
                        expanded = false
                    }
                )
            }
            Divider(color = Color.DarkGray)
            allBlocks.forEachIndexed { i, b ->
                DropdownMenuItem(
                    text = { Text("Block ${i + 1} (${b.label})") },
                    onClick = {
                        onTargetSelected("BLOCK", i)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleStateDropdown(
    label: String,
    targetType: String,
    state: String,
    onStateSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isBlock = targetType == "BLOCK"
    val states = if (isBlock) listOf("OCCUPIED", "CLEAR") else listOf("NORMAL", "REVERSED")
    val formatDisplay = { s: String -> s.lowercase().split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } } }
    
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = formatDisplay(state),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            colors = brassTextFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            states.forEach { st ->
                DropdownMenuItem(
                    text = { Text(formatDisplay(st)) },
                    onClick = {
                        onStateSelected(st)
                        expanded = false
                    }
                )
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
                            "HARDWARE_SENSOR" to "Hardware Sensor (Read-Only)",
                            "VIRTUAL_SENSOR" to "Virtual Sensor (Broadcasts)",
                            "LOCAL_ONLY" to "Local Only (No Broadcast)"
                        )
                        ExposedDropdownMenuBox(expanded = modeExpanded, onExpandedChange = { modeExpanded = !modeExpanded }) {
                            OutlinedTextField(
                                value = modes[block.mode] ?: block.mode,
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

