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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Brush

@Composable
fun LeverStatusScreen(
    leverIndex: Int,
    leverDef: LeverDef,
    levers: List<DomainLever>,
    blocks: List<DomainBlock>,
    onClose: () -> Unit,
    onEditConfig: () -> Unit,
    onLccEnabledChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
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
                text = "Lever ${leverIndex + 1} Status",
                color = LeverFrameTheme.Colors.Brass,
                fontSize = 14.sp,
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
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusItem("Label", leverDef.label.replace("\n", " "))
                StatusItem("State", if (levers.getOrNull(leverIndex)?.isReversed == true) "Reversed" else "Normal")
                StatusItem("Type", leverDef.type.name)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("LCC Enabled", color = LeverFrameTheme.Colors.Brass, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = leverDef.lcc_enabled,
                        onCheckedChange = onLccEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LeverFrameTheme.Colors.PaleBlue
                        )
                    )
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusItem("Event ID (Normal)", if (leverDef.lcc_event_normal.isBlank()) "None" else leverDef.lcc_event_normal)
                    StatusItem("Event ID (Reversed)", if (leverDef.lcc_event_reversed.isBlank()) "None" else leverDef.lcc_event_reversed)
                }
                
                if (leverDef.conditions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Interlocking Rules:", color = LeverFrameTheme.Colors.Brass, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        leverDef.conditions.forEach { rule ->
                            val reqStateStr = if (rule.targetType == TargetType.BLOCK) {
                                if (rule.requiredState) "OCCUPIED" else "EMPTY"
                            } else {
                                if (rule.requiredState) "REVERSED" else "NORMAL"
                            }
                            val targetLabel = if (rule.targetType == TargetType.BLOCK) "Block" else "Lever"
                            
                            val altStr = if (rule.altTargetIndex != -1) {
                                val altStateStr = if (rule.altTargetType == TargetType.BLOCK) {
                                    if (rule.altRequiredState) "OCCUPIED" else "EMPTY"
                                } else {
                                    if (rule.altRequiredState) "REVERSED" else "NORMAL"
                                }
                                val altTargetLabel = if (rule.altTargetType == TargetType.BLOCK) "Block" else "Lever"
                                " OR $altTargetLabel ${rule.altTargetIndex + 1} is $altStateStr"
                            } else ""
                            
                            val mainState = if (rule.targetType == TargetType.BLOCK) blocks.getOrNull(rule.targetIndex) ?: false else levers.getOrNull(rule.targetIndex) ?: false
                            val mainSatisfied = mainState == rule.requiredState
                            
                            val altState = if (rule.altTargetType == TargetType.BLOCK) blocks.getOrNull(rule.altTargetIndex) ?: false else levers.getOrNull(rule.altTargetIndex) ?: false
                            val altSatisfied = rule.altTargetIndex != -1 && altState == rule.altRequiredState
                            
                            val isSatisfied = mainSatisfied || altSatisfied
                            
                            val statusIcon = if (isSatisfied) "✅" else "❌"
                            Text("$statusIcon $targetLabel ${rule.targetIndex + 1} must be $reqStateStr$altStr", color = Color.White, fontSize = 10.sp)
                        }
                    }
                } else {
                    Text("No interlocking rules.", color = Color.White, fontSize = 10.sp)
                }
            }
            
            if (scrollState.canScrollBackward) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF2A2A2A), Color.Transparent)
                            )
                        )
                )
            }
            
            if (scrollState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF2A2A2A))
                            )
                        )
                )
            }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onEditConfig,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LeverFrameTheme.Colors.Brass, contentColor = Color.Black)
        ) {
            Text("Edit Configuration", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
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
            color = LeverFrameTheme.Colors.Brass,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
