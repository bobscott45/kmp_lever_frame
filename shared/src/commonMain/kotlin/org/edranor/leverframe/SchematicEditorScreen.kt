package org.edranor.leverframe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchematicEditorScreen(
    tabDef: JsonTab,
    onTabDefChange: (JsonTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxX = tabDef.schematic_elements.maxOfOrNull { it.x } ?: 0
    val maxY = tabDef.schematic_elements.maxOfOrNull { it.y } ?: 0
    val cellsX = (maxX + 3).coerceAtLeast(10) // Always show some extra grid space
    val cellsY = (maxY + 3).coerceAtLeast(6)

    val textMeasurer = rememberTextMeasurer()

    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editType by remember { mutableStateOf("STRAIGHT_H") }
    var editLinkedBlock by remember { mutableStateOf(-1) }
    var editLinkedLever by remember { mutableStateOf(-1) }
    var editLinkedLever2 by remember { mutableStateOf(-1) }

    BoxWithConstraints(
        modifier = modifier.background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        val minGridSizeX = tabDef.schematic_grid_size.dp
        val maxGridSizeX = 120.dp
        
        val containerMaxWidth = maxWidth
        val calculatedGridSizeX = containerMaxWidth / cellsX
        val gridDpX = calculatedGridSizeX.coerceIn(minGridSizeX, maxGridSizeX)
        val widthDp = gridDpX * cellsX
        val canvasWidthDp = maxOf(widthDp, containerMaxWidth)

        val heightDp = (cellsY * tabDef.schematic_grid_size).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .width(canvasWidthDp)
                    .height(heightDp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val gridSizeX = gridDpX.toPx()
                            val gridSizeY = tabDef.schematic_grid_size.dp.toPx()
                            
                            val actualDrawingWidth = cellsX * gridSizeX
                            val startX = (size.width - actualDrawingWidth) / 2f
                            
                            val clickedX = ((offset.x - startX) / gridSizeX).toInt()
                            val clickedY = (offset.y / gridSizeY).toInt()
                            
                            if (clickedX in 0 until cellsX && clickedY in 0 until cellsY) {
                                val existing = tabDef.schematic_elements.find { it.x == clickedX && it.y == clickedY }
                                editType = existing?.type ?: "STRAIGHT_H"
                                editLinkedBlock = existing?.linked_block ?: -1
                                editLinkedLever = existing?.linked_lever ?: -1
                                editLinkedLever2 = existing?.linked_lever_2 ?: -1
                                editingCell = Pair(clickedX, clickedY)
                            }
                        }
                    }
            ) {
                val gridSizeX = gridDpX.toPx()
                val gridSizeY = tabDef.schematic_grid_size.dp.toPx()
                
                val actualDrawingWidth = cellsX * gridSizeX
                val startX = (size.width - actualDrawingWidth) / 2f

                // Draw Grid Lines
                val gridStroke = 1.dp.toPx()
                val gridColor = Color.Gray.copy(alpha = 0.5f)
                for (i in 0..cellsX) {
                    val px = startX + i * gridSizeX
                    drawLine(gridColor, Offset(px, 0f), Offset(px, cellsY * gridSizeY), strokeWidth = gridStroke)
                }
                for (i in 0..cellsY) {
                    val py = i * gridSizeY
                    drawLine(gridColor, Offset(startX, py), Offset(startX + actualDrawingWidth, py), strokeWidth = gridStroke)
                }

                // Draw Elements
                tabDef.schematic_elements.forEach { element ->
                    val px = startX + element.x * gridSizeX
                    val py = element.y * gridSizeY

                    val trackColor = Color.Gray

                    when (element.type) {
                        "STRAIGHT_H" -> drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 4f)
                        "STRAIGHT_V" -> drawLine(trackColor, Offset(px + gridSizeX / 2, py), Offset(px + gridSizeX / 2, py + gridSizeY), strokeWidth = 4f)
                        "TURNOUT_RIGHT" -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 4f)
                            drawLine(trackColor, Offset(px + gridSizeX / 2, py + gridSizeY / 2), Offset(px + gridSizeX, py - gridSizeY / 2), strokeWidth = 4f)
                            if (element.linked_lever >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX * 0.7f, py + gridSizeY * 0.15f)
                                )
                            }
                        }
                        "SIGNAL_LEFT" -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 4f)
                            val leverType = tabDef.levers.getOrNull(element.linked_lever)?.type
                            val normalColor = if (leverType == "DISTANT_SIGNAL") Color.Yellow else Color.Red
                            drawCircle(normalColor, radius = gridSizeY / 5, center = Offset(px + gridSizeX / 2, py + gridSizeY / 2))
                            if (element.linked_lever >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX / 2.5f, py + gridSizeY / 1.3f)
                                )
                            }
                        }
                        "SIGNAL_RIGHT" -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 4f)
                            val leverType = tabDef.levers.getOrNull(element.linked_lever)?.type
                            val normalColor = if (leverType == "DISTANT_SIGNAL") Color.Yellow else Color.Red
                            drawCircle(normalColor, radius = gridSizeY / 5, center = Offset(px + gridSizeX / 2, py + gridSizeY / 2))
                            if (element.linked_lever >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX / 2.5f, py + gridSizeY / 1.3f)
                                )
                            }
                        }
                        "BRACKET_SIGNAL" -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 4f)
                            drawLine(Color.Gray, Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2), Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.15f), strokeWidth = 2f)
                            drawCircle(Color.Red, radius = gridSizeY / 5, center = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2))
                            if (element.linked_lever >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX * 0.65f - gridSizeY / 10, py + gridSizeY / 1.3f)
                                )
                            }
                            drawCircle(Color.Red, radius = gridSizeY / 5, center = Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.15f))
                            if (element.linked_lever_2 >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever_2 + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX * 0.35f - gridSizeY / 10, py - gridSizeY * 0.4f)
                                )
                            }
                        }
                    }
                }

                // Draw block names
                val blockElementsMap = tabDef.schematic_elements
                    .filter { it.linked_block >= 0 }
                    .groupBy { it.linked_block }

                blockElementsMap.forEach { (blockIdx, elements) ->
                    val straightElements = elements.filter { it.type == "STRAIGHT_H" || it.type == "STRAIGHT_V" }
                    val elementsToCenter = if (straightElements.isNotEmpty()) straightElements else elements
                    val minX = elementsToCenter.minOf { it.x }
                    val maxX = elementsToCenter.maxOf { it.x }
                    val minY = elementsToCenter.minOf { it.y }
                    val maxY = elementsToCenter.maxOf { it.y }
                    
                    val centerPx = startX + (minX + maxX + 1) * gridSizeX / 2f
                    val centerPy = (minY + maxY + 1) * gridSizeY / 2f
                    
                    val blockDef = tabDef.blocks.getOrNull(blockIdx)
                    val blockNameStr = blockDef?.label ?: "Block ${blockIdx + 1}"
                    val displayText = if (tabDef.use_short_codes && blockDef?.short_code?.isNotBlank() == true) {
                        blockDef.short_code
                    } else {
                        blockNameStr
                    }
                    
                    val textLayout = textMeasurer.measure(
                        text = displayText,
                        style = TextStyle(color = Color.LightGray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    )
                    
                    var textCenterX = centerPx
                    if (elements.size == 1 && elements.first().type.contains("SIGNAL")) {
                        val elem = elements.first()
                        if (elem.type == "SIGNAL_RIGHT") {
                            textCenterX += gridSizeX * 0.4f
                        } else if (elem.type == "SIGNAL_LEFT") {
                            textCenterX -= gridSizeX * 0.4f
                        }
                    }
                    
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(
                            x = textCenterX - textLayout.size.width / 2f,
                            y = centerPy - textLayout.size.height / 2f - gridSizeY / 4f
                        )
                    )
                }
            }
        }
    }

    if (editingCell != null) {
        val (cx, cy) = editingCell!!
        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = { Text("Edit Cell ($cx, $cy)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val types = listOf("STRAIGHT_H", "STRAIGHT_V", "TURNOUT_RIGHT", "SIGNAL_LEFT", "SIGNAL_RIGHT", "BRACKET_SIGNAL")
                    var typeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                        OutlinedTextField(
                            value = editType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Component Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            types.forEach { t ->
                                DropdownMenuItem(text = { Text(t) }, onClick = { editType = t; typeExpanded = false })
                            }
                        }
                    }

                    // Linked Lever
                    var leverExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = leverExpanded, onExpandedChange = { leverExpanded = !leverExpanded }) {
                        OutlinedTextField(
                            value = if (editLinkedLever >= 0) "${editLinkedLever + 1}" else "None",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Linked Lever (Main)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = leverExpanded) },
                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = leverExpanded, onDismissRequest = { leverExpanded = false }) {
                            DropdownMenuItem(text = { Text("None") }, onClick = { editLinkedLever = -1; leverExpanded = false })
                            tabDef.levers.forEachIndexed { i, l ->
                                DropdownMenuItem(text = { Text("${i + 1}: ${l.label.replace("\\n", " ")}") }, onClick = { editLinkedLever = i; leverExpanded = false })
                            }
                        }
                    }

                    // Linked Lever 2
                    if (editType == "BRACKET_SIGNAL") {
                        var lever2Expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = lever2Expanded, onExpandedChange = { lever2Expanded = !lever2Expanded }) {
                            OutlinedTextField(
                                value = if (editLinkedLever2 >= 0) "${editLinkedLever2 + 1}" else "None",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Linked Lever (Branch)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lever2Expanded) },
                                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = lever2Expanded, onDismissRequest = { lever2Expanded = false }) {
                                DropdownMenuItem(text = { Text("None") }, onClick = { editLinkedLever2 = -1; lever2Expanded = false })
                                tabDef.levers.forEachIndexed { i, l ->
                                    DropdownMenuItem(text = { Text("${i + 1}: ${l.label.replace("\\n", " ")}") }, onClick = { editLinkedLever2 = i; lever2Expanded = false })
                                }
                            }
                        }
                    }

                    // Linked Block
                    var blockExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = blockExpanded, onExpandedChange = { blockExpanded = !blockExpanded }) {
                        val blockLabel = if (editLinkedBlock >= 0 && editLinkedBlock < tabDef.blocks.size) {
                            tabDef.blocks[editLinkedBlock].label.ifBlank { "Block ${editLinkedBlock + 1}" }
                        } else { "None" }
                        OutlinedTextField(
                            value = blockLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Linked Block") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = blockExpanded) },
                            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = blockExpanded, onDismissRequest = { blockExpanded = false }) {
                            DropdownMenuItem(text = { Text("None") }, onClick = { editLinkedBlock = -1; blockExpanded = false })
                            tabDef.blocks.forEachIndexed { index, b ->
                                val displayLabel = b.label.ifBlank { "Block ${index + 1}" }.replace("\\n", " ")
                                DropdownMenuItem(text = { Text(displayLabel) }, onClick = { editLinkedBlock = index; blockExpanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val elements = tabDef.schematic_elements.toMutableList()
                    elements.removeAll { it.x == cx && it.y == cy }
                    elements.add(JsonSchematicElement(type = editType, x = cx, y = cy, linked_lever = editLinkedLever, linked_lever_2 = editLinkedLever2, linked_block = editLinkedBlock))
                    onTabDefChange(tabDef.copy(schematic_elements = elements))
                    editingCell = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val elements = tabDef.schematic_elements.toMutableList()
                        elements.removeAll { it.x == cx && it.y == cy }
                        onTabDefChange(tabDef.copy(schematic_elements = elements))
                        editingCell = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { editingCell = null }) { Text("Cancel") }
                }
            }
        )
    }
}
