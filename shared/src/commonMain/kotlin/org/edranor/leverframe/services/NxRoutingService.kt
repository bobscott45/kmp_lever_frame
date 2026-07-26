package org.edranor.leverframe.services

import org.edranor.leverframe.NxRoute
import org.edranor.leverframe.NxRoutingEngine
import org.edranor.leverframe.toAstNode

sealed class NxRoutingResult {
    object Success : NxRoutingResult()
    object Cancelled : NxRoutingResult()
    data class Error(val message: String, val errorCells: List<Pair<Int, Int>> = emptyList()) : NxRoutingResult()
}

class NxRoutingService(
    private val configService: ConfigurationService,
    private val interlockingService: InterlockingService
) {

    fun cancelNxRoute(tabIndex: Int, entrancePos: Pair<Int, Int>, selectedTabIndex: Int): NxRoutingResult {
        val tabDef = configService.configState.value.tabs.getOrNull(tabIndex)?.second ?: return NxRoutingResult.Error("Configuration not found")
        val map = tabDef.schematicElements.associateBy { Pair(it.x, it.y) }
        
        val startElem = map[entrancePos]
        if (startElem != null && startElem.type.contains("SIGNAL") && startElem.linkedLever >= 0) {
            val levers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: return NxRoutingResult.Error("State not found")
            val isReversed = levers.getOrNull(startElem.linkedLever)?.isReversed == true
            if (isReversed) {
                interlockingService.toggleLever(tabIndex, startElem.linkedLever, selectedTabIndex)
                
                var currentQueue = listOf(startElem)
                val visited = mutableSetOf<Pair<Int, Int>>()
                visited.add(entrancePos)
                
                while (currentQueue.isNotEmpty()) {
                    val nextQueue = mutableListOf<org.edranor.leverframe.SchematicElementDef>()
                    for (elem in currentQueue) {
                        val neighbors = NxRoutingEngine.getConnections(elem, map)
                        for (n in neighbors) {
                            if (!visited.contains(n)) {
                                visited.add(n)
                                val neighborElem = map[n]
                                if (neighborElem != null) {
                                    if (neighborElem.type.contains("SIGNAL") && neighborElem.linkedLever >= 0) {
                                        val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: continue
                                        if (freshLevers.getOrNull(neighborElem.linkedLever)?.isReversed == true) {
                                            interlockingService.toggleLever(tabIndex, neighborElem.linkedLever, selectedTabIndex)
                                        }
                                        if (neighborElem.type.startsWith("BRACKET_SIGNAL") && neighborElem.linkedLever2 >= 0) {
                                            if (freshLevers.getOrNull(neighborElem.linkedLever2)?.isReversed == true) {
                                                interlockingService.toggleLever(tabIndex, neighborElem.linkedLever2, selectedTabIndex)
                                            }
                                        }
                                    }
                                    if (!neighborElem.type.contains("SIGNAL") || neighborElem.nxButton != org.edranor.leverframe.NxButtonType.EXIT_ONLY) {
                                        nextQueue.add(neighborElem)
                                    }
                                }
                            }
                        }
                    }
                    currentQueue = nextQueue
                }
            }
        }
        return NxRoutingResult.Success
    }

    fun setNxRoute(tabIndex: Int, route: NxRoute, selectedTabIndex: Int): NxRoutingResult {
        val tabDef = configService.configState.value.tabs.getOrNull(tabIndex)?.second ?: return NxRoutingResult.Error("Configuration not found")
        val map = tabDef.schematicElements.associateBy { Pair(it.x, it.y) }
        
        val startElemCheck = map[route.pathCells.firstOrNull()]
        if (startElemCheck != null && startElemCheck.type.contains("SIGNAL") && startElemCheck.linkedLever >= 0) {
            val levers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: return NxRoutingResult.Error("State not found")
            val isReversed = levers.getOrNull(startElemCheck.linkedLever)?.isReversed == true
            if (isReversed) {
                return cancelNxRoute(tabIndex, route.pathCells.first(), selectedTabIndex)
            }
        }
        
        val requiredLeverStates = mutableMapOf<Int, Boolean>()
        val primarySignalLeversToPull = mutableListOf<Int>()
        val secondarySignalLeversToPull = mutableListOf<Int>()
        
        val blocks = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.blocks ?: return NxRoutingResult.Error("State not found")
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
                        primarySignalLeversToPull.add(leverToPull)
                    }
                } else {
                    if (elem.linkedLever >= 0) {
                        primarySignalLeversToPull.add(elem.linkedLever)
                    }
                }
            }
        }
        
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
                        if (leverToPull >= 0 && !primarySignalLeversToPull.contains(leverToPull)) {
                            secondarySignalLeversToPull.add(leverToPull)
                        }
                    }
                }
            }
        }
        
        val allSignalLeversToPull = primarySignalLeversToPull + secondarySignalLeversToPull
        
        if (isRouteOccupied) {
            return NxRoutingResult.Error("Cannot set route: Track circuit occupied", occupiedCells)
        }
        
        for (signalLeverIdx in allSignalLeversToPull) {
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
        
        for ((leverIdx, targetReversed) in requiredLeverStates) {
            val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            if (freshLevers[leverIdx].isReversed != targetReversed) {
                interlockingService.toggleLever(tabIndex, leverIdx, selectedTabIndex)
            }
        }
        
        var madeProgress = true
        var loops = 0
        var lastErrorMsg: String? = null
        
        while (madeProgress && loops < 5) {
            madeProgress = false
            val currentLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            for (signalLeverIdx in allSignalLeversToPull) {
                if (!currentLevers[signalLeverIdx].isReversed) {
                    val result = interlockingService.toggleLever(tabIndex, signalLeverIdx, selectedTabIndex)
                    if (result.didChange) {
                        madeProgress = true
                        if (primarySignalLeversToPull.contains(signalLeverIdx)) {
                            lastErrorMsg = null
                        }
                    } else if (result.errorMessage != null && primarySignalLeversToPull.contains(signalLeverIdx)) {
                        lastErrorMsg = result.errorMessage
                    }
                }
            }
            loops++
        }
        
        val anyPrimarySignalFailed = primarySignalLeversToPull.any { idx ->
            interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers?.getOrNull(idx)?.isReversed == false
        }
        
        if (anyPrimarySignalFailed) {
            val msg = lastErrorMsg ?: "Interlocking rejected route"
            return NxRoutingResult.Error(msg, emptyList())
        }
        
        return NxRoutingResult.Success
    }
}
