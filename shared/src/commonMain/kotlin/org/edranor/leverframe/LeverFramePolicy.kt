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
    fun attemptToggle(tabDef: TabDef, states: BooleanArray, blockStates: BooleanArray, leverIndex: Int, target: Boolean): BooleanArray? {
        val isValid = Interlocking.evaluate(tabDef, states, blockStates, leverIndex, target)
        if (isValid) {
            val newStates = states.copyOf()
            newStates[leverIndex] = target
            return newStates
        }
        return null
    }
}
