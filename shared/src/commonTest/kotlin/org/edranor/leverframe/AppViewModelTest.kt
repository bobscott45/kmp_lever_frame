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
    
    override suspend fun loadSavedLeverStates(): List<BooleanArray>? = null
    
    override suspend fun saveCurrentLeverStates(states: List<BooleanArray>) {}
    
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

    override fun initialize() {
        initCalled = true
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
                ))
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
        
        viewModel = AppViewModel(configRepo, lccClient)
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
        
        val state = viewModel.uiState.value
        assertEquals(2, state.tabs.size)
        assertEquals("Test Box", state.tabs[0].first)
        assertEquals(2, state.tabs[0].second.levers.size)
    }

    @Test
    fun testToggleLever() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Toggle the first lever (Signal)
        viewModel.toggleLever(tabIndex = 0, leverIndex = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state.leverStates[0][0], "Signal lever should be reversed (true)")
        
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
        
        val state = viewModel.uiState.value
        assertFalse(state.leverStates[0][1], "Points lever should NOT be reversed due to interlocking")
        assertTrue(state.errorMessage != null, "Error message should be set")
    }

    @Test
    fun testExternalEventUpdatesState() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // External event matching Signal lever's reversed event
        lccClient.emitEvent("1122334400000002")
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state.leverStates[0][0], "Signal lever should be reversed by external event")
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
        
        val state = viewModel.uiState.value
        assertEquals("192.168.1.100", state.config.jmri_hub_ip)
    }

    @Test
    fun testNetworkErrorHandling() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Emit a network error
        lccClient.emitConnectionError("Connection Refused")
        testDispatcher.scheduler.advanceUntilIdle()
        
        var state = viewModel.uiState.value
        assertEquals("Connection Refused", state.networkError)
        
        // Dismiss the error
        viewModel.dismissNetworkError()
        testDispatcher.scheduler.advanceUntilIdle()
        
        state = viewModel.uiState.value
        assertEquals(null, state.networkError)
    }

    @Test
    fun testUiModeIntents() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Config mode
        viewModel.enterConfigMode()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isConfigMode)
        viewModel.exitConfigMode()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isConfigMode)

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
        
        assertFalse(viewModel.uiState.value.manualLocks[0][0])
        viewModel.toggleManualLock(0, 0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.manualLocks[0][0])
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
        var state = viewModel.uiState.value
        assertTrue(state.conflictingLevers.isEmpty(), "Initially should have no conflicting levers")

        // Toggle lever 0 to REVERSED
        viewModel.toggleLever(tabIndex = 0, leverIndex = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        // Since lever 0 has no conditions, no conflicts should exist
        state = viewModel.uiState.value
        assertTrue(state.conflictingLevers.isEmpty(), "Toggling signal should not create conflicts")

        // Now, emit an external event to force lever 1 (Points) to REVERSED.
        // The mock config uses default conflict_policy which is PERMISSIVE/ALARM (allows invalid states).
        // Since lever 1 requires lever 0 to be NORMAL, this will create a conflict.
        lccClient.emitEvent("1122334400000004") // Points reversed
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertTrue(state.leverStates[0][1], "Points lever should be reversed by external event")
        assertEquals(listOf(1, 0), state.conflictingLevers, "conflictingLevers should contain indices 1 and 0")

        // Now select another tab and verify conflicts clear (since tab 1 has no conflicts)
        viewModel.tabSelected(1)
        testDispatcher.scheduler.advanceUntilIdle()
        
        state = viewModel.uiState.value
        assertTrue(state.conflictingLevers.isEmpty(), "conflictingLevers should be empty for tab 1")

        // Switch back to tab 0 and verify conflicts return
        viewModel.tabSelected(0)
        testDispatcher.scheduler.advanceUntilIdle()
        
        state = viewModel.uiState.value
        assertEquals(listOf(1, 0), state.conflictingLevers, "conflictingLevers should be populated again for tab 0")
    }
}
