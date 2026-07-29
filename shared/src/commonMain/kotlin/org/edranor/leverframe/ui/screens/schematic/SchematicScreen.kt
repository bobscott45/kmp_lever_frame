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
 * Renders the interactive layout schematic using a custom Canvas grid.
 * Handles touch gestures for the NX (eNtrance-eXit) routing system, highlighting
 * active paths, unreachable elements, and drawing block states dynamically.
 */
package org.edranor.leverframe.ui.screens.schematic
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
/**
 * Renders the track layout schematic on a responsive grid canvas.
 * Handles user interactions for the eNtrance-eXit (NX) routing system, calculating
 * tap coordinates to resolve route endpoints and drawing active path highlights.
 *
 * @param tabDef The configuration for the current tab, including grid size and elements.
 * @param levers The current domain states of all levers, used to draw signals/turnouts accurately.
 * @param blocks The current domain states of all track blocks, used to colorize track occupancy.
 * @param routeErrorCells A list of (X, Y) grid coordinates to highlight with a flashing error indicator.
 * @param onNxRouteExecute Callback triggered when a valid NX route exit button is tapped.
 * @param onNxRouteCancel Callback triggered to cancel an ongoing NX route selection.
 * @param modifier Standard Compose modifier for the container layout.
 */
@Composable
fun SchematicScreen(
    tabDef: TabDef,
    levers: List<DomainLever>,
    blocks: List<DomainBlock>,
    routeErrorCells: List<Pair<Int, Int>> = emptyList(),
    onNxRouteExecute: (NxRoute) -> Unit,
    onNxRouteCancel: (Pair<Int, Int>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val maxX = tabDef.schematicElements.maxOfOrNull { it.x } ?: 0
    val maxY = tabDef.schematicElements.maxOfOrNull { it.y } ?: 0
    val cellsX = (maxX + 1).coerceAtLeast(1)
    val cellsY = (maxY + 1).coerceAtLeast(1)

    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier.background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        val minGridSizeX = tabDef.schematicGridSize.dp
        val maxGridSizeX = 120.dp
        
        val containerMaxWidth = maxWidth
        val calculatedGridSizeX = containerMaxWidth / cellsX
        val gridDpX = calculatedGridSizeX.coerceIn(minGridSizeX, maxGridSizeX)
        val widthDp = gridDpX * cellsX

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            val heightDp = (cellsY * tabDef.schematicGridSize + 10).dp // Add 10dp padding at the bottom to prevent text clipping
            val canvasWidthDp = maxOf(widthDp, containerMaxWidth)
            
            var activeNxStart: Pair<Int, Int>? by remember { mutableStateOf(null) }
            var reachableRoutes: List<NxRoute> by remember { mutableStateOf(emptyList()) }
            var reachableExits: List<Pair<Int, Int>> by remember { mutableStateOf(emptyList()) }
            
            Canvas(
                modifier = Modifier.width(canvasWidthDp).height(heightDp).pointerInput(tabDef.schematicElements) {
                    detectTapGestures(
                        onTap = { offset ->
                            val gridSizeX = gridDpX.toPx()
                            val gridSizeY = tabDef.schematicGridSize.dp.toPx()
                            val actualDrawingWidth = cellsX * gridSizeX
                            val startX = (size.width - actualDrawingWidth) / 2f
                            
                            val clickedX = ((offset.x - startX) / gridSizeX).toInt()
                            val clickedY = (offset.y / gridSizeY).toInt()
                            
                            val clickedPos = Pair(clickedX, clickedY)
                            if (reachableExits.contains(clickedPos)) {
                                // User clicked a valid exit! Execute the route.
                                val route = reachableRoutes.find { it.pathCells.last() == clickedPos }
                                if (route != null) {
                                    onNxRouteExecute(route)
                                }
                                // Reset highlights
                                activeNxStart = null
                                reachableRoutes = emptyList()
                                reachableExits = emptyList()
                            } else {
                                val clickedElem = tabDef.schematicElements.find { it.x == clickedX && it.y == clickedY }
                                if (clickedElem != null && (clickedElem.nxButton == NxButtonType.ENTRANCE_ONLY || clickedElem.nxButton == NxButtonType.BOTH)) {
                                    if (activeNxStart == clickedPos) {
                                        onNxRouteCancel(clickedPos)
                                        activeNxStart = null
                                        reachableRoutes = emptyList()
                                        reachableExits = emptyList()
                                    } else {
                                        activeNxStart = clickedPos
                                        reachableRoutes = NxRoutingEngine.findReachableExits(clickedX, clickedY, tabDef.schematicElements)
                                        reachableExits = reachableRoutes.map { it.pathCells.last() }.distinct()
                                    }
                                } else {
                                    activeNxStart = null
                                    reachableRoutes = emptyList()
                                    reachableExits = emptyList()
                                }
                            }
                        },
                        onDoubleTap = { offset ->
                            val gridSizeX = gridDpX.toPx()
                            val gridSizeY = tabDef.schematicGridSize.dp.toPx()
                            val actualDrawingWidth = cellsX * gridSizeX
                            val startX = (size.width - actualDrawingWidth) / 2f
                            
                            val clickedX = ((offset.x - startX) / gridSizeX).toInt()
                            val clickedY = (offset.y / gridSizeY).toInt()
                            val clickedPos = Pair(clickedX, clickedY)
                            
                            val clickedElem = tabDef.schematicElements.find { it.x == clickedX && it.y == clickedY }
                            if (clickedElem != null && clickedElem.nxButton != NxButtonType.NONE) {
                                onNxRouteCancel(clickedPos)
                                activeNxStart = null
                                reachableRoutes = emptyList()
                                reachableExits = emptyList()
                            }
                        },
                        onLongPress = { offset ->
                            val gridSizeX = gridDpX.toPx()
                            val gridSizeY = tabDef.schematicGridSize.dp.toPx()
                            val actualDrawingWidth = cellsX * gridSizeX
                            val startX = (size.width - actualDrawingWidth) / 2f
                            
                            val clickedX = ((offset.x - startX) / gridSizeX).toInt()
                            val clickedY = (offset.y / gridSizeY).toInt()
                            val clickedPos = Pair(clickedX, clickedY)
                            
                            val clickedElem = tabDef.schematicElements.find { it.x == clickedX && it.y == clickedY }
                            if (clickedElem != null && clickedElem.nxButton != NxButtonType.NONE) {
                                onNxRouteCancel(clickedPos)
                                activeNxStart = null
                                reachableRoutes = emptyList()
                                reachableExits = emptyList()
                            }
                        }
                    )
                }
            ) {
                val gridSizeX = gridDpX.toPx()
                val gridSizeY = tabDef.schematicGridSize.dp.toPx()
                
                val actualDrawingWidth = cellsX * gridSizeX
                val startX = (size.width - actualDrawingWidth) / 2f

                tabDef.schematicElements.forEach { element ->
                    drawSchematicElement(
                        element = element,
                        tabDef = tabDef,
                        levers = levers,
                        blocks = blocks,
                        textMeasurer = textMeasurer,
                        gridSizeX = gridSizeX,
                        gridSizeY = gridSizeY,
                        startX = startX
                    )
                }
                
                // Draw error indicators
                for (pos in routeErrorCells) {
                    val px = startX + pos.first * gridSizeX
                    val py = pos.second * gridSizeY
                    
                    // Draw a flashing red indicator (simplified to just a red exclamation mark box)
                    val boxSize = gridSizeY * 0.6f
                    drawRect(
                        color = LeverFrameTheme.Colors.ErrorText.copy(alpha = 0.8f),
                        topLeft = androidx.compose.ui.geometry.Offset(px + (gridSizeX - boxSize) / 2, py + (gridSizeY - boxSize) / 2),
                        size = androidx.compose.ui.geometry.Size(boxSize, boxSize)
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "!",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White, 
                            fontSize = (gridSizeY * 0.4f).toSp(), 
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(px + (gridSizeX - boxSize) / 2 + boxSize * 0.35f, py + (gridSizeY - boxSize) / 2 + boxSize * 0.1f)
                    )
                }

                // Draw NX Highlights
                activeNxStart?.let { start ->
                    val px = startX + start.first * gridSizeX
                    val py = start.second * gridSizeY
                    drawRect(Color.Yellow.copy(alpha = 0.4f), topLeft = Offset(px, py), size = Size(gridSizeX, gridSizeY))
                }
                
                reachableExits.forEach { exit ->
                    val px = startX + exit.first * gridSizeX
                    val py = exit.second * gridSizeY
                    drawRect(Color.Green.copy(alpha = 0.4f), topLeft = Offset(px, py), size = Size(gridSizeX, gridSizeY))
                }

                // Draw block names once per block, centered across all their elements
                val blockElementsMap = tabDef.schematicElements
                    .filter { it.linkedBlock >= 0 }
                    .groupBy { it.linkedBlock }

                blockElementsMap.forEach { (blockIdx, elements) ->
                    val straightElements = elements.filter { it.type.name == "STRAIGHT_H" || it.type.name == "STRAIGHT_V" }
                    val elementsToCenter = if (straightElements.isNotEmpty()) straightElements else elements
                    val minX = elementsToCenter.minOf { it.x }
                    val maxX = elementsToCenter.maxOf { it.x }
                    val minY = elementsToCenter.minOf { it.y }
                    val maxY = elementsToCenter.maxOf { it.y }
                    
                    val centerPx = startX + (minX + maxX + 1) * gridSizeX / 2f
                    val centerPy = (minY + maxY + 1) * gridSizeY / 2f
                    
                    val blockDef = tabDef.blocks.getOrNull(blockIdx)
                    val blockNameStr = blockDef?.label ?: "Block ${blockIdx + 1}"
                    val displayText = if (tabDef.useShortCodes && blockDef?.shortCode?.isNotBlank() == true) {
                        blockDef.shortCode
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
                        if (elem.type.name == "SIGNAL_RIGHT") {
                            textCenterX += gridSizeX * 0.4f
                        } else if (elem.type.name == "SIGNAL_LEFT") {
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
}
