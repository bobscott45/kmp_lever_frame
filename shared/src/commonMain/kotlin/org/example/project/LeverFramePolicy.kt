package org.example.project

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
            val newStates = states.clone()
            newStates[leverIndex] = target
            return newStates
        }
        return null
    }
}
