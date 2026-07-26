package org.edranor.leverframe

import org.edranor.openlcb.LccNetworkClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.edranor.leverframe.services.ConfigurationService
import org.edranor.leverframe.services.InterlockingService

class AppViewModel(
    private val configService: ConfigurationService,
    private val interlockingService: InterlockingService,
    private val nxRoutingService: org.edranor.leverframe.services.NxRoutingService,
    private val lccClient: LccNetworkClient
) : ViewModel() {

    val domainState: StateFlow<DomainState> = interlockingService.domainState
    val configState: StateFlow<ConfigState> = configService.configState
    
    private val _uiState = MutableStateFlow(TransientUiState())
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

    fun tabSelected(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
        interlockingService.recalculateConflicts(index)
    }
    
    fun dismissErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

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

    fun toggleManualLock(tabIndex: Int, leverIndex: Int) {
        interlockingService.toggleManualLock(tabIndex, leverIndex)
    }
    
    fun setNxRoute(tabIndex: Int, route: NxRoute) {
        val result = nxRoutingService.setNxRoute(tabIndex, route, _uiState.value.selectedTabIndex)
        handleNxRoutingResult(result)
    }
    
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
        configService.setLeverLccEnabled(tabIndex, leverIndex, enabled)
    }

    fun toggleBlockState(tabIndex: Int, blockIndex: Int) {
        interlockingService.toggleBlockState(tabIndex, blockIndex, _uiState.value.selectedTabIndex)
    }

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
