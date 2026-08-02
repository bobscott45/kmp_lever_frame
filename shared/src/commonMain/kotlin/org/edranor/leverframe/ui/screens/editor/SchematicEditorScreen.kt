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
 * Provides an interactive, grid-based canvas for designing track schematics.
 * Allows users to place track elements, link them to physical levers or blocks,
 * and configure NX (eNtrance-eXit) routing buttons directly on the layout.
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Provides an interactive canvas-based editor for designing the layout's track schematic.
 * Allows users to place signals, turnouts, blocks, and NX buttons on a grid.
 */
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
    var editType by remember { mutableStateOf(SchematicElementType.STRAIGHT_H) }
    var editLinkedBlock by remember { mutableStateOf(-1) }
    var editLinkedLever by remember { mutableStateOf(-1) }
    var editLinkedLever2 by remember { mutableStateOf(-1) }
    var editNxButton by remember { mutableStateOf(NxButtonType.NONE) }
    var editNxPlacement by remember { mutableStateOf(NxButtonPlacement.DEFAULT) }
    var editNxColor by remember { mutableStateOf(NxButtonColor.BLACK) }

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
                    .pointerInput(tabDef) {
                        detectTapGestures { offset ->
                            val gridSizeX = gridDpX.toPx()
                            val gridSizeY = tabDef.schematic_grid_size.dp.toPx()
                            
                            val actualDrawingWidth = cellsX * gridSizeX
                            val startX = (size.width - actualDrawingWidth) / 2f
                            
                            val clickedX = ((offset.x - startX) / gridSizeX).toInt()
                            val clickedY = (offset.y / gridSizeY).toInt()
                            
                            if (clickedX in 0 until cellsX && clickedY in 0 until cellsY) {
                                val existing = tabDef.schematic_elements.find { it.x == clickedX && it.y == clickedY }
                                editType = existing?.type ?: SchematicElementType.STRAIGHT_H
                                editLinkedBlock = existing?.linked_block ?: -1
                                editLinkedLever = existing?.linked_lever ?: -1
                                editLinkedLever2 = existing?.linked_lever_2 ?: -1
                                editNxButton = existing?.nx_button ?: NxButtonType.NONE
                                editNxPlacement = existing?.nx_button_placement ?: NxButtonPlacement.DEFAULT
                                editNxColor = existing?.nx_button_color ?: NxButtonColor.BLACK
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
                        SchematicElementType.STRAIGHT_H -> drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
                        SchematicElementType.STRAIGHT_V -> drawLine(trackColor, Offset(px + gridSizeX / 2, py), Offset(px + gridSizeX / 2, py + gridSizeY), strokeWidth = 2f)
                        SchematicElementType.TURNOUT_LEFT -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
                            drawLine(trackColor, Offset(px + gridSizeX / 2, py + gridSizeY / 2), Offset(px + gridSizeX, py - gridSizeY / 2), strokeWidth = 2f)
                            if (element.linked_lever >= 0) {
                                drawText(textMeasurer = textMeasurer, text = "${element.linked_lever + 1}", style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold), topLeft = Offset(px + gridSizeX * 0.7f, py + gridSizeY * 0.15f))
                            }
                        }
                        SchematicElementType.TURNOUT_RIGHT -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
                            drawLine(trackColor, Offset(px + gridSizeX / 2, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY * 1.5f), strokeWidth = 2f)
                            if (element.linked_lever >= 0) {
                                drawText(textMeasurer = textMeasurer, text = "${element.linked_lever + 1}", style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold), topLeft = Offset(px + gridSizeX * 0.7f, py + gridSizeY * 0.85f))
                            }
                        }
                        SchematicElementType.TURNOUT_LEFT_TRAILING -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
                            drawLine(trackColor, Offset(px + gridSizeX / 2, py + gridSizeY / 2), Offset(px, py - gridSizeY / 2), strokeWidth = 2f)
                            if (element.linked_lever >= 0) {
                                drawText(textMeasurer = textMeasurer, text = "${element.linked_lever + 1}", style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold), topLeft = Offset(px + gridSizeX * 0.1f, py + gridSizeY * 0.15f))
                            }
                        }
                        SchematicElementType.TURNOUT_RIGHT_TRAILING -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
                            drawLine(trackColor, Offset(px + gridSizeX / 2, py + gridSizeY / 2), Offset(px, py + gridSizeY * 1.5f), strokeWidth = 2f)
                            if (element.linked_lever >= 0) {
                                drawText(textMeasurer = textMeasurer, text = "${element.linked_lever + 1}", style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold), topLeft = Offset(px + gridSizeX * 0.1f, py + gridSizeY * 0.85f))
                            }
                        }
                        SchematicElementType.DIAMOND_CROSSING -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
                            drawLine(trackColor, Offset(px + gridSizeX / 2, py), Offset(px + gridSizeX / 2, py + gridSizeY), strokeWidth = 2f)
                        }
                        SchematicElementType.SIGNAL_LEFT -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
                            val leverType = tabDef.levers.getOrNull(element.linked_lever)?.type
                            val normalColor = if (leverType == LeverType.DISTANT_SIGNAL) Color.Yellow else Color.Red
                            drawCircle(normalColor, radius = gridSizeY / 5, center = Offset(px + gridSizeX / 2, py + gridSizeY / 2))
                            val arrowColor = if (normalColor == Color.Red) Color.White else Color.Black
                            val cx = px + gridSizeX / 2
                            val cy = py + gridSizeY / 2
                            val arrowWidth = gridSizeY / 5 * 1.2f
                            val arrowHeight = gridSizeY / 5 * 0.8f
                            drawLine(arrowColor, Offset(cx - arrowWidth / 2, cy), Offset(cx + arrowWidth / 2, cy), strokeWidth = 2f)
                            drawLine(arrowColor, Offset(cx - arrowWidth / 2, cy), Offset(cx - arrowWidth / 2 + arrowHeight / 2, cy - arrowHeight / 2), strokeWidth = 2f)
                            drawLine(arrowColor, Offset(cx - arrowWidth / 2, cy), Offset(cx - arrowWidth / 2 + arrowHeight / 2, cy + arrowHeight / 2), strokeWidth = 2f)
                            if (element.linked_lever >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX / 2 - gridSizeY / 10, py + gridSizeY * 0.75f)
                                )
                            }
                        }
                        SchematicElementType.SIGNAL_RIGHT -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
                            val leverType = tabDef.levers.getOrNull(element.linked_lever)?.type
                            val normalColor = if (leverType == LeverType.DISTANT_SIGNAL) Color.Yellow else Color.Red
                            drawCircle(normalColor, radius = gridSizeY / 5, center = Offset(px + gridSizeX / 2, py + gridSizeY / 2))
                            val arrowColor = if (normalColor == Color.Red) Color.White else Color.Black
                            val cx = px + gridSizeX / 2
                            val cy = py + gridSizeY / 2
                            val arrowWidth = gridSizeY / 5 * 1.2f
                            val arrowHeight = gridSizeY / 5 * 0.8f
                            drawLine(arrowColor, Offset(cx - arrowWidth / 2, cy), Offset(cx + arrowWidth / 2, cy), strokeWidth = 2f)
                            drawLine(arrowColor, Offset(cx + arrowWidth / 2, cy), Offset(cx + arrowWidth / 2 - arrowHeight / 2, cy - arrowHeight / 2), strokeWidth = 2f)
                            drawLine(arrowColor, Offset(cx + arrowWidth / 2, cy), Offset(cx + arrowWidth / 2 - arrowHeight / 2, cy + arrowHeight / 2), strokeWidth = 2f)
                            if (element.linked_lever >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX / 2 - gridSizeY / 10, py + gridSizeY * 0.75f)
                                )
                            }
                        }
                        SchematicElementType.BRACKET_SIGNAL_LEFT -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
                            drawLine(Color.Gray, Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2), Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.15f), strokeWidth = 2f)
                            drawCircle(Color.Red, radius = gridSizeY / 5, center = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2))
                            val arrowColor1 = Color.White
                            val cx1 = px + gridSizeX * 0.65f
                            val cy1 = py + gridSizeY / 2
                            val arrowWidth1 = gridSizeY / 5 * 1.2f
                            val arrowHeight1 = gridSizeY / 5 * 0.8f
                            drawLine(arrowColor1, Offset(cx1 - arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2, cy1), strokeWidth = 2f)
                            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 - arrowHeight1 / 2), strokeWidth = 2f)
                            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 + arrowHeight1 / 2), strokeWidth = 2f)
                            if (element.linked_lever >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX * 0.65f - gridSizeY / 10, py + gridSizeY * 0.75f)
                                )
                            }
                            drawCircle(Color.Red, radius = gridSizeY / 5, center = Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.15f))
                            val arrowColor2 = Color.White
                            val cx2 = px + gridSizeX * 0.35f
                            val cy2 = py + gridSizeY * 0.15f
                            val arrowWidth2 = gridSizeY / 5 * 1.2f
                            val arrowHeight2 = gridSizeY / 5 * 0.8f
                            rotate(-45f, Offset(cx2, cy2)) {
                                drawLine(arrowColor2, Offset(cx2 - arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2, cy2), strokeWidth = 2f)
                                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 - arrowHeight2 / 2), strokeWidth = 2f)
                                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 + arrowHeight2 / 2), strokeWidth = 2f)
                            }
                            if (element.linked_lever_2 >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever_2 + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX * 0.35f - gridSizeY / 10, py - gridSizeY * 0.4f)
                                )
                            }
                        }
                        SchematicElementType.BRACKET_SIGNAL_RIGHT -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
                            drawLine(Color.Gray, Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2), Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.85f), strokeWidth = 2f)
                            drawCircle(Color.Red, radius = gridSizeY / 5, center = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2))
                            val arrowColor1 = Color.White
                            val cx1 = px + gridSizeX * 0.65f
                            val cy1 = py + gridSizeY / 2
                            val arrowWidth1 = gridSizeY / 5 * 1.2f
                            val arrowHeight1 = gridSizeY / 5 * 0.8f
                            drawLine(arrowColor1, Offset(cx1 - arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2, cy1), strokeWidth = 2f)
                            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 - arrowHeight1 / 2), strokeWidth = 2f)
                            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 + arrowHeight1 / 2), strokeWidth = 2f)
                            if (element.linked_lever >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX * 0.65f - gridSizeY / 10, py + gridSizeY * 0.75f)
                                )
                            }
                            drawCircle(Color.Red, radius = gridSizeY / 5, center = Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.85f))
                            val arrowColor2 = Color.White
                            val cx2 = px + gridSizeX * 0.35f
                            val cy2 = py + gridSizeY * 0.85f
                            val arrowWidth2 = gridSizeY / 5 * 1.2f
                            val arrowHeight2 = gridSizeY / 5 * 0.8f
                            rotate(45f, Offset(cx2, cy2)) {
                                drawLine(arrowColor2, Offset(cx2 - arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2, cy2), strokeWidth = 2f)
                                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 - arrowHeight2 / 2), strokeWidth = 2f)
                                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 + arrowHeight2 / 2), strokeWidth = 2f)
                            }
                            if (element.linked_lever_2 >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever_2 + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX * 0.35f - gridSizeY / 10, py + gridSizeY * 1.15f)
                                )
                            }
                        }
                    }
                    
                    val nxBtn = element.nx_button
                    val nxPlacement = element.nx_button_placement
                    val nxColorStr = element.nx_button_color
                    if (nxBtn != NxButtonType.NONE) {
                        val cx = px + when (nxPlacement) {
                            NxButtonPlacement.LEFT -> gridSizeX * 0.15f
                            NxButtonPlacement.RIGHT -> gridSizeX * 0.85f
                            NxButtonPlacement.TOP, NxButtonPlacement.BOTTOM -> gridSizeX * 0.5f
                            else -> gridSizeX * 0.25f
                        }
                        val cy = py + when (nxPlacement) {
                            NxButtonPlacement.TOP -> gridSizeY * 0.15f
                            NxButtonPlacement.BOTTOM -> gridSizeY * 0.85f
                            NxButtonPlacement.LEFT, NxButtonPlacement.RIGHT -> gridSizeY * 0.5f
                            else -> gridSizeY * 0.25f
                        }
                        val r = gridSizeY * 0.15f
                        
                        val fillCol = when (nxColorStr) {
                            NxButtonColor.WHITE -> Color.White
                            NxButtonColor.RED -> Color.Red
                            NxButtonColor.YELLOW -> Color.Yellow
                            NxButtonColor.GREEN -> Color.Green
                            NxButtonColor.BLUE -> Color.Blue
                            else -> Color.Black
                        }
                        val borderCol = if (fillCol == Color.White || fillCol == Color.Yellow) Color.Black else Color.White
                        
                        when (nxBtn) {
                            NxButtonType.ENTRANCE_ONLY -> {
                                drawCircle(color = fillCol, radius = r, center = Offset(cx, cy))
                                drawCircle(color = borderCol, radius = r, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                            }
                            NxButtonType.EXIT_ONLY -> {
                                val path = Path().apply {
                                    moveTo(cx, cy - r)
                                    lineTo(cx + r, cy + r)
                                    lineTo(cx - r, cy + r)
                                    close()
                                }
                                drawPath(path = path, color = fillCol)
                                drawPath(path = path, color = borderCol, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                            }
                            NxButtonType.BOTH -> {
                                val path = Path().apply {
                                    moveTo(cx, cy - r)
                                    lineTo(cx + r, cy)
                                    lineTo(cx, cy + r)
                                    lineTo(cx - r, cy)
                                    close()
                                }
                                drawPath(path = path, color = fillCol)
                                drawPath(path = path, color = borderCol, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                            }
                        }
                    }
                }

                // Draw block names
                val blockElementsMap = tabDef.schematic_elements
                    .filter { it.linked_block >= 0 }
                    .groupBy { it.linked_block }

                blockElementsMap.forEach { (blockIdx, elements) ->
                    val straightElements = elements.filter { it.type == SchematicElementType.STRAIGHT_H || it.type == SchematicElementType.STRAIGHT_V }
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
                    if (elements.size == 1 && elements.first().type.name.contains("SIGNAL")) {
                        val elem = elements.first()
                        if (elem.type == SchematicElementType.SIGNAL_RIGHT) {
                            textCenterX += gridSizeX * 0.4f
                        } else if (elem.type == SchematicElementType.SIGNAL_LEFT) {
                            textCenterX -= gridSizeX * 0.4f
                        }
                    }
                    
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(
                            x = textCenterX - textLayout.size.width / 2f,
                            y = centerPy - textLayout.size.height / 2f - gridSizeY * 0.2f
                        )
                    )
                }
            }
        }
    }

    if (editingCell != null) {
        val (cx, cy) = editingCell!!
        SchematicElementEditorDialog(
            tabDef = tabDef,
            cx = cx,
            cy = cy,
            initialEditType = editType,
            initialLinkedLever = editLinkedLever,
            initialLinkedLever2 = editLinkedLever2,
            initialLinkedBlock = editLinkedBlock,
            initialNxButton = editNxButton,
            initialNxPlacement = editNxPlacement,
            initialNxColor = editNxColor,
            onDismiss = { editingCell = null },
            onSave = { newType, newLever, newLever2, newBlock, newNxButton, newNxPlacement, newNxColor ->
                val elements = tabDef.schematic_elements.toMutableList()
                elements.removeAll { it.x == cx && it.y == cy }
                elements.add(JsonSchematicElement(type = newType, x = cx, y = cy, linked_lever = newLever, linked_lever_2 = newLever2, linked_block = newBlock, nx_button = newNxButton, nx_button_placement = newNxPlacement, nx_button_color = newNxColor))
                onTabDefChange(tabDef.copy(schematic_elements = elements))
                editingCell = null
            },
            onDelete = {
                val elements = tabDef.schematic_elements.toMutableList()
                elements.removeAll { it.x == cx && it.y == cy }
                onTabDefChange(tabDef.copy(schematic_elements = elements))
                editingCell = null
            }
        )
    }
}

