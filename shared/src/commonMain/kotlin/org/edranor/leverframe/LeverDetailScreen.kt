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
 * User interface component for editing individual levers, their behavior, and logical rules.
 */
package org.edranor.leverframe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A detailed editor screen that allows users to modify a single [JsonLever]'s properties,
 * configure its LCC events, set up auto-reversal, and author complex interlocking logic rules.
 */
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
                            val types = LeverType.entries.map { it.name }
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
                                    RestoreOverride.DEFAULT.name to "Follow Frame Default",
                                    RestoreOverride.ALWAYS.name to "Always Restore to Normal",
                                    RestoreOverride.NEVER.name to "Never Restore (Leave As-Is)"
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
            HorizontalDivider(color = Color.DarkGray)
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
