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

enum class ConflictPolicy(val id: Int) { 
    STRICT(1), 
    PERMISSIVE(2), 
    ALARM(3);
    
    companion object { 
        fun of(id: Int) = entries.firstOrNull { it.id == id } ?: PERMISSIVE 
    }
}

object LeverFramePolicy {
    /**
     * Determines whether an external LCC event should mutate the UI state.
     * Based on the user's VM_PATTERN_PLAN.md:
     * "STRICT-ignore vs. else-apply; ALARM falls into the `else` and applies 
     * while `getConflictingLevers` provides the visual flag."
     */
    fun shouldApplyExternalEvent(policy: ConflictPolicy, isValid: Boolean): Boolean {
        return !(policy == ConflictPolicy.STRICT && !isValid)
    }

    /**
     * Helper to attempt toggling a lever state. Returns a new array if the toggle
     * is valid according to Interlocking rules, or null if it violates the rules.
     */
    fun attemptToggle(tabDef: TabDef, states: BooleanArray, leverIndex: Int, target: Boolean): BooleanArray? {
        val isValid = Interlocking.evaluate(tabDef, states, leverIndex, target)
        if (isValid) {
            val newStates = states.copyOf()
            newStates[leverIndex] = target
            return newStates
        }
        return null
    }
}
