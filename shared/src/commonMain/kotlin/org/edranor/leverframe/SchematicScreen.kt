package org.edranor.leverframe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment

@Composable
fun SchematicScreen(
    tabDef: TabDef,
    leverStates: BooleanArray,
    blockStates: BooleanArray,
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
            Canvas(
                modifier = Modifier.width(canvasWidthDp).height(heightDp)
            ) {
                val gridSizeX = gridDpX.toPx()
                val gridSizeY = tabDef.schematicGridSize.dp.toPx()
                
                val actualDrawingWidth = cellsX * gridSizeX
                val startX = (size.width - actualDrawingWidth) / 2f

                fun getBlockColor(blockIdx: Int): Color {
                    if (blockIdx < 0 || blockIdx >= tabDef.blocks.size) return Color.Gray
                    val occupied = if (blockIdx in blockStates.indices) blockStates[blockIdx] else false
                    return if (occupied) Color.Red else Color.White
                }

                tabDef.schematicElements.forEach { element ->
                    val px = startX + element.x * gridSizeX
                    val py = element.y * gridSizeY

                    val trackColor = getBlockColor(element.linkedBlock)



                    when (element.type) {
                        "STRAIGHT_H" -> drawLine(
                            color = trackColor,
                            start = Offset(px, py + gridSizeY / 2),
                            end = Offset(px + gridSizeX, py + gridSizeY / 2),
                            strokeWidth = 4f
                        )
                        "STRAIGHT_V" -> drawLine(
                            color = trackColor,
                            start = Offset(px + gridSizeX / 2, py),
                            end = Offset(px + gridSizeX / 2, py + gridSizeY),
                            strokeWidth = 4f
                        )
                        "TURNOUT_RIGHT" -> {
                            val isReversed = if (element.linkedLever in leverStates.indices) leverStates[element.linkedLever] else false
                            // Draw main line
                            drawLine(
                                color = trackColor,
                                start = Offset(px, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX, py + gridSizeY / 2),
                                strokeWidth = 4f
                            )
                            // Draw diverging line
                            val divergeElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y - 1 }
                            val divergeBlockColor = divergeElement?.let { getBlockColor(it.linkedBlock) } ?: trackColor
                            val divergeColor = if (isReversed) Color.Green else divergeBlockColor
                            drawLine(
                                color = divergeColor,
                                start = Offset(px + gridSizeX / 2, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX, py - gridSizeY / 2),
                                strokeWidth = 4f
                            )
                            if (element.linkedLever >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linkedLever + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX * 0.7f, py + gridSizeY * 0.15f)
                                )
                            }
                        }
                        "SIGNAL_LEFT" -> {
                            val isReversed = if (element.linkedLever in leverStates.indices) leverStates[element.linkedLever] else false
                            var leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y }
                            if (leftElement == null) {
                                // Check if a turnout from the row below points up to this cell
                                leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y + 1 && it.type == "TURNOUT_RIGHT" }
                            }
                            val rightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
                            
                            val leftColor = trackColor
                            val rightColor = rightElement?.let { getBlockColor(it.linkedBlock) } ?: Color.Gray

                            // Draw left half of track through the signal cell
                            drawLine(
                                color = leftColor,
                                start = Offset(px, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX / 2, py + gridSizeY / 2),
                                strokeWidth = 4f
                            )
                            // Draw right half of track through the signal cell
                            drawLine(
                                color = rightColor,
                                start = Offset(px + gridSizeX / 2, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX, py + gridSizeY / 2),
                                strokeWidth = 4f
                            )
                            val leverType = tabDef.levers.getOrNull(element.linkedLever)?.type
                            val normalColor = if (leverType?.name == "DISTANT_SIGNAL") Color.Yellow else Color.Red
                            drawCircle(
                                color = if (isReversed) Color.Green else normalColor,
                                radius = gridSizeY / 5,
                                center = Offset(px + gridSizeX / 2, py + gridSizeY / 2)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "${element.linkedLever + 1}",
                                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(px + gridSizeX / 2.5f, py + gridSizeY / 1.3f)
                            )
                        }
                        "SIGNAL_RIGHT" -> {
                            val isReversed = if (element.linkedLever in leverStates.indices) leverStates[element.linkedLever] else false
                            var leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y }
                            if (leftElement == null) {
                                // Check if a turnout from the row below points up to this cell
                                leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y + 1 && it.type == "TURNOUT_RIGHT" }
                            }
                            val rightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
                            
                            val rightColor = trackColor
                            val leftColor = leftElement?.let { getBlockColor(it.linkedBlock) } ?: Color.Gray

                            // Draw left half of track through the signal cell
                            drawLine(
                                color = leftColor,
                                start = Offset(px, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX / 2, py + gridSizeY / 2),
                                strokeWidth = 4f
                            )
                            // Draw right half of track through the signal cell
                            drawLine(
                                color = rightColor,
                                start = Offset(px + gridSizeX / 2, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX, py + gridSizeY / 2),
                                strokeWidth = 4f
                            )
                            val leverType = tabDef.levers.getOrNull(element.linkedLever)?.type
                            val normalColor = if (leverType?.name == "DISTANT_SIGNAL") Color.Yellow else Color.Red
                            drawCircle(
                                color = if (isReversed) Color.Green else normalColor,
                                radius = gridSizeY / 5,
                                center = Offset(px + gridSizeX / 2, py + gridSizeY / 2)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "${element.linkedLever + 1}",
                                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(px + gridSizeX / 2.5f, py + gridSizeY / 1.3f)
                            )
                        }
                        "BRACKET_SIGNAL" -> {
                            val isReversed1 = if (element.linkedLever in leverStates.indices) leverStates[element.linkedLever] else false
                            val isReversed2 = if (element.linkedLever2 in leverStates.indices) leverStates[element.linkedLever2] else false
                            
                            val leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y }
                            val rightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
                            
                            val rightColor = trackColor
                            val leftColor = leftElement?.let { getBlockColor(it.linkedBlock) } ?: Color.Gray

                            // Draw left half of track
                            drawLine(
                                color = leftColor,
                                start = Offset(px, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2),
                                strokeWidth = 4f
                            )
                            // Draw right half of track
                            drawLine(
                                color = rightColor,
                                start = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX, py + gridSizeY / 2),
                                strokeWidth = 4f
                            )
                            
                            // Draw branch stem (feather diverging to the left)
                            drawLine(
                                color = Color.Gray,
                                start = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.15f),
                                strokeWidth = 2f
                            )
                            
                            // Draw Main Signal (linked_lever) on the track
                            drawCircle(
                                color = if (isReversed1) Color.Green else Color.Red,
                                radius = gridSizeY / 5,
                                center = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "${element.linkedLever + 1}",
                                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(px + gridSizeX * 0.65f - gridSizeY / 10, py + gridSizeY / 1.3f)
                            )

                            // Draw Branch Signal (linked_lever_2) above
                            drawCircle(
                                color = if (isReversed2) Color.Green else Color.Red,
                                radius = gridSizeY / 5,
                                center = Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.15f)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "${element.linkedLever2 + 1}",
                                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(px + gridSizeX * 0.35f - gridSizeY / 10, py - gridSizeY * 0.4f)
                            )
                        }
                    }
                }

                // Draw block names once per block, centered across all their elements
                val blockElementsMap = tabDef.schematicElements
                    .filter { it.linkedBlock >= 0 }
                    .groupBy { it.linkedBlock }

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
}
