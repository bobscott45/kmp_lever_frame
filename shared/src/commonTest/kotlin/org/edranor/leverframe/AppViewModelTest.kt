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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeConfigRepository : AppConfigRepository {
    override var currentConfig: JsonConfig = JsonConfig()
    
    var initCalled = false
    var saveCalled = false
    
    override suspend fun initConfig() {
        initCalled = true
    }
    
    override fun toJsonString(): String = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), currentConfig)
    
    override fun parseConfig(jsonString: String): List<Pair<String, TabDef>> {
        return ConfigManager.parseConfig(jsonString)
    }
    
    var savedLeverStates: SavedStatesData? = null
    override suspend fun loadSavedStates(): SavedStatesData? = savedLeverStates
    
    override suspend fun saveCurrentStates(states: SavedStatesData) {
        savedLeverStates = states
    }

    override suspend fun clearSavedStates() {
        savedLeverStates = null
    }
    
    override suspend fun saveConfig(newConfig: JsonConfig) {
        currentConfig = newConfig
        saveCalled = true
    }
}

class FakeLccClient : LccNetworkClient {
    private val _events = MutableSharedFlow<String>()
    override val externalEvents: SharedFlow<String> = _events.asSharedFlow()
    private val _connectionStatus = kotlinx.coroutines.flow.MutableStateFlow("Disconnected")
    override val connectionStatus: kotlinx.coroutines.flow.StateFlow<String> = _connectionStatus
    private val _connectionErrors = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    override val connectionErrors: SharedFlow<String> = _connectionErrors.asSharedFlow()
    
    val producedEvents = mutableListOf<String>()
    var initCalled = false
    var disconnectCalled = false

    override fun initialize() {
        initCalled = true
        disconnectCalled = false
    }

    override fun disconnect() {
        disconnectCalled = true
    }

    override fun identifyProducer(eventIdStr: String) {
        // No-op for test
    }
    
    override fun produceEvent(eventIdStr: String) {
        producedEvents.add(eventIdStr)
    }

    override fun parseEventId(eventIdStr: String): String {
        return eventIdStr.replace(".", "").padEnd(16, '0').uppercase()
    }
    
    suspend fun emitEvent(event: String) {
        _events.emit(event)
    }

    suspend fun emitConnectionError(error: String) {
        _connectionErrors.emit(error)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private lateinit var configRepo: FakeConfigRepository
    private lateinit var lccClient: FakeLccClient
    private lateinit var viewModel: AppViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        configRepo = FakeConfigRepository()
        lccClient = FakeLccClient()
        
        // Setup a simple config with one tab and two levers
                val jsonTab = JsonTab(
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
        )
        val jsonTab2 = JsonTab(
            name = "Test Box 2",
            label_lines = 1,
            label_line_height = 18,
            levers = listOf(
                JsonLever(label = "Signal 2", type = "HOME_SIGNAL")
            )
        )
        configRepo.currentConfig = JsonConfig(
            node_id = "11.22.33.44.00.00",
            tabs = listOf(jsonTab, jsonTab2)
        )
        
        val eventProcessor = NetworkEventProcessor(lccClient, configRepo)
        viewModel = AppViewModel(configRepo, lccClient, eventProcessor)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() = runTest {
        // Wait for the init block coroutine to finish
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertTrue(configRepo.initCalled, "Config repository should be initialized")
        assertTrue(lccClient.initCalled, "LCC client should be initialized")
        
                assertEquals(2, viewModel.configState.value.tabs.size)
        assertEquals("Test Box", viewModel.configState.value.tabs[0].first)
        assertEquals(3, viewModel.configState.value.tabs[0].second.levers.size)
    }

    @Test
    fun testToggleLever() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Toggle the first lever (Signal)
        viewModel.toggleLever(tabIndex = 0, leverIndex = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        
                assertTrue(viewModel.domainState.value.frames[0].levers[0].isReversed, "Signal lever should be reversed (true)")
        
        // Ensure LCC event was fired for reversed state
        assertTrue(lccClient.producedEvents.contains("11.22.33.44.00.00.00.02"))
    }

    @Test
    fun testToggleLeverWithInterlocking() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Reverse the signal lever first
        viewModel.toggleLever(tabIndex = 0, leverIndex = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Now try to reverse points, but signal is REVERSED, which violates interlocking (target = 0, state = NORMAL)
        viewModel.toggleLever(tabIndex = 0, leverIndex = 1)
        testDispatcher.scheduler.advanceUntilIdle()
        
                assertFalse(viewModel.domainState.value.frames[0].levers[1].isReversed, "Points lever should NOT be reversed due to interlocking")
        assertTrue(viewModel.uiState.value.errorMessage != null, "Error message should be set")
    }

    @Test
    fun testExternalEventUpdatesState() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // External event matching Signal lever's reversed event
        lccClient.emitEvent("1122334400000002")
        testDispatcher.scheduler.advanceUntilIdle()
        
                assertTrue(viewModel.domainState.value.frames[0].levers[0].isReversed, "Signal lever should be reversed by external event")
    }

