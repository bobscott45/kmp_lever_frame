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
 * User interface components for managing global system-wide configuration settings.
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun brassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LeverFrameTheme.Colors.Brass,
    unfocusedBorderColor = LeverFrameTheme.Colors.Brass.copy(alpha = 0.5f),
    focusedLabelColor = LeverFrameTheme.Colors.Brass,
    unfocusedLabelColor = LeverFrameTheme.Colors.Brass.copy(alpha = 0.8f),
    cursorColor = LeverFrameTheme.Colors.Brass
)

/**
 * The main settings panel encompassing network, behavior, developer, and JMRI options.
 */
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
fun SettingSwitchRow(
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
