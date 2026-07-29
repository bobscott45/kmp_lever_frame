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
 * Acts as the single source of truth for the application's state and Unidirectional Data Flow (UDF).
 * Manages DomainState, ConfigState, and TransientUiState flows to optimize UI recomposition,
 * and routes user intents to the appropriate underlying services (Networking, Interlocking, Configuration).
 */
package org.edranor.leverframe
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

import org.edranor.openlcb.LccNetworkClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages the UI lifecycle and routes user intents to the appropriate underlying services.
 * Instances of this ViewModel observe the interlocking domain model, system configuration, 
 * and LCC network connection to drive Unidirectional Data Flow (UDF) to the Compose views.
 * 
 * @property configService Manages layout configuration, tabs, and persistence preferences.
 * @property interlockingService The core logic engine evaluating lever constraints and cascades.
 * @property nxRoutingService Engine for calculating and resolving Entrance-Exit routing requests.
 * @property lccClient Manages bidirectional OpenLCB/LCC network traffic and node discovery.
 */
class AppViewModel(
    private val configService: ConfigurationService,
    private val interlockingService: InterlockingService,
    private val nxRoutingService: org.edranor.leverframe.services.NxRoutingService,
    private val lccClient: LccNetworkClient
) : ViewModel() {

    /** State flow of the interlocking domain model, representing levers and blocks. */
    val domainState: StateFlow<DomainState> = interlockingService.domainState
    
    /** State flow containing the currently loaded system configuration. */
    val configState: StateFlow<ConfigState> = configService.configState
    
    private val _uiState = MutableStateFlow(TransientUiState())
    
    /** State flow of transient UI properties, such as active modes, errors, and navigation. */
    val uiState: StateFlow<TransientUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            configService.initialize()
            interlockingService.buildInitialState()
            
            val initialConfig = configState.value.config
            if (initialConfig.lcc_enabled) {
                lccClient.initialize()
            }
            
            launch {
                lccClient.connectionStatus.collect { status ->
                    _uiState.update { it.copy(networkStatus = status) }
                    if (status.startsWith("Connected") && configState.value.config.lcc_master) {
                        interlockingService.broadcastCurrentStates()
                    }
                }
            }
            launch {
                lccClient.connectionErrors.collect { error ->
                    _uiState.update { it.copy(networkError = error) }
                }
            }
            launch {
                lccClient.externalEvents.collect { hexEventId ->
                    interlockingService.handleExternalEvent(hexEventId, _uiState.value)
                }
            }
        }
    }

    /**
     * Updates the currently active tab (frame) in the UI.
     * Triggers a recalculation of interlocking conflicts for the new tab.
     *
     * @param index The zero-based index of the selected tab.
     */
    fun tabSelected(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
        interlockingService.recalculateConflicts(index)
    }
    
    /** Dismisses the currently displayed transient error message. */
    fun dismissErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Attempts to toggle the state (Normal/Reversed) of a specific lever.
     * Enforces interlocking rules; if the toggle is invalid, an error is temporarily surfaced to the UI.
     *
     * @param tabIndex The index of the frame containing the lever.
     * @param leverIndex The index of the lever within the frame.
     * @return `true` if the lever state was successfully changed, `false` otherwise.
     */
    fun toggleLever(tabIndex: Int, leverIndex: Int): Boolean {
        val result = interlockingService.toggleLever(tabIndex, leverIndex, _uiState.value.selectedTabIndex)
        if (result.errorMessage != null) {
            _uiState.update { it.copy(errorMessage = result.errorMessage) }
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                _uiState.update { state -> 
                    if (state.errorMessage == result.errorMessage) state.copy(errorMessage = null) else state
                }
            }
        } else {
            _uiState.update { it.copy(errorMessage = null) }
        }
        return result.didChange
    }

    /**
     * Toggles the manual (white collar) software lock on a lever, preventing or allowing manipulation.
     *
     * @param tabIndex The index of the frame containing the lever.
     * @param leverIndex The index of the lever within the frame.
     */
    fun toggleManualLock(tabIndex: Int, leverIndex: Int) {
        interlockingService.toggleManualLock(tabIndex, leverIndex)
    }
    
    /**
     * Initiates or advances an Entrance-Exit (NX) routing request.
     *
     * @param tabIndex The index of the frame where the route is being set.
     * @param route The NxRoute configuration to apply.
     */
    fun setNxRoute(tabIndex: Int, route: NxRoute) {
        val result = nxRoutingService.setNxRoute(tabIndex, route, _uiState.value.selectedTabIndex)
        handleNxRoutingResult(result)
    }
    
    /**
     * Cancels an active or pending Entrance-Exit (NX) route originating from the given coordinates.
     *
     * @param tabIndex The index of the active frame.
     * @param pos The (X, Y) grid coordinates of the route's entrance element.
     */
    fun cancelNxRoute(tabIndex: Int, pos: Pair<Int, Int>) {
        val result = nxRoutingService.cancelNxRoute(tabIndex, pos, _uiState.value.selectedTabIndex)
        handleNxRoutingResult(result)
    }
    
    private fun handleNxRoutingResult(result: org.edranor.leverframe.services.NxRoutingResult) {
        when (result) {
            is org.edranor.leverframe.services.NxRoutingResult.Error -> {
                _uiState.update { 
                    it.copy(
                        errorMessage = result.message,
                        routeErrorCells = result.errorCells
                    ) 
                }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { state -> 
                        val newMsg = if (state.errorMessage == result.message) null else state.errorMessage
                        state.copy(routeErrorCells = emptyList(), errorMessage = newMsg) 
                    }
                }
            }
            else -> {}
        }
    }

    /**
     * Opens the status inspection view for a specific lever, displaying its logic evaluation.
     *
     * @param leverIndex The index of the lever to inspect.
     */
    fun leverLabelClicked(leverIndex: Int) {
        _uiState.update { it.copy(isStatusMode = true, statusLeverIndex = leverIndex) }
    }

    /**
     * Enters a specific configuration editing mode (e.g., SYSTEM, FRAMES).
     *
     * @param mode The configuration mode to enter.
     * @param frameIndex Optional initial frame index to edit.
     * @param leverIndex Optional initial lever index to edit.
     */
    fun enterConfigMode(mode: ConfigMode, frameIndex: Int? = null, leverIndex: Int? = null) {
        _uiState.update { 
            it.copy(
                configMode = mode,
                initialEditFrameIndex = frameIndex,
                initialEditLeverIndex = leverIndex
            ) 
        }
    }

    /** Exits the configuration editing view and returns to the operational UI. */
    fun exitConfigMode() {
        _uiState.update { 
            it.copy(
                configMode = ConfigMode.NONE,
                initialEditFrameIndex = null,
                initialEditLeverIndex = null
            ) 
        }
    }

    /** Opens the general status overlay. */
    fun enterStatusMode() {
        _uiState.update { it.copy(isStatusMode = true, statusLeverIndex = null) }
    }

    /** Closes the general status overlay. */
    fun exitStatusMode() {
        _uiState.update { it.copy(isStatusMode = false, statusLeverIndex = null) }
    }

    /** Closes the specific lever status inspection view. */
    fun dismissStatusLever() {
        _uiState.update { it.copy(isStatusMode = false, statusLeverIndex = null, errorMessage = null) }
    }

    /** Clears any active network connection error from the UI state. */
    fun dismissNetworkError() {
        _uiState.update { it.copy(networkError = null) }
    }

    /**
     * Toggles whether a specific lever will broadcast and respond to LCC network events.
     *
     * @param tabIndex The index of the frame containing the lever.
     * @param leverIndex The index of the lever within the frame.
     * @param enabled `true` to enable LCC networking for the lever, `false` to disable.
     */
    fun setLeverLccEnabled(tabIndex: Int, leverIndex: Int, enabled: Boolean) {
        configService.setLeverLccEnabled(tabIndex, leverIndex, enabled)
    }

    /**
     * Toggles the hardware/virtual state (Occupied/Empty) of a track block.
     *
     * @param tabIndex The index of the frame containing the block.
     * @param blockIndex The index of the block within the frame.
     */
    fun toggleBlockState(tabIndex: Int, blockIndex: Int) {
        interlockingService.toggleBlockState(tabIndex, blockIndex, _uiState.value.selectedTabIndex)
    }

    /**
     * Submits a new system configuration, potentially reloading network connections and rebuilding domain state.
     *
     * @param newConfig The updated system configuration.
     * @param rulesOnly If `true`, avoids rebuilding the entire state and only refreshes rules.
     * @param clearStates If `true`, wipes persisted lever states during reload.
     */
    fun updateSystemConfig(newConfig: JsonConfig, rulesOnly: Boolean = false, clearStates: Boolean = false) {
        val prevIp = configState.value.config.jmri_hub_ip
        val prevEnabled = configState.value.config.lcc_enabled
        val prevNodeId = configState.value.config.node_id
        
        viewModelScope.launch {
            configService.updateSystemConfig(newConfig, rulesOnly)
            if (clearStates) {
                interlockingService.clearSavedStates()
            }
            
            if (!newConfig.lcc_enabled) {
                lccClient.disconnect()
                _uiState.update { it.copy(networkError = null) }
            } else if (!prevEnabled || prevIp != newConfig.jmri_hub_ip || prevNodeId != newConfig.node_id) {
                lccClient.disconnect()
                _uiState.update { it.copy(networkError = null) }
                lccClient.initialize()
            }
            
            if (!rulesOnly) {
                interlockingService.buildInitialState()
            }
        }
    }

    fun saveLayoutWeights(landscapeWeight: Float, portraitWeight: Float) {
        configService.saveLayoutWeights(landscapeWeight, portraitWeight)
    }
}
