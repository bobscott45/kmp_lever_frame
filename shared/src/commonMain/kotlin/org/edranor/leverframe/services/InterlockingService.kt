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
 * The central brain for evaluating interlocking rules, handling lever/block toggles,
 * and propagating network events (LCC) to the physical state of the lever frames.
 */
package org.edranor.leverframe.services

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

/**
 * The central service managing the evaluation of interlocking rules, block cascades, 
 * and network event processing. It holds the authoritative source of truth for the 
 * [DomainState] and triggers physical save operations via [PersistenceService].
 * 
 * @property configService Provides the active system layout configuration.
 * @property persistenceRepo Repository interface for persisting state across reboots.
 * @property configRepo Repository interface for accessing application settings.
 * @property lccClient Client used to emit network events based on state changes.
 * @property eventProcessor Handles incoming hex LCC events mapping to block/lever changes.
 */
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

    /**
     * Rebuilds the initial domain state (levers and blocks) from the current configuration,
     * optionally loading persisted states from disk if the configuration allows it.
     */
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

    /**
     * Broadcasts the current states of all enabled levers and polls block occupancy sensors
     * on the LCC network upon initialization.
     */
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

    /**
     * Processes an incoming LCC event ID string, delegating to the `NetworkEventProcessor`
     * and potentially updating the local domain state and responding with outgoing events.
     *
     * @param hexEventId The 16-character hex representation of the LCC Event ID.
     * @param currentUiState The current transient UI state, used for determining active tabs.
     */
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

    /**
     * Re-evaluates interlocking rules across the currently selected tab to update visual conflict alarms.
     *
     * @param selectedTabIndex The index of the newly focused tab.
     */
    fun recalculateConflicts(selectedTabIndex: Int) {
        _domainState.update { currentDomain ->
            val configState = configService.configState.value
            val conflicts = if (configState.tabs.isNotEmpty() && selectedTabIndex in currentDomain.frames.indices) {
                Interlocking.getConflictingLevers(
                    configState.tabs[selectedTabIndex].second.toInterlockingGraph(),
                    currentDomain.frames[selectedTabIndex].levers,
                    currentDomain.frames[selectedTabIndex].blocks
                )
            } else emptyList()
            currentDomain.copy(conflictingLevers = conflicts)
        }
    }

    /**
     * Attempts to toggle a lever's state locally, respecting interlocking logic.
     * If successful, saves state and optionally broadcasts an LCC network event.
     *
     * @param tabIndex Index of the frame containing the lever.
     * @param leverIndex Index of the lever being toggled.
     * @param selectedTabIndex The currently active tab, used to update visual alarms immediately.
     * @return A [ToggleResult] indicating success or failure, with an optional error message.
     */
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
                        configState.tabs[selectedTabIndex].second.toInterlockingGraph(),
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

    /**
     * Toggles the Occupied/Empty state of a track block (e.g., when a virtual sensor is clicked).
     * This may cascade and trigger `autoReverser` logic on bound levers, returning them to NORMAL.
     *
     * @param tabIndex Index of the frame containing the block.
     * @param blockIndex Index of the block being toggled.
     * @param selectedTabIndex The currently active tab, for refreshing conflict alarms.
     */
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
                val cascadedLeverIndices = Interlocking.applyCascades(tabDef.toInterlockingGraph(), newLevers, newBlocks)
                cascadedLeverIndices.forEach { leverIdx ->
                    val lDef = tabDef.levers[leverIdx]
                    if (lDef.lcc_event_normal.isNotBlank()) {
                        outgoingEvents.add(lDef.lcc_event_normal)
                    }
                }
                
                val blockDef = tabDef.blocks[blockIndex]
                if (blockDef.mode == BlockMode.VIRTUAL_SENSOR && !configState.config.sim_mode) {
                    val isOccupied = newBlocks[blockIndex].isOccupied
                    val eventStr = if (isOccupied) blockDef.lcc_event_occupied else blockDef.lcc_event_empty
                    if (eventStr.isNotBlank()) {
                        outgoingEvents.add(eventStr)
                    }
                }
                
                updatedFrames[tabIndex] = frame.copy(blocks = newBlocks, levers = newLevers)
                
                val conflicts = if (configState.tabs.isNotEmpty() && selectedTabIndex in updatedFrames.indices) {
                    Interlocking.getConflictingLevers(
                        configState.tabs[selectedTabIndex].second.toInterlockingGraph(),
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

/**
 * Result wrapper for lever toggle operations.
 *
 * @property didChange `true` if the state was mutated, `false` otherwise.
 * @property errorMessage Present if the toggle failed (e.g., interlocking conflict).
 */
data class ToggleResult(val didChange: Boolean, val errorMessage: String?)
