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
 * Unit tests validating the graph search algorithms used to set and cancel NX routes.
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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NxRoutingEngineTest {

    @Test
    fun testGetRequiredLeverStatesFromAst() {
        // Lever 4 Reversed
        val reqL4R = LeverStateNode(leverIndex = 4, requiredReversed = true)
        var map = NxRoutingEngine.getRequiredLeverStatesFromAst(reqL4R)
        assertEquals(true, map[4])

        // Not (Lever 5 Reversed) -> Lever 5 Normal
        val reqL5N = NotNode(LeverStateNode(leverIndex = 5, requiredReversed = true))
        map = NxRoutingEngine.getRequiredLeverStatesFromAst(reqL5N)
        assertEquals(false, map[5])

        // And(Lever 4 Reversed, Not(Lever 5 Reversed))
        val andNode = AndNode(listOf(reqL4R, reqL5N))
        map = NxRoutingEngine.getRequiredLeverStatesFromAst(andNode)
        assertEquals(true, map[4])
        assertEquals(false, map[5])

        // Block state node should be ignored
        val blockNode = BlockStateNode(blockIndex = 2, requiredOccupied = false)
        map = NxRoutingEngine.getRequiredLeverStatesFromAst(blockNode)
        assertTrue(map.isEmpty())
        
        // And(Lever 4 Reversed, Block 2 Clear) -> Only Lever 4 is extracted
        val mixedNode = AndNode(listOf(reqL4R, blockNode))
        map = NxRoutingEngine.getRequiredLeverStatesFromAst(mixedNode)
        assertEquals(1, map.size)
        assertEquals(true, map[4])
    }

    @Test
    fun testFindReachableExitsStraightLine() {
        val elements = listOf(
            SchematicElementDef(x = 0, y = 0, type = SchematicElementType.SIGNAL_LEFT, nxButton = NxButtonType.ENTRANCE_ONLY),
            SchematicElementDef(x = 1, y = 0, type = SchematicElementType.STRAIGHT_H),
            SchematicElementDef(x = 2, y = 0, type = SchematicElementType.STRAIGHT_H),
            SchematicElementDef(x = 3, y = 0, type = SchematicElementType.SIGNAL_LEFT, nxButton = NxButtonType.EXIT_ONLY)
        )
        
        val routes = NxRoutingEngine.findReachableExits(0, 0, elements)
        assertEquals(1, routes.size)
        val route = routes.first()
        assertEquals(4, route.pathCells.size)
        assertEquals(Pair(3, 0), route.pathCells.last())
    }

    @Test
    fun testFindReachableExitsDiverging() {
        val elements = listOf(
            SchematicElementDef(x = 0, y = 0, type = SchematicElementType.SIGNAL_LEFT, nxButton = NxButtonType.ENTRANCE_ONLY),
            SchematicElementDef(x = 1, y = 0, type = SchematicElementType.TURNOUT_RIGHT), // Splits to (2,0) and (2,1)
            SchematicElementDef(x = 2, y = 0, type = SchematicElementType.SIGNAL_LEFT, nxButton = NxButtonType.EXIT_ONLY),
            SchematicElementDef(x = 2, y = 1, type = SchematicElementType.SIGNAL_LEFT, nxButton = NxButtonType.EXIT_ONLY)
        )
        
        val routes = NxRoutingEngine.findReachableExits(0, 0, elements)
        assertEquals(2, routes.size)
        
        val straightRoute = routes.find { it.pathCells.last() == Pair(2, 0) }
        val divergingRoute = routes.find { it.pathCells.last() == Pair(2, 1) }
        
        assertTrue(straightRoute != null)
        assertTrue(divergingRoute != null)
        
        assertEquals(3, straightRoute?.pathCells?.size)
        assertEquals(3, divergingRoute?.pathCells?.size)
    }
}
