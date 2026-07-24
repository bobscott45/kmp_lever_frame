package org.edranor.leverframe

// This file contains business logic for safely mutating configuration state
// extracted from the UI layer.

fun JsonConfig.withoutRules(): JsonConfig {
    return this.copy(
        tabs = this.tabs.map { tab ->
            tab.copy(
                levers = tab.levers.map { lever ->
                    lever.copy(interlocking = emptyList(), ast_logic = null, auto_reverser = false)
                }
            )
        }
    )
}

fun JsonConfig.withoutUiAndRules(): JsonConfig {
    return this.copy(
        jmri_hub_ip = "",
        node_id = "",
        node_name = "",
        conflict_policy = 1,
        display_sleep_timeout_ms = 0,
        restore_last_state = false,
        lcc_enabled = false,
        lcc_master = false,
        enable_sound = false,
        rule_editor_mode = "",
        rule_display_mode = "",
        tabs = this.tabs.map { tab ->
            tab.copy(
                show_lever_numbers = true,
                show_block_numbers = true,
                use_short_codes = false,
                use_short_codes_in_indicators = false,
                schematic_grid_size = 40,
                label_lines = 2,
                label_line_height = 18,
                block_layout = "HORIZONTAL",
                block_label_size = 8,
                levers = tab.levers.map { lever ->
                    lever.copy(interlocking = emptyList(), ast_logic = null, auto_reverser = false)
                }
            )
        }
    )
}

// AST Index mutators
fun AstNode.updateLeverIndicesForDelete(deletedIndex: Int): AstNode? {
    return when (this) {
        is LeverStateNode -> {
            if (this.leverIndex == deletedIndex) null // Rule invalid
            else if (this.leverIndex > deletedIndex) this.copy(leverIndex = this.leverIndex - 1)
            else this
        }
        is BlockStateNode -> this
        is AndNode -> {
            val newChildren = this.children.mapNotNull { it.updateLeverIndicesForDelete(deletedIndex) }
            if (newChildren.isEmpty()) null else this.copy(children = newChildren)
        }
        is OrNode -> {
            val newChildren = this.children.mapNotNull { it.updateLeverIndicesForDelete(deletedIndex) }
            if (newChildren.isEmpty()) null else this.copy(children = newChildren)
        }
        is NotNode -> {
            val newChild = this.child.updateLeverIndicesForDelete(deletedIndex)
            if (newChild == null) null else this.copy(child = newChild)
        }
    }
}

fun AstNode.updateBlockIndicesForDelete(deletedIndex: Int): AstNode? {
    return when (this) {
        is BlockStateNode -> {
            if (this.blockIndex == deletedIndex) null // Rule invalid
            else if (this.blockIndex > deletedIndex) this.copy(blockIndex = this.blockIndex - 1)
            else this
        }
        is LeverStateNode -> this
        is AndNode -> {
            val newChildren = this.children.mapNotNull { it.updateBlockIndicesForDelete(deletedIndex) }
            if (newChildren.isEmpty()) null else this.copy(children = newChildren)
        }
        is OrNode -> {
            val newChildren = this.children.mapNotNull { it.updateBlockIndicesForDelete(deletedIndex) }
            if (newChildren.isEmpty()) null else this.copy(children = newChildren)
        }
        is NotNode -> {
            val newChild = this.child.updateBlockIndicesForDelete(deletedIndex)
            if (newChild == null) null else this.copy(child = newChild)
        }
    }
}

fun AstNode.updateLeverIndicesForSwap(indexA: Int, indexB: Int): AstNode {
    return when (this) {
        is LeverStateNode -> {
            if (this.leverIndex == indexA) this.copy(leverIndex = indexB)
            else if (this.leverIndex == indexB) this.copy(leverIndex = indexA)
            else this
        }
        is BlockStateNode -> this
        is AndNode -> this.copy(children = this.children.map { it.updateLeverIndicesForSwap(indexA, indexB) })
        is OrNode -> this.copy(children = this.children.map { it.updateLeverIndicesForSwap(indexA, indexB) })
        is NotNode -> this.copy(child = this.child.updateLeverIndicesForSwap(indexA, indexB))
    }
}

fun AstNode.updateBlockIndicesForSwap(indexA: Int, indexB: Int): AstNode {
    return when (this) {
        is BlockStateNode -> {
            if (this.blockIndex == indexA) this.copy(blockIndex = indexB)
            else if (this.blockIndex == indexB) this.copy(blockIndex = indexA)
            else this
        }
        is LeverStateNode -> this
        is AndNode -> this.copy(children = this.children.map { it.updateBlockIndicesForSwap(indexA, indexB) })
        is OrNode -> this.copy(children = this.children.map { it.updateBlockIndicesForSwap(indexA, indexB) })
        is NotNode -> this.copy(child = this.child.updateBlockIndicesForSwap(indexA, indexB))
    }
}


