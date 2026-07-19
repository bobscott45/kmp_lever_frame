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

    @Test
    fun testSimpleLock() {
        // Lever 0 locks Lever 1 Normal
        val levers = listOf(
            LeverDef(listOf(InterlockingCondition(targetIndex = 1, requiredState = false))),
            LeverDef()
        )
        val tab = TabDef(levers)
        val states = booleanArrayOf(false, false)

        assertTrue(Interlocking.evaluate(tab, states, booleanArrayOf(), 0, true))

        states[0] = true

        assertFalse(Interlocking.evaluate(tab, states, booleanArrayOf(), 1, true))

        assertTrue(Interlocking.evaluate(tab, states, booleanArrayOf(), 0, false))
    }

    @Test
    fun testOtherwiseLogic() {
        // Lever 0 locks Lever 1 Normal o/w Lever 2 is Reversed
        val levers = listOf(
            LeverDef(listOf(InterlockingCondition(
                targetIndex = 1, requiredState = false,
                altTargetIndex = 2, altRequiredState = true
            ))),
            LeverDef(),
            LeverDef()
        )
        val tab = TabDef(levers)
        
        val states1 = booleanArrayOf(false, false, false)
        assertTrue(Interlocking.evaluate(tab, states1, booleanArrayOf(), 0, true))
        
        val states2 = booleanArrayOf(false, true, false)
        assertFalse(Interlocking.evaluate(tab, states2, booleanArrayOf(), 0, true))
    }

    @Test
    fun testReverseLocking() {
        // Lever 1 locks Lever 0 Normal
        val levers = listOf(
            LeverDef(),
            LeverDef(listOf(InterlockingCondition(targetIndex = 0, requiredState = false)))
        )
        val tab = TabDef(levers)
        val states = booleanArrayOf(false, true)

        assertFalse(Interlocking.evaluate(tab, states, booleanArrayOf(), 0, true))
    }

    @Test
    fun testMutualLocking() {
        // Lever 0 locks Lever 1 Normal, Lever 1 locks Lever 0 Normal
        val levers = listOf(
            LeverDef(listOf(InterlockingCondition(targetIndex = 1, requiredState = false))),
            LeverDef(listOf(InterlockingCondition(targetIndex = 0, requiredState = false)))
        )
        val tab = TabDef(levers)
        val states = booleanArrayOf(false, false)

        assertTrue(Interlocking.evaluate(tab, states, booleanArrayOf(), 0, true))
        
        states[0] = true
        
        assertFalse(Interlocking.evaluate(tab, states, booleanArrayOf(), 1, true))
    }

    @Test
    fun testMultipleConditions() {
        // Lever 0 locks Lever 1 Normal AND Lever 2 Reversed
        val levers = listOf(
            LeverDef(listOf(
                InterlockingCondition(targetIndex = 1, requiredState = false),
                InterlockingCondition(targetIndex = 2, requiredState = true)
            )),
            LeverDef(),
            LeverDef()
        )
        val tab = TabDef(levers)
        
        val states1 = booleanArrayOf(false, false, false)
        assertFalse(Interlocking.evaluate(tab, states1, booleanArrayOf(), 0, true))

        val states2 = booleanArrayOf(false, true, true)
        assertFalse(Interlocking.evaluate(tab, states2, booleanArrayOf(), 0, true))

        val states3 = booleanArrayOf(false, false, true)
        assertTrue(Interlocking.evaluate(tab, states3, booleanArrayOf(), 0, true))
    }
}
