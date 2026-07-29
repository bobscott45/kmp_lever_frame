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
 * Unit tests verifying that the AST rule evaluation logic correctly identifies valid and invalid conditions.
 */
package org.edranor.leverframe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuleValidatorTest {

    @Test
    fun testValidConfigurationReturnsEmptyMap() {
        // Lever 0 requires nothing. Lever 1 requires Lever 0 reversed.
        val tab = TabDef(
            levers = listOf(
                LeverDef(
                    label = "Lever 0",
                    type = LeverType.HOME_SIGNAL,
                    logic = null
                ),
                LeverDef(
                    label = "Lever 1",
                    type = LeverType.HOME_SIGNAL,
                    logic = LeverStateNode(0, true)
                )
            )
        )

        val result = RuleValidator.validate(tab)
        assertTrue(result.unreachableLevers.isEmpty(), "Expected no unreachable levers.")
    }

    @Test
    fun testContradictoryLeversDetected() {
        // Lever 0 requires Lever 1 Reversed.
        // Lever 1 requires Lever 0 Normal.
        // If Lever 0 is reversed, Lever 1 must be reversed.
        // If Lever 1 is reversed, Lever 0 must be normal.
        // Thus Lever 0 cannot be reversed, because pulling it requires L1 Reversed, which requires L0 Normal.
        // Lever 1 cannot be reversed unless L0 is normal, which is possible. Wait, if L1 is pulled while L0 is normal, it's valid!
        // Wait, if L0 is Normal, can we pull L1? L1 requires L0 Normal. Yes! So L1 IS reachable!
        // But L0 requires L1 Reversed. If we pull L1, L1 is Reversed, L0 is Normal.
        // From (L0=N, L1=R), can we pull L0? L0 requires L1 Reversed. Yes, but pulling L0 means state is (L0=R, L1=R).
        // Is (L0=R, L1=R) valid?
        // Let's check:
        // L0 logic: requires L1 Reversed. (True, L1 is R)
        // L1 logic: requires L0 Normal. (False, L0 is R)
        // So (L0=R, L1=R) is a conflict!
        // Therefore, L0 can NEVER be reversed.

        val tab = TabDef(
            levers = listOf(
                LeverDef(
                    label = "Lever 0",
                    type = LeverType.HOME_SIGNAL,
                    logic = LeverStateNode(1, true)
                ),
                LeverDef(
                    label = "Lever 1",
                    type = LeverType.HOME_SIGNAL,
                    logic = LeverStateNode(0, false)
                )
            )
        )

        val result = RuleValidator.validate(tab)
        
        // Lever 1 is reachable. Lever 0 is unreachable.
        assertNotNull(result.unreachableLevers[0])
        val explanation = result.unreachableLevers[0]!!
        assertTrue(explanation.contains("Contradiction"), "Explanation should contain 'Contradiction', got: $explanation")
    }

    @Test
    fun testSelfContradiction() {
        // Lever 0 requires itself to be Normal.
        val tab = TabDef(
            levers = listOf(
                LeverDef(
                    label = "Lever 0",
                    type = LeverType.HOME_SIGNAL,
                    logic = LeverStateNode(0, false)
                )
            )
        )

        val result = RuleValidator.validate(tab)
        assertNotNull(result.unreachableLevers[0])
        val explanation = result.unreachableLevers[0]!!
        assertTrue(explanation.contains("Normal and Reversed"), "Explanation should indicate self-contradiction, got: $explanation")
    }

    @Test
    fun testBlockContradiction() {
        // Lever 0 requires Block 0 Occupied.
        // Lever 1 requires Block 0 Clear.
        // Lever 2 requires Lever 0 Reversed AND Lever 1 Reversed.
        // Therefore, Lever 2 requires Block 0 to be both Occupied and Clear.
        val tab = TabDef(
            levers = listOf(
                LeverDef(
                    label = "Lever 0",
                    type = LeverType.HOME_SIGNAL,
                    logic = BlockStateNode(0, true)
                ),
                LeverDef(
                    label = "Lever 1",
                    type = LeverType.HOME_SIGNAL,
                    logic = BlockStateNode(0, false)
                ),
                LeverDef(
                    label = "Lever 2",
                    type = LeverType.HOME_SIGNAL,
                    logic = AndNode(listOf(
                        LeverStateNode(0, true),
                        LeverStateNode(1, true)
                    ))
                )
            ),
            blocks = listOf(
                BlockDef(label = "Block 0")
            )
        )

        val result = RuleValidator.validate(tab)
        // Lever 0 and 1 are reachable individually.
        // Lever 2 is unreachable.
        assertNotNull(result.unreachableLevers[2])
        val explanation = result.unreachableLevers[2]!!
        assertTrue(explanation.contains("Occupied and Clear") || explanation.contains("Occupied, but"), "Explanation should indicate block contradiction, got: $explanation")
    }
}
