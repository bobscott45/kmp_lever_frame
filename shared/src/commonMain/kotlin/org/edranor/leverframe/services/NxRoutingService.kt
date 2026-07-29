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
 * Evaluates eNtrance-eXit (NX) routes on the schematic, finding safe paths
 * and cascading lever movements to establish or cancel valid track routes.
 */
package org.edranor.leverframe.services

import org.edranor.leverframe.NxRoute
import org.edranor.leverframe.NxRoutingEngine
import org.edranor.leverframe.toAstNode

/** Result sum type for a requested NX route operation. */
sealed class NxRoutingResult {
    object Success : NxRoutingResult()
    object Cancelled : NxRoutingResult()
    data class Error(val message: String, val errorCells: List<Pair<Int, Int>> = emptyList()) : NxRoutingResult()
}

/**
 * Stateful service that orchestrates NX (eNtrance-eXit) routing operations.
 * Communicates with the core InterlockingService to flip required turnouts, plunge FPLs, 
 * and clear signals along the computed safe track path.
 */
class NxRoutingService(
    private val configService: ConfigurationService,
    private val interlockingService: InterlockingService
) {

    fun cancelNxRoute(tabIndex: Int, entrancePos: Pair<Int, Int>, selectedTabIndex: Int): NxRoutingResult {
        val tabDef = configService.configState.value.tabs.getOrNull(tabIndex)?.second ?: return NxRoutingResult.Error("Configuration not found")
        val map = tabDef.schematicElements.associateBy { Pair(it.x, it.y) }
        
        val levers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: return NxRoutingResult.Error("State not found")
        
        var actualEntrancePos = entrancePos
        val clickedElem = map[entrancePos]
        
        val isClickedSignalReversed = clickedElem != null && clickedElem.type.name.contains("SIGNAL") && clickedElem.linkedLever >= 0 && (
            levers.getOrNull(clickedElem.linkedLever)?.isReversed == true || 
            (clickedElem.type.name.startsWith("BRACKET_SIGNAL") && clickedElem.linkedLever2 >= 0 && levers.getOrNull(clickedElem.linkedLever2)?.isReversed == true)
        )
        

        
        if (!isClickedSignalReversed) {
            val reversedSignals = tabDef.schematicElements.filter { it.type.name.contains("SIGNAL") && it.linkedLever >= 0 && (
                levers.getOrNull(it.linkedLever)?.isReversed == true ||
                (it.type.name.startsWith("BRACKET_SIGNAL") && it.linkedLever2 >= 0 && levers.getOrNull(it.linkedLever2)?.isReversed == true)
            ) }
            
            var foundStart: Pair<Int, Int>? = null
            for (sig in reversedSignals) {
                var q = listOf(sig)
                val v = mutableSetOf(Pair(sig.x, sig.y))
                var reached = false
                while (q.isNotEmpty()) {
                    val nq = mutableListOf<org.edranor.leverframe.SchematicElementDef>()
                    for (e in q) {
                        if (e.x == entrancePos.first && e.y == entrancePos.second) {
                            reached = true
                            break
                        }
                        val conns = NxRoutingEngine.getConnections(e, map)
                        for (c in conns) {
                            if (v.add(c)) {
                                map[c]?.let { nq.add(it) }
                            }
                        }
                    }
                    if (reached) break
                    q = nq
                }
                if (reached) {
                    foundStart = Pair(sig.x, sig.y)
                    break
                }
            }
            if (foundStart != null) {
                actualEntrancePos = foundStart
            }
        }
        
        val startElem = map[actualEntrancePos]
        if (startElem != null && startElem.type.name.contains("SIGNAL") && startElem.linkedLever >= 0) {
            val isReversed1 = levers.getOrNull(startElem.linkedLever)?.isReversed == true
            val isReversed2 = if (startElem.type.name.startsWith("BRACKET_SIGNAL") && startElem.linkedLever2 >= 0) {
                levers.getOrNull(startElem.linkedLever2)?.isReversed == true
            } else false
            
            if (isReversed1) interlockingService.toggleLever(tabIndex, startElem.linkedLever, selectedTabIndex)
            if (isReversed2) interlockingService.toggleLever(tabIndex, startElem.linkedLever2, selectedTabIndex)
            
            var currentQueue = listOf(startElem)
            val visited = mutableSetOf<Pair<Int, Int>>()
            visited.add(actualEntrancePos)
                
                while (currentQueue.isNotEmpty()) {
                    val nextQueue = mutableListOf<org.edranor.leverframe.SchematicElementDef>()
                    for (elem in currentQueue) {
                        val neighbors = NxRoutingEngine.getConnections(elem, map)
                        for (n in neighbors) {
                            if (!visited.contains(n)) {
                                visited.add(n)
                                val neighborElem = map[n]
                                if (neighborElem != null) {
                                    if (neighborElem.type.name.contains("SIGNAL") && neighborElem.linkedLever >= 0) {
                                        val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: continue
                                        if (freshLevers.getOrNull(neighborElem.linkedLever)?.isReversed == true) {
                                            interlockingService.toggleLever(tabIndex, neighborElem.linkedLever, selectedTabIndex)
                                        }
                                        if (neighborElem.type.name.startsWith("BRACKET_SIGNAL") && neighborElem.linkedLever2 >= 0) {
                                            if (freshLevers.getOrNull(neighborElem.linkedLever2)?.isReversed == true) {
                                                interlockingService.toggleLever(tabIndex, neighborElem.linkedLever2, selectedTabIndex)
                                            }
                                        }
                                    }
                                    if (!neighborElem.type.name.contains("SIGNAL") || neighborElem.nxButton != org.edranor.leverframe.NxButtonType.EXIT_ONLY) {
                                        nextQueue.add(neighborElem)
                                    }
                                }
                            }
                        }
                    }
                    currentQueue = nextQueue
                }
                
                // Optional enhancement: Attempt to restore specific FPLs and Points to Normal.
                // This simulates specific local rulebook instructions (e.g. trap points).
                // It will gracefully fail and leave them Reversed if they are locked by another active route.
                val postSignalLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers
                if (postSignalLevers != null) {
                    val isRestoring = { leverDef: org.edranor.leverframe.LeverDef ->
                        when (leverDef.restoreOverride) {
                            org.edranor.leverframe.RestoreOverride.ALWAYS -> true
                            org.edranor.leverframe.RestoreOverride.NEVER -> false
                            org.edranor.leverframe.RestoreOverride.DEFAULT -> tabDef.defaultRestorePointsOnCancel
                        }
                    }
                    val fplLevers = tabDef.levers.indices.filter { (tabDef.levers[it].type.name == "FACING_POINTS" || tabDef.levers[it].type.name == "BROWN") && isRestoring(tabDef.levers[it]) }
                    val pointLevers = tabDef.levers.indices.filter { tabDef.levers[it].type.name == "POINTS" && isRestoring(tabDef.levers[it]) }
                    
                    // Unplunge specific FPLs
                    for (fplIdx in fplLevers) {
                        val currentLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
                        if (currentLevers[fplIdx].isReversed) {
                            interlockingService.toggleLever(tabIndex, fplIdx, selectedTabIndex)
                        }
                    }
                    
                    // Normalize specific Points
                    for (pointIdx in pointLevers) {
                        val currentLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
                        if (currentLevers[pointIdx].isReversed) {
                            interlockingService.toggleLever(tabIndex, pointIdx, selectedTabIndex)
                        }
                    }
                }
        }
        return NxRoutingResult.Success
    }

    fun setNxRoute(tabIndex: Int, route: NxRoute, selectedTabIndex: Int): NxRoutingResult {
        val tabDef = configService.configState.value.tabs.getOrNull(tabIndex)?.second ?: return NxRoutingResult.Error("Configuration not found")
        val map = tabDef.schematicElements.associateBy { Pair(it.x, it.y) }
        

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
            
            if (elem.type.name.startsWith("TURNOUT")) {
                if (elem.linkedLever >= 0) {
                    val prev = route.pathCells.getOrNull(i - 1)
                    val next = route.pathCells.getOrNull(i + 1)
                    val isDiverging = if (elem.type.name == "TURNOUT_LEFT") {
                        prev == Pair(pos.first + 1, pos.second - 1) || next == Pair(pos.first + 1, pos.second - 1)
                    } else { // TURNOUT_RIGHT
                        prev == Pair(pos.first + 1, pos.second + 1) || next == Pair(pos.first + 1, pos.second + 1)
                    }
                    requiredLeverStates[elem.linkedLever] = isDiverging
                }
            } else if (elem.type.name.contains("SIGNAL")) {
                if (elem.type.name.startsWith("BRACKET_SIGNAL")) {
                    var nextTurnoutDiverging = false
                    for (j in (i + 1) until route.pathCells.size) {
                        val aheadPos = route.pathCells[j]
                        val aheadElem = map[aheadPos]
                        if (aheadElem != null && aheadElem.type.name.startsWith("TURNOUT")) {
                            val tPrev = route.pathCells.getOrNull(j - 1)
                            val tNext = route.pathCells.getOrNull(j + 1)
                            nextTurnoutDiverging = if (aheadElem.type.name == "TURNOUT_LEFT") {
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
                    if (behindElem != null && behindElem.type.name.contains("SIGNAL")) {
                        val leverToPull = if (behindElem.type.name.startsWith("BRACKET_SIGNAL")) {
                            var nextTurnoutDiverging = false
                            for (j in 0 until route.pathCells.size) {
                                val aheadPos = route.pathCells[j]
                                val aheadElem = map[aheadPos]
                                if (aheadElem != null && aheadElem.type.name.startsWith("TURNOUT")) {
                                    val tPrev = route.pathCells.getOrNull(j - 1) ?: behindPos
                                    val tNext = route.pathCells.getOrNull(j + 1)
                                    nextTurnoutDiverging = if (aheadElem.type.name == "TURNOUT_LEFT") {
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
        
        val levers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: return NxRoutingResult.Error("State not found")
        
        var isPrimaryReversed = false
        for (leverIdx in primarySignalLeversToPull) {
            if (levers.getOrNull(leverIdx)?.isReversed == true) {
                isPrimaryReversed = true
                break
            }
        }
        
        if (isPrimaryReversed) {
            return cancelNxRoute(tabIndex, route.pathCells.first(), selectedTabIndex)
        }
        
        var isAnySignalReversed = false
        for (pos in route.pathCells) {
            val elemCheck = map[pos]
            if (elemCheck != null && elemCheck.type.name.contains("SIGNAL") && elemCheck.linkedLever >= 0) {
                if (levers.getOrNull(elemCheck.linkedLever)?.isReversed == true) {
                    isAnySignalReversed = true
                    break
                }
                if (elemCheck.type.name.startsWith("BRACKET_SIGNAL") && elemCheck.linkedLever2 >= 0) {
                    if (levers.getOrNull(elemCheck.linkedLever2)?.isReversed == true) {
                        isAnySignalReversed = true
                        break
                    }
                }
            }
        }
        
        if (isAnySignalReversed) {
            cancelNxRoute(tabIndex, route.pathCells.first(), selectedTabIndex)
        }
        
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
        
        // Simulate the correct signalman sequence: Unplunge FPLs -> Move Points -> Replunge FPLs
        val fplLevers = requiredLeverStates.keys.filter { val t = tabDef.levers.getOrNull(it)?.type?.name; t == "FACING_POINTS" || t == "BROWN" }
        val pointLevers = requiredLeverStates.keys.filter { tabDef.levers.getOrNull(it)?.type?.name == "POINTS" }
        val otherLevers = requiredLeverStates.keys.filter { !fplLevers.contains(it) && !pointLevers.contains(it) }
        
        // 1) Unplunge all FPLs (move to Normal)
        for (fplIdx in fplLevers) {
            val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            if (freshLevers[fplIdx].isReversed) {
                interlockingService.toggleLever(tabIndex, fplIdx, selectedTabIndex)
            }
        }
        
        // 2) Move all Points to their target states
        for (pointIdx in pointLevers) {
            val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            val targetReversed = requiredLeverStates[pointIdx] ?: false
            if (freshLevers[pointIdx].isReversed != targetReversed) {
                interlockingService.toggleLever(tabIndex, pointIdx, selectedTabIndex)
            }
        }
        
        // 3) Move all FPLs to their target states (usually Reversed)
        for (fplIdx in fplLevers) {
            val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            val targetReversed = requiredLeverStates[fplIdx] ?: false
            if (freshLevers[fplIdx].isReversed != targetReversed) {
                interlockingService.toggleLever(tabIndex, fplIdx, selectedTabIndex)
            }
        }
        
        // 4) Execute any other non-signal levers
        for (otherIdx in otherLevers) {
            val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            val targetReversed = requiredLeverStates[otherIdx] ?: false
            if (freshLevers[otherIdx].isReversed != targetReversed) {
                interlockingService.toggleLever(tabIndex, otherIdx, selectedTabIndex)
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
            val leverDef = tabDef.levers.getOrNull(idx)
            val isReversed = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers?.getOrNull(idx)?.isReversed == true
            // If a Distant signal fails to clear (e.g. because a diverging route is set), this is perfectly normal and shouldn't fail the route.
            if (leverDef?.type?.name == "DISTANT_SIGNAL") false else !isReversed
        }
        
        if (anyPrimarySignalFailed) {
            val msg = lastErrorMsg ?: "Interlocking rejected route"
            return NxRoutingResult.Error(msg, emptyList())
        }
        
        return NxRoutingResult.Success
    }
}