    @Test
    fun testUpdateSystemConfig() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        val newConfig = configRepo.currentConfig.copy(jmri_hub_ip = "192.168.1.100")
        
        // Reset initCalled to verify if it's called again
        lccClient.initCalled = false
        
        viewModel.updateSystemConfig(newConfig)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertTrue(configRepo.saveCalled, "Config should be saved")
        assertEquals("192.168.1.100", configRepo.currentConfig.jmri_hub_ip, "Config should be updated")
        assertTrue(lccClient.initCalled, "LCC client should be re-initialized when IP changes")
        
                assertEquals("192.168.1.100", viewModel.configState.value.config.jmri_hub_ip)
    }

    @Test
    fun testNetworkErrorHandling() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Emit a network error
        lccClient.emitConnectionError("Connection Refused")
        testDispatcher.scheduler.advanceUntilIdle()
        
                assertEquals("Connection Refused", viewModel.uiState.value.networkError)
        
        // Dismiss the error
        viewModel.dismissNetworkError()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.networkError)
    }

    @Test
    fun testUiModeIntents() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Config mode without returning to status
        viewModel.enterConfigMode(ConfigMode.SYSTEM)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConfigMode.SYSTEM, viewModel.uiState.value.configMode)
        assertEquals(null, viewModel.uiState.value.initialEditFrameIndex)
        assertEquals(null, viewModel.uiState.value.initialEditLeverIndex)
        viewModel.exitConfigMode()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConfigMode.NONE, viewModel.uiState.value.configMode)

        // Status mode
        viewModel.enterStatusMode()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isStatusMode)
        assertEquals(null, viewModel.uiState.value.statusLeverIndex)
        viewModel.exitStatusMode()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isStatusMode)

        // Lever label clicked
        viewModel.leverLabelClicked(1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isStatusMode)
        assertEquals(1, viewModel.uiState.value.statusLeverIndex)
        
        // Jump to config from status mode
        viewModel.enterConfigMode(ConfigMode.FRAMES, frameIndex = 0, leverIndex = 1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConfigMode.FRAMES, viewModel.uiState.value.configMode)
        assertEquals(0, viewModel.uiState.value.initialEditFrameIndex)
        assertEquals(1, viewModel.uiState.value.initialEditLeverIndex)
        
        // Exit config should restore status mode
        viewModel.exitConfigMode()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ConfigMode.NONE, viewModel.uiState.value.configMode)
        assertTrue(viewModel.uiState.value.isStatusMode)
        assertEquals(1, viewModel.uiState.value.statusLeverIndex)
        
        // Dismiss status screen entirely
        viewModel.dismissStatusLever()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isStatusMode)
        assertEquals(null, viewModel.uiState.value.statusLeverIndex)
    }

    @Test
    fun testTabSelection() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(0, viewModel.uiState.value.selectedTabIndex)
        viewModel.tabSelected(1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.selectedTabIndex)
    }

    @Test
    fun testToggleManualLock() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertFalse(viewModel.domainState.value.frames[0].levers[0].isManuallyLocked)
        viewModel.toggleManualLock(0, 0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.domainState.value.frames[0].levers[0].isManuallyLocked)
    }

    @Test
    fun testSetLeverLccEnabled() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.setLeverLccEnabled(0, 0, false)
        testDispatcher.scheduler.advanceUntilIdle()
        
        val newConfig = configRepo.currentConfig
        assertFalse(newConfig.tabs[0].levers[0].lcc_enabled)
        assertTrue(configRepo.saveCalled)
    }

    @Test
    fun testConflictingLeversUpdate() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify initial state has no conflicts
                assertTrue(viewModel.domainState.value.conflictingLevers.isEmpty(), "Initially should have no conflicting levers")

        // Toggle lever 0 to REVERSED
        viewModel.toggleLever(tabIndex = 0, leverIndex = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        // Since lever 0 has no conditions, no conflicts should exist
        assertTrue(viewModel.domainState.value.conflictingLevers.isEmpty(), "Toggling signal should not create conflicts")

        // Now, emit an external event to force lever 1 (Points) to REVERSED.
        // The mock config uses default conflict_policy which is PERMISSIVE/ALARM (allows invalid states).
        // Since lever 1 requires lever 0 to be NORMAL, this will create a conflict.
        lccClient.emitEvent("1122334400000004") // Points reversed
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.domainState.value.frames[0].levers[1].isReversed, "Points lever should be reversed by external event")
        assertEquals(listOf(1, 0), viewModel.domainState.value.conflictingLevers, "conflictingLevers should contain indices 1 and 0")

        // Now select another tab and verify conflicts clear (since tab 1 has no conflicts)
        viewModel.tabSelected(1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.domainState.value.conflictingLevers.isEmpty(), "conflictingLevers should be empty for tab 1")

        // Switch back to tab 0 and verify conflicts return
        viewModel.tabSelected(0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(1, 0), viewModel.domainState.value.conflictingLevers, "conflictingLevers should be populated again for tab 0")
    }

    @Test
    fun testToggleBlockStateAndAutoReverser() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Block 0 starts as OCCUPIED (true).
        // Toggle Block 0 state to EMPTY (false)
        viewModel.toggleBlockState(tabIndex = 0, blockIndex = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.domainState.value.frames[0].blocks[0].isOccupied, "Block should be EMPTY")

        // Lever 2 requires Block 0 to be EMPTY. Now we can reverse Lever 2 (Auto Signal).
        val toggled = viewModel.toggleLever(tabIndex = 0, leverIndex = 2)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(toggled, "Auto Signal should be reversed")
        assertTrue(viewModel.domainState.value.frames[0].levers[2].isReversed)
        
        // Now toggle Block 0 state back to OCCUPIED (true)
        viewModel.toggleBlockState(tabIndex = 0, blockIndex = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Block should be OCCUPIED
        assertTrue(viewModel.domainState.value.frames[0].blocks[0].isOccupied)
        
        // Auto Reverser should have forced Lever 2 back to NORMAL (false)
        assertFalse(viewModel.domainState.value.frames[0].levers[2].isReversed, "Auto Signal should snap back to normal")
        
        // LCC event for normal state should have been emitted
        assertTrue(lccClient.producedEvents.contains("11.22.33.44.00.00.00.05"))
    }

    @Test
    fun testExternalEventToggleBlockState() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertTrue(viewModel.domainState.value.frames[0].blocks[0].isOccupied, "Block starts OCCUPIED")
        
        // External event for EMPTY
        lccClient.emitEvent("1122334400000007")
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertFalse(viewModel.domainState.value.frames[0].blocks[0].isOccupied, "Block should be EMPTY")
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
