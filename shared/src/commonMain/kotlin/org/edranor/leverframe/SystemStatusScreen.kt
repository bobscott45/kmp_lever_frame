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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemStatusScreen(
    config: JsonConfig,
    networkStatus: String,
    onUpdateSystemConfig: (JsonConfig) -> Unit,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val ipAddress = remember { getLocalIpAddress() }
    val port = 12021 // Standard OpenLCB GridConnect Port

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .padding(vertical = 16.dp)
                .shadow(8.dp, shape = RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF444444), shape = RoundedCornerShape(12.dp))
                .clickable { /* consume click so it doesn't close */ }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "System Status",
                color = LeverFrameTheme.Colors.Brass,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Text("✕", color = LeverFrameTheme.Colors.Brass, fontSize = 16.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).weight(1f, fill = false),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusItem("Node Name", config.node_name)
                StatusItem("Node ID", config.node_id)
                StatusItem("Version", "1.1.1-dev")
                StatusItem("IP Address", ipAddress)
                StatusItem("TCP Port", port.toString())
                StatusItem("Network Status", networkStatus)
                
                var policyExpanded by remember { mutableStateOf(false) }
                val policies = mapOf(1 to "Strict Local", 2 to "Override Allowed", 3 to "Accept & Warn")
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("External Event\nPolicy", color = LeverFrameTheme.Colors.Brass, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    ExposedDropdownMenuBox(
                        expanded = policyExpanded,
                        onExpandedChange = { policyExpanded = !policyExpanded }
                    ) {
                        OutlinedTextField(
                            value = policies[config.conflict_policy] ?: "Unknown",
                            onValueChange = {},
                            readOnly = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = policyExpanded) },
                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).width(140.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = policyExpanded,
                            onDismissRequest = { policyExpanded = false }
                        ) {
                            policies.forEach { (key, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onUpdateSystemConfig(config.copy(conflict_policy = key))
                                        policyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("LCC Master Enabled", color = LeverFrameTheme.Colors.Brass, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = config.lcc_master,
                        onCheckedChange = { 
                            onUpdateSystemConfig(config.copy(lcc_master = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LeverFrameTheme.Colors.PaleBlue
                        )
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Restore Last State on Startup", color = LeverFrameTheme.Colors.Brass, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = config.restore_last_state,
                        onCheckedChange = { 
                            onUpdateSystemConfig(config.copy(restore_last_state = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LeverFrameTheme.Colors.PaleBlue
                        )
                    )
                }
                
                StatusItem("Platform", getPlatform().name)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4A4A))
        ) {
            Text("Back")
        }
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = LeverFrameTheme.Colors.Brass,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
