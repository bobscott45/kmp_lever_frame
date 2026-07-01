package org.example.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ConfigurationScreen(
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    var config by remember { mutableStateOf(ConfigManager.currentConfig) }
    val coroutineScope = rememberCoroutineScope()
    
    // Main navigation tabs: 0 for System Settings, 1 for Frames
    var selectedMainTab by remember { mutableStateOf(0) }
    
    // Sub-navigation for the selected Frame
    var selectedFrameIndex by remember { mutableStateOf(0) }
    if (selectedFrameIndex >= config.tabs.size && config.tabs.isNotEmpty()) {
        selectedFrameIndex = config.tabs.size - 1
    }

    val clipboard = LocalClipboard.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showSaveWarning by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = BrassColor) },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text("✕", style = MaterialTheme.typography.titleLarge, color = BrassColor)
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
                    val hasChanges = config != ConfigManager.currentConfig
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
                    text = { Text("System Settings", color = if (selectedMainTab == 0) BrassColor else androidx.compose.ui.graphics.Color.White) }
                )
                Tab(
                    selected = selectedMainTab == 1,
                    onClick = { selectedMainTab = 1 },
                    text = { Text("Frames & Levers", color = if (selectedMainTab == 1) BrassColor else androidx.compose.ui.graphics.Color.White) }
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
                }
            } else {
                // FRAMES & LEVERS VIEW
                Column(modifier = Modifier.fillMaxSize()) {
                    // Frame Selection Tabs (Secondary navigation)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Frames", style = MaterialTheme.typography.titleMedium, color = BrassColor, modifier = Modifier.padding(start = 8.dp))
                        TextButton(onClick = {
                            val newTabs = config.tabs.toMutableList()
                            newTabs.add(JsonTab(name = "New Frame"))
                            config = config.copy(tabs = newTabs)
                            selectedFrameIndex = newTabs.size - 1
                        }) {
                            Text("＋ Add Frame")
                        }
                    }

                    if (config.tabs.isNotEmpty()) {
                        SecondaryScrollableTabRow(
                            selectedTabIndex = selectedFrameIndex,
                            edgePadding = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            config.tabs.forEachIndexed { index, tab ->
                                Tab(
                                    selected = selectedFrameIndex == index,
                                    onClick = { selectedFrameIndex = index },
                                    text = { Text(tab.name, color = if (selectedFrameIndex == index) BrassColor else androidx.compose.ui.graphics.Color.White) }
                                )
                            }
                        }

                        // Content for the selected frame
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val tab = config.tabs[selectedFrameIndex]

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
                                    OutlinedTextField(
                                        value = tab.label_lines.toString(),
                                        onValueChange = { 
                                            val newTabs = config.tabs.toMutableList()
                                            newTabs[selectedFrameIndex] = tab.copy(label_lines = it.toIntOrNull() ?: 2)
                                            config = config.copy(tabs = newTabs)
                                        },
                                        label = { Text("Label Lines") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = tab.label_line_height.toString(),
                                        onValueChange = { 
                                            val newTabs = config.tabs.toMutableList()
                                            newTabs[selectedFrameIndex] = tab.copy(label_line_height = it.toIntOrNull() ?: 18)
                                            config = config.copy(tabs = newTabs)
                                        },
                                        label = { Text("Line Height") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            item {
                                Text("Levers", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }

                            itemsIndexed(tab.levers) { leverIndex, lever ->
                                MobileLeverCard(
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
                    val prevIp = ConfigManager.currentConfig.jmri_hub_ip
                    ConfigManager.currentConfig = config
                    coroutineScope.launch {
                        saveConfigToFile(ConfigManager.toJsonString())
                        if (prevIp != config.jmri_hub_ip) {
                            LccNode.initialize()
                        }
                        onSave()
                    }
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
                    coroutineScope.launch {
                        clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(AnnotatedString(jsonString)))
                    }
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
    focusedBorderColor = BrassColor,
    unfocusedBorderColor = BrassColor.copy(alpha = 0.5f),
    focusedLabelColor = BrassColor,
    unfocusedLabelColor = BrassColor.copy(alpha = 0.8f),
    cursorColor = BrassColor
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
                Text("Restore Last State", style = MaterialTheme.typography.bodyLarge, color = BrassColor)
                Switch(
                    checked = config.restore_last_state,
                    onCheckedChange = { onConfigChange(config.copy(restore_last_state = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PaleBlue
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LCC Master", style = MaterialTheme.typography.bodyLarge, color = BrassColor)
                Switch(
                    checked = config.lcc_master,
                    onCheckedChange = { onConfigChange(config.copy(lcc_master = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PaleBlue
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
    leverIndex: Int,
    lever: JsonLever,
    onLeverChange: (JsonLever) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            ListItem(
                headlineContent = { Text(lever.label.replace("\n", " ").takeIf { it.isNotBlank() } ?: "Unnamed Lever") },
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
                    
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Basic Info", style = MaterialTheme.typography.titleSmall, color = BrassColor)
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
                        Text("LCC Enabled", style = MaterialTheme.typography.bodyLarge, color = BrassColor)
                        Switch(
                            checked = lever.lcc_enabled,
                            onCheckedChange = { onLeverChange(lever.copy(lcc_enabled = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PaleBlue
                            )
                        )
                    }

                    Text("LCC Events (Optional)", style = MaterialTheme.typography.titleSmall, color = BrassColor, modifier = Modifier.padding(top = 8.dp))
                    
                    val isNormalValid = lever.lcc_event_normal.isBlank() || LccNode.parseEventId(lever.lcc_event_normal).length == 16
                    OutlinedTextField(
                        value = lever.lcc_event_normal,
                        onValueChange = { onLeverChange(lever.copy(lcc_event_normal = it)) },
                        label = { Text("Event ID (Normal)") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = !isNormalValid,
                        supportingText = if (!isNormalValid) { { Text("Invalid event format") } } else { { Text("Parsed: ${LccNode.parseEventId(lever.lcc_event_normal)}") } },
                        colors = brassTextFieldColors()
                    )
                    
                    val isReversedValid = lever.lcc_event_reversed.isBlank() || LccNode.parseEventId(lever.lcc_event_reversed).length == 16
                    OutlinedTextField(
                        value = lever.lcc_event_reversed,
                        onValueChange = { onLeverChange(lever.copy(lcc_event_reversed = it)) },
                        label = { Text("Event ID (Reversed)") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = !isReversedValid,
                        supportingText = if (!isReversedValid) { { Text("Invalid event format") } } else { { Text("Parsed: ${LccNode.parseEventId(lever.lcc_event_reversed)}") } },
                        colors = brassTextFieldColors()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Interlocking Rules", style = MaterialTheme.typography.titleSmall, color = BrassColor)
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
                OutlinedTextField(
                    value = rule.target.toString(),
                    onValueChange = { onRuleChange(rule.copy(target = it.toIntOrNull() ?: 0)) },
                    label = { Text("Target Index") },
                    modifier = Modifier.weight(1f),
                    colors = brassTextFieldColors()
                )

                var stateExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = stateExpanded, onExpandedChange = { stateExpanded = !stateExpanded }, modifier = Modifier.weight(1.5f)) {
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
                        listOf("NORMAL", "REVERSED").forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { onRuleChange(rule.copy(state = s)); stateExpanded = false })
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rule.alt_target.toString(),
                    onValueChange = { onRuleChange(rule.copy(alt_target = it.toIntOrNull() ?: -1)) },
                    label = { Text("Alt Target") },
                    modifier = Modifier.weight(1f),
                    colors = brassTextFieldColors()
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
                        listOf("NORMAL", "REVERSED").forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { onRuleChange(rule.copy(alt_state = s)); altStateExpanded = false })
                        }
                    }
                }
            }
        }
    }
}
