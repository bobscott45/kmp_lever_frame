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

enum class TargetType { LEVER, BLOCK }

data class InterlockingCondition(
    val targetType: TargetType = TargetType.LEVER,
    val targetIndex: Int = -1,
    val requiredState: Boolean = false,
    val altTargetType: TargetType = TargetType.LEVER,
    val altTargetIndex: Int = -1,
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
    val lcc_enabled: Boolean = true,
    val autoReverser: Boolean = false
)

data class BlockDef(
    val label: String = "",
    val shortCode: String = "",
    val lcc_event_occupied: String = "",
    val lcc_event_empty: String = ""
)

data class SchematicElementDef(
    val type: String,
    val x: Int,
    val y: Int,
    val linkedLever: Int = -1,
    val linkedLever2: Int = -1,
    val linkedBlock: Int = -1
)

data class TabDef(
    val levers: List<LeverDef>,
    val labelLines: Int = 2,
    val labelLineHeight: Int = 18,
    val blockLayout: String = "HORIZONTAL",
    val blockLabelSize: Int = 8,
    val showLeverNumbers: Boolean = true,
    val showBlockNumbers: Boolean = false,
    val useShortCodes: Boolean = false,
    val useShortCodesInIndicators: Boolean = false,
    val schematicGridSize: Int = 40,
    val blocks: List<BlockDef> = emptyList(),
    val schematicElements: List<SchematicElementDef> = emptyList()
)

object Interlocking {
    /**
     * Evaluates whether a lever can be thrown to the new state based on interlocking rules.
     */
    fun evaluate(
        tab: TabDef,
        levers: List<DomainLever>,
        blocks: List<DomainBlock>,
        leverIndex: Int,
        attemptingState: Boolean
    ): Boolean {
        // Create the new hypothetical state
        val newLevers = levers.map { if (it.id == leverIndex) it.copy(isReversed = attemptingState) else it }

        val currentConflicts = getConflictingLevers(tab, levers, blocks).toSet()
        val newConflicts = getConflictingLevers(tab, newLevers, blocks).toSet()

        // The move is valid if it doesn't introduce any new conflicts.
        // i.e., newConflicts must be a subset of currentConflicts.
        return currentConflicts.containsAll(newConflicts)
    }

    /**
     * Returns a list of lever indices that are involved in an interlocking conflict.
     */
    fun getConflictingLevers(tab: TabDef, levers: List<DomainLever>, blocks: List<DomainBlock>): List<Int> {
        val conflicts = mutableSetOf<Int>()
        for (i in tab.levers.indices) {
            if (levers[i].isReversed) {
                for (condition in tab.levers[i].conditions) {
                    if (condition.targetIndex != -1) {
                        val primaryTargetState = if (condition.targetType == TargetType.BLOCK) {
                            blocks.getOrNull(condition.targetIndex)?.isOccupied ?: false
                        } else {
                            levers.getOrNull(condition.targetIndex)?.isReversed ?: false
                        }
                        val primaryMatch = primaryTargetState == condition.requiredState
                        
                        val altMatch = if (condition.altTargetIndex != -1) {
                            val altTargetState = if (condition.altTargetType == TargetType.BLOCK) {
                                blocks.getOrNull(condition.altTargetIndex)?.isOccupied ?: false
                            } else {
                                levers.getOrNull(condition.altTargetIndex)?.isReversed ?: false
                            }
                            altTargetState == condition.altRequiredState
                        } else false
                        
                        if (!primaryMatch && !altMatch) {
                            conflicts.add(i)
                            if (condition.targetType == TargetType.LEVER) conflicts.add(condition.targetIndex)
                            if (condition.altTargetIndex != -1 && condition.altTargetType == TargetType.LEVER) {
                                conflicts.add(condition.altTargetIndex)
                            }
                        }
                    }
                }
            }
        }
        return conflicts.toList()
    }
}
