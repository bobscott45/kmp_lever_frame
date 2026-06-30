package org.example.project

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
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemStatusScreen(onClose: () -> Unit) {
    val ipAddress = remember { getLocalIpAddress() }
    val port = 12021 // Standard OpenLCB GridConnect Port

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
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
                color = BrassColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Text("✕", color = BrassColor, fontSize = 20.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusItem("Node Name", ConfigManager.currentConfig.node_name)
                StatusItem("Node ID", ConfigManager.currentConfig.node_id)
                StatusItem("IP Address", ipAddress)
                StatusItem("TCP Port", port.toString())
                StatusItem("Network Status", GridConnectNetwork.connectionStatus.collectAsState().value)
                
                var policyExpanded by remember { mutableStateOf(false) }
                val policies = mapOf(1 to "Strict Local", 2 to "Override Allowed", 3 to "Accept & Warn")
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("External Event Policy", color = BrassColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    ExposedDropdownMenuBox(
                        expanded = policyExpanded,
                        onExpandedChange = { policyExpanded = !policyExpanded }
                    ) {
                        OutlinedTextField(
                            value = policies[ConfigManager.currentConfig.conflict_policy] ?: "Unknown",
                            onValueChange = {},
                            readOnly = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = policyExpanded) },
                            modifier = Modifier.menuAnchor().width(200.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = policyExpanded,
                            onDismissRequest = { policyExpanded = false }
                        ) {
                            policies.forEach { (key, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        val newConfig = ConfigManager.currentConfig.copy(conflict_policy = key)
                                        ConfigManager.currentConfig = newConfig
                                        saveConfigToFile(ConfigManager.toJsonString())
                                        policyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("LCC Master Enabled", color = BrassColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = ConfigManager.currentConfig.lcc_master,
                        onCheckedChange = { 
                            val newConfig = ConfigManager.currentConfig.copy(lcc_master = it)
                            ConfigManager.currentConfig = newConfig
                            saveConfigToFile(ConfigManager.toJsonString())
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PaleBlue
                        )
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Restore Last State on Startup", color = BrassColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = ConfigManager.currentConfig.restore_last_state,
                        onCheckedChange = { 
                            val newConfig = ConfigManager.currentConfig.copy(restore_last_state = it)
                            ConfigManager.currentConfig = newConfig
                            saveConfigToFile(ConfigManager.toJsonString())
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PaleBlue
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = BrassColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
