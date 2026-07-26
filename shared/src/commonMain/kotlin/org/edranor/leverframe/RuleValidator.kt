package org.edranor.leverframe

data class ValidationResult(
    val unreachableLevers: Map<Int, String> // Map of lever index to explanation
)

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
        for (i in leverStates.indices) {
            if (leverStates[i]) {
                val logic = tab.levers[i].logic
                if (logic != null) {
                    if (!evaluateNode(logic, leverStates)) return true // conflict found
                }
            }
        }
        return false
    }

    private fun evaluateNode(node: AstNode, leverStates: List<Boolean>): Boolean {
        return when (node) {
            is LeverStateNode -> leverStates.getOrElse(node.leverIndex) { false } == node.requiredReversed
            is BlockStateNode -> true // Assume blocks can always be set appropriately by the user
            is AndNode -> node.children.all { evaluateNode(it, leverStates) }
            is OrNode -> node.children.isEmpty() || node.children.any { evaluateNode(it, leverStates) }
            is NotNode -> !evaluateNode(node.child, leverStates)
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
