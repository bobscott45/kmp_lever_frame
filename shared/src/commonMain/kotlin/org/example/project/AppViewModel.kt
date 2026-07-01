package org.example.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LeverFrameUiState())
    val uiState: StateFlow<LeverFrameUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LeverFrameUiState()
    )

    init {
        viewModelScope.launch {
            LccNode.initialize()
            LccNode.externalEvents.collect { hexEventId ->
                handleExternalEvent(hexEventId)
            }
        }
        loadConfig()
    }

    private fun loadConfig() {
        val configStr = ConfigManager.toJsonString()
        val parsedTabs = ConfigManager.parseConfig(configStr)
        val initialVersion = _uiState.value.configVersion
        
        // Build initial states
        val leverStates = parsedTabs.map { (tabName, tabDef) ->
            BooleanArray(tabDef.levers.size) { false }
        }
        val manualLocks = parsedTabs.map { (tabName, tabDef) ->
            BooleanArray(tabDef.levers.size) { false }
        }
        
        // Restore from disk if configured
        val storedStates = ConfigManager.loadSavedLeverStates()
        if (ConfigManager.currentConfig.restore_last_state && storedStates != null && storedStates.isNotEmpty()) {
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
                configVersion = initialVersion + 1
            )
        }
    }

    private fun persistStatesIfEnabled() {
        if (ConfigManager.currentConfig.restore_last_state) {
            ConfigManager.saveCurrentLeverStates(_uiState.value.leverStates)
        }
    }

    private fun handleExternalEvent(hexEventId: String) {
        val state = _uiState.value
        if (!ConfigManager.currentConfig.lcc_master) return
        
        var stateChanged = false
        val newLeverStates = state.leverStates.map { it.clone() }
        val policy = ConflictPolicy.of(ConfigManager.currentConfig.conflict_policy)

        state.tabs.forEachIndexed { tabIdx, tabPair ->
            val tabDef = tabPair.second
            tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                if (!leverDef.lcc_enabled) return@forEachIndexed
                var attemptState: Boolean? = null
                
                if (leverDef.lcc_event_normal.isNotBlank()) {
                    val normalHex = LccNode.parseEventId(leverDef.lcc_event_normal)
                    if (normalHex == hexEventId) attemptState = false
                }
                if (leverDef.lcc_event_reversed.isNotBlank()) {
                    val reversedHex = LccNode.parseEventId(leverDef.lcc_event_reversed)
                    if (reversedHex == hexEventId) attemptState = true
                }
                
                if (attemptState != null) {
                    val currentState = newLeverStates[tabIdx][leverIdx]
                    if (currentState != attemptState) {
                        val isValid = Interlocking.evaluate(tabDef, newLeverStates[tabIdx], leverIdx, attemptState)
                        if (LeverFramePolicy.shouldApplyExternalEvent(policy, isValid)) {
                            newLeverStates[tabIdx][leverIdx] = attemptState
                            stateChanged = true
                        }
                    }
                }
            }
        }
        
        if (stateChanged) {
            _uiState.update { it.copy(leverStates = newLeverStates) }
            persistStatesIfEnabled()
            updateConflictingLevers()
        }
    }

    fun dispatch(intent: LeverFrameIntent) {
        val state = _uiState.value
        when (intent) {
            is LeverFrameIntent.TabSelected -> {
                _uiState.update { it.copy(selectedTabIndex = intent.index) }
            }
            is LeverFrameIntent.ToggleLever -> {
                val tabDef = state.tabs[intent.tabIndex].second
                val currentStates = state.leverStates[intent.tabIndex]
                val currentState = currentStates[intent.leverIndex]
                val targetState = !currentState
                
                val newStates = LeverFramePolicy.attemptToggle(tabDef, currentStates, intent.leverIndex, targetState)
                if (newStates != null) {
                    val updatedAllStates = state.leverStates.toMutableList()
                    updatedAllStates[intent.tabIndex] = newStates
                    _uiState.update { it.copy(leverStates = updatedAllStates, errorMessage = null) }
                    
                    // Fire LCC event
                    val leverDef = tabDef.levers[intent.leverIndex]
                    if (targetState && leverDef.lcc_event_reversed.isNotBlank()) {
                        LccNode.produceEvent(leverDef.lcc_event_reversed)
                    } else if (!targetState && leverDef.lcc_event_normal.isNotBlank()) {
                        LccNode.produceEvent(leverDef.lcc_event_normal)
                    }
                    
                    persistStatesIfEnabled()
                    updateConflictingLevers()
                } else {
                    _uiState.update { it.copy(errorMessage = "Interlocking prevents this lever from moving.") }
                }
            }
            is LeverFrameIntent.ToggleManualLock -> {
                val updatedLocks = state.manualLocks.toMutableList()
                val tabLocks = updatedLocks[intent.tabIndex].clone()
                tabLocks[intent.leverIndex] = !tabLocks[intent.leverIndex]
                updatedLocks[intent.tabIndex] = tabLocks
                _uiState.update { it.copy(manualLocks = updatedLocks) }
                persistStatesIfEnabled()
            }
            is LeverFrameIntent.LeverLabelClicked -> {
                _uiState.update { it.copy(isStatusMode = true, statusLeverIndex = intent.leverIndex) }
            }
            LeverFrameIntent.EnterConfigMode -> {
                _uiState.update { it.copy(isConfigMode = true) }
            }
            LeverFrameIntent.ExitConfigMode -> {
                _uiState.update { it.copy(isConfigMode = false) }
            }
            LeverFrameIntent.ConfigSaved -> {
                loadConfig() // Reloads tabs and triggers recomposition
            }
            LeverFrameIntent.EnterStatusMode -> {
                _uiState.update { it.copy(isStatusMode = true, statusLeverIndex = null) }
            }
            LeverFrameIntent.ExitStatusMode -> {
                _uiState.update { it.copy(isStatusMode = false, statusLeverIndex = null) }
            }
            LeverFrameIntent.DismissStatusLever -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            is LeverFrameIntent.SetLeverLccEnabled -> {
                val newTabsJson = ConfigManager.currentConfig.tabs.toMutableList()
                val currentTabJson = newTabsJson[intent.tabIndex].copy()
                val newLeversJson = currentTabJson.levers.toMutableList()
                newLeversJson[intent.leverIndex] = newLeversJson[intent.leverIndex].copy(lcc_enabled = intent.enabled)
                val newConfig = ConfigManager.currentConfig.copy(
                    tabs = newTabsJson.apply { set(intent.tabIndex, currentTabJson.copy(levers = newLeversJson)) }
                )
                ConfigManager.currentConfig = newConfig
                saveConfigToFile(ConfigManager.toJsonString())
                loadConfig()
            }
        }
    }
    
    private fun updateConflictingLevers() {
        val state = _uiState.value
        if (state.tabs.isEmpty()) return
        
        val conflicts = Interlocking.getConflictingLevers(
            state.tabs[state.selectedTabIndex].second,
            state.leverStates[state.selectedTabIndex]
        )
        _uiState.update { it.copy(conflictingLevers = conflicts) }
    }
}
