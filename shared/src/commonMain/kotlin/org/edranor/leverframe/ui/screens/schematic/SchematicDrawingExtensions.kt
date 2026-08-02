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
 * Extension functions for drawing individual track schematic elements on a Canvas.
 * Encapsulates the visual rendering logic for turnouts, signals, crossovers, and NX buttons.
 */
package org.edranor.leverframe.ui.screens.schematic
import org.edranor.leverframe.domain.engine.LeverType
import org.edranor.leverframe.domain.engine.SchematicElementType
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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * An extension function on [DrawScope] responsible for rendering a single schematic element
 * (like a straight track, turnout, or signal) at its assigned grid coordinates.
 */
fun DrawScope.drawSchematicElement(
    element: SchematicElementDef,
    tabDef: TabDef,
    levers: List<DomainLever>,
    blocks: List<DomainBlock>,
    textMeasurer: TextMeasurer,
    gridSizeX: Float,
    gridSizeY: Float,
    startX: Float
) {
    /**
     * Determines the fill color of a track block based on its occupancy state.
     * @return [Color.Red] if occupied, otherwise [Color.White] or [Color.Gray] if invalid.
     */
    fun getBlockColor(blockIdx: Int): Color {
        if (blockIdx < 0 || blockIdx >= tabDef.blocks.size) return Color.Gray
        val occupied = if (blockIdx in blocks.indices) blocks[blockIdx].isOccupied else false
        return if (occupied) Color.Red else Color.White
    }

    val px = startX + element.x * gridSizeX
    val py = element.y * gridSizeY

    val trackColor = getBlockColor(element.linkedBlock)

    when (element.type) {
        SchematicElementType.STRAIGHT_H -> drawLine(
            color = trackColor,
            start = Offset(px, py + gridSizeY / 2),
            end = Offset(px + gridSizeX, py + gridSizeY / 2),
            strokeWidth = 2f
        )
        SchematicElementType.STRAIGHT_V -> drawLine(
            color = trackColor,
            start = Offset(px + gridSizeX / 2, py),
            end = Offset(px + gridSizeX / 2, py + gridSizeY),
            strokeWidth = 2f
        )
        SchematicElementType.TURNOUT_LEFT -> {
            val isReversed = if (element.linkedLever in levers.indices) levers[element.linkedLever].isReversed else false
            val mainRightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
            val mainRightColor = mainRightElement?.let { getBlockColor(it.linkedBlock) } ?: trackColor

            // Draw left half of main line
            drawLine(color = trackColor, start = Offset(px, py + gridSizeY / 2), end = Offset(px + gridSizeX / 2, py + gridSizeY / 2), strokeWidth = 2f)
            // Draw right half of main line
            drawLine(color = mainRightColor, start = Offset(px + gridSizeX / 2, py + gridSizeY / 2), end = Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
            // Draw diverging line
            val divergeElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y - 1 }
            val divergeBlockColor = divergeElement?.let { getBlockColor(it.linkedBlock) } ?: trackColor
            val divergeColor = if (isReversed) Color.Green else divergeBlockColor
            drawLine(color = divergeColor, start = Offset(px + gridSizeX / 2, py + gridSizeY / 2), end = Offset(px + gridSizeX, py - gridSizeY / 2), strokeWidth = 2f)
            if (element.linkedLever >= 0) {
                drawText(textMeasurer = textMeasurer, text = "${element.linkedLever + 1}", style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold), topLeft = Offset(px + gridSizeX * 0.7f, py + gridSizeY * 0.15f))
            }
        }
        SchematicElementType.TURNOUT_RIGHT -> {
            val isReversed = if (element.linkedLever in levers.indices) levers[element.linkedLever].isReversed else false
            val mainRightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
            val mainRightColor = mainRightElement?.let { getBlockColor(it.linkedBlock) } ?: trackColor

            // Draw left half of main line
            drawLine(color = trackColor, start = Offset(px, py + gridSizeY / 2), end = Offset(px + gridSizeX / 2, py + gridSizeY / 2), strokeWidth = 2f)
            // Draw right half of main line
            drawLine(color = mainRightColor, start = Offset(px + gridSizeX / 2, py + gridSizeY / 2), end = Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
            // Draw diverging line
            val divergeElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y + 1 }
            val divergeBlockColor = divergeElement?.let { getBlockColor(it.linkedBlock) } ?: trackColor
            val divergeColor = if (isReversed) Color.Green else divergeBlockColor
            drawLine(color = divergeColor, start = Offset(px + gridSizeX / 2, py + gridSizeY / 2), end = Offset(px + gridSizeX, py + gridSizeY * 1.5f), strokeWidth = 2f)
            if (element.linkedLever >= 0) {
                drawText(textMeasurer = textMeasurer, text = "${element.linkedLever + 1}", style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold), topLeft = Offset(px + gridSizeX * 0.7f, py + gridSizeY * 0.85f))
            }
        }
        SchematicElementType.TURNOUT_LEFT_TRAILING -> {
            val isReversed = if (element.linkedLever in levers.indices) levers[element.linkedLever].isReversed else false
            val mainRightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
            val mainRightColor = mainRightElement?.let { getBlockColor(it.linkedBlock) } ?: trackColor

            // Draw left half of main line
            drawLine(color = trackColor, start = Offset(px, py + gridSizeY / 2), end = Offset(px + gridSizeX / 2, py + gridSizeY / 2), strokeWidth = 2f)
            // Draw right half of main line
            drawLine(color = mainRightColor, start = Offset(px + gridSizeX / 2, py + gridSizeY / 2), end = Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
            
            val divergeElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y - 1 }
            val divergeBlockColor = divergeElement?.let { getBlockColor(it.linkedBlock) } ?: trackColor
            val divergeColor = if (isReversed) Color.Green else divergeBlockColor
            
            drawLine(color = divergeColor, start = Offset(px + gridSizeX / 2, py + gridSizeY / 2), end = Offset(px, py - gridSizeY / 2), strokeWidth = 2f)
            if (element.linkedLever >= 0) {
                drawText(textMeasurer = textMeasurer, text = "${element.linkedLever + 1}", style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold), topLeft = Offset(px + gridSizeX * 0.1f, py + gridSizeY * 0.15f))
            }
        }
        SchematicElementType.TURNOUT_RIGHT_TRAILING -> {
            val isReversed = if (element.linkedLever in levers.indices) levers[element.linkedLever].isReversed else false
            val mainRightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
            val mainRightColor = mainRightElement?.let { getBlockColor(it.linkedBlock) } ?: trackColor

            // Draw left half of main line
            drawLine(color = trackColor, start = Offset(px, py + gridSizeY / 2), end = Offset(px + gridSizeX / 2, py + gridSizeY / 2), strokeWidth = 2f)
            // Draw right half of main line
            drawLine(color = mainRightColor, start = Offset(px + gridSizeX / 2, py + gridSizeY / 2), end = Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)

            val divergeElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y + 1 }
            val divergeBlockColor = divergeElement?.let { getBlockColor(it.linkedBlock) } ?: trackColor
            val divergeColor = if (isReversed) Color.Green else divergeBlockColor

            drawLine(color = divergeColor, start = Offset(px + gridSizeX / 2, py + gridSizeY / 2), end = Offset(px, py + gridSizeY * 1.5f), strokeWidth = 2f)
            if (element.linkedLever >= 0) {
                drawText(textMeasurer = textMeasurer, text = "${element.linkedLever + 1}", style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold), topLeft = Offset(px + gridSizeX * 0.1f, py + gridSizeY * 0.85f))
            }
        }
        SchematicElementType.DIAMOND_CROSSING -> {
            drawLine(trackColor, Offset(px, py + gridSizeY / 2), Offset(px + gridSizeX, py + gridSizeY / 2), strokeWidth = 2f)
            drawLine(trackColor, Offset(px + gridSizeX / 2, py), Offset(px + gridSizeX / 2, py + gridSizeY), strokeWidth = 2f)
        }
        SchematicElementType.SIGNAL_LEFT -> {
            val isReversed = if (element.linkedLever in levers.indices) levers[element.linkedLever].isReversed else false
            var leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y }
            if (leftElement == null) {
                // Check if a turnout from the row below points up to this cell
                leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y + 1 && it.type == SchematicElementType.TURNOUT_LEFT }
            }
            if (leftElement == null) {
                // Check if a turnout from the row above points down to this cell
                leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y - 1 && it.type == SchematicElementType.TURNOUT_RIGHT }
            }
            val rightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
            
            val leftColor = trackColor
            val rightColor = rightElement?.let { getBlockColor(it.linkedBlock) } ?: Color.Gray

            // Draw left half of track through the signal cell
            drawLine(
                color = leftColor,
                start = Offset(px, py + gridSizeY / 2),
                end = Offset(px + gridSizeX / 2, py + gridSizeY / 2),
                strokeWidth = 2f
            )
            // Draw right half of track through the signal cell
            drawLine(
                color = rightColor,
                start = Offset(px + gridSizeX / 2, py + gridSizeY / 2),
                end = Offset(px + gridSizeX, py + gridSizeY / 2),
                strokeWidth = 2f
            )
            val leverType = tabDef.levers.getOrNull(element.linkedLever)?.type
            val normalColor = if (leverType == LeverType.DISTANT_SIGNAL) Color.Yellow else Color.Red
            val signalColor = if (isReversed) Color.Green else normalColor
            drawCircle(
                color = signalColor,
                radius = gridSizeY / 5,
                center = Offset(px + gridSizeX / 2, py + gridSizeY / 2)
            )
            val arrowColor = if (signalColor == Color.Red) Color.White else Color.Black
            val cx = px + gridSizeX / 2
            val cy = py + gridSizeY / 2
            val arrowWidth = gridSizeY / 5 * 1.2f
            val arrowHeight = gridSizeY / 5 * 0.8f
            drawLine(arrowColor, Offset(cx - arrowWidth / 2, cy), Offset(cx + arrowWidth / 2, cy), strokeWidth = 2f)
            drawLine(arrowColor, Offset(cx - arrowWidth / 2, cy), Offset(cx - arrowWidth / 2 + arrowHeight / 2, cy - arrowHeight / 2), strokeWidth = 2f)
            drawLine(arrowColor, Offset(cx - arrowWidth / 2, cy), Offset(cx - arrowWidth / 2 + arrowHeight / 2, cy + arrowHeight / 2), strokeWidth = 2f)
            drawText(
                textMeasurer = textMeasurer,
                text = "${element.linkedLever + 1}",
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                topLeft = Offset(px + gridSizeX / 2 - gridSizeY / 10, py + gridSizeY * 0.75f)
            )
        }
        SchematicElementType.SIGNAL_RIGHT -> {
            val isReversed = if (element.linkedLever in levers.indices) levers[element.linkedLever].isReversed else false
            var leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y }
            if (leftElement == null) {
                // Check if a turnout from the row below points up to this cell
                leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y + 1 && it.type == SchematicElementType.TURNOUT_LEFT }
            }
            if (leftElement == null) {
                // Check if a turnout from the row above points down to this cell
                leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y - 1 && it.type == SchematicElementType.TURNOUT_RIGHT }
            }
            val rightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
            
            val rightColor = trackColor
            val leftColor = leftElement?.let { getBlockColor(it.linkedBlock) } ?: Color.Gray

            // Draw left half of track through the signal cell
            drawLine(
                color = leftColor,
                start = Offset(px, py + gridSizeY / 2),
                end = Offset(px + gridSizeX / 2, py + gridSizeY / 2),
                strokeWidth = 2f
            )
            // Draw right half of track through the signal cell
            drawLine(
                color = rightColor,
                start = Offset(px + gridSizeX / 2, py + gridSizeY / 2),
                end = Offset(px + gridSizeX, py + gridSizeY / 2),
                strokeWidth = 2f
            )
            val leverType = tabDef.levers.getOrNull(element.linkedLever)?.type
            val normalColor = if (leverType == LeverType.DISTANT_SIGNAL) Color.Yellow else Color.Red
            val signalColor = if (isReversed) Color.Green else normalColor
            drawCircle(
                color = signalColor,
                radius = gridSizeY / 5,
                center = Offset(px + gridSizeX / 2, py + gridSizeY / 2)
            )
            val arrowColor = if (signalColor == Color.Red) Color.White else Color.Black
            val cx = px + gridSizeX / 2
            val cy = py + gridSizeY / 2
            val arrowWidth = gridSizeY / 5 * 1.2f
            val arrowHeight = gridSizeY / 5 * 0.8f
            drawLine(arrowColor, Offset(cx - arrowWidth / 2, cy), Offset(cx + arrowWidth / 2, cy), strokeWidth = 2f)
            drawLine(arrowColor, Offset(cx + arrowWidth / 2, cy), Offset(cx + arrowWidth / 2 - arrowHeight / 2, cy - arrowHeight / 2), strokeWidth = 2f)
            drawLine(arrowColor, Offset(cx + arrowWidth / 2, cy), Offset(cx + arrowWidth / 2 - arrowHeight / 2, cy + arrowHeight / 2), strokeWidth = 2f)
            drawText(
                textMeasurer = textMeasurer,
                text = "${element.linkedLever + 1}",
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                topLeft = Offset(px + gridSizeX / 2 - gridSizeY / 10, py + gridSizeY * 0.75f)
            )
        }
        SchematicElementType.BRACKET_SIGNAL_LEFT -> {
            val isReversed1 = if (element.linkedLever in levers.indices) levers[element.linkedLever].isReversed else false
            val isReversed2 = if (element.linkedLever2 in levers.indices) levers[element.linkedLever2].isReversed else false
            
            val leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y }
            val rightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
            
            val rightColor = trackColor
            val leftColor = leftElement?.let { getBlockColor(it.linkedBlock) } ?: Color.Gray

            // Draw left half of track
            drawLine(
                color = leftColor,
                start = Offset(px, py + gridSizeY / 2),
                end = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2),
                strokeWidth = 2f
            )
            // Draw right half of track
            drawLine(
                color = rightColor,
                start = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2),
                end = Offset(px + gridSizeX, py + gridSizeY / 2),
                strokeWidth = 2f
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
            drawLine(arrowColor1, Offset(cx1 - arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2, cy1), strokeWidth = 2f)
            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 - arrowHeight1 / 2), strokeWidth = 2f)
            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 + arrowHeight1 / 2), strokeWidth = 2f)
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
            rotate(-45f, Offset(cx2, cy2)) {
                drawLine(arrowColor2, Offset(cx2 - arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2, cy2), strokeWidth = 2f)
                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 - arrowHeight2 / 2), strokeWidth = 2f)
                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 + arrowHeight2 / 2), strokeWidth = 2f)
            }
            drawText(
                textMeasurer = textMeasurer,
                text = "${element.linkedLever2 + 1}",
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                topLeft = Offset(px + gridSizeX * 0.35f - gridSizeY / 10, py - gridSizeY * 0.4f)
            )
        }
        SchematicElementType.BRACKET_SIGNAL_RIGHT -> {
            val isReversed1 = if (element.linkedLever in levers.indices) levers[element.linkedLever].isReversed else false
            val isReversed2 = if (element.linkedLever2 in levers.indices) levers[element.linkedLever2].isReversed else false
            
            val leftElement = tabDef.schematicElements.find { it.x == element.x - 1 && it.y == element.y }
            val rightElement = tabDef.schematicElements.find { it.x == element.x + 1 && it.y == element.y }
            
            val rightColor = trackColor
            val leftColor = leftElement?.let { getBlockColor(it.linkedBlock) } ?: Color.Gray

            // Draw left half of track
            drawLine(
                color = leftColor,
                start = Offset(px, py + gridSizeY / 2),
                end = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2),
                strokeWidth = 2f
            )
            // Draw right half of track
            drawLine(
                color = rightColor,
                start = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2),
                end = Offset(px + gridSizeX, py + gridSizeY / 2),
                strokeWidth = 2f
            )
            
            // Draw branch stem (feather diverging to the right)
            drawLine(
                color = Color.Gray,
                start = Offset(px + gridSizeX * 0.65f, py + gridSizeY / 2),
                end = Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.85f),
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
            drawLine(arrowColor1, Offset(cx1 - arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2, cy1), strokeWidth = 2f)
            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 - arrowHeight1 / 2), strokeWidth = 2f)
            drawLine(arrowColor1, Offset(cx1 + arrowWidth1 / 2, cy1), Offset(cx1 + arrowWidth1 / 2 - arrowHeight1 / 2, cy1 + arrowHeight1 / 2), strokeWidth = 2f)
            drawText(
                textMeasurer = textMeasurer,
                text = "${element.linkedLever + 1}",
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                topLeft = Offset(px + gridSizeX * 0.65f - gridSizeY / 10, py + gridSizeY * 0.75f)
            )

            // Draw Branch Signal (linked_lever_2) below
            val signalColor2 = if (isReversed2) Color.Green else Color.Red
            drawCircle(
                color = signalColor2,
                radius = gridSizeY / 5,
                center = Offset(px + gridSizeX * 0.35f, py + gridSizeY * 0.85f)
            )
            val arrowColor2 = if (signalColor2 == Color.Red) Color.White else Color.Black
            val cx2 = px + gridSizeX * 0.35f
            val cy2 = py + gridSizeY * 0.85f
            val arrowWidth2 = gridSizeY / 5 * 1.2f
            val arrowHeight2 = gridSizeY / 5 * 0.8f
            rotate(45f, Offset(cx2, cy2)) {
                drawLine(arrowColor2, Offset(cx2 - arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2, cy2), strokeWidth = 2f)
                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 - arrowHeight2 / 2), strokeWidth = 2f)
                drawLine(arrowColor2, Offset(cx2 + arrowWidth2 / 2, cy2), Offset(cx2 + arrowWidth2 / 2 - arrowHeight2 / 2, cy2 + arrowHeight2 / 2), strokeWidth = 2f)
            }
            drawText(
                textMeasurer = textMeasurer,
                text = "${element.linkedLever2 + 1}",
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                topLeft = Offset(px + gridSizeX * 0.35f - gridSizeY / 10, py + gridSizeY * 1.15f)
            )
        }
    }

    if (element.nxButton != NxButtonType.NONE) {
        val cx = px + when (element.nxButtonPlacement) {
            NxButtonPlacement.LEFT -> gridSizeX * 0.15f
            NxButtonPlacement.RIGHT -> gridSizeX * 0.85f
            NxButtonPlacement.TOP, NxButtonPlacement.BOTTOM -> gridSizeX * 0.5f
            else -> gridSizeX * 0.25f
        }
        val cy = py + when (element.nxButtonPlacement) {
            NxButtonPlacement.TOP -> gridSizeY * 0.15f
            NxButtonPlacement.BOTTOM -> gridSizeY * 0.85f
            NxButtonPlacement.LEFT, NxButtonPlacement.RIGHT -> gridSizeY * 0.5f
            else -> gridSizeY * 0.25f
        }
        val r = gridSizeY * 0.15f
        
        val fillCol = when (element.nxButtonColor) {
            NxButtonColor.WHITE -> Color.White
            NxButtonColor.RED -> Color.Red
            NxButtonColor.YELLOW -> Color.Yellow
            NxButtonColor.GREEN -> Color.Green
            NxButtonColor.BLUE -> Color.Blue
            else -> Color.Black
        }
        val borderCol = if (fillCol == Color.White || fillCol == Color.Yellow) Color.Black else Color.White
        
        when (element.nxButton) {
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
            else -> {}
        }
    }
}
