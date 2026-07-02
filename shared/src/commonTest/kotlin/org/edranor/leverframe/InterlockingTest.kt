/*
 * Copyright (C) 2026 Robert Scott
 *
 * This file is part of LeverFrame.
 *
 * LeverFrame is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LeverFrame is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LeverFrame.  If not, see <https://www.gnu.org/licenses/>.
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
            LeverDef(listOf(InterlockingCondition(targetLeverIndex = 1, requiredState = false))),
            LeverDef()
        )
        val tab = TabDef(levers)
        val states = booleanArrayOf(false, false)

        assertTrue(Interlocking.evaluate(tab, states, 0, true))

        states[0] = true

        assertFalse(Interlocking.evaluate(tab, states, 1, true))

        assertTrue(Interlocking.evaluate(tab, states, 0, false))
    }

    @Test
    fun testOtherwiseLogic() {
        // Lever 0 locks Lever 1 Normal o/w Lever 2 is Reversed
        val levers = listOf(
            LeverDef(listOf(InterlockingCondition(
                targetLeverIndex = 1, requiredState = false,
                altTargetLeverIndex = 2, altRequiredState = true
            ))),
            LeverDef(),
            LeverDef()
        )
        val tab = TabDef(levers)
        val states = booleanArrayOf(false, true, false)

        assertFalse(Interlocking.evaluate(tab, states, 0, true))

        states[2] = true

        assertTrue(Interlocking.evaluate(tab, states, 0, true))
    }

    @Test
    fun testReverseLocking() {
        // Lever 1 locks Lever 0 Normal
        val levers = listOf(
            LeverDef(),
            LeverDef(listOf(InterlockingCondition(targetLeverIndex = 0, requiredState = false)))
        )
        val tab = TabDef(levers)
        val states = booleanArrayOf(false, true)

        assertFalse(Interlocking.evaluate(tab, states, 0, true))
    }

    @Test
    fun testMutualLocking() {
        // Lever 0 locks Lever 1 Normal, Lever 1 locks Lever 0 Normal
        val levers = listOf(
            LeverDef(listOf(InterlockingCondition(targetLeverIndex = 1, requiredState = false))),
            LeverDef(listOf(InterlockingCondition(targetLeverIndex = 0, requiredState = false)))
        )
        val tab = TabDef(levers)
        val states = booleanArrayOf(false, false)

        assertTrue(Interlocking.evaluate(tab, states, 0, true))
        
        states[0] = true
        
        assertFalse(Interlocking.evaluate(tab, states, 1, true))
    }

    @Test
    fun testMultipleConditions() {
        // Lever 0 locks Lever 1 Normal AND Lever 2 Reversed
        val levers = listOf(
            LeverDef(listOf(
                InterlockingCondition(targetLeverIndex = 1, requiredState = false),
                InterlockingCondition(targetLeverIndex = 2, requiredState = true)
            )),
            LeverDef(),
            LeverDef()
        )
        val tab = TabDef(levers)
        
        val states1 = booleanArrayOf(false, false, false)
        assertFalse(Interlocking.evaluate(tab, states1, 0, true))

        val states2 = booleanArrayOf(false, true, true)
        assertFalse(Interlocking.evaluate(tab, states2, 0, true))

        val states3 = booleanArrayOf(false, false, true)
        assertTrue(Interlocking.evaluate(tab, states3, 0, true))
    }
}
