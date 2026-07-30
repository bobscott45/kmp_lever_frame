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
 * Provides the expansive settings UI for both system-wide parameters (LCC, JMRI, etc.)
 * and frame-specific configurations (levers, blocks, logic rules, and schematic layout).
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

/**
 * The main configuration management screen, providing access to either global system settings
 * or frame-specific configurations based on the initialMode. Acts as the root for detailed editors.
 */
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

