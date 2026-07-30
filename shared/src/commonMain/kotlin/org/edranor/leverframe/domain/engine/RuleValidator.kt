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
 * Analyzes lever interlocking rules using a graph and breadth-first search to detect 
 * circular dependencies or contradictory requirements that would result in a lever being permanently locked.
 */
package org.edranor.leverframe.domain.engine
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

/** Contains the findings of a rule validation sweep. */
data class ValidationResult(
    val unreachableLevers: Map<Int, String> // Map of lever index to explanation
)

/**
 * Engine that performs static analysis on the interlocking logic graph.
 * Detects circular dependencies or impossible conditions that would permanently lock a lever.
 */
object RuleValidator {
    
    /**
     * Performs static analysis on the layout's interlocking logic to detect permanently locked levers.
     *
     * @param tab The frame configuration containing lever and block definitions.
     * @return A [ValidationResult] containing any unreachable levers and the contradictions causing them.
     */
    fun validate(tab: TabDef): ValidationResult {
        val numLevers = tab.levers.size
        if (numLevers == 0) return ValidationResult(emptyMap())

        val reversedLevers = exploreReachableStates(tab, numLevers)
        val unreachableLevers = analyzeUnreachableLevers(tab, numLevers, reversedLevers)
        
        return ValidationResult(unreachableLevers)
    }

    /** Helper function: explorereachablestates */
    private fun exploreReachableStates(tab: TabDef, numLevers: Int): Set<Int> {
        val reachableStates = mutableSetOf<List<Boolean>>()
        val queue = ArrayDeque<List<Boolean>>()
        
        val startState = List(numLevers) { false }
        reachableStates.add(startState)
        queue.addLast(startState)
        
        val reversedLevers = mutableSetOf<Int>()
        
        while(queue.isNotEmpty()) {
            val state = queue.removeFirst()
            
            for (i in 0 until numLevers) {
                if (!state[i]) { // Try reversing it
                    val newState = state.toMutableList()
                    newState[i] = true
                    if (!checkConflict(tab, newState)) {
                        reversedLevers.add(i)
                        if (reachableStates.add(newState)) {
                            queue.addLast(newState)
                        }
                    }
                } else { // Try normalizing it
                    val newState = state.toMutableList()
                    newState[i] = false
                    if (!checkConflict(tab, newState)) {
                        if (reachableStates.add(newState)) {
                            queue.addLast(newState)
                        }
                    }
                }
            }
        }
        return reversedLevers
    }

    /** Helper function: analyzeunreachablelevers */
    private fun analyzeUnreachableLevers(tab: TabDef, numLevers: Int, reversedLevers: Set<Int>): Map<Int, String> {
        val unreachable = mutableMapOf<Int, String>()
        for (i in 0 until numLevers) {
            if (i !in reversedLevers) {
                val explanation = analyzeContradiction(i, tab)
                unreachable[i] = explanation
            }
        }
        return unreachable
    }

    /** Helper function: checkconflict */
    private fun checkConflict(tab: TabDef, leverStates: List<Boolean>): Boolean {
        val referencedBlocks = mutableSetOf<Int>()
        for (i in leverStates.indices) {
            if (leverStates[i]) {
                val logic = tab.levers[i].logic
                if (logic != null) {
                    collectBlocks(logic, referencedBlocks)
                }
            }
        }
        
        val blockList = referencedBlocks.toList()
        val numAssignments = 1 shl blockList.size
        
        for (assignment in 0 until numAssignments) {
            if (isValidStateFound(tab, leverStates, blockList, assignment)) {
                return false
            }
        }
        return true
    }
    
    /** Helper function: isvalidstatefound */
    private fun isValidStateFound(tab: TabDef, leverStates: List<Boolean>, blockList: List<Int>, assignment: Int): Boolean {
        val blockStates = mutableMapOf<Int, Boolean>()
        for (b in blockList.indices) {
            blockStates[blockList[b]] = (assignment and (1 shl b)) != 0
        }
        
        for (i in leverStates.indices) {
            if (leverStates[i]) {
                val logic = tab.levers[i].logic
                if (logic != null) {
                    if (!evaluateNode(logic, leverStates, blockStates)) {
                        return false
                    }
                }
            }
        }
        return true
    }

    /** Helper function: collectblocks */
    private fun collectBlocks(node: AstNode, blocks: MutableSet<Int>) {
        when (node) {
            is BlockStateNode -> blocks.add(node.blockIndex)
            is AndNode -> node.children.forEach { collectBlocks(it, blocks) }
            is OrNode -> node.children.forEach { collectBlocks(it, blocks) }
            is NotNode -> collectBlocks(node.child, blocks)
            is LeverStateNode -> {}
        }
    }

    /** Helper function: evaluatenode */
    private fun evaluateNode(node: AstNode, leverStates: List<Boolean>, blockStates: Map<Int, Boolean>): Boolean {
        return when (node) {
            is LeverStateNode -> leverStates.getOrElse(node.leverIndex) { false } == node.requiredReversed
            is BlockStateNode -> blockStates[node.blockIndex] == node.requiredOccupied
            is AndNode -> node.children.all { evaluateNode(it, leverStates, blockStates) }
            is OrNode -> node.children.isEmpty() || node.children.any { evaluateNode(it, leverStates, blockStates) }
            is NotNode -> !evaluateNode(node.child, leverStates, blockStates)
        }
    }

