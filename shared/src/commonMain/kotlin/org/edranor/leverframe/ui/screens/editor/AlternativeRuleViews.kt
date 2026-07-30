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
 * Contains alternative rule configuration views, such as the text-based formula editor
 * and the locking table overview. Provides power users with faster ways to edit rules.
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * A text-based formula editor allowing power users to directly input locking logic formulas.
 * Supports a custom syntax parsing lever and block conditions into an [AstNode] tree.
 */
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

/**
 * Displays a summarized, read-only view of a lever's locking logic in a tabular format.
 * Useful for reviewing interlocking constraints quickly.
 */
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
            HorizontalDivider(color = Color.DarkGray)
            
            // Just show this specific lever in the table for now
            // To make it frame-wide we'd move this component up the hierarchy
            val targetName = lever.label.ifBlank { "L${allLevers.indexOf(lever) + 1}" }
            val formula = lever.ast_logic?.toFormulaString() ?: migrateJsonInterlockingToAst(lever.interlocking)?.toFormulaString() ?: "None"
            
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(targetName, modifier = Modifier.weight(0.3f), color = Color.White)
                Text(formula, modifier = Modifier.weight(0.7f), color = Color.White, fontFamily = FontFamily.Monospace)
            }
            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
        }
    }
}
