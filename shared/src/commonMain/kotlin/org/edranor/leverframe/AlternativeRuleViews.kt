package org.edranor.leverframe

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun FormulaTextView(ast: AstNode?, onAstChange: (AstNode?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Text Formula Editor", style = MaterialTheme.typography.titleMedium, color = LeverFrameTheme.Colors.Brass)
            Spacer(modifier = Modifier.height(16.dp))
            
            var textValue by remember(ast) { mutableStateOf(ast?.toFormulaString() ?: "") }
            var isError by remember(textValue) {
                mutableStateOf(textValue.isNotBlank() && FormulaParser.parse(textValue) == null)
            }
            
            OutlinedTextField(
                value = textValue,
                onValueChange = { 
                    textValue = it
                    val newAst = FormulaParser.parse(it)
                    isError = it.isNotBlank() && newAst == null
                    if (!isError) {
                        onAstChange(newAst)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                isError = isError,
                supportingText = {
                    if (isError) {
                        Text("Invalid syntax. Check parentheses and tokens.", color = MaterialTheme.colorScheme.error)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            
            Text(
                text = "Syntax: L<num>:<N/R> or B<num>:<E/O> separated by AND, OR. Example: (L1:N OR B2:O) AND NOT L3:R",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun LockingTableView(lever: JsonLever, allLevers: List<JsonLever>, allBlocks: List<JsonBlock>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Locking Table (Overview)", style = MaterialTheme.typography.titleMedium, color = LeverFrameTheme.Colors.Brass)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text("Target", modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.labelLarge, color = LeverFrameTheme.Colors.Brass)
                Text("Locking Logic Formula", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelLarge, color = LeverFrameTheme.Colors.Brass)
            }
            Divider(color = Color.DarkGray)
            
            // Just show this specific lever in the table for now
            // To make it frame-wide we'd move this component up the hierarchy
            val targetName = lever.label.ifBlank { "L${allLevers.indexOf(lever) + 1}" }
            val formula = lever.ast_logic?.toFormulaString() ?: migrateJsonInterlockingToAst(lever.interlocking)?.toFormulaString() ?: "None"
            
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(targetName, modifier = Modifier.weight(0.3f), color = Color.White)
                Text(formula, modifier = Modifier.weight(0.7f), color = Color.White, fontFamily = FontFamily.Monospace)
            }
            Divider(color = Color.DarkGray, thickness = 0.5.dp)
        }
    }
}
