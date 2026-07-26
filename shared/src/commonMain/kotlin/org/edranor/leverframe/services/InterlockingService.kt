package org.edranor.leverframe.services

import org.edranor.leverframe.*
import org.edranor.openlcb.LccNetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InterlockingService(
    private val configService: ConfigurationService,
    private val persistenceRepo: StatePersistenceRepository,
    private val configRepo: ConfigurationRepository,
    private val lccClient: LccNetworkClient,
    private val eventProcessor: NetworkEventProcessor
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _domainState = MutableStateFlow(DomainState())
    val domainState: StateFlow<DomainState> = _domainState.asStateFlow()

    private val persistenceService = PersistenceService(
        persistenceRepo = persistenceRepo,
        configRepo = configRepo,
        scope = coroutineScope,
        domainStateFlow = domainState
    )

    fun persistStatesIfEnabled() {
        persistenceService.triggerSave()
    }

    suspend fun clearSavedStates() {
        persistenceRepo.clearSavedStates()
    }

    suspend fun buildInitialState() {
        val configState = configService.configState.value
        val frames = configState.tabs.mapIndexed { tabIdx, (_, tabDef) ->
            DomainFrame(
                id = tabIdx,
                levers = tabDef.levers.mapIndexed { i, _ -> DomainLever(i, false, false) },
                blocks = tabDef.blocks.mapIndexed { i, _ -> DomainBlock(i, true) }
            )
        }.toMutableList()

        val storedData = persistenceRepo.loadSavedStates()
        if (configState.config.restore_last_state && storedData != null) {
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

        _domainState.update {
            it.copy(frames = frames.toList())
        }
    }

    fun broadcastCurrentStates() {
        coroutineScope.launch {
            kotlinx.coroutines.delay(1000) // Wait for LccNode init sequence to finish
            val domain = _domainState.value
            val config = configService.configState.value
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

    fun handleExternalEvent(hexEventId: String, currentUiState: TransientUiState) {
        var result: EventProcessorResult? = null
        
        _domainState.update { currentDomain ->
            val r = eventProcessor.processEvent(hexEventId, currentDomain, configService.configState.value, currentUiState)
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

    fun recalculateConflicts(selectedTabIndex: Int) {
        _domainState.update { currentDomain ->
            val configState = configService.configState.value
            val conflicts = if (configState.tabs.isNotEmpty() && selectedTabIndex in currentDomain.frames.indices) {
                Interlocking.getConflictingLevers(
                    configState.tabs[selectedTabIndex].second,
                    currentDomain.frames[selectedTabIndex].levers,
                    currentDomain.frames[selectedTabIndex].blocks
                )
            } else emptyList()
            currentDomain.copy(conflictingLevers = conflicts)
        }
    }

    fun toggleLever(tabIndex: Int, leverIndex: Int, selectedTabIndex: Int): ToggleResult {
        var lccEventStr: String? = null
        var didChange = false
        var errorMessage: String? = null
        
        _domainState.update { currentDomain ->
            val configState = configService.configState.value
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
                
                val conflicts = if (configState.tabs.isNotEmpty() && selectedTabIndex in updatedFrames.indices) {
                    Interlocking.getConflictingLevers(
                        configState.tabs[selectedTabIndex].second,
                        updatedFrames[selectedTabIndex].levers,
                        updatedFrames[selectedTabIndex].blocks
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

                currentDomain.copy(frames = updatedFrames, conflictingLevers = conflicts)
            } else {
                didChange = false
                lccEventStr = null
                errorMessage = "Interlocking conflict: Cannot move lever"
                currentDomain
            }
        }
        
        lccEventStr?.let { lccClient.produceEvent(it) }
        if (didChange) {
            persistStatesIfEnabled()
        }
        return ToggleResult(didChange, errorMessage)
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

    fun toggleBlockState(tabIndex: Int, blockIndex: Int, selectedTabIndex: Int) {
        val outgoingEvents = mutableListOf<String>()
        var didChange = false
        
        _domainState.update { currentDomain ->
            val configState = configService.configState.value
            if (tabIndex in currentDomain.frames.indices && blockIndex in currentDomain.frames[tabIndex].blocks.indices) {
                val updatedFrames = currentDomain.frames.toMutableList()
                val frame = updatedFrames[tabIndex]
                
                val newBlocks = frame.blocks.toMutableList()
                newBlocks[blockIndex] = newBlocks[blockIndex].copy(isOccupied = !newBlocks[blockIndex].isOccupied)
                
                val newLevers = frame.levers.toMutableList()
                val tabDef = configState.tabs[tabIndex].second
                
                // Evaluate auto-reversers (cascade until steady state)
                Interlocking.applyCascades(tabDef, newLevers, newBlocks, outgoingEvents)
                
                val blockDef = tabDef.blocks[blockIndex]
                if (blockDef.broadcastToggles) {
                    val isOccupied = newBlocks[blockIndex].isOccupied
                    val eventStr = if (isOccupied) blockDef.lcc_event_occupied else blockDef.lcc_event_empty
                    if (eventStr.isNotBlank()) {
                        outgoingEvents.add(eventStr)
                    }
                }
                
                updatedFrames[tabIndex] = frame.copy(blocks = newBlocks, levers = newLevers)
                
                val conflicts = if (configState.tabs.isNotEmpty() && selectedTabIndex in updatedFrames.indices) {
                    Interlocking.getConflictingLevers(
                        configState.tabs[selectedTabIndex].second,
                        updatedFrames[selectedTabIndex].levers,
                        updatedFrames[selectedTabIndex].blocks
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
        
        if (configService.configState.value.config.lcc_enabled) {
            outgoingEvents.forEach { eventStr ->
                lccClient.produceEvent(eventStr)
            }
        }
    }
}

data class ToggleResult(val didChange: Boolean, val errorMessage: String?)
