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
package org.edranor.leverframe
/** Contains the findings of a rule validation sweep. */
data class ValidationResult(
    val unreachableLevers: Map<Int, String> // Map of lever index to explanation
)

/**
 * Engine that performs static analysis on the interlocking logic graph.
 * Detects circular dependencies or impossible conditions that would permanently lock a lever.
 */
object RuleValidator {
    
    fun validate(tab: TabDef): ValidationResult {
        val numLevers = tab.levers.size
        if (numLevers == 0) return ValidationResult(emptyMap())

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
        
        val unreachable = mutableMapOf<Int, String>()
        for (i in 0 until numLevers) {
            if (i !in reversedLevers) {
                val explanation = analyzeContradiction(i, tab)
                unreachable[i] = explanation
            }
        }
        
        return ValidationResult(unreachable)
    }

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
            val blockStates = mutableMapOf<Int, Boolean>()
            for (b in blockList.indices) {
                blockStates[blockList[b]] = (assignment and (1 shl b)) != 0
            }
            
            var allPass = true
            for (i in leverStates.indices) {
                if (leverStates[i]) {
                    val logic = tab.levers[i].logic
                    if (logic != null) {
                        if (!evaluateNode(logic, leverStates, blockStates)) {
                            allPass = false
                            break
                        }
                    }
                }
            }
            if (allPass) return false // valid state found
        }
        return true
    }

    private fun collectBlocks(node: AstNode, blocks: MutableSet<Int>) {
        when (node) {
            is BlockStateNode -> blocks.add(node.blockIndex)
            is AndNode -> node.children.forEach { collectBlocks(it, blocks) }
            is OrNode -> node.children.forEach { collectBlocks(it, blocks) }
            is NotNode -> collectBlocks(node.child, blocks)
            is LeverStateNode -> {}
        }
    }

    private fun evaluateNode(node: AstNode, leverStates: List<Boolean>, blockStates: Map<Int, Boolean>): Boolean {
        return when (node) {
            is LeverStateNode -> leverStates.getOrElse(node.leverIndex) { false } == node.requiredReversed
            is BlockStateNode -> blockStates[node.blockIndex] == node.requiredOccupied
            is AndNode -> node.children.all { evaluateNode(it, leverStates, blockStates) }
            is OrNode -> node.children.isEmpty() || node.children.any { evaluateNode(it, leverStates, blockStates) }
            is NotNode -> !evaluateNode(node.child, leverStates, blockStates)
        }
    }

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

    private fun extractRequirements(
        node: AstNode, 
        sourceLever: Int, 
        requirements: MutableMap<Int, Pair<Boolean, Int>>, 
        blockRequirements: MutableMap<Int, Pair<Boolean, Int>>,
        stack: MutableList<Int>, 
        tab: TabDef
    ): String? {
        when (node) {
            is LeverStateNode -> {
                val reqState = node.requiredReversed
                val existing = requirements[node.leverIndex]
                if (existing != null && existing.first != reqState) {
                    val reqStateStr = if (reqState) "Reversed" else "Normal"
                    val existingStateStr = if (existing.first) "Reversed" else "Normal"
                    
                    val targetName = tab.levers[node.leverIndex].label.replace("\n", " ").trim()
                    val sourceName = tab.levers[sourceLever].label.replace("\n", " ").trim()
                    val existingSourceName = tab.levers[existing.second].label.replace("\n", " ").trim()
                    
                    if (sourceLever == existing.second) {
                        return "Contradiction: '$sourceName' requires lever '$targetName' to be both Normal and Reversed."
                    } else {
                        return "Contradiction: '$sourceName' requires lever '$targetName' to be $reqStateStr, but '$existingSourceName' requires it to be $existingStateStr."
                    }
                }
                if (existing == null) {
                    requirements[node.leverIndex] = Pair(reqState, sourceLever)
                    stack.add(node.leverIndex)
                }
            }
            is BlockStateNode -> {
                val reqState = node.requiredOccupied
                val existing = blockRequirements[node.blockIndex]
                if (existing != null && existing.first != reqState) {
                    val reqStateStr = if (reqState) "Occupied" else "Clear"
                    val existingStateStr = if (existing.first) "Occupied" else "Clear"
                    
                    val targetName = tab.blocks.getOrNull(node.blockIndex)?.label?.replace("\n", " ")?.trim() ?: "Block ${node.blockIndex + 1}"
                    val sourceName = tab.levers[sourceLever].label.replace("\n", " ").trim()
                    val existingSourceName = tab.levers[existing.second].label.replace("\n", " ").trim()
                    
                    if (sourceLever == existing.second) {
                        return "Contradiction: '$sourceName' requires block '$targetName' to be both Occupied and Clear."
                    } else {
                        return "Contradiction: '$sourceName' requires block '$targetName' to be $reqStateStr, but '$existingSourceName' requires it to be $existingStateStr."
                    }
                }
                if (existing == null) {
                    blockRequirements[node.blockIndex] = Pair(reqState, sourceLever)
                }
            }
            is AndNode -> {
                for (child in node.children) {
                    val contradiction = extractRequirements(child, sourceLever, requirements, blockRequirements, stack, tab)
                    if (contradiction != null) return contradiction
                }
            }
            else -> {}
        }
        return null
    }
}
