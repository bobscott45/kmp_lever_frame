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
    val gridDp = 40.dp
    val maxX = tabDef.schematicElements.maxOfOrNull { it.x } ?: 0
    val widthDp = ((maxX + 2) * 40).dp

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .background(Color(0xFF1E1E1E))
            .horizontalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.width(widthDp).fillMaxHeight()
        ) {
            val gridSize = gridDp.toPx()

            tabDef.schematicElements.forEach { element ->
                val px = element.x * gridSize
                val py = element.y * gridSize

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
                        topLeft = Offset(px + 4f, py + gridSize / 4)
                    )
                }

                when (element.type) {
                    "STRAIGHT_H" -> drawLine(
                        color = trackColor,
                        start = Offset(px, py + gridSize / 2),
                        end = Offset(px + gridSize, py + gridSize / 2),
                        strokeWidth = 4f
                    )
                    "STRAIGHT_V" -> drawLine(
                        color = trackColor,
                        start = Offset(px + gridSize / 2, py),
                        end = Offset(px + gridSize / 2, py + gridSize),
                        strokeWidth = 4f
                    )
                    "TURNOUT_RIGHT" -> {
                        val isReversed = if (element.linkedLever in leverStates.indices) leverStates[element.linkedLever] else false
                        // Draw main line
                        drawLine(
                            color = trackColor,
                            start = Offset(px, py + gridSize / 2),
                            end = Offset(px + gridSize, py + gridSize / 2),
                            strokeWidth = 4f
                        )
                        // Draw diverging line
                        val divergeColor = if (isReversed) Color.Green else trackColor
                        drawLine(
                            color = divergeColor,
                            start = Offset(px + gridSize / 2, py + gridSize / 2),
                            end = Offset(px + gridSize, py),
                            strokeWidth = 4f
                        )
                    }
                    "SIGNAL_LEFT" -> {
                        val isReversed = if (element.linkedLever in leverStates.indices) leverStates[element.linkedLever] else false
                        drawCircle(
                            color = if (isReversed) Color.Green else Color.Red,
                            radius = gridSize / 4,
                            center = Offset(px + gridSize / 2, py + gridSize / 2)
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${element.linkedLever}",
                            style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(px + gridSize / 2.5f, py + gridSize / 1.3f)
                        )
                    }
                }
            }
        }
    }
}
