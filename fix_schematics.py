import sys

def process_file(path, is_editor):
    with open(path, 'r') as f:
        data = f.read()

    # Add rotate import
    if "import androidx.compose.ui.graphics.drawscope.rotate" not in data:
        data = data.replace(
            "import androidx.compose.ui.graphics.drawscope.DrawScope\n",
            "import androidx.compose.ui.graphics.drawscope.DrawScope\nimport androidx.compose.ui.graphics.drawscope.rotate\n"
        )
        
    # TURNOUT_RIGHT rename -> TURNOUT_LEFT
    if is_editor:
        # types list
        data = data.replace(
            '"STRAIGHT_H", "STRAIGHT_V", "TURNOUT_RIGHT", "SIGNAL_LEFT", "SIGNAL_RIGHT", "BRACKET_SIGNAL"',
            '"STRAIGHT_H", "STRAIGHT_V", "TURNOUT_LEFT", "TURNOUT_RIGHT", "SIGNAL_LEFT", "SIGNAL_RIGHT", "BRACKET_SIGNAL_LEFT", "BRACKET_SIGNAL_RIGHT"'
        )
        
        turnout_right_block = """                        "TURNOUT_RIGHT" -> {
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
                        }"""
        
        turnout_left_block = turnout_right_block.replace('"TURNOUT_RIGHT"', '"TURNOUT_LEFT"')
        
        turnout_right_new_block = turnout_right_block.replace(
            'Offset(px + gridSizeX, py - gridSizeY / 2)', 'Offset(px + gridSizeX, py + gridSizeY * 1.5f)'
        ).replace(
            'py + gridSizeY * 0.15f', 'py + gridSizeY * 0.85f'
        )
        
        data = data.replace(turnout_right_block, turnout_left_block + "\n" + turnout_right_new_block)
        
        bracket_block = """                        "BRACKET_SIGNAL" -> {
                            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 4f)
                            drawLine(Color.Gray, Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2), Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.15f), strokeWidth = 2f)
                            drawCircle(Color.Red, radius = gridSizeY / 5, center = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2))
                            val arrowColor1 = Color.White
                            val cx1 = px + gridSizeX * 0.65f
                            val cy1 = py + gridSizeY / 2
                            val arrowWidth1 = gridSizeY / 5 * 1.2f
                            val arrowHeight1 = gridSizeY / 5 * 0.8f
                            drawLine(arrowColor1, Offset(cx1 - arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2, cy1), strokeWidth = 3f)
                            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 - arrowHeight1 / 2), strokeWidth = 3f)
                            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 + arrowHeight1 / 2), strokeWidth = 3f)
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
                            drawLine(arrowColor2, Offset(cx2 - arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2, cy2), strokeWidth = 3f)
                            drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 - arrowHeight2 / 2), strokeWidth = 3f)
                            drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 + arrowHeight2 / 2), strokeWidth = 3f)
                            if (element.linked_lever_2 >= 0) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = "${element.linked_lever_2 + 1}",
                                    style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    topLeft = Offset(px + gridSizeX * 0.35f - gridSizeY / 10, py - gridSizeY * 0.4f)
                                )
                            }
                        }"""
                        
        bracket_left_block = bracket_block.replace('"BRACKET_SIGNAL"', '"BRACKET_SIGNAL_LEFT"').replace(
            "drawLine(arrowColor2, Offset(cx2 - arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2, cy2), strokeWidth = 3f)\n                            drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 - arrowHeight2 / 2), strokeWidth = 3f)\n                            drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 + arrowHeight2 / 2), strokeWidth = 3f)",
            "rotate(-45f, Offset(cx2, cy2)) {\n                                drawLine(arrowColor2, Offset(cx2 - arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2, cy2), strokeWidth = 3f)\n                                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 - arrowHeight2 / 2), strokeWidth = 3f)\n                                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 + arrowHeight2 / 2), strokeWidth = 3f)\n                            }"
        )
        
        bracket_right_block = bracket_left_block.replace('"BRACKET_SIGNAL_LEFT"', '"BRACKET_SIGNAL_RIGHT"').replace(
            'py + gridSizeY * 0.15f', 'py + gridSizeY * 0.85f'
        ).replace(
            'py - gridSizeY * 0.4f', 'py + gridSizeY * 1.15f'
        ).replace(
            'rotate(-45f', 'rotate(45f'
        )
        
        data = data.replace(bracket_block, bracket_left_block + "\n" + bracket_right_block)
        
    else:
        # SchematicScreen.kt
        
        # fix finding turnout right in left logic
        data = data.replace('it.type == "TURNOUT_RIGHT"', 'it.type == "TURNOUT_LEFT"\n                            }\n                            if (leftElement == null) {\n                                // Check if a turnout from the row above points down to this cell\n                                leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y - 1 && it.type == "TURNOUT_RIGHT" }')
        
        turnout_right_block = """                        "TURNOUT_RIGHT" -> {
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
                        }"""
                        
        turnout_left_block = turnout_right_block.replace('"TURNOUT_RIGHT"', '"TURNOUT_LEFT"')
        turnout_right_new_block = turnout_right_block.replace(
            'element.y - 1', 'element.y + 1'
        ).replace(
            'py - gridSizeY / 2', 'py + gridSizeY * 1.5f'
        ).replace(
            'py + gridSizeY * 0.15f', 'py + gridSizeY * 0.85f'
        )
        data = data.replace(turnout_right_block, turnout_left_block + "\n" + turnout_right_new_block)
        
        bracket_block = """                        "BRACKET_SIGNAL" -> {
                            val isReversed1 = if (element.linkedLever in leverStates.indices) leverStates[element.linkedLever] else false
                            val isReversed2 = if (element.linkedLever2 in leverStates.indices) leverStates[element.linkedLever2] else false
                            // Draw main line
                            drawLine(
                                color = trackColor,
                                start = Offset(px, py + gridSizeY / 2),
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
                            val signalColor1 = if (isReversed1) Color.Green else Color.Red
                            drawCircle(
                                color = signalColor1,
                                radius = gridSizeY / 5,
                                center = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2)
                            )
                            val arrowColor1 = if (signalColor1 == Color.Red) Color.White else Color.Black
                            val cx1 = px + gridSizeX * 0.65f
                            val cy1 = py + gridSizeY / 2
                            val arrowWidth1 = gridSizeY / 5 * 1.2f
                            val arrowHeight1 = gridSizeY / 5 * 0.8f
                            drawLine(arrowColor1, Offset(cx1 - arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2, cy1), strokeWidth = 3f)
                            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 - arrowHeight1 / 2), strokeWidth = 3f)
                            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 + arrowHeight1 / 2), strokeWidth = 3f)
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "${element.linkedLever + 1}",
                                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(px + gridSizeX * 0.65f - gridSizeY / 10, py + gridSizeY * 0.75f)
                            )

                            // Draw Branch Signal (linked_lever_2) above
                            val signalColor2 = if (isReversed2) Color.Green else Color.Red
                            drawCircle(
                                color = signalColor2,
                                radius = gridSizeY / 5,
                                center = Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.15f)
                            )
                            val arrowColor2 = if (signalColor2 == Color.Red) Color.White else Color.Black
                            val cx2 = px + gridSizeX * 0.35f
                            val cy2 = py + gridSizeY * 0.15f
                            val arrowWidth2 = gridSizeY / 5 * 1.2f
                            val arrowHeight2 = gridSizeY / 5 * 0.8f
                            drawLine(arrowColor2, Offset(cx2 - arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2, cy2), strokeWidth = 3f)
                            drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 - arrowHeight2 / 2), strokeWidth = 3f)
                            drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 + arrowHeight2 / 2), strokeWidth = 3f)
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "${element.linkedLever2 + 1}",
                                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(px + gridSizeX * 0.35f - gridSizeY / 10, py - gridSizeY * 0.4f)
                            )
                        }"""
                        
        bracket_left_block = bracket_block.replace('"BRACKET_SIGNAL"', '"BRACKET_SIGNAL_LEFT"').replace(
            "drawLine(arrowColor2, Offset(cx2 - arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2, cy2), strokeWidth = 3f)\n                            drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 - arrowHeight2 / 2), strokeWidth = 3f)\n                            drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 + arrowHeight2 / 2), strokeWidth = 3f)",
            "rotate(-45f, Offset(cx2, cy2)) {\n                                drawLine(arrowColor2, Offset(cx2 - arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2, cy2), strokeWidth = 3f)\n                                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 - arrowHeight2 / 2), strokeWidth = 3f)\n                                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 + arrowHeight2 / 2), strokeWidth = 3f)\n                            }"
        )
        
        bracket_right_block = bracket_left_block.replace('"BRACKET_SIGNAL_LEFT"', '"BRACKET_SIGNAL_RIGHT"').replace(
            'py + gridSizeY * 0.15f', 'py + gridSizeY * 0.85f'
        ).replace(
            'py - gridSizeY * 0.4f', 'py + gridSizeY * 1.15f'
        ).replace(
            'rotate(-45f', 'rotate(45f'
        ).replace(
            'feather diverging to the left', 'feather diverging to the right'
        ).replace(
            'above', 'below'
        )
        
        data = data.replace(bracket_block, bracket_left_block + "\n" + bracket_right_block)
        
    with open(path, 'w') as f:
        f.write(data)

process_file('shared/src/commonMain/kotlin/org/edranor/leverframe/SchematicEditorScreen.kt', True)
process_file('shared/src/commonMain/kotlin/org/edranor/leverframe/SchematicScreen.kt', False)

