package org.example.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class LeverFramePolicyTest {

    @Test
    fun testStrictPolicyRejectsInvalidExternalEvent() {
        // STRICT means if it's invalid, it should NOT apply.
        val shouldApply = LeverFramePolicy.shouldApplyExternalEvent(ConflictPolicy.STRICT, isValid = false)
        assertFalse(shouldApply, "STRICT policy should reject an invalid external event.")
    }

    @Test
    fun testStrictPolicyAppliesValidExternalEvent() {
        val shouldApply = LeverFramePolicy.shouldApplyExternalEvent(ConflictPolicy.STRICT, isValid = true)
        assertTrue(shouldApply, "STRICT policy should apply a valid external event.")
    }

    @Test
    fun testPermissivePolicyAppliesInvalidExternalEvent() {
        // PERMISSIVE applies regardless of validity.
        val shouldApply = LeverFramePolicy.shouldApplyExternalEvent(ConflictPolicy.PERMISSIVE, isValid = false)
        assertTrue(shouldApply, "PERMISSIVE policy should apply even if it's an invalid external event.")
    }

    @Test
    fun testAlarmPolicyAppliesInvalidExternalEvent() {
        // ALARM applies regardless of validity (but later flags conflict visually).
        val shouldApply = LeverFramePolicy.shouldApplyExternalEvent(ConflictPolicy.ALARM, isValid = false)
        assertTrue(shouldApply, "ALARM policy should apply even if it's an invalid external event.")
    }

    @Test
    fun testAttemptToggleRespectsInterlocking() {
        // Create a simple tab with 2 levers.
        // Lever 1 requires Lever 0 to be reversed (true).
        val tabDef = TabDef(
            levers = listOf(
                LeverDef(label = "0"),
                LeverDef(
                    label = "1",
                    conditions = listOf(InterlockingCondition(targetLeverIndex = 0, requiredState = true))
                )
            )
        )
        
        val initialStates = booleanArrayOf(false, false)

        // Attempting to reverse Lever 1 should fail because Lever 0 is false.
        val invalidAttempt = LeverFramePolicy.attemptToggle(tabDef, initialStates, 1, true)
        assertNull(invalidAttempt, "Attempting to reverse lever 1 should fail interlocking rules.")

        // Reverse Lever 0
        val statesWithLever0Reversed = booleanArrayOf(true, false)
        
        // Attempting to reverse Lever 1 should now succeed.
        val validAttempt = LeverFramePolicy.attemptToggle(tabDef, statesWithLever0Reversed, 1, true)
        assertNotNull(validAttempt, "Attempting to reverse lever 1 should succeed when lever 0 is reversed.")
        assertTrue(validAttempt[1], "Lever 1 should be reversed in the resulting state.")
    }
}
