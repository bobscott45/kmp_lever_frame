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
    onUpdateSystemConfig: (JsonConfig) -> Unit,
    onClose: () -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }
    val coroutineScope = rememberCoroutineScope()
    
    // Main navigation tabs: 0 for System Settings, 1 for Frames
    var selectedMainTab by rememberSaveable { mutableStateOf(0) }
    
    // Sub-navigation for the selected Frame
    var selectedFrameIndex by rememberSaveable { mutableStateOf(0) }
    if (selectedFrameIndex >= config.tabs.size && config.tabs.isNotEmpty()) {
        selectedFrameIndex = config.tabs.size - 1
    }
    var selectedFrameConfigTab by rememberSaveable { mutableStateOf(0) }

    val clipboardManager = LocalClipboardManager.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showSaveWarning by remember { mutableStateOf(false) }
    var showResetWarning by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = LeverFrameTheme.Colors.Brass) },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text("✕", style = MaterialTheme.typography.titleLarge, color = LeverFrameTheme.Colors.Brass)
                    }
                },
                actions = {
                    TextButton(onClick = { 
                        if (isFilePickerAvailable) {
                            importConfigurationFile { json ->
                                if (json != null) {
                                    try {
                                        val importedConfig = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(json)
                                        config = importedConfig
                                    } catch (e: Exception) {
                                        println("Failed to import: ${e.message}")
                                    }
                                }
                            }
                        } else {
                            showImportDialog = true
                        }
                    }) {
                        Text("Import")
                    }
                    TextButton(onClick = { 
                        if (isFilePickerAvailable) {
                            try {
                                val jsonString = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), config)
                                exportConfigurationFile(jsonString)
                            } catch (e: Exception) {
                                println("Failed to export: ${e.message}")
                            }
                        } else {
                            showExportDialog = true
                        }
                    }) {
                        Text("Export")
                    }
                    val hasChanges = config != initialConfig
                    TextButton(
                        onClick = { showSaveWarning = true },
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
            // Main Top Navigation
            PrimaryTabRow(selectedTabIndex = selectedMainTab) {
                Tab(
                    selected = selectedMainTab == 0,
                    onClick = { selectedMainTab = 0 },
                    text = { Text("System", color = if (selectedMainTab == 0) LeverFrameTheme.Colors.Brass else androidx.compose.ui.graphics.Color.White) }
                )
                Tab(
                    selected = selectedMainTab == 1,
                    onClick = { selectedMainTab = 1 },
                    text = { Text("Frames", color = if (selectedMainTab == 1) LeverFrameTheme.Colors.Brass else androidx.compose.ui.graphics.Color.White) }
                )
            }

            if (selectedMainTab == 0) {
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
                            onClick = { showResetWarning = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reset ALL Settings & Frames to Factory Defaults", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            } else {
                // FRAMES & LEVERS VIEW
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
                        }

                        // Content for the selected frame
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
                                        IconButton(onClick = {
                                            val newTabs = config.tabs.toMutableList()
                                            newTabs.removeAt(selectedFrameIndex)
                                            config = config.copy(tabs = newTabs)
                                            if (selectedFrameIndex >= newTabs.size && newTabs.isNotEmpty()) {
                                                selectedFrameIndex = newTabs.size - 1
                                            }
                                        }) {
                                            Text("✕", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
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
                            }

                            if (selectedFrameConfigTab == 2) {

                            // Blocks and Levers item headers are no longer needed
                            // as they are split into sub-tabs

                            itemsIndexed(tab.blocks) { blockIndex, block ->
                                MobileBlockCard(
                                    nodeId = config.node_id,
                                    blockIndex = blockIndex,
                                    block = block,
                                    onBlockChange = { newBlock ->
                                        val newTabs = config.tabs.toMutableList()
                                        val newBlocks = newTabs[selectedFrameIndex].blocks.toMutableList()
                                        newBlocks[blockIndex] = newBlock
                                        newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(blocks = newBlocks)
                                        config = config.copy(tabs = newTabs)
                                    },
                                    onDelete = {
                                        val newTabs = config.tabs.toMutableList()
                                        val newBlocks = newTabs[selectedFrameIndex].blocks.toMutableList()
                                        newBlocks.removeAt(blockIndex)
                                        newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(blocks = newBlocks)
                                        config = config.copy(tabs = newTabs)
                                    }
                                )
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
                                    MobileLeverCard(
                                        nodeId = config.node_id,
                                        leverIndex = leverIndex,
                                        lever = lever,
                                        onLeverChange = { newLever ->
                                            val newTabs = config.tabs.toMutableList()
                                            val newLevers = newTabs[selectedFrameIndex].levers.toMutableList()
                                            newLevers[leverIndex] = newLever
                                            newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(levers = newLevers)
                                            config = config.copy(tabs = newTabs)
                                        },
                                        onDelete = {
                                            val newTabs = config.tabs.toMutableList()
                                            val newLevers = newTabs[selectedFrameIndex].levers.toMutableList()
                                            newLevers.removeAt(leverIndex)
                                            newTabs[selectedFrameIndex] = newTabs[selectedFrameIndex].copy(levers = newLevers)
                                            config = config.copy(tabs = newTabs)
                                        }
                                    )
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
                    } else {
                        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No Frames configured.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    onUpdateSystemConfig(config)
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

    if (showResetWarning) {
        AlertDialog(
            onDismissRequest = { showResetWarning = false },
            title = { Text("Reset to Defaults", color = MaterialTheme.colorScheme.error) },
            text = { Text("WARNING: This will completely erase ALL System Settings and ALL Frame configurations (including levers and blocks) and replace them with the factory default configuration.\n\nThis cannot be undone. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    showResetWarning = false
                    try {
                        config = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(ConfigManager.defaultPrototypicalConfigJson)
                    } catch (e: Exception) {
                        println("Failed to reset: ${e.message}")
                    }
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetWarning = false }) { Text("Cancel") }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Configuration") },
            text = {
                val jsonString = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), config)
                OutlinedTextField(
                    value = jsonString,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val jsonString = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), config)
                    clipboardManager.setText(AnnotatedString(jsonString))
                    showExportDialog = false
                }) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Close") }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Configuration") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("Paste JSON here") },
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val importedConfig = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(importText)
                        config = importedConfig
                        showImportDialog = false
                        importText = ""
                    } catch (e: Exception) {
                        println("Failed to import: ${e.message}")
                    }
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportDialog = false
                    importText = ""
                }) { Text("Cancel") }
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
fun MobileLeverCard(
    nodeId: String,
    leverIndex: Int,
    lever: JsonLever,
    onLeverChange: (JsonLever) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
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
                    Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.titleMedium)
                },
                modifier = Modifier.clickable { expanded = !expanded }
            )

            if (expanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    // Basic Info Group
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Basic Info", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
                                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                                    Text("✕", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                                }
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
                        }
                    }

                    // LCC Events Group
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("LCC Events (Optional)", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
                            
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

                    // Interlocking Rules Group
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Interlocking Rules", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
                                TextButton(onClick = {
                                    val newRules = lever.interlocking.toMutableList()
                                    newRules.add(JsonInterlocking(target = 0, state = "NORMAL"))
                                    onLeverChange(lever.copy(interlocking = newRules))
                                }) {
                                    Text("＋ Add Rule")
                                }
                            }

                            lever.interlocking.forEachIndexed { rIndex, rule ->
                                MobileRuleCard(
                                    ruleIndex = rIndex,
                                    rule = rule,
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
    onRuleChange: (JsonInterlocking) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Rule ${ruleIndex + 1}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp).offset(x = 4.dp)) {
                    Text("✕", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }, modifier = Modifier.weight(1f)) {
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
                
                IntTextField(
                    value = rule.target,
                    onValueChange = { onRuleChange(rule.copy(target = it)) },
                    label = "Index",
                    modifier = Modifier.weight(1f)
                )

                var stateExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = stateExpanded, onExpandedChange = { stateExpanded = !stateExpanded }, modifier = Modifier.weight(1.5f)) {
                    OutlinedTextField(
                        value = rule.state,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("State") },
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
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var altTypeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = altTypeExpanded, onExpandedChange = { altTypeExpanded = !altTypeExpanded }, modifier = Modifier.weight(1f)) {
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

                IntTextField(
                    value = rule.alt_target,
                    onValueChange = { onRuleChange(rule.copy(alt_target = it)) },
                    label = "Alt Idx",
                    modifier = Modifier.weight(1f)
                )

                var altStateExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = altStateExpanded, onExpandedChange = { altStateExpanded = !altStateExpanded }, modifier = Modifier.weight(1.5f)) {
                    OutlinedTextField(
                        value = rule.alt_state,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alt State") },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileBlockCard(
    nodeId: String,
    blockIndex: Int,
    block: JsonBlock,
    onBlockChange: (JsonBlock) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
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
                    Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.titleMedium)
                },
                modifier = Modifier.clickable { expanded = !expanded }
            )

            if (expanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    // Basic Info Group
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Basic Info", style = MaterialTheme.typography.titleSmall, color = LeverFrameTheme.Colors.Brass)
                                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                                    Text("✕", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                                }
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
}

@Composable
fun IntTextField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toIntOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label) },
        modifier = modifier
    )
}
