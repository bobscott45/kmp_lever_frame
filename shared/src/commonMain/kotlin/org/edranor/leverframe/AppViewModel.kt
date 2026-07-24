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
    private val configRepo: AppConfigRepository,
    private val lccClient: LccNetworkClient,
    private val eventProcessor: NetworkEventProcessor
) : ViewModel() {

    private val _domainState = MutableStateFlow(DomainState())
    val domainState: StateFlow<DomainState> = _domainState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DomainState()
    )

    private val _configState = MutableStateFlow(ConfigState())
    val configState: StateFlow<ConfigState> = _configState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ConfigState()
    )

    private val _uiState = MutableStateFlow(TransientUiState())
    val uiState: StateFlow<TransientUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TransientUiState()
    )

    private val persistenceService = PersistenceService(configRepo, viewModelScope, domainState)

    init {
        viewModelScope.launch {
            configRepo.initConfig()
            loadConfig()
            if (configRepo.currentConfig.lcc_enabled) {
                lccClient.initialize()
            }
            launch {
                lccClient.connectionStatus.collect { status ->
                    _uiState.update { it.copy(networkStatus = status) }
                    if (status == "Connected" && configRepo.currentConfig.lcc_master) {
                        broadcastCurrentStates()
                    }
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
        
        
    }

    private suspend fun loadConfig() {
        val configStr = configRepo.toJsonString()
        val parsedTabs = configRepo.parseConfig(configStr)
        val initialVersion = _configState.value.configVersion
        
        // Build initial states
        val frames = parsedTabs.mapIndexed { tabIdx, (_, tabDef) ->
            DomainFrame(
                id = tabIdx,
                levers = tabDef.levers.mapIndexed { i, _ -> DomainLever(i, false, false) },
                blocks = tabDef.blocks.mapIndexed { i, _ -> DomainBlock(i, true) }
            )
        }.toMutableList()

        // Restore from disk if configured
        val storedData = configRepo.loadSavedStates()
        if (configRepo.currentConfig.restore_last_state && storedData != null) {
            frames.forEachIndexed { tabIdx, frame ->
                var updatedLevers = frame.levers
                if (tabIdx < storedData.tabs.size) {
                    val leverStates = storedData.tabs[tabIdx]
                    updatedLevers = frame.levers.mapIndexed { i, l -> 
                        if (i < leverStates.size) l.copy(isReversed = leverStates[i]) else l 
                    }
                }
                var updatedBlocks = frame.blocks
                if (tabIdx < storedData.blocks.size) {
                    val blockStates = storedData.blocks[tabIdx]
                    updatedBlocks = frame.blocks.mapIndexed { i, b -> 
                        if (i < blockStates.size) b.copy(isOccupied = blockStates[i]) else b 
                    }
                }
                frames[tabIdx] = frame.copy(levers = updatedLevers, blocks = updatedBlocks)
            }
        }

        _configState.update {
            it.copy(
                tabs = parsedTabs,
                configVersion = initialVersion + 1,
                config = configRepo.currentConfig
            )
        }
        _domainState.update {
            it.copy(frames = frames.toList())
        }
    }

    private fun persistStatesIfEnabled() {
        persistenceService.triggerSave()
    }

    private fun broadcastCurrentStates() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000) // Wait for LccNode init sequence to finish
            val domain = _domainState.value
            val config = _configState.value
            if (config.tabs.isEmpty() || domain.frames.isEmpty()) return@launch

            config.tabs.forEachIndexed { tabIdx, (_, tabDef) ->
                if (tabIdx < domain.frames.size) {
                    val statesForTab = domain.frames[tabIdx].levers
                    tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                        if (leverIdx < statesForTab.size && leverDef.lcc_enabled && config.config.lcc_enabled) {
                            val isReversed = statesForTab[leverIdx].isReversed
                            val eventId = if (isReversed) leverDef.lcc_event_reversed else leverDef.lcc_event_normal
                            if (eventId.isNotBlank()) {
                                lccClient.produceEvent(eventId)
                                kotlinx.coroutines.delay(20) // prevent flooding the bus
                            }
                        }
                    }
                    
                    // Also identify block states from the network
                    tabDef.blocks.forEach { blockDef ->
                        if (config.config.lcc_enabled) {
                            if (blockDef.lcc_event_occupied.isNotBlank()) {
                                lccClient.identifyProducer(blockDef.lcc_event_occupied)
                                kotlinx.coroutines.delay(20)
                            }
                            if (blockDef.lcc_event_empty.isNotBlank()) {
                                lccClient.identifyProducer(blockDef.lcc_event_empty)
                                kotlinx.coroutines.delay(20)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleExternalEvent(hexEventId: String) {
        var result: EventProcessorResult? = null
        
        _domainState.update { currentDomain ->
            val r = eventProcessor.processEvent(hexEventId, currentDomain, _configState.value, _uiState.value)
            result = r
            r.newState
        }
        
        if (result?.didChange == true) {
            persistStatesIfEnabled()
        }
        
        result?.outgoingEvents?.forEach { eventStr ->
            lccClient.produceEvent(eventStr)
        }
    }

    fun tabSelected(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
        _domainState.update { currentDomain ->
            val configState = _configState.value
            val conflicts = if (configState.tabs.isNotEmpty() && index in currentDomain.frames.indices) {
                Interlocking.getConflictingLevers(
                    configState.tabs[index].second,
                    currentDomain.frames[index].levers,
                    currentDomain.frames[index].blocks
                )
            } else emptyList()
            currentDomain.copy(conflictingLevers = conflicts)
        }
    }

    fun toggleLever(tabIndex: Int, leverIndex: Int): Boolean {
        var lccEventStr: String? = null
        var didChange = false
        
        _domainState.update { currentDomain ->
            val configState = _configState.value
            val uiState = _uiState.value
            val tabDef = configState.tabs[tabIndex].second
            val frame = currentDomain.frames[tabIndex]
            val currentStates = frame.levers
            val leverState = currentStates[leverIndex].isReversed
            val targetState = !leverState
            
            val newStates = LeverFramePolicy.attemptToggle(tabDef, currentStates, frame.blocks, leverIndex, targetState)
            if (newStates != null) {
                didChange = true
                val updatedFrames = currentDomain.frames.toMutableList()
                updatedFrames[tabIndex] = frame.copy(levers = newStates)
                
                val conflicts = if (configState.tabs.isNotEmpty() && uiState.selectedTabIndex in updatedFrames.indices) {
                    Interlocking.getConflictingLevers(
                        configState.tabs[uiState.selectedTabIndex].second,
                        updatedFrames[uiState.selectedTabIndex].levers,
                        updatedFrames[uiState.selectedTabIndex].blocks
                    )
                } else emptyList()

                val leverDef = tabDef.levers[leverIndex]
                val shouldSendLcc = configState.config.lcc_enabled && leverDef.lcc_enabled
                if (shouldSendLcc && targetState && leverDef.lcc_event_reversed.isNotBlank()) {
                    lccEventStr = leverDef.lcc_event_reversed
                } else if (shouldSendLcc && !targetState && leverDef.lcc_event_normal.isNotBlank()) {
                    lccEventStr = leverDef.lcc_event_normal
                } else {
                    lccEventStr = null
                }

                _uiState.update { it.copy(errorMessage = null) }
                currentDomain.copy(frames = updatedFrames, conflictingLevers = conflicts)
            } else {
                didChange = false
                lccEventStr = null
                _uiState.update { it.copy(errorMessage = "Interlocking conflict: Cannot move lever") }
                currentDomain
            }
        }
        
        lccEventStr?.let { lccClient.produceEvent(it) }
        if (didChange) {
            persistStatesIfEnabled()
        }
        return didChange
    }

    fun toggleManualLock(tabIndex: Int, leverIndex: Int) {
        _domainState.update { currentDomain ->
            val updatedFrames = currentDomain.frames.toMutableList()
            val frame = updatedFrames[tabIndex]
            val updatedLevers = frame.levers.toMutableList()
            updatedLevers[leverIndex] = updatedLevers[leverIndex].copy(isManuallyLocked = !updatedLevers[leverIndex].isManuallyLocked)
            updatedFrames[tabIndex] = frame.copy(levers = updatedLevers)
            currentDomain.copy(frames = updatedFrames)
        }
        persistStatesIfEnabled()
    }

    fun leverLabelClicked(leverIndex: Int) {
        _uiState.update { it.copy(isStatusMode = true, statusLeverIndex = leverIndex) }
    }

    fun enterConfigMode(mode: ConfigMode, frameIndex: Int? = null, leverIndex: Int? = null) {
        _uiState.update { 
            it.copy(
                configMode = mode,
                initialEditFrameIndex = frameIndex,
                initialEditLeverIndex = leverIndex
            ) 
        }
    }

    fun exitConfigMode() {
        _uiState.update { 
            it.copy(
                configMode = ConfigMode.NONE,
                initialEditFrameIndex = null,
                initialEditLeverIndex = null
            ) 
        }
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
        val outgoingEvents = mutableListOf<String>()
        var didChange = false
        
        _domainState.update { currentDomain ->
            val configState = _configState.value
            val uiState = _uiState.value
            if (tabIndex in currentDomain.frames.indices && blockIndex in currentDomain.frames[tabIndex].blocks.indices) {
                val updatedFrames = currentDomain.frames.toMutableList()
                val frame = updatedFrames[tabIndex]
                
                val newBlocks = frame.blocks.toMutableList()
                newBlocks[blockIndex] = newBlocks[blockIndex].copy(isOccupied = !newBlocks[blockIndex].isOccupied)
                
                val newLevers = frame.levers.toMutableList()
                val tabDef = configState.tabs[tabIndex].second
                
                // Evaluate auto-reversers (cascade until steady state)
                Interlocking.applyCascades(tabDef, newLevers, newBlocks, outgoingEvents)
                
                updatedFrames[tabIndex] = frame.copy(blocks = newBlocks, levers = newLevers)
                
                val conflicts = if (configState.tabs.isNotEmpty() && uiState.selectedTabIndex in updatedFrames.indices) {
                    Interlocking.getConflictingLevers(
                        configState.tabs[uiState.selectedTabIndex].second,
                        updatedFrames[uiState.selectedTabIndex].levers,
                        updatedFrames[uiState.selectedTabIndex].blocks
                    )
                } else emptyList()
                
                didChange = true
                currentDomain.copy(
                    frames = updatedFrames,
                    conflictingLevers = conflicts
                )
            } else {
                currentDomain
            }
        }
        
        if (didChange) {
            persistStatesIfEnabled()
        }
        
        if (configRepo.currentConfig.lcc_enabled) {
            outgoingEvents.forEach { eventStr ->
                lccClient.produceEvent(eventStr)
            }
        }
    }

    fun updateSystemConfig(newConfig: JsonConfig, rulesOnly: Boolean = false, clearStates: Boolean = false) {
        val prevIp = configRepo.currentConfig.jmri_hub_ip
        val prevEnabled = configRepo.currentConfig.lcc_enabled
        val prevNodeId = configRepo.currentConfig.node_id
        
        viewModelScope.launch {
            configRepo.saveConfig(newConfig)
            if (clearStates) {
                configRepo.clearSavedStates()
            }
            
            if (!newConfig.lcc_enabled) {
                lccClient.disconnect()
                _uiState.update { it.copy(networkError = null) }
            } else if (!prevEnabled || prevIp != newConfig.jmri_hub_ip || prevNodeId != newConfig.node_id) {
                lccClient.disconnect()
                _uiState.update { it.copy(networkError = null) }
                lccClient.initialize()
            }
            
            if (rulesOnly) {
                val configStr = configRepo.toJsonString()
                val parsedTabs = configRepo.parseConfig(configStr)
                _configState.update { 
                    it.copy(
                        tabs = parsedTabs,
                        config = configRepo.currentConfig
                    )
                }
            } else {
                loadConfig()
            }
        }
    }

    fun saveLayoutWeights(landscapeWeight: Float, portraitWeight: Float) {
        val newConfig = configRepo.currentConfig.copy(
            schematic_weight_landscape = landscapeWeight,
            schematic_weight_portrait = portraitWeight
        )
        viewModelScope.launch {
            configRepo.saveConfig(newConfig)
            _configState.update { it.copy(config = newConfig) }
        }
    }
    
}
