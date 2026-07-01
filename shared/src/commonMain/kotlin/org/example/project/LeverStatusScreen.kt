package org.example.project

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

@Composable
fun LeverStatusScreen(
    leverIndex: Int,
    leverDef: LeverDef,
    onClose: () -> Unit,
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
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Text("✕", color = LeverFrameTheme.Colors.Brass, fontSize = 20.sp)
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
                StatusItem("Label", leverDef.label.replace("\n", " "))
                StatusItem("Type", leverDef.type.name)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("LCC Enabled", color = LeverFrameTheme.Colors.Brass, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = leverDef.lcc_enabled,
                        onCheckedChange = onLccEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LeverFrameTheme.Colors.PaleBlue
                        )
                    )
                }
                
                StatusItem("Event ID (Normal)", if (leverDef.lcc_event_normal.isBlank()) "None" else leverDef.lcc_event_normal)
                StatusItem("Event ID (Reversed)", if (leverDef.lcc_event_reversed.isBlank()) "None" else leverDef.lcc_event_reversed)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (leverDef.conditions.isNotEmpty()) {
                    Text("Interlocking Rules:", color = LeverFrameTheme.Colors.Brass, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    leverDef.conditions.forEach { rule ->
                        val reqStateStr = if (rule.requiredState) "REVERSED" else "NORMAL"
                        val altStr = if (rule.altTargetLeverIndex != -1) {
                            val altStateStr = if (rule.altRequiredState) "REVERSED" else "NORMAL"
                            " OR Lever ${rule.altTargetLeverIndex} is $altStateStr"
                        } else ""
                        Text("• Lever ${rule.targetLeverIndex} must be $reqStateStr$altStr", color = Color.White, fontSize = 14.sp)
                    }
                } else {
                    Text("No interlocking rules.", color = Color.White, fontSize = 14.sp)
                }
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
            color = LeverFrameTheme.Colors.Brass,
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