fun swapBlocksSafe(tab: JsonTab, indexA: Int, indexB: Int): JsonTab {
    val newBlocks = tab.blocks.toMutableList()
    val temp = newBlocks[indexA]
    newBlocks[indexA] = newBlocks[indexB]
    newBlocks[indexB] = temp

    val newSchematicElements = tab.schematic_elements.map { elem ->
        var newElem = elem
        if (elem.linked_block == indexA) newElem = newElem.copy(linked_block = indexB)
        else if (elem.linked_block == indexB) newElem = newElem.copy(linked_block = indexA)
        newElem
    }

    val newLevers = tab.levers.map { lever ->
        val newRules = lever.interlocking.map { rule ->
            var newRule = rule
            if (rule.target_type == "BLOCK") {
                if (rule.target == indexA) newRule = newRule.copy(target = indexB)
                else if (rule.target == indexB) newRule = newRule.copy(target = indexA)
            }
            if (rule.alt_target_type == "BLOCK") {
                if (rule.alt_target == indexA) newRule = newRule.copy(alt_target = indexB)
                else if (rule.alt_target == indexB) newRule = newRule.copy(alt_target = indexA)
            }
            newRule
        }
        val newAst = lever.ast_logic?.updateBlockIndicesForSwap(indexA, indexB)
        lever.copy(interlocking = newRules, ast_logic = newAst)
    }

    return tab.copy(blocks = newBlocks, schematic_elements = newSchematicElements, levers = newLevers)
}

fun swapLeversSafe(tab: JsonTab, indexA: Int, indexB: Int): JsonTab {
    val newLevers = tab.levers.toMutableList()
    val temp = newLevers[indexA]
    newLevers[indexA] = newLevers[indexB]
    newLevers[indexB] = temp

    val newSchematicElements = tab.schematic_elements.map { elem ->
        var newElem = elem
        if (elem.linked_lever == indexA) newElem = newElem.copy(linked_lever = indexB)
        else if (elem.linked_lever == indexB) newElem = newElem.copy(linked_lever = indexA)

        if (elem.linked_lever_2 == indexA) newElem = newElem.copy(linked_lever_2 = indexB)
        else if (elem.linked_lever_2 == indexB) newElem = newElem.copy(linked_lever_2 = indexA)
        newElem
    }

    val newLeversMapped = newLevers.map { lever ->
        val newRules = lever.interlocking.map { rule ->
            var newRule = rule
            if (rule.target_type == "LEVER") {
                if (rule.target == indexA) newRule = newRule.copy(target = indexB)
                else if (rule.target == indexB) newRule = newRule.copy(target = indexA)
            }
            if (rule.alt_target_type == "LEVER") {
                if (rule.alt_target == indexA) newRule = newRule.copy(alt_target = indexB)
                else if (rule.alt_target == indexB) newRule = newRule.copy(alt_target = indexA)
            }
            newRule
        }
        val newAst = lever.ast_logic?.updateLeverIndicesForSwap(indexA, indexB)
        lever.copy(interlocking = newRules, ast_logic = newAst)
    }

    return tab.copy(levers = newLeversMapped, schematic_elements = newSchematicElements)
}

fun deleteBlockSafe(tab: JsonTab, index: Int): JsonTab {
    val newBlocks = tab.blocks.toMutableList()
    newBlocks.removeAt(index)
    
    val newSchematicElements = tab.schematic_elements.map { elem ->
        var newElem = elem
        if (elem.linked_block == index) newElem = newElem.copy(linked_block = -1)
        else if (elem.linked_block > index) newElem = newElem.copy(linked_block = elem.linked_block - 1)
        newElem
    }

    val newLevers = tab.levers.map { lever ->
        val newRules = lever.interlocking.mapNotNull { rule ->
            var newRule = rule
            if (rule.target_type == "BLOCK") {
                if (rule.target == index) return@mapNotNull null
                else if (rule.target > index) newRule = newRule.copy(target = rule.target - 1)
            }
            if (rule.alt_target_type == "BLOCK") {
                if (rule.alt_target == index) newRule = newRule.copy(alt_target = -1)
                else if (rule.alt_target > index) newRule = newRule.copy(alt_target = rule.alt_target - 1)
            }
            newRule
        }
        val newAst = lever.ast_logic?.updateBlockIndicesForDelete(index)
        lever.copy(interlocking = newRules, ast_logic = newAst)
    }
    return tab.copy(blocks = newBlocks, schematic_elements = newSchematicElements, levers = newLevers)
}

fun deleteLeverSafe(tab: JsonTab, index: Int): JsonTab {
    val newLevers = tab.levers.toMutableList()
    newLevers.removeAt(index)
    
    val newSchematicElements = tab.schematic_elements.map { elem ->
        var newElem = elem
        if (elem.linked_lever == index) newElem = newElem.copy(linked_lever = -1)
        else if (elem.linked_lever > index) newElem = newElem.copy(linked_lever = elem.linked_lever - 1)

        if (elem.linked_lever_2 == index) newElem = newElem.copy(linked_lever_2 = -1)
        else if (elem.linked_lever_2 > index) newElem = newElem.copy(linked_lever_2 = elem.linked_lever_2 - 1)
        newElem
    }

    val newLeversMapped = newLevers.map { lever ->
        val newRules = lever.interlocking.mapNotNull { rule ->
            var newRule = rule
            if (rule.target_type == "LEVER") {
                if (rule.target == index) return@mapNotNull null
                else if (rule.target > index) newRule = newRule.copy(target = rule.target - 1)
            }
            if (rule.alt_target_type == "LEVER") {
                if (rule.alt_target == index) newRule = newRule.copy(alt_target = -1)
                else if (rule.alt_target > index) newRule = newRule.copy(alt_target = rule.alt_target - 1)
            }
            newRule
        }
        val newAst = lever.ast_logic?.updateLeverIndicesForDelete(index)
        lever.copy(interlocking = newRules, ast_logic = newAst)
    }
    return tab.copy(levers = newLeversMapped, schematic_elements = newSchematicElements)
}

fun generateShortCode(label: String): String {
    return label.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { it.take(1).uppercase() }
}
