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

import org.edranor.leverframe.*

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
        
        val actualEntrancePos = findActualEntrancePos(entrancePos, map, levers, tabDef)
        
        val startElem = map[actualEntrancePos]
        if (startElem != null && startElem.type.name.contains("SIGNAL") && startElem.linkedLever >= 0) {
            cancelSignalLevers(startElem, levers, tabIndex, selectedTabIndex)
            cancelDependentSignals(startElem, actualEntrancePos, map, tabIndex, selectedTabIndex)
            restoreNormalLevers(tabIndex, selectedTabIndex)
            return NxRoutingResult.Cancelled
        }
        
        return NxRoutingResult.Error("No valid entrance signal found at this location")
    }

    /** Helper function: findactualentrancepos */
    private fun findActualEntrancePos(entrancePos: Pair<Int, Int>, map: Map<Pair<Int, Int>, SchematicElementDef>, levers: List<DomainLever>, tabDef: TabDef): Pair<Int, Int> {
        val clickedElem = map[entrancePos]
        val isClickedSignalReversed = isSignalReversed(clickedElem, levers)
        
        if (!isClickedSignalReversed) {
            val reversedSignals = tabDef.schematicElements.filter { isSignalReversed(it, levers) }
            val foundStart = traceReversedSignalsToEntrance(reversedSignals, entrancePos, map)
            if (foundStart != null) {
                return foundStart
            }
        }
        return entrancePos
    }

    /** Helper function: issignalreversed */
    private fun isSignalReversed(elem: SchematicElementDef?, levers: List<DomainLever>): Boolean {
        if (elem == null || !elem.type.name.contains("SIGNAL") || elem.linkedLever < 0) return false
        if (levers.getOrNull(elem.linkedLever)?.isReversed == true) return true
        if (elem.type.name.startsWith("BRACKET_SIGNAL") && elem.linkedLever2 >= 0) {
            if (levers.getOrNull(elem.linkedLever2)?.isReversed == true) return true
        }
        return false
    }

    /** Helper function: tracereversedsignalstoentrance */
    private fun traceReversedSignalsToEntrance(reversedSignals: List<SchematicElementDef>, entrancePos: Pair<Int, Int>, map: Map<Pair<Int, Int>, SchematicElementDef>): Pair<Int, Int>? {
        for (sig in reversedSignals) {
            var q = listOf(sig)
            val v = mutableSetOf(Pair(sig.x, sig.y))
            var reached = false
            while (q.isNotEmpty()) {
                val nq = mutableListOf<SchematicElementDef>()
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
                return Pair(sig.x, sig.y)
            }
        }
        return null
    }

    /** Helper function: cancelsignallevers */
    private fun cancelSignalLevers(startElem: SchematicElementDef, levers: List<DomainLever>, tabIndex: Int, selectedTabIndex: Int) {
        val isReversed1 = levers.getOrNull(startElem.linkedLever)?.isReversed == true
        val isReversed2 = if (startElem.type.name.startsWith("BRACKET_SIGNAL") && startElem.linkedLever2 >= 0) {
            levers.getOrNull(startElem.linkedLever2)?.isReversed == true
        } else false
        
        if (isReversed1) interlockingService.toggleLever(tabIndex, startElem.linkedLever, selectedTabIndex)
        if (isReversed2) interlockingService.toggleLever(tabIndex, startElem.linkedLever2, selectedTabIndex)
    }

    /** Helper function: canceldependentsignals */
    private fun cancelDependentSignals(startElem: SchematicElementDef, actualEntrancePos: Pair<Int, Int>, map: Map<Pair<Int, Int>, SchematicElementDef>, tabIndex: Int, selectedTabIndex: Int) {
        var currentQueue = listOf(startElem)
        val visited = mutableSetOf<Pair<Int, Int>>()
        visited.add(actualEntrancePos)
            
        while (currentQueue.isNotEmpty()) {
            val nextQueue = mutableListOf<SchematicElementDef>()
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
                            if (!neighborElem.type.name.contains("SIGNAL") || neighborElem.nxButton != NxButtonType.EXIT_ONLY) {
                                nextQueue.add(neighborElem)
                            }
                        }
                    }
                }
            }
            currentQueue = nextQueue
        }
    }

    /** Helper function: restorenormallevers */
    private fun restoreNormalLevers(tabIndex: Int, selectedTabIndex: Int) {
        val postSignalLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers
        if (postSignalLevers != null) {
            val isRestoring = { leverDef: LeverDef ->
                when (leverDef.restoreOverride) {
                    RestoreOverride.ALWAYS -> true
                    RestoreOverride.NEVER -> false
                    RestoreOverride.DEFAULT -> configService.configState.value.tabs.getOrNull(tabIndex)?.second?.defaultRestorePointsOnCancel == true
                }
            }
            
            val tabDef = configService.configState.value.tabs.getOrNull(tabIndex)?.second
            if (tabDef != null) {
                for (i in postSignalLevers.indices) {
                    if (postSignalLevers[i].isReversed) {
                        val ld = tabDef.levers.getOrNull(i)
                        if (ld != null && !ld.type.name.contains("SIGNAL") && isRestoring(ld)) {
                            interlockingService.toggleLever(tabIndex, i, selectedTabIndex)
                        }
                    }
                }
            }
        }
    }

    fun setNxRoute(tabIndex: Int, route: NxRoute, selectedTabIndex: Int): NxRoutingResult {
        val tabDef = configService.configState.value.tabs.getOrNull(tabIndex)?.second ?: return NxRoutingResult.Error("Configuration not found")
        val map = tabDef.schematicElements.associateBy { Pair(it.x, it.y) }
        val blocks = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.blocks ?: return NxRoutingResult.Error("State not found")
        
        val occupiedCells = findOccupiedCells(route, map, blocks)
        if (occupiedCells.isNotEmpty()) {
            return NxRoutingResult.Error("Cannot set route: Track circuit occupied", occupiedCells)
        }
        
        val requiredLeverStates = calculateRequiredTurnoutStates(route, map).toMutableMap()
        val primarySignals = identifyPrimarySignalLevers(route, map)
        val secondarySignals = identifySecondarySignalLevers(route, map, primarySignals)
        val allSignals = primarySignals + secondarySignals
        
        val levers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: return NxRoutingResult.Error("State not found")
        if (isAnySignalAlreadyCleared(primarySignals, allSignals, levers, map, route)) {
            return cancelNxRoute(tabIndex, route.pathCells.first(), selectedTabIndex)
        }
        
        resolveDependenciesFromAst(tabDef, allSignals, requiredLeverStates)
        
        return executeLeverSequence(tabIndex, selectedTabIndex, requiredLeverStates, allSignals, primarySignals, tabDef)
    }

    /** Helper function: findoccupiedcells */
    private fun findOccupiedCells(route: NxRoute, map: Map<Pair<Int, Int>, SchematicElementDef>, blocks: List<DomainBlock>): List<Pair<Int, Int>> {
        val occupiedCells = mutableListOf<Pair<Int, Int>>()
        for (pos in route.pathCells) {
            val elem = map[pos] ?: continue
            if (elem.linkedBlock >= 0 && blocks.getOrNull(elem.linkedBlock)?.isOccupied == true) {
                occupiedCells.add(pos)
            }
        }
        return occupiedCells
    }

    /** Helper function: calculaterequiredturnoutstates */
    private fun calculateRequiredTurnoutStates(route: NxRoute, map: Map<Pair<Int, Int>, SchematicElementDef>): Map<Int, Boolean> {
        val requiredLeverStates = mutableMapOf<Int, Boolean>()
        for (i in route.pathCells.indices) {
            val pos = route.pathCells[i]
            val elem = map[pos] ?: continue
            
            if (elem.type.name.startsWith("TURNOUT") && elem.linkedLever >= 0) {
                val prev = route.pathCells.getOrNull(i - 1)
                val next = route.pathCells.getOrNull(i + 1)
                val isDiverging = if (elem.type.name == "TURNOUT_LEFT") {
                    prev == Pair(pos.first + 1, pos.second - 1) || next == Pair(pos.first + 1, pos.second - 1)
                } else {
                    prev == Pair(pos.first + 1, pos.second + 1) || next == Pair(pos.first + 1, pos.second + 1)
                }
                requiredLeverStates[elem.linkedLever] = isDiverging
            }
        }
        return requiredLeverStates
    }

    /** Helper function: identifyprimarysignallevers */
    private fun identifyPrimarySignalLevers(route: NxRoute, map: Map<Pair<Int, Int>, SchematicElementDef>): List<Int> {
        val primarySignals = mutableListOf<Int>()
        for (i in route.pathCells.indices) {
            val pos = route.pathCells[i]
            val elem = map[pos] ?: continue
            
            if (elem.type.name.contains("SIGNAL")) {
                if (elem.type.name.startsWith("BRACKET_SIGNAL")) {
                    val nextTurnoutDiverging = isNextTurnoutDiverging(route, i, map)
                    val leverToPull = if (nextTurnoutDiverging) elem.linkedLever2 else elem.linkedLever
                    if (leverToPull >= 0) primarySignals.add(leverToPull)
                } else if (elem.linkedLever >= 0) {
                    primarySignals.add(elem.linkedLever)
                }
            }
        }
        return primarySignals
    }

    /** Helper function: isnextturnoutdiverging */
    private fun isNextTurnoutDiverging(route: NxRoute, startIndex: Int, map: Map<Pair<Int, Int>, SchematicElementDef>): Boolean {
        for (j in (startIndex + 1) until route.pathCells.size) {
            val aheadPos = route.pathCells[j]
            val aheadElem = map[aheadPos]
            if (aheadElem != null && aheadElem.type.name.startsWith("TURNOUT")) {
                val tPrev = route.pathCells.getOrNull(j - 1) ?: (if (startIndex >= 0) route.pathCells[startIndex] else null)
                val tNext = route.pathCells.getOrNull(j + 1)
                return if (aheadElem.type.name == "TURNOUT_LEFT") {
                    tPrev == Pair(aheadPos.first + 1, aheadPos.second - 1) || tNext == Pair(aheadPos.first + 1, aheadPos.second - 1)
                } else {
                    tPrev == Pair(aheadPos.first + 1, aheadPos.second + 1) || tNext == Pair(aheadPos.first + 1, aheadPos.second + 1)
                }
            }
        }
        return false
    }

    /** Helper function: identifysecondarysignallevers */
    private fun identifySecondarySignalLevers(route: NxRoute, map: Map<Pair<Int, Int>, SchematicElementDef>, primarySignals: List<Int>): List<Int> {
        val secondarySignals = mutableListOf<Int>()
        if (route.pathCells.size < 2) return secondarySignals
        
        val startPos = route.pathCells[0]
        val nextPos = route.pathCells[1]
        val startElem = map[startPos] ?: return secondarySignals
        
        val startNeighbors = NxRoutingEngine.getConnections(startElem, map)
        val behindPos = startNeighbors.find { it != nextPos } ?: return secondarySignals
        val behindElem = map[behindPos] ?: return secondarySignals
        
        if (behindElem.type.name.contains("SIGNAL")) {
            val leverToPull = if (behindElem.type.name.startsWith("BRACKET_SIGNAL")) {
                val nextTurnoutDiverging = isNextTurnoutDiverging(route, -1, map)
                if (nextTurnoutDiverging) behindElem.linkedLever2 else behindElem.linkedLever
            } else {
                behindElem.linkedLever
            }
            if (leverToPull >= 0 && !primarySignals.contains(leverToPull)) {
                secondarySignals.add(leverToPull)
            }
        }
        
        return secondarySignals
    }

    /** Helper function: isanysignalalreadycleared */
    private fun isAnySignalAlreadyCleared(primarySignals: List<Int>, allSignals: List<Int>, levers: List<DomainLever>, map: Map<Pair<Int, Int>, SchematicElementDef>, route: NxRoute): Boolean {
        var isPrimaryReversed = false
        for (leverIdx in primarySignals) {
            if (levers.getOrNull(leverIdx)?.isReversed == true) {
                isPrimaryReversed = true
                break
            }
        }
        if (isPrimaryReversed) return true
        
        for (pos in route.pathCells) {
            val elemCheck = map[pos]
            if (elemCheck != null && elemCheck.type.name.contains("SIGNAL") && elemCheck.linkedLever >= 0) {
                if (levers.getOrNull(elemCheck.linkedLever)?.isReversed == true) return true
                if (elemCheck.type.name.startsWith("BRACKET_SIGNAL") && elemCheck.linkedLever2 >= 0) {
                    if (levers.getOrNull(elemCheck.linkedLever2)?.isReversed == true) return true
                }
            }
        }
        return false
    }

    /** Helper function: resolvedependenciesfromast */
    private fun resolveDependenciesFromAst(tabDef: TabDef, allSignals: List<Int>, requiredLeverStates: MutableMap<Int, Boolean>) {
        for (signalLeverIdx in allSignals) {
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
    }

    /** Helper function: executeleversequence */
    private fun executeLeverSequence(
        tabIndex: Int, 
        selectedTabIndex: Int, 
        requiredLeverStates: Map<Int, Boolean>, 
        allSignalLevers: List<Int>, 
        primarySignalLevers: List<Int>, 
        tabDef: TabDef
    ): NxRoutingResult {
        val fplLevers = requiredLeverStates.keys.filter { val t = tabDef.levers.getOrNull(it)?.type?.name; t == "FACING_POINTS" || t == "BROWN" }
        val pointLevers = requiredLeverStates.keys.filter { tabDef.levers.getOrNull(it)?.type?.name == "POINTS" }
        val otherLevers = requiredLeverStates.keys.filter { !fplLevers.contains(it) && !pointLevers.contains(it) }
        
        unplungeFacingPoints(fplLevers, tabIndex, selectedTabIndex)
        throwPoints(pointLevers, requiredLeverStates, tabIndex, selectedTabIndex)
        replungeFacingPoints(fplLevers, requiredLeverStates, tabIndex, selectedTabIndex)
        throwOtherLevers(otherLevers, requiredLeverStates, tabIndex, selectedTabIndex)
        
        return clearSignals(allSignalLevers, primarySignalLevers, tabIndex, selectedTabIndex, tabDef)
    }
    
    /** Helper function: unplungefacingpoints */
    private fun unplungeFacingPoints(fplLevers: List<Int>, tabIndex: Int, selectedTabIndex: Int) {
        for (fplIdx in fplLevers) {
            val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            if (freshLevers[fplIdx].isReversed) {
                interlockingService.toggleLever(tabIndex, fplIdx, selectedTabIndex)
            }
        }
    }
    
    /** Helper function: throwpoints */
    private fun throwPoints(pointLevers: List<Int>, requiredLeverStates: Map<Int, Boolean>, tabIndex: Int, selectedTabIndex: Int) {
        for (pointIdx in pointLevers) {
            val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            val targetReversed = requiredLeverStates[pointIdx] ?: false
            if (freshLevers[pointIdx].isReversed != targetReversed) {
                interlockingService.toggleLever(tabIndex, pointIdx, selectedTabIndex)
            }
        }
    }
    
    /** Helper function: replungefacingpoints */
    private fun replungeFacingPoints(fplLevers: List<Int>, requiredLeverStates: Map<Int, Boolean>, tabIndex: Int, selectedTabIndex: Int) {
        for (fplIdx in fplLevers) {
            val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            val targetReversed = requiredLeverStates[fplIdx] ?: false
            if (freshLevers[fplIdx].isReversed != targetReversed) {
                interlockingService.toggleLever(tabIndex, fplIdx, selectedTabIndex)
            }
        }
    }
    
    /** Helper function: throwotherlevers */
    private fun throwOtherLevers(otherLevers: List<Int>, requiredLeverStates: Map<Int, Boolean>, tabIndex: Int, selectedTabIndex: Int) {
        for (otherIdx in otherLevers) {
            val freshLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            val targetReversed = requiredLeverStates[otherIdx] ?: false
            if (freshLevers[otherIdx].isReversed != targetReversed) {
                interlockingService.toggleLever(tabIndex, otherIdx, selectedTabIndex)
            }
        }
    }
    
    /** Helper function: clearsignals */
    private fun clearSignals(
        allSignalLevers: List<Int>, 
        primarySignalLevers: List<Int>, 
        tabIndex: Int, 
        selectedTabIndex: Int, 
        tabDef: TabDef
    ): NxRoutingResult {
        var madeProgress = true
        var loops = 0
        var lastErrorMsg: String? = null
        
        while (madeProgress && loops < 5) {
            madeProgress = false
            val currentLevers = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers ?: break
            for (signalLeverIdx in allSignalLevers) {
                if (!currentLevers[signalLeverIdx].isReversed) {
                    val result = interlockingService.toggleLever(tabIndex, signalLeverIdx, selectedTabIndex)
                    if (result.didChange) {
                        madeProgress = true
                        if (primarySignalLevers.contains(signalLeverIdx)) {
                            lastErrorMsg = null
                        }
                    } else if (result.errorMessage != null && primarySignalLevers.contains(signalLeverIdx)) {
                        lastErrorMsg = result.errorMessage
                    }
                }
            }
            loops++
        }
        
        val anyPrimarySignalFailed = primarySignalLevers.any { idx ->
            val leverDef = tabDef.levers.getOrNull(idx)
            val isReversed = interlockingService.domainState.value.frames.getOrNull(tabIndex)?.levers?.getOrNull(idx)?.isReversed == true
            if (leverDef?.type?.name == "DISTANT_SIGNAL") false else !isReversed
        }
        
        if (anyPrimarySignalFailed) {
            val msg = lastErrorMsg ?: "Interlocking rejected route"
            return NxRoutingResult.Error(msg, emptyList())
        }
        
        return NxRoutingResult.Success
    }
}
