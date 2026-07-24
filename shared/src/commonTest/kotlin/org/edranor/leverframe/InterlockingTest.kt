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
package org.edranor.leverframe

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class InterlockingTest {
    private fun createLevers(vararg reversed: Boolean): List<DomainLever> = reversed.mapIndexed { i, b -> DomainLever(i, b) }
    private fun createBlocks(vararg occupied: Boolean): List<DomainBlock> = occupied.mapIndexed { i, b -> DomainBlock(i, b) }


    @Test
    fun testSimpleLock() {
        // Lever 0 locks Lever 1 Normal
        val leverDefs = listOf(
            LeverDef(listOf(InterlockingCondition(targetIndex = 1, requiredState = false))),
            LeverDef()
        )
        val tab = TabDef(leverDefs)
        val levers = createLevers(false, false)

        assertTrue(Interlocking.evaluate(tab, levers, emptyList(), 0, true))

        val leversMod = createLevers(true, false)

        assertFalse(Interlocking.evaluate(tab, leversMod, emptyList(), 1, true))

        assertTrue(Interlocking.evaluate(tab, leversMod, emptyList(), 0, false))
    }

    @Test
    fun testOtherwiseLogic() {
        // Lever 0 locks Lever 1 Normal o/w Lever 2 is Reversed
        val leverDefs = listOf(
            LeverDef(listOf(InterlockingCondition(
                targetIndex = 1, requiredState = false,
                altTargetIndex = 2, altRequiredState = true
            ))),
            LeverDef(),
            LeverDef()
        )
        val tab = TabDef(leverDefs)
        
        val levers1 = createLevers(false, false, false)
        assertTrue(Interlocking.evaluate(tab, levers1, emptyList(), 0, true))
        
        val levers2 = createLevers(false, true, false)
        assertFalse(Interlocking.evaluate(tab, levers2, emptyList(), 0, true))
    }

    @Test
    fun testReverseLocking() {
        // Lever 1 locks Lever 0 Normal
        val leverDefs = listOf(
            LeverDef(),
            LeverDef(listOf(InterlockingCondition(targetIndex = 0, requiredState = false)))
        )
        val tab = TabDef(leverDefs)
        val levers = createLevers(false, true)

        assertFalse(Interlocking.evaluate(tab, levers, emptyList(), 0, true))
    }

    @Test
    fun testMutualLocking() {
        // Lever 0 locks Lever 1 Normal, Lever 1 locks Lever 0 Normal
        val leverDefs = listOf(
            LeverDef(listOf(InterlockingCondition(targetIndex = 1, requiredState = false))),
            LeverDef(listOf(InterlockingCondition(targetIndex = 0, requiredState = false)))
        )
        val tab = TabDef(leverDefs)
        val levers = createLevers(false, false)

        assertTrue(Interlocking.evaluate(tab, levers, emptyList(), 0, true))
        
        val leversMod = createLevers(true, false)
        
        assertFalse(Interlocking.evaluate(tab, leversMod, emptyList(), 1, true))
    }

    @Test
    fun testMultipleConditions() {
        // Lever 0 locks Lever 1 Normal AND Lever 2 Reversed
        val leverDefs = listOf(
            LeverDef(listOf(
                InterlockingCondition(targetIndex = 1, requiredState = false),
                InterlockingCondition(targetIndex = 2, requiredState = true)
            )),
            LeverDef(),
            LeverDef()
        )
        val tab = TabDef(leverDefs)
        
        val levers1 = createLevers(false, false, false)
        assertFalse(Interlocking.evaluate(tab, levers1, emptyList(), 0, true))

        val levers2 = createLevers(false, true, true)
        assertFalse(Interlocking.evaluate(tab, levers2, emptyList(), 0, true))

        val levers3 = createLevers(false, false, true)
        assertTrue(Interlocking.evaluate(tab, levers3, emptyList(), 0, true))
    }

    @Test
    fun testBlockCondition() {
        // Lever 0 requires Block 0 to be EMPTY (false)
        val leverDefs = listOf(
            LeverDef(listOf(
                InterlockingCondition(targetType = TargetType.BLOCK, targetIndex = 0, requiredState = false)
            ))
        )
        val tab = TabDef(leverDefs, blocks = listOf(BlockDef()))
        
        // blockStates: false means EMPTY, true means OCCUPIED
        val levers = createLevers(false)
        val blocksEmpty = createBlocks(false)
        val blocksOccupied = createBlocks(true)

        assertTrue(Interlocking.evaluate(tab, levers, blocksEmpty, 0, true))
        assertFalse(Interlocking.evaluate(tab, levers, blocksOccupied, 0, true))
    }

    @Test
    fun testAstLogic() {
        val logicTree = AndNode(listOf(
            OrNode(listOf(
                LeverStateNode(leverIndex = 1, requiredReversed = false),
                BlockStateNode(blockIndex = 0, requiredOccupied = true)
            )),
            NotNode(LeverStateNode(leverIndex = 2, requiredReversed = true))
        ))

        val leverDefs = listOf(
            LeverDef(logic = logicTree),
            LeverDef(),
            LeverDef()
        )
        val tab = TabDef(leverDefs, blocks = listOf(BlockDef()))

        // Condition: (L1:Normal OR Block0:Occupied) AND (NOT L2:Reversed)
        
        // Both conditions met: L1 is Normal, L2 is Normal
        val levers1 = createLevers(false, false, false)
        assertTrue(Interlocking.evaluate(tab, levers1, createBlocks(false), 0, true))

        // Fails AND condition: L2 is Reversed
        val levers2 = createLevers(false, false, true)
        assertFalse(Interlocking.evaluate(tab, levers2, createBlocks(false), 0, true))
        
        // Fails OR condition: L1 is Reversed, Block0 is Empty
        val levers3 = createLevers(false, true, false)
        assertFalse(Interlocking.evaluate(tab, levers3, createBlocks(false), 0, true))

        // Passes OR condition (Block0 is Occupied, despite L1 Reversed), AND passes (L2 Normal)
        val levers4 = createLevers(false, true, false)
        assertTrue(Interlocking.evaluate(tab, levers4, createBlocks(true), 0, true))
    }
}