    /** Helper function: analyzecontradiction */
    private fun analyzeContradiction(targetLever: Int, tab: TabDef): String {
        val requirements = mutableMapOf<Int, Pair<Boolean, Int>>()
        val blockRequirements = mutableMapOf<Int, Pair<Boolean, Int>>()
        val stack = mutableListOf<Int>()
        
        requirements[targetLever] = Pair(true, targetLever)
        stack.add(targetLever)
        
        val processed = mutableSetOf<Int>()
        
        while(stack.isNotEmpty()) {
            val leverIndex = stack.removeLast()
            if (!processed.add(leverIndex)) continue
            
            val (requiredState, _) = requirements[leverIndex]!!
            
            if (requiredState) { // Only evaluate logic when lever is REVERSED
                val logic = tab.levers[leverIndex].logic
                if (logic != null) {
                    val contradiction = extractRequirements(logic, leverIndex, requirements, blockRequirements, stack, tab)
                    if (contradiction != null) {
                        return contradiction
                    }
                }
            }
        }
        
        return "Complex or cyclical interlocking rules prevent this lever from ever being pulled."
    }

    /** Helper function: extractrequirements */
    private fun extractRequirements(
        node: AstNode, 
        sourceLever: Int, 
        requirements: MutableMap<Int, Pair<Boolean, Int>>, 
        blockRequirements: MutableMap<Int, Pair<Boolean, Int>>,
        stack: MutableList<Int>, 
        tab: TabDef
    ): String? {
        return when (node) {
            is LeverStateNode -> evaluateLeverContradiction(node, sourceLever, requirements, stack, tab)
            is BlockStateNode -> evaluateBlockContradiction(node, sourceLever, blockRequirements, tab)
            is AndNode -> {
                node.children.firstNotNullOfOrNull { 
                    extractRequirements(it, sourceLever, requirements, blockRequirements, stack, tab) 
                }
            }
            else -> null
        }
    }
    
    /** Helper function: evaluatelevercontradiction */
    private fun evaluateLeverContradiction(
        node: LeverStateNode,
        sourceLever: Int,
        requirements: MutableMap<Int, Pair<Boolean, Int>>,
        stack: MutableList<Int>,
        tab: TabDef
    ): String? {
        val reqState = node.requiredReversed
        val existing = requirements[node.leverIndex]
        if (existing != null && existing.first != reqState) {
            return formatLeverContradictionMessage(reqState, existing, node, sourceLever, tab)
        }
        if (existing == null) {
            requirements[node.leverIndex] = Pair(reqState, sourceLever)
            stack.add(node.leverIndex)
        }
        return null
    }
    
    /** Helper function: evaluateblockcontradiction */
    private fun evaluateBlockContradiction(
        node: BlockStateNode,
        sourceLever: Int,
        blockRequirements: MutableMap<Int, Pair<Boolean, Int>>,
        tab: TabDef
    ): String? {
        val reqState = node.requiredOccupied
        val existing = blockRequirements[node.blockIndex]
        if (existing != null && existing.first != reqState) {
            return formatBlockContradictionMessage(reqState, existing, node, sourceLever, tab)
        }
        if (existing == null) {
            blockRequirements[node.blockIndex] = Pair(reqState, sourceLever)
        }
        return null
    }

    /** Helper function: formatlevercontradictionmessage */
    private fun formatLeverContradictionMessage(
        reqState: Boolean,
        existing: Pair<Boolean, Int>,
        node: LeverStateNode,
        sourceLever: Int,
        tab: TabDef
    ): String {
        val reqStateStr = if (reqState) "Reversed" else "Normal"
        val existingStateStr = if (existing.first) "Reversed" else "Normal"
        
        val targetName = tab.levers[node.leverIndex].label.replace("\n", " ").trim()
        val sourceName = tab.levers[sourceLever].label.replace("\n", " ").trim()
        val existingSourceName = tab.levers[existing.second].label.replace("\n", " ").trim()
        
        return if (sourceLever == existing.second) {
            "Contradiction: '$sourceName' requires lever '$targetName' to be both Normal and Reversed."
        } else {
            "Contradiction: '$sourceName' requires lever '$targetName' to be $reqStateStr, but '$existingSourceName' requires it to be $existingStateStr."
        }
    }

    /** Helper function: formatblockcontradictionmessage */
    private fun formatBlockContradictionMessage(
        reqState: Boolean,
        existing: Pair<Boolean, Int>,
        node: BlockStateNode,
        sourceLever: Int,
        tab: TabDef
    ): String {
        val reqStateStr = if (reqState) "Occupied" else "Clear"
        val existingStateStr = if (existing.first) "Occupied" else "Clear"
        
        val targetName = tab.blocks.getOrNull(node.blockIndex)?.label?.replace("\n", " ")?.trim() ?: "Block ${node.blockIndex + 1}"
        val sourceName = tab.levers[sourceLever].label.replace("\n", " ").trim()
        val existingSourceName = tab.levers[existing.second].label.replace("\n", " ").trim()
        
        return if (sourceLever == existing.second) {
            "Contradiction: '$sourceName' requires block '$targetName' to be both Occupied and Clear."
        } else {
            "Contradiction: '$sourceName' requires block '$targetName' to be $reqStateStr, but '$existingSourceName' requires it to be $existingStateStr."
        }
    }
}
