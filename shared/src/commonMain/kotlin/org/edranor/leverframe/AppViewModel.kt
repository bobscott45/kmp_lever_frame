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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce

class AppViewModel(
    private val configRepo: AppConfigRepository = ConfigManager,
    private val lccClient: LccNetworkClient = LccNode
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeverFrameUiState())
    val uiState: StateFlow<LeverFrameUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LeverFrameUiState()
    )

    private val saveStateTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    init {
        viewModelScope.launch {
            configRepo.initConfig()
            loadConfig()
            lccClient.initialize()
            launch {
                lccClient.connectionStatus.collect { status ->
                    _uiState.update { it.copy(networkStatus = status) }
                }
            }
            launch {
                lccClient.connectionErrors.collect { error ->
                    _uiState.update { it.copy(networkError = error) }
                }
            }
            lccClient.externalEvents.collect { hexEventId ->
                handleExternalEvent(hexEventId)
            }
        }
        
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch {
            saveStateTrigger.debounce(500).collect {
                if (configRepo.currentConfig.restore_last_state) {
                    val statesToSave = _uiState.value.leverStates.map { it.copyOf() }
                    configRepo.saveCurrentLeverStates(statesToSave)
                }
            }
        }
    }

    private suspend fun loadConfig() {
        val configStr = configRepo.toJsonString()
        val parsedTabs = configRepo.parseConfig(configStr)
        val initialVersion = _uiState.value.configVersion
        
        // Build initial states
        val leverStates = parsedTabs.map { (tabName, tabDef) ->
            BooleanArray(tabDef.levers.size) { false }
        }
        val manualLocks = parsedTabs.map { (tabName, tabDef) ->
            BooleanArray(tabDef.levers.size) { false }
        }
        val blockStates = parsedTabs.map { (tabName, tabDef) ->
            BooleanArray(tabDef.blocks.size) { true }
        }
        
        // Restore from disk if configured
        val storedStates = configRepo.loadSavedLeverStates()
        if (configRepo.currentConfig.restore_last_state && storedStates != null && storedStates.isNotEmpty()) {
            storedStates.forEachIndexed { tabIdx, states ->
                if (tabIdx < leverStates.size) {
                    val tabLeversCount = leverStates[tabIdx].size
                    val copyCount = minOf(tabLeversCount, states.size)
                    states.copyInto(leverStates[tabIdx], 0, 0, copyCount)
                }
            }
        }

        _uiState.update {
            it.copy(
                tabs = parsedTabs,
                leverStates = leverStates,
                manualLocks = manualLocks,
                blockStates = blockStates,
                configVersion = initialVersion + 1,
                config = configRepo.currentConfig
            )
        }
    }

    private fun persistStatesIfEnabled() {
        if (configRepo.currentConfig.restore_last_state) {
            saveStateTrigger.tryEmit(Unit)
        }
    }

    private fun handleExternalEvent(hexEventId: String) {
        if (!configRepo.currentConfig.lcc_master) return
        
        var didChange = false
        _uiState.update { currentState ->
            var stateChanged = false
            var stateToReturn = currentState
            val newLeverStates = currentState.leverStates.map { it.copyOf() }
            val policy = ConflictPolicy.of(configRepo.currentConfig.conflict_policy)

            currentState.tabs.forEachIndexed { tabIdx, tabPair ->
                val tabDef = tabPair.second
                tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                    if (!leverDef.lcc_enabled) return@forEachIndexed
                    var attemptState: Boolean? = null
                    
                    if (leverDef.lcc_event_normal.isNotBlank()) {
                        val normalHex = lccClient.parseEventId(leverDef.lcc_event_normal)
                        if (normalHex == hexEventId) attemptState = false
                    }
                    if (leverDef.lcc_event_reversed.isNotBlank()) {
                        val reversedHex = lccClient.parseEventId(leverDef.lcc_event_reversed)
                        if (reversedHex == hexEventId) attemptState = true
                    }
                    
                    if (attemptState != null) {
                        val currState = newLeverStates[tabIdx][leverIdx]
                        if (currState != attemptState) {
                            val isValid = Interlocking.evaluate(tabDef, newLeverStates[tabIdx], currentState.blockStates[tabIdx], leverIdx, attemptState)
                            if (LeverFramePolicy.shouldApplyExternalEvent(policy, isValid)) {
                                newLeverStates[tabIdx][leverIdx] = attemptState
                                stateChanged = true
                            }
                        }
                    }
                }
                // Handle Block states
                val newBlockStates = currentState.blockStates.map { it.copyOf() }.toMutableList()
                tabDef.blocks.forEachIndexed { blockIdx, blockDef ->
                    var attemptBlockState: Boolean? = null
                    if (blockDef.lcc_event_occupied.isNotBlank()) {
                        val occupiedHex = lccClient.parseEventId(blockDef.lcc_event_occupied)
                        if (occupiedHex == hexEventId) attemptBlockState = true
                    }
                    if (blockDef.lcc_event_empty.isNotBlank()) {
                        val emptyHex = lccClient.parseEventId(blockDef.lcc_event_empty)
                        if (emptyHex == hexEventId) attemptBlockState = false
                    }
                    if (attemptBlockState != null) {
                        if (newBlockStates[tabIdx][blockIdx] != attemptBlockState) {
                            newBlockStates[tabIdx][blockIdx] = attemptBlockState
                            stateChanged = true
                        }
                    }
                }
                if (stateChanged && newBlockStates !== currentState.blockStates) {
                    stateToReturn = stateToReturn.copy(blockStates = newBlockStates)
                }
            }
            
            if (stateChanged) {
                didChange = true
                val conflicts = if (stateToReturn.tabs.isNotEmpty()) {
                    Interlocking.getConflictingLevers(
                        stateToReturn.tabs[stateToReturn.selectedTabIndex].second,
                        newLeverStates[stateToReturn.selectedTabIndex],
                        stateToReturn.blockStates[stateToReturn.selectedTabIndex]
                    )
                } else emptyList()
                stateToReturn.copy(leverStates = newLeverStates, conflictingLevers = conflicts)
            } else {
                didChange = false
                stateToReturn
            }
        }
        
        if (didChange) {
            persistStatesIfEnabled()
        }
    }

    fun tabSelected(index: Int) {
        _uiState.update { currentState ->
            val conflicts = if (currentState.tabs.isNotEmpty()) {
                Interlocking.getConflictingLevers(
                    currentState.tabs[index].second,
                    currentState.leverStates[index],
                    currentState.blockStates[index]
                )
            } else emptyList()
            currentState.copy(selectedTabIndex = index, conflictingLevers = conflicts)
        }
    }

    fun toggleLever(tabIndex: Int, leverIndex: Int): Boolean {
        var lccEventStr: String? = null
        var didChange = false
        
        _uiState.update { currentState ->
            val tabDef = currentState.tabs[tabIndex].second
            val currentStates = currentState.leverStates[tabIndex]
            val leverState = currentStates[leverIndex]
            val targetState = !leverState
            
            val newStates = LeverFramePolicy.attemptToggle(tabDef, currentStates, currentState.blockStates[tabIndex], leverIndex, targetState)
            if (newStates != null) {
                didChange = true
                val updatedAllStates = currentState.leverStates.toMutableList()
                updatedAllStates[tabIndex] = newStates
                
                val conflicts = if (currentState.tabs.isNotEmpty()) {
                    Interlocking.getConflictingLevers(
                        currentState.tabs[currentState.selectedTabIndex].second,
                        updatedAllStates[currentState.selectedTabIndex],
                        currentState.blockStates[currentState.selectedTabIndex]
                    )
                } else emptyList()

                val leverDef = tabDef.levers[leverIndex]
                if (targetState && leverDef.lcc_event_reversed.isNotBlank()) {
                    lccEventStr = leverDef.lcc_event_reversed
                } else if (!targetState && leverDef.lcc_event_normal.isNotBlank()) {
                    lccEventStr = leverDef.lcc_event_normal
                } else {
                    lccEventStr = null
                }

                currentState.copy(leverStates = updatedAllStates, conflictingLevers = conflicts, errorMessage = null)
            } else {
                didChange = false
                lccEventStr = null
                currentState.copy(errorMessage = "Interlocking conflict: Cannot move lever")
            }
        }
        
        lccEventStr?.let { lccClient.produceEvent(it) }
        if (didChange) {
            persistStatesIfEnabled()
        }
        return didChange
    }

    fun toggleManualLock(tabIndex: Int, leverIndex: Int) {
        _uiState.update { currentState ->
            val updatedLocks = currentState.manualLocks.toMutableList()
            val tabLocks = updatedLocks[tabIndex].copyOf()
            tabLocks[leverIndex] = !tabLocks[leverIndex]
            updatedLocks[tabIndex] = tabLocks
            currentState.copy(manualLocks = updatedLocks)
        }
        persistStatesIfEnabled()
    }

    fun leverLabelClicked(leverIndex: Int) {
        _uiState.update { it.copy(isStatusMode = true, statusLeverIndex = leverIndex) }
    }

    fun enterConfigMode() {
        _uiState.update { it.copy(isConfigMode = true) }
    }

    fun exitConfigMode() {
        _uiState.update { it.copy(isConfigMode = false) }
    }

    fun configSaved() {
        viewModelScope.launch {
            loadConfig() // Reloads tabs and triggers recomposition
        }
    }

    fun enterStatusMode() {
        _uiState.update { it.copy(isStatusMode = true, statusLeverIndex = null) }
    }

    fun exitStatusMode() {
        _uiState.update { it.copy(isStatusMode = false, statusLeverIndex = null) }
    }

    fun dismissStatusLever() {
        _uiState.update { it.copy(isStatusMode = false, statusLeverIndex = null, errorMessage = null) }
    }

    fun dismissNetworkError() {
        _uiState.update { it.copy(networkError = null) }
    }

    fun setLeverLccEnabled(tabIndex: Int, leverIndex: Int, enabled: Boolean) {
        val newTabsJson = configRepo.currentConfig.tabs.toMutableList()
        val currentTabJson = newTabsJson[tabIndex].copy()
        val newLeversJson = currentTabJson.levers.toMutableList()
        newLeversJson[leverIndex] = newLeversJson[leverIndex].copy(lcc_enabled = enabled)
        val newConfig = configRepo.currentConfig.copy(
            tabs = newTabsJson.apply { set(tabIndex, currentTabJson.copy(levers = newLeversJson)) }
        )
        viewModelScope.launch {
            configRepo.saveConfig(newConfig)
            loadConfig()
        }
    }

    fun toggleBlockState(tabIndex: Int, blockIndex: Int) {
        _uiState.update { currentState ->
            if (tabIndex in currentState.blockStates.indices && blockIndex in currentState.blockStates[tabIndex].indices) {
                val newBlockStates = currentState.blockStates.map { it.copyOf() }.toMutableList()
                newBlockStates[tabIndex][blockIndex] = !newBlockStates[tabIndex][blockIndex]
                currentState.copy(blockStates = newBlockStates)
            } else {
                currentState
            }
        }
    }

    fun updateSystemConfig(newConfig: JsonConfig) {
        val prevIp = configRepo.currentConfig.jmri_hub_ip
        viewModelScope.launch {
            configRepo.saveConfig(newConfig)
            if (prevIp != newConfig.jmri_hub_ip) {
                lccClient.initialize()
            }
            loadConfig()
        }
    }
    
}
