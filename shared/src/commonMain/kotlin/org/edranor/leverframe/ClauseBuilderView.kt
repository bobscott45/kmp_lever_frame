package org.edranor.leverframe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ClauseBuilderView(
    ast: AstNode?,
    allLevers: List<JsonLever>,
    allBlocks: List<JsonBlock>,
    onAstChange: (AstNode?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Clause Builder (Visual AST)", style = MaterialTheme.typography.titleMedium, color = LeverFrameTheme.Colors.Brass)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (ast == null) {
                TextButton(onClick = { onAstChange(AndNode(emptyList())) }) {
                    Text("＋ Start Building Rules")
                }
            } else {
                AstNodeEditor(
                    node = ast,
                    allLevers = allLevers,
                    allBlocks = allBlocks,
                    isRoot = true,
                    onNodeChange = { onAstChange(it) },
                    onDelete = { onAstChange(null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstNodeEditor(
    node: AstNode,
    allLevers: List<JsonLever>,
    allBlocks: List<JsonBlock>,
    isRoot: Boolean = false,
    onNodeChange: (AstNode) -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = if (isRoot) Color.Transparent else Color(0xFF3A3A3A)
    
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AstNodeHeader(node, isRoot, onDelete)
            
            when (node) {
                is AndNode -> AndOrNodeView(node.children, isAnd = true, allLevers, allBlocks, onNodeChange)
                is OrNode -> AndOrNodeView(node.children, isAnd = false, allLevers, allBlocks, onNodeChange)
                is NotNode -> NotNodeView(node.child, allLevers, allBlocks, onNodeChange)
                is LeverStateNode -> LeverStateNodeView(node, allLevers, onNodeChange)
                is BlockStateNode -> BlockStateNodeView(node, allBlocks, onNodeChange)
            }
        }
    }
}

@Composable
private fun AstNodeHeader(node: AstNode, isRoot: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val nodeLabel = when (node) {
            is AndNode -> "Require ALL of:"
            is OrNode -> "Require ANY of:"
            is NotNode -> "Require NOT:"
            is LeverStateNode -> "Lever State"
            is BlockStateNode -> "Block State"
        }
        
        Text(nodeLabel, style = MaterialTheme.typography.labelMedium, color = LeverFrameTheme.Colors.Brass)
        
        if (!isRoot) {
            TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp)) {
                Text("✕", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AndOrNodeView(
    children: List<AstNode>,
    isAnd: Boolean,
    allLevers: List<JsonLever>,
    allBlocks: List<JsonBlock>,
    onNodeChange: (AstNode) -> Unit
) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        children.forEachIndexed { idx, child ->
            AstNodeEditor(
                node = child,
                allLevers = allLevers,
                allBlocks = allBlocks,
                onNodeChange = { newChild ->
                    val newChildren = children.toMutableList().apply { this[idx] = newChild }
                    onNodeChange(if (isAnd) AndNode(newChildren) else OrNode(newChildren))
                },
                onDelete = {
                    val newChildren = children.toMutableList().apply { removeAt(idx) }
                    onNodeChange(if (isAnd) AndNode(newChildren) else OrNode(newChildren))
                }
            )
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = { 
                val newChildren = children.toMutableList().apply { add(LeverStateNode(0, false)) }
                onNodeChange(if (isAnd) AndNode(newChildren) else OrNode(newChildren))
            }) { Text("＋ Condition") }
            TextButton(onClick = { 
                val newChildren = children.toMutableList().apply { add(if (isAnd) AndNode(emptyList()) else OrNode(emptyList())) }
                onNodeChange(if (isAnd) AndNode(newChildren) else OrNode(newChildren))
            }) { Text("＋ Group") }
        }
    }
}

@Composable
private fun NotNodeView(
    child: AstNode,
    allLevers: List<JsonLever>,
    allBlocks: List<JsonBlock>,
    onNodeChange: (AstNode) -> Unit
) {
    Column(modifier = Modifier.padding(start = 16.dp)) {
        AstNodeEditor(
            node = child,
            allLevers = allLevers,
            allBlocks = allBlocks,
            onNodeChange = { onNodeChange(NotNode(it)) },
            onDelete = { onNodeChange(AndNode(emptyList())) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeverStateNodeView(
    node: LeverStateNode,
    allLevers: List<JsonLever>,
    onNodeChange: (AstNode) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var targetExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = targetExpanded, onExpandedChange = { targetExpanded = !targetExpanded }, modifier = Modifier.weight(1f)) {
            val lText = allLevers.getOrNull(node.leverIndex)?.label?.replace("\n", " ") ?: "Lever ${node.leverIndex + 1}"
            OutlinedTextField(
                value = lText,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                colors = brassTextFieldColors()
            )
            ExposedDropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
                allLevers.forEachIndexed { idx, lvr ->
                    DropdownMenuItem(text = { Text("[${idx + 1}] ${lvr.label.replace("\n", " ")}") }, onClick = { onNodeChange(node.copy(leverIndex = idx)); targetExpanded = false })
                }
                DropdownMenuItem(text = { Text("Switch to Block...") }, onClick = { onNodeChange(BlockStateNode(0, true)); targetExpanded = false })
            }
        }
        
        var stateExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = stateExpanded, onExpandedChange = { stateExpanded = !stateExpanded }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = if (node.requiredReversed) "REVERSED" else "NORMAL",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                colors = brassTextFieldColors()
            )
            ExposedDropdownMenu(expanded = stateExpanded, onDismissRequest = { stateExpanded = false }) {
                DropdownMenuItem(text = { Text("NORMAL") }, onClick = { onNodeChange(node.copy(requiredReversed = false)); stateExpanded = false })
                DropdownMenuItem(text = { Text("REVERSED") }, onClick = { onNodeChange(node.copy(requiredReversed = true)); stateExpanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockStateNodeView(
    node: BlockStateNode,
    allBlocks: List<JsonBlock>,
    onNodeChange: (AstNode) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var targetExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = targetExpanded, onExpandedChange = { targetExpanded = !targetExpanded }, modifier = Modifier.weight(1f)) {
            val bText = allBlocks.getOrNull(node.blockIndex)?.label?.replace("\n", " ") ?: "Block ${node.blockIndex + 1}"
            OutlinedTextField(
                value = bText,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                colors = brassTextFieldColors()
            )
            ExposedDropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
                allBlocks.forEachIndexed { idx, blk ->
                    DropdownMenuItem(text = { Text("[${idx + 1}] ${blk.label.replace("\n", " ")}") }, onClick = { onNodeChange(node.copy(blockIndex = idx)); targetExpanded = false })
                }
                DropdownMenuItem(text = { Text("Switch to Lever...") }, onClick = { onNodeChange(LeverStateNode(0, false)); targetExpanded = false })
            }
        }
        
        var stateExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = stateExpanded, onExpandedChange = { stateExpanded = !stateExpanded }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = if (node.requiredOccupied) "OCCUPIED" else "EMPTY",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                colors = brassTextFieldColors()
            )
            ExposedDropdownMenu(expanded = stateExpanded, onDismissRequest = { stateExpanded = false }) {
                DropdownMenuItem(text = { Text("EMPTY") }, onClick = { onNodeChange(node.copy(requiredOccupied = false)); stateExpanded = false })
                DropdownMenuItem(text = { Text("OCCUPIED") }, onClick = { onNodeChange(node.copy(requiredOccupied = true)); stateExpanded = false })
            }
        }
    }
}

