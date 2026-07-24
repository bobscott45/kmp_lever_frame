package org.edranor.leverframe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameSetupView(
    config: JsonConfig,
    selectedFrameIndex: Int,
    onSelectedFrameIndexChange: (Int) -> Unit,
    selectedFrameConfigTab: Int,
    onSelectedFrameConfigTabChange: (Int) -> Unit,
    onConfigChange: (JsonConfig) -> Unit,
    onEditLever: (Int) -> Unit,
    onEditBlock: (Int) -> Unit,
    onShowFramesResetWarning: () -> Unit
) {
    if (selectedFrameIndex < config.tabs.size) {
        val tab = config.tabs[selectedFrameIndex]
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
                        value = tab.name,
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
                        config.tabs.forEachIndexed { index, t ->
                            DropdownMenuItem(
                                text = { Text(t.name) },
                                onClick = {
                                    onSelectedFrameIndexChange(index)
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
                    onConfigChange(config.copy(tabs = newTabs))
                    onSelectedFrameIndexChange(newTabs.size - 1)
                }) {
                    Text("＋ New Frame", color = LeverFrameTheme.Colors.Brass)
                }
            }

            // Sub-tabs
            TabRow(
                selectedTabIndex = selectedFrameConfigTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = LeverFrameTheme.Colors.Brass
            ) {
                Tab(
                    selected = selectedFrameConfigTab == 0,
                    onClick = { onSelectedFrameConfigTabChange(0) },
                    text = { Text("General", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedFrameConfigTab == 1,
                    onClick = { onSelectedFrameConfigTabChange(1) },
                    text = { Text("Levers (${tab.levers.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedFrameConfigTab == 2,
                    onClick = { onSelectedFrameConfigTabChange(2) },
                    text = { Text("Blocks (${tab.blocks.size})", fontWeight = FontWeight.Bold) }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedFrameConfigTab == 0) {
                    frameGeneralSettingsTab(tab, selectedFrameIndex, config, onConfigChange)
                }
                if (selectedFrameConfigTab == 1) {
                    frameLeversTab(tab, selectedFrameIndex, config, onConfigChange, onEditLever)
                }
                if (selectedFrameConfigTab == 2) {
                    frameBlocksTab(tab, selectedFrameIndex, config, onConfigChange, onEditBlock)
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Frames configured.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.frameGeneralSettingsTab(
    tab: JsonTab,
    selectedFrameIndex: Int,
    config: JsonConfig,
    onConfigChange: (JsonConfig) -> Unit
) {
    item {
        OutlinedTextField(
            value = tab.name,
            onValueChange = { newName ->
                val newTabs = config.tabs.toMutableList()
                newTabs[selectedFrameIndex] = tab.copy(name = newName)
                onConfigChange(config.copy(tabs = newTabs))
            },
            label = { Text("Frame Name") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = brassTextFieldColors()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IntTextField(value = tab.label_lines, onValueChange = {
                val newTabs = config.tabs.toMutableList()
                newTabs[selectedFrameIndex] = tab.copy(label_lines = it)
                onConfigChange(config.copy(tabs = newTabs))
            }, label = "Lever Label Lines", modifier = Modifier.weight(1f))
            IntTextField(value = tab.label_line_height, onValueChange = {
                val newTabs = config.tabs.toMutableList()
                newTabs[selectedFrameIndex] = tab.copy(label_line_height = it)
                onConfigChange(config.copy(tabs = newTabs))
            }, label = "Label Line Height", modifier = Modifier.weight(1f))
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Checkbox(
                    checked = tab.show_lever_numbers,
                    onCheckedChange = {
                        val newTabs = config.tabs.toMutableList()
                        newTabs[selectedFrameIndex] = tab.copy(show_lever_numbers = it)
                        onConfigChange(config.copy(tabs = newTabs))
                    }
                )
                Text("Show Lever Numbers")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Checkbox(
                    checked = tab.use_short_codes,
                    onCheckedChange = {
                        val newTabs = config.tabs.toMutableList()
                        newTabs[selectedFrameIndex] = tab.copy(use_short_codes = it)
                        onConfigChange(config.copy(tabs = newTabs))
                    }
                )
                Text("Use Short Codes on Schematic")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Checkbox(
                    checked = tab.show_block_numbers,
                    onCheckedChange = {
                        val newTabs = config.tabs.toMutableList()
                        newTabs[selectedFrameIndex] = tab.copy(show_block_numbers = it)
                        onConfigChange(config.copy(tabs = newTabs))
                    }
                )
                Text("Show Block Numbers on Frame")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Checkbox(
                    checked = tab.use_short_codes_in_indicators,
                    onCheckedChange = {
                        val newTabs = config.tabs.toMutableList()
                        newTabs[selectedFrameIndex] = tab.copy(use_short_codes_in_indicators = it)
                        onConfigChange(config.copy(tabs = newTabs))
                    }
                )
                Text("Use Short Codes in Block Indicators")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.frameBlocksTab(
    tab: JsonTab,
    selectedFrameIndex: Int,
    config: JsonConfig,
    onConfigChange: (JsonConfig) -> Unit,
    onEditBlock: (Int) -> Unit
) {
    itemsIndexed(tab.blocks) { blockIndex, block ->
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().clickable { onEditBlock(blockIndex) }
        ) {
            ListItem(
                headlineContent = { Text(block.label.takeIf { it.isNotBlank() } ?: "Unnamed Block", style = MaterialTheme.typography.bodyMedium) },
                supportingContent = { Text(block.short_code.takeIf { it.isNotBlank() } ?: "No Short Code") },
                leadingContent = {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${blockIndex + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = {
                            val newTabs = config.tabs.toMutableList()
                            newTabs[selectedFrameIndex] = swapBlocksSafe(newTabs[selectedFrameIndex], blockIndex, blockIndex - 1)
                            onConfigChange(config.copy(tabs = newTabs))
                        }, enabled = blockIndex > 0) { Text("↑", style = MaterialTheme.typography.titleLarge) }
                        IconButton(onClick = {
                            val newTabs = config.tabs.toMutableList()
                            newTabs[selectedFrameIndex] = swapBlocksSafe(newTabs[selectedFrameIndex], blockIndex, blockIndex + 1)
                            onConfigChange(config.copy(tabs = newTabs))
                        }, enabled = blockIndex < tab.blocks.size - 1) { Text("↓", style = MaterialTheme.typography.titleLarge) }
                        Text("→", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically))
                    }
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
                onConfigChange(config.copy(tabs = newTabs))
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text("＋ Add Block")
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.frameLeversTab(
    tab: JsonTab,
    selectedFrameIndex: Int,
    config: JsonConfig,
    onConfigChange: (JsonConfig) -> Unit,
    onEditLever: (Int) -> Unit
) {
    itemsIndexed(tab.levers) { leverIndex, lever ->
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().clickable { onEditLever(leverIndex) }
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
                    Row {
                        IconButton(onClick = {
                            val newTabs = config.tabs.toMutableList()
                            newTabs[selectedFrameIndex] = swapLeversSafe(newTabs[selectedFrameIndex], leverIndex, leverIndex - 1)
                            onConfigChange(config.copy(tabs = newTabs))
                        }, enabled = leverIndex > 0) { Text("↑", style = MaterialTheme.typography.titleLarge) }
                        IconButton(onClick = {
                            val newTabs = config.tabs.toMutableList()
                            newTabs[selectedFrameIndex] = swapLeversSafe(newTabs[selectedFrameIndex], leverIndex, leverIndex + 1)
                            onConfigChange(config.copy(tabs = newTabs))
                        }, enabled = leverIndex < tab.levers.size - 1) { Text("↓", style = MaterialTheme.typography.titleLarge) }
                        Text("→", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically))
                    }
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
                onConfigChange(config.copy(tabs = newTabs))
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            Text("＋ Add Lever")
        }
    }
}

