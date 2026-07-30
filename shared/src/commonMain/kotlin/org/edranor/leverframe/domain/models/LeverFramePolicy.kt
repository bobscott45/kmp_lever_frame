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
 * Defines the application's policy for handling external LCC network events that conflict with
 * local interlocking rules (e.g., STRICT rejection, PERMISSIVE overriding, or ALARM states).
 */
package org.edranor.leverframe.domain.models
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
 * Defines the modes for handling external state conflicts from the network.
 * 
 * @property id The integer ID used for serialization and configuration.
 */
enum class ConflictPolicy(val id: Int) { 
    /** Rejects incoming LCC network events if they violate local interlocking rules. */
    STRICT(1), 
    
    /** Accepts incoming LCC network events and updates local state silently, bypassing interlocking rules. */
    PERMISSIVE(2), 
    
    /** Accepts incoming LCC network events but marks the conflicting levers for visual alarm. */
    ALARM(3);
    
    /**
     * Factory methods and evaluation policies for LeverFrame operations.
     */
    companion object { 
        /** Resolves a [ConflictPolicy] from its integer ID, defaulting to [PERMISSIVE]. */
        fun of(id: Int) = entries.firstOrNull { it.id == id } ?: PERMISSIVE 
    }
}

/**
 * Policy orchestrator governing the interaction between user interactions, external network events,
 * and the local interlocking graph logic.
 */
object LeverFramePolicy {
    /**
     * Determines whether an external LCC event should mutate the UI state.
     * Based on the user's VM_PATTERN_PLAN.md:
     * "STRICT-ignore vs. else-apply; ALARM falls into the `else` and applies 
     * while `getConflictingLevers` provides the visual flag."
     * 
     * @param policy The currently active [ConflictPolicy].
     * @param isValid Whether the external event satisfies local interlocking logic.
     * @return `true` if the event should be applied locally, `false` otherwise.
     */
    fun shouldApplyExternalEvent(policy: ConflictPolicy, isValid: Boolean): Boolean {
        return !(policy == ConflictPolicy.STRICT && !isValid)
    }

    /**
     * Helper to attempt toggling a lever state. Returns a new list of levers if the toggle
     * is valid according to Interlocking rules, or null if it violates the rules.
     * 
     * @param tabDef The configuration definition of the active frame.
     * @param levers The current state of all levers in the frame.
     * @param blocks The current state of all blocks in the frame.
     * @param leverIndex The index of the lever being toggled.
     * @param target The requested state (`true` for Reversed, `false` for Normal).
     * @return A mutated list containing the new states, or `null` if the toggle was invalid.
     */
    fun attemptToggle(tabDef: TabDef, levers: List<DomainLever>, blocks: List<DomainBlock>, leverIndex: Int, target: Boolean): List<DomainLever>? {
        val isValid = Interlocking.evaluate(tabDef.toInterlockingGraph(), levers, blocks, leverIndex, target)
        if (isValid) {
            val newLevers = levers.toMutableList()
            newLevers[leverIndex] = newLevers[leverIndex].copy(isReversed = target)
            return newLevers
        }
        return null
    }
}
