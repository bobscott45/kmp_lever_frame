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
 * Provides the pathfinding logic for eNtrance-eXit (NX) routing on the track schematic.
 * Calculates valid routes through turnouts and crossings, and translates them into required lever states.
 */
package org.edranor.leverframe.domain.engine
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
/**
 * Represents a resolved Entrance-Exit (NX) route across the schematic.
 * 
 * @property pathCells An ordered list of (X, Y) grid coordinates tracing the route from entrance to exit.
 */
data class NxRoute(
    val pathCells: List<Pair<Int, Int>>
)

/**
 * Engine responsible for evaluating Entrance-Exit (NX) routing logic on the track schematic.
 * It builds a connection graph from [SchematicElementDef] instances and resolves paths 
 * through complex junctions, translating them into the necessary lever throws.
 */
object NxRoutingEngine {

    /**
     * Determines the adjacent grid coordinates that a specific schematic element physically connects to.
     *
     * @param element The schematic element to evaluate.
     * @param map A lookup map of all elements on the grid, keyed by (X, Y) coordinates.
     * @return A list of (X, Y) coordinates this element can route to.
     */
    fun getConnections(element: SchematicElementDef, map: Map<Pair<Int, Int>, SchematicElementDef>): List<Pair<Int, Int>> {
        val x = element.x
        val y = element.y
        val conns = mutableListOf<Pair<Int, Int>>()
        
        when (element.type.name) {
            "STRAIGHT_H", "SIGNAL_LEFT", "SIGNAL_RIGHT", "BRACKET_SIGNAL_LEFT", "BRACKET_SIGNAL_RIGHT" -> {
                // Connects right
                conns.add(Pair(x + 1, y))
                
                // Connects left to straight
                val leftStraight = map[Pair(x - 1, y)]
                if (leftStraight != null) {
                    conns.add(Pair(x - 1, y))
                }
                // Connects left to a turnout coming from below
                val leftTurnoutUp = map[Pair(x - 1, y + 1)]
                if (leftTurnoutUp?.type?.name == "TURNOUT_LEFT") {
                    conns.add(Pair(x - 1, y + 1))
                }
                // Connects left to a turnout coming from above
                val leftTurnoutDown = map[Pair(x - 1, y - 1)]
                if (leftTurnoutDown?.type?.name == "TURNOUT_RIGHT") {
                    conns.add(Pair(x - 1, y - 1))
                }
                // Fallback: if there's nothing diagonal, we still report left so we don't break simple tracks
                if (leftTurnoutUp?.type?.name != "TURNOUT_LEFT" && leftTurnoutDown?.type?.name != "TURNOUT_RIGHT") {
                    conns.add(Pair(x - 1, y))
                }
            }
            "STRAIGHT_V" -> {
                conns.add(Pair(x, y - 1))
                conns.add(Pair(x, y + 1))
            }
            "TURNOUT_LEFT" -> {
                conns.add(Pair(x - 1, y))
                conns.add(Pair(x + 1, y))
                conns.add(Pair(x + 1, y - 1))
            }
            "TURNOUT_RIGHT" -> {
                conns.add(Pair(x - 1, y))
                conns.add(Pair(x + 1, y))
                conns.add(Pair(x + 1, y + 1))
            }
            "TURNOUT_LEFT_TRAILING" -> {
                conns.add(Pair(x - 1, y))
                conns.add(Pair(x + 1, y))
                conns.add(Pair(x - 1, y - 1))
            }
            "TURNOUT_RIGHT_TRAILING" -> {
                conns.add(Pair(x - 1, y))
                conns.add(Pair(x + 1, y))
                conns.add(Pair(x - 1, y + 1))
            }
            "DIAMOND_CROSSING" -> {
                // Connects the straights
                conns.add(Pair(x - 1, y))
                conns.add(Pair(x + 1, y))
                // And maybe it connects the diagonals?
                // A true diamond crossing typically connects top-left to bottom-right, and bottom-left to top-right.
                // However, in our grid, it's usually just two straight tracks crossing.
                // We'll connect all 4 adjacent cells for now if they are on a straight line.
                // Wait, if it's two straight tracks crossing, one is horizontal, one is vertical.
                conns.add(Pair(x, y - 1))
                conns.add(Pair(x, y + 1))
            }
        }
        return conns.distinct()
    }

    /**
     * Performs a breadth-first search to find all valid, connected NX exit points from a starting coordinate.
     *
     * @param startX The X coordinate of the entrance button.
     * @param startY The Y coordinate of the entrance button.
     * @param elements The list of all schematic elements in the current frame.
     * @return A list of valid [NxRoute]s that originate at the starting point.
     */
    fun findReachableExits(
        startX: Int,
        startY: Int,
        elements: List<SchematicElementDef>
    ): List<NxRoute> {
        val map = elements.associateBy { Pair(it.x, it.y) }
        
        fun getConnectedNeighbors(pos: Pair<Int, Int>): List<Pair<Int, Int>> {
            val elem = map[pos] ?: return emptyList()
            val neighbors = getConnections(elem, map)
            return neighbors.filter { n ->
                val neighborElem = map[n]
                // Mutual connection check
                neighborElem != null && getConnections(neighborElem, map).contains(pos)
            }
        }
        
        val routes = mutableListOf<NxRoute>()
        val queue = ArrayDeque<List<Pair<Int, Int>>>()
        queue.add(listOf(Pair(startX, startY)))
        
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val current = path.last()
            
            val elem = map[current]!!
            if (path.size > 1 && (elem.nxButton == NxButtonType.EXIT_ONLY || elem.nxButton == NxButtonType.BOTH)) {
                routes.add(NxRoute(path))
                continue // Stop searching this branch beyond an exit
            }
            
            val neighbors = getConnectedNeighbors(current)
            for (n in neighbors) {
                if (!path.contains(n)) {
                    queue.add(path + n)
                }
            }
        }
        return routes
    }
    /**
     * Traverses an Abstract Syntax Tree (AST) representing interlocking logic and extracts
     * the mandatory lever states required to satisfy the condition. Used by the NX routing
     * engine to automatically throw points required for a specific route.
     *
     * @param node The root AST node to evaluate.
     * @param inNot Internal flag used during recursive descent to invert required states.
     * @return A map of Lever Index -> Required State (`true` for Reversed, `false` for Normal).
     */
    fun getRequiredLeverStatesFromAst(node: AstNode, inNot: Boolean = false): Map<Int, Boolean> {
        val res = mutableMapOf<Int, Boolean>()
        when (node) {
            is AndNode -> node.children.forEach { res.putAll(getRequiredLeverStatesFromAst(it, inNot)) }
            is NotNode -> res.putAll(getRequiredLeverStatesFromAst(node.child, !inNot))
            is LeverStateNode -> res[node.leverIndex] = if (inNot) !node.requiredReversed else node.requiredReversed
            else -> {}
        }
        return res
    }
}
