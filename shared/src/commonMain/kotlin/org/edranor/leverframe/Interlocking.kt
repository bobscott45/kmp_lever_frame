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

data class InterlockingCondition(
    val targetLeverIndex: Int = -1,
    val requiredState: Boolean = false,
    val altTargetLeverIndex: Int = -1,
    val altRequiredState: Boolean = false
)

enum class LeverType {
    HOME_SIGNAL,
    DISTANT_SIGNAL,
    POINTS,
    FACING_POINTS,
    BROWN,
    GREEN,
    SPARE
}

data class LeverDef(
    val conditions: List<InterlockingCondition> = emptyList(),
    val type: LeverType = LeverType.SPARE,
    val label: String = "",
    val lcc_event_normal: String = "",
    val lcc_event_reversed: String = "",
    val lcc_enabled: Boolean = true
)

data class BlockDef(
    val label: String = "",
    val lcc_event_occupied: String = "",
    val lcc_event_empty: String = ""
)

data class TabDef(
    val levers: List<LeverDef>,
    val labelLines: Int = 2,
    val labelLineHeight: Int = 18,
    val blocks: List<BlockDef> = emptyList()
)

object Interlocking {
    /**
     * Evaluates whether a lever can be thrown to the new state based on interlocking rules.
     */
    fun evaluate(
        tab: TabDef,
        states: BooleanArray,
        leverIndex: Int,
        attemptingState: Boolean
    ): Boolean {
        // Create the new hypothetical state
        val newStates = states.copyOf()
        newStates[leverIndex] = attemptingState

        // Validate all rules for any lever that is (or will be) reversed
        for (i in tab.levers.indices) {
            // Rules only apply when the lever is in the Reversed (true) state
            if (newStates[i]) {
                for (condition in tab.levers[i].conditions) {
                    if (condition.targetLeverIndex != -1) {
                        val primaryMatch = newStates[condition.targetLeverIndex] == condition.requiredState
                        val altMatch = condition.altTargetLeverIndex != -1 && 
                                       newStates[condition.altTargetLeverIndex] == condition.altRequiredState
                        
                        if (!primaryMatch && !altMatch) {
                            return false
                        }
                    }
                }
            }
        }
        return true
    }

    /**
     * Returns a list of lever indices that are involved in an interlocking conflict.
     */
    fun getConflictingLevers(tab: TabDef, states: BooleanArray): List<Int> {
        val conflicts = mutableSetOf<Int>()
        for (i in tab.levers.indices) {
            if (states[i]) {
                for (condition in tab.levers[i].conditions) {
                    if (condition.targetLeverIndex != -1) {
                        val primaryMatch = states[condition.targetLeverIndex] == condition.requiredState
                        val altMatch = condition.altTargetLeverIndex != -1 && 
                                       states[condition.altTargetLeverIndex] == condition.altRequiredState
                        
                        if (!primaryMatch && !altMatch) {
                            conflicts.add(i)
                            conflicts.add(condition.targetLeverIndex)
                            if (condition.altTargetLeverIndex != -1) {
                                conflicts.add(condition.altTargetLeverIndex)
                            }
                        }
                    }
                }
            }
        }
        return conflicts.toList()
    }
}
