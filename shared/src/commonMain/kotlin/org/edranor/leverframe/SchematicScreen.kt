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
import androidx.compose.ui.graphics.drawscope.rotate
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
    levers: List<DomainLever>,
    blocks: List<DomainBlock>,
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
                            y = centerPy - textLayout.size.height / 2f - gridSizeY * 0.2f
                        )
                    )
                }
            }
        }
    }
}
