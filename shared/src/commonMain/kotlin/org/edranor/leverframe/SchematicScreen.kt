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
    val cellsX = (maxX + 2).coerceAtLeast(1)

    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier.background(Color(0xFF1E1E1E))
    ) {
        val minGridSizeX = 40.dp
        val maxGridSizeX = 120.dp
        
        val calculatedGridSizeX = maxWidth / cellsX
        val gridDpX = calculatedGridSizeX.coerceIn(minGridSizeX, maxGridSizeX)
        val widthDp = gridDpX * cellsX

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.width(widthDp).fillMaxHeight()
            ) {
                val gridSizeX = gridDpX.toPx()
                val gridSizeY = 40.dp.toPx()

                tabDef.schematicElements.forEach { element ->
                    val px = element.x * gridSizeX
                    val py = element.y * gridSizeY

                    val isBlockOccupied = if (element.linkedBlock.isNotEmpty()) {
                        val blockIndex = tabDef.blocks.indexOfFirst { it.label == element.linkedBlock }
                        if (blockIndex in blockStates.indices) blockStates[blockIndex] else false
                    } else false

                    val trackColor = if (isBlockOccupied) Color.Red else Color.LightGray

                    if (element.linkedBlock.isNotEmpty() && element.type.startsWith("STRAIGHT")) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = element.linkedBlock,
                            style = TextStyle(color = Color.LightGray, fontSize = 8.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(px + 4f, py + gridSizeY / 4)
                        )
                    }

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
                            val divergeColor = if (isReversed) Color.Green else trackColor
                            drawLine(
                                color = divergeColor,
                                start = Offset(px + gridSizeX / 2, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX, py),
                                strokeWidth = 4f
                            )
                        }
                        "SIGNAL_LEFT" -> {
                            val isReversed = if (element.linkedLever in leverStates.indices) leverStates[element.linkedLever] else false
                            // Draw horizontal track through the signal cell
                            drawLine(
                                color = trackColor,
                                start = Offset(px, py + gridSizeY / 2),
                                end = Offset(px + gridSizeX, py + gridSizeY / 2),
                                strokeWidth = 4f
                            )
                            drawCircle(
                                color = if (isReversed) Color.Green else Color.Red,
                                radius = gridSizeY / 4,
                                center = Offset(px + gridSizeX / 2, py + gridSizeY / 2)
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "${element.linkedLever}",
                                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(px + gridSizeX / 2.5f, py + gridSizeY / 1.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
