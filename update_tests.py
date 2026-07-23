import re

def update_test(path):
    with open(path, 'r') as f:
        content = f.read()

    # Modify setup to add a block and an auto-reversing lever
    setup_replace = """        val jsonTab = JsonTab(
            name = "Test Box",
            label_lines = 2,
            label_line_height = 18,
            levers = listOf(
                JsonLever(label = "Signal", type = "HOME_SIGNAL", lcc_event_normal = "11.22.33.44.00.00.00.01", lcc_event_reversed = "11.22.33.44.00.00.00.02"),
                JsonLever(label = "Points", type = "POINTS", lcc_event_reversed = "11.22.33.44.00.00.00.04", interlocking = listOf(
                    JsonInterlocking(target = 0, state = "NORMAL", alt_target = -1, alt_state = "NORMAL")
                )),
                JsonLever(label = "Auto Signal", type = "HOME_SIGNAL", auto_reverser = true, lcc_event_normal = "11.22.33.44.00.00.00.05", lcc_event_reversed = "11.22.33.44.00.00.00.08", interlocking = listOf(
                    JsonInterlocking(target = 0, target_type = "BLOCK", state = "EMPTY", alt_target = -1, alt_state = "NORMAL")
                ))
            ),
            blocks = listOf(
                JsonBlock(label = "Platform 1", lcc_event_occupied = "11.22.33.44.00.00.00.06", lcc_event_empty = "11.22.33.44.00.00.00.07")
            )
        )"""

    content = re.sub(
        r'val jsonTab = JsonTab\([\s\S]*?levers = listOf\([\s\S]*?JsonLever\([\s\S]*?JsonLever\([\s\S]*?\)\s*\n\s*\)\s*\n\s*\)',
        setup_replace,
        content
    )
    
    # Check if we replaced successfully
    if "Auto Signal" not in content:
        print("Setup replace failed!")
        return
        
    # We also need to fix assertions in testInitialization if they expect 2 levers
    content = content.replace(
        'assertEquals(2, viewModel.configState.value.tabs[0].second.levers.size)',
        'assertEquals(3, viewModel.configState.value.tabs[0].second.levers.size)'
    )

    new_tests = """
    @Test
    fun testToggleBlockStateAndAutoReverser() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Block 0 starts as EMPTY (true). Lever 2 requires Block 0 to be EMPTY.
        // Reverse Lever 2 (Auto Signal).
        val toggled = viewModel.toggleLever(tabIndex = 0, leverIndex = 2)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(toggled, "Auto Signal should be reversed")
        assertTrue(viewModel.domainState.value.leverStates[0][2])
        
        // Now toggle Block 0 state to OCCUPIED (false)
        viewModel.toggleBlockState(tabIndex = 0, blockIndex = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Block should be OCCUPIED
        assertFalse(viewModel.domainState.value.blockStates[0][0])
        
        // Auto Reverser should have forced Lever 2 back to NORMAL
        assertFalse(viewModel.domainState.value.leverStates[0][2], "Auto Signal should snap back to normal")
        
        // LCC event for normal state should have been emitted
        assertTrue(lccClient.producedEvents.contains("11.22.33.44.00.00.00.05"))
    }

    @Test
    fun testExternalEventToggleBlockState() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertTrue(viewModel.domainState.value.blockStates[0][0], "Block starts EMPTY")
        
        // External event for OCCUPIED
        lccClient.emitEvent("1122334400000006")
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertFalse(viewModel.domainState.value.blockStates[0][0], "Block should be OCCUPIED")
    }

    @Test
    fun testConfigSavedReloadsConfig() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val initialVersion = viewModel.configState.value.configVersion
        
        // Simulate a config save (modifies repository and calls configSaved)
        val newConfig = configRepo.currentConfig.copy(jmri_hub_ip = "192.168.99.99")
        configRepo.saveConfig(newConfig)
        viewModel.configSaved()
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Version should be incremented since loadConfig() updates it by +1
        assertEquals(initialVersion + 1, viewModel.configState.value.configVersion)
    }
}
"""
    # Replace the final closing brace with the new tests
    content = re.sub(r'}\s*$', new_tests, content)
    
    with open(path, 'w') as f:
        f.write(content)

update_test('shared/src/commonTest/kotlin/org/edranor/leverframe/AppViewModelTest.kt')
