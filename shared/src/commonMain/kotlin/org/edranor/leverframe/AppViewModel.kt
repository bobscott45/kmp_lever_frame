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
        val tabDef = configState.value.tabs.getOrNull(tabIndex)?.second ?: return
        val levers = domainState.value.frames.getOrNull(tabIndex)?.levers ?: return
        val map = tabDef.schematicElements.associateBy { Pair(it.x, it.y) }
        
        // If the entrance signal is already clear, the user wants to CANCEL the route
        val startElemCheck = map[route.pathCells.firstOrNull()]
        if (startElemCheck != null && startElemCheck.type.contains("SIGNAL") && startElemCheck.linkedLever >= 0) {
            val isReversed = levers.getOrNull(startElemCheck.linkedLever)?.isReversed == true
            if (isReversed) {
                cancelNxRoute(tabIndex, route.pathCells.first())
                return
            }
        }
        
        val requiredLeverStates = mutableMapOf<Int, Boolean>()
        val signalLeversToPull = mutableListOf<Int>()
        
        val blocks = domainState.value.frames.getOrNull(tabIndex)?.blocks ?: return
        var isRouteOccupied = false
        val occupiedCells = mutableListOf<Pair<Int, Int>>()
        
        for (i in route.pathCells.indices) {
            val pos = route.pathCells[i]
            val elem = map[pos] ?: continue
            
            if (elem.linkedBlock >= 0) {
                if (blocks.getOrNull(elem.linkedBlock)?.isOccupied == true) {
                    isRouteOccupied = true
                    occupiedCells.add(pos)
                }
            }
            
            if (elem.type.startsWith("TURNOUT")) {
                if (elem.linkedLever >= 0) {
                    val prev = route.pathCells.getOrNull(i - 1)
                    val next = route.pathCells.getOrNull(i + 1)
                    val isDiverging = if (elem.type == "TURNOUT_LEFT") {
                        prev == Pair(pos.first + 1, pos.second - 1) || next == Pair(pos.first + 1, pos.second - 1)
                    } else { // TURNOUT_RIGHT
                        prev == Pair(pos.first + 1, pos.second + 1) || next == Pair(pos.first + 1, pos.second + 1)
                    }
                    requiredLeverStates[elem.linkedLever] = isDiverging
                }
            } else if (elem.type.contains("SIGNAL")) {
                if (elem.type.startsWith("BRACKET_SIGNAL")) {
                    // Look ahead for the next turnout on the path to determine if we pull main or branch signal
                    var nextTurnoutDiverging = false
                    for (j in (i + 1) until route.pathCells.size) {
                        val aheadPos = route.pathCells[j]
                        val aheadElem = map[aheadPos]
                        if (aheadElem != null && aheadElem.type.startsWith("TURNOUT")) {
                            val tPrev = route.pathCells.getOrNull(j - 1)
                            val tNext = route.pathCells.getOrNull(j + 1)
                            nextTurnoutDiverging = if (aheadElem.type == "TURNOUT_LEFT") {
                                tPrev == Pair(aheadPos.first + 1, aheadPos.second - 1) || tNext == Pair(aheadPos.first + 1, aheadPos.second - 1)
                            } else {
                                tPrev == Pair(aheadPos.first + 1, aheadPos.second + 1) || tNext == Pair(aheadPos.first + 1, aheadPos.second + 1)
                            }
                            break
                        }
                    }
                    val leverToPull = if (nextTurnoutDiverging) elem.linkedLever2 else elem.linkedLever
                    if (leverToPull >= 0) {
                        signalLeversToPull.add(leverToPull)
                    }
                } else {
                    if (elem.linkedLever >= 0) {
                        signalLeversToPull.add(elem.linkedLever)
                    }
                }
            }
        }
        
        // Check if there is a signal immediately BEHIND the entrance
        if (route.pathCells.size >= 2) {
            val startPos = route.pathCells[0]
            val nextPos = route.pathCells[1]
            val startElem = map[startPos]
            if (startElem != null) {
                val startNeighbors = NxRoutingEngine.getConnections(startElem, map)
                val behindPos = startNeighbors.find { it != nextPos }
                if (behindPos != null) {
                    val behindElem = map[behindPos]
                    if (behindElem != null && behindElem.type.contains("SIGNAL")) {
                        val leverToPull = if (behindElem.type.startsWith("BRACKET_SIGNAL")) {
                            // Determine main or branch for bracket signal behind entrance
                            var nextTurnoutDiverging = false
                            for (j in 0 until route.pathCells.size) {
                                val aheadPos = route.pathCells[j]
                                val aheadElem = map[aheadPos]
                                if (aheadElem != null && aheadElem.type.startsWith("TURNOUT")) {
                                    val tPrev = route.pathCells.getOrNull(j - 1) ?: behindPos
                                    val tNext = route.pathCells.getOrNull(j + 1)
                                    nextTurnoutDiverging = if (aheadElem.type == "TURNOUT_LEFT") {
                                        tPrev == Pair(aheadPos.first + 1, aheadPos.second - 1) || tNext == Pair(aheadPos.first + 1, aheadPos.second - 1)
                                    } else {
                                        tPrev == Pair(aheadPos.first + 1, aheadPos.second + 1) || tNext == Pair(aheadPos.first + 1, aheadPos.second + 1)
                                    }
                                    break
                                }
                            }
                            if (nextTurnoutDiverging) behindElem.linkedLever2 else behindElem.linkedLever
                        } else {
                            behindElem.linkedLever
                        }
                        if (leverToPull >= 0 && !signalLeversToPull.contains(leverToPull)) {
                            signalLeversToPull.add(leverToPull)
                        }
                    }
                }
            }
        }
        
        if (isRouteOccupied) {
            _uiState.update { 
                it.copy(
                    errorMessage = "Cannot set route: Track circuit occupied",
                    routeErrorCells = occupiedCells
                ) 
            }
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                _uiState.update { state -> 
                    val newMsg = if (state.errorMessage == "Cannot set route: Track circuit occupied") null else state.errorMessage
                    state.copy(routeErrorCells = emptyList(), errorMessage = newMsg) 
                }
            }
            return
        }
        
        // Resolve out-of-path prerequisites (like FPLs, Trap Points, Flank Turnouts)
        for (signalLeverIdx in signalLeversToPull) {
            val leverDef = tabDef.levers.getOrNull(signalLeverIdx) ?: continue
            val ast = leverDef.logic ?: leverDef.conditions.toAstNode()
            if (ast != null) {
                val reqs = NxRoutingEngine.getRequiredLeverStatesFromAst(ast)
                for ((reqLeverIdx, reqState) in reqs) {
                    if (!requiredLeverStates.containsKey(reqLeverIdx)) {
                        val reqDef = tabDef.levers.getOrNull(reqLeverIdx)
                        if (reqDef != null && !reqDef.type.name.contains("SIGNAL")) {
                            requiredLeverStates[reqLeverIdx] = reqState
                        }
                    }
                }
            }
        }
        
        // Execute point changes first
        for ((leverIdx, targetReversed) in requiredLeverStates) {
            val freshLevers = domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            if (freshLevers[leverIdx].isReversed != targetReversed) {
                interlockingService.toggleLever(tabIndex, leverIdx, _uiState.value.selectedTabIndex)
            }
        }
        
        // Try to clear all signals along the route, looping to handle dependencies (e.g. Distant requires Home)
        var madeProgress = true
        var loops = 0
        var lastErrorMsg: String? = null
        
        while (madeProgress && loops < 5) {
            madeProgress = false
            val currentLevers = domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            for (signalLeverIdx in signalLeversToPull) {
                if (!currentLevers[signalLeverIdx].isReversed) {
                    val result = interlockingService.toggleLever(tabIndex, signalLeverIdx, _uiState.value.selectedTabIndex)
                    if (result.didChange) {
                        madeProgress = true
                        lastErrorMsg = null // clear error if we made progress
                    } else if (result.errorMessage != null) {
                        lastErrorMsg = result.errorMessage
                    }
                }
            }
            loops++
        }
        
        // If we finished looping but still have an error (meaning some signals failed to clear), show it!
        if (lastErrorMsg != null) {
            _uiState.update { it.copy(errorMessage = lastErrorMsg) }
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                _uiState.update { state -> 
                    if (state.errorMessage == lastErrorMsg) state.copy(errorMessage = null) else state
                }
            }
        }
    }
    
    fun cancelNxRoute(tabIndex: Int, pos: Pair<Int, Int>) {
        val tabDef = configState.value.tabs.getOrNull(tabIndex)?.second ?: return
        val map = tabDef.schematicElements.associateBy { Pair(it.x, it.y) }
        val startElem = map[pos] ?: return
        
        val currentLevers = domainState.value.frames.getOrNull(tabIndex)?.levers ?: return
        
        fun getActiveConnections(p: Pair<Int, Int>): List<Pair<Int, Int>> {
            val el = map[p] ?: return emptyList()
            val baseConns = NxRoutingEngine.getConnections(el, map)
            
            return baseConns.filter { targetPos ->
                if (el.type.startsWith("TURNOUT")) {
                    val isReversed = if (el.linkedLever >= 0) currentLevers.getOrNull(el.linkedLever)?.isReversed == true else false
                    val isDivergingTarget = if (el.type == "TURNOUT_LEFT") {
                        targetPos == Pair(p.first + 1, p.second - 1)
                    } else {
                        targetPos == Pair(p.first + 1, p.second + 1)
                    }
                    val isStraightTarget = targetPos == Pair(p.first + 1, p.second)
                    
                    if (isReversed) !isStraightTarget else !isDivergingTarget
                } else {
                    true
                }
            }
        }

        val leversToNormal = mutableSetOf<Int>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        val visited = mutableSetOf<Pair<Int, Int>>()
        queue.add(pos)
        
        while (queue.isNotEmpty()) {
            val currPos = queue.removeFirst()
            if (!visited.add(currPos)) continue
            
            val elem = map[currPos] ?: continue
            
            if (elem.type.contains("SIGNAL")) {
                if (elem.linkedLever >= 0 && currentLevers.getOrNull(elem.linkedLever)?.isReversed == true) {
                    leversToNormal.add(elem.linkedLever)
                }
                if (elem.linkedLever2 >= 0 && currentLevers.getOrNull(elem.linkedLever2)?.isReversed == true) {
                    leversToNormal.add(elem.linkedLever2)
                }
            }
            
            val activeNeighbors = getActiveConnections(currPos)
            for (n in activeNeighbors) {
                if (getActiveConnections(n).contains(currPos)) {
                    queue.add(n)
                }
            }
        }
        for (leverIdx in leversToNormal) {
            if (currentLevers[leverIdx].isReversed) {
                val result = interlockingService.toggleLever(tabIndex, leverIdx, _uiState.value.selectedTabIndex)
                if (result.errorMessage != null) {
                    _uiState.update { it.copy(errorMessage = result.errorMessage) }
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(3000)
                        _uiState.update { state -> 
                            if (state.errorMessage == result.errorMessage) state.copy(errorMessage = null) else state
                        }
                    }
                }
            }
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
