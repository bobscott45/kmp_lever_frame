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
 * Provides extension and helper functions for safely mutating the application's configuration state.
 * Contains utilities to strip UI-specific data or safely rewrite indices in the 
 * AST when levers or blocks are added, swapped, or deleted in the schematic editor.
 */
package org.edranor.leverframe.config
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

/**
 * Creates a copy of the configuration with all lever rules and logic stripped out.
 * Useful for exporting a bare-bones configuration or resetting interlocking logic.
 *
 * @return A new [JsonConfig] instance with empty rules.
 */
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

/**
 * Creates a minimal copy of the configuration by removing UI layout, rules, and LCC events.
 * Returns a sterile structure primarily useful as a blank slate for generating new node configurations.
 *
 * @return A minimized [JsonConfig] instance.
 */
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
        ui_scale = 0.0f,
        landscape_schematic_position = org.edranor.leverframe.domain.engine.LandscapeSchematicPosition.SIDE_BY_SIDE,
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
                    lever.copy(
                        interlocking = emptyList(), 
                        ast_logic = null, 
                        auto_reverser = false,
                        lcc_event_normal = "",
                        lcc_event_reversed = "",
                        lcc_enabled = false
                    )
                },
                blocks = tab.blocks.map { block ->
                    block.copy(lcc_event_occupied = "", lcc_event_empty = "", mode = org.edranor.leverframe.domain.engine.BlockMode.LOCAL_ONLY)
                }
            )
        }
    )
}

// AST Index mutators
/**
 * Traverses the AST and updates lever indices to account for a lever being deleted from the frame.
 * If a node directly references the deleted index, it (and potentially its parents) may be invalidated and return null.
 *
 * @param deletedIndex The index of the lever that was removed.
 * @return The updated [AstNode] or null if the logic becomes invalid.
 */
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

/**
 * Traverses the AST and updates block indices to account for a block being deleted from the frame.
 * If a node directly references the deleted index, it may return null to invalidate the rule.
 *
 * @param deletedIndex The index of the block that was removed.
 * @return The updated [AstNode] or null if the logic becomes invalid.
 */
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

/**
 * Traverses the AST and swaps occurrences of two lever indices.
 * Used to keep logic intact when the user reorders levers in the editor.
 *
 * @param indexA The first lever index.
 * @param indexB The second lever index.
 * @return The newly updated [AstNode] with swapped indices.
 */
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

/**
 * Traverses the AST and swaps occurrences of two block indices.
 * Used to keep logic intact when the user reorders blocks in the editor.
 *
 * @param indexA The first block index.
 * @param indexB The second block index.
 * @return The newly updated [AstNode] with swapped indices.
 */
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


/**
 * Safely swaps two blocks in the tab configuration.
 * Automatically cascades the index swap to all schematic elements, legacy interlocking rules, and AST nodes.
 *
 * @param tab The tab configuration containing the blocks.
 * @param indexA The first block index to swap.
 * @param indexB The second block index to swap.
 * @return A new [JsonTab] with blocks, schematic links, and logic correctly updated.
 */
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
            if (rule.target_type == org.edranor.leverframe.domain.engine.TargetType.BLOCK) {
                if (rule.target == indexA) newRule = newRule.copy(target = indexB)
                else if (rule.target == indexB) newRule = newRule.copy(target = indexA)
            }
            if (rule.alt_target_type == org.edranor.leverframe.domain.engine.TargetType.BLOCK) {
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

/**
 * Safely swaps two levers in the tab configuration.
 * Automatically cascades the index swap to all schematic elements, legacy interlocking rules, and AST nodes.
 *
 * @param tab The tab configuration containing the levers.
 * @param indexA The first lever index to swap.
 * @param indexB The second lever index to swap.
 * @return A new [JsonTab] with levers, schematic links, and logic correctly updated.
 */
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
            if (rule.target_type == org.edranor.leverframe.domain.engine.TargetType.LEVER) {
                if (rule.target == indexA) newRule = newRule.copy(target = indexB)
                else if (rule.target == indexB) newRule = newRule.copy(target = indexA)
            }
            if (rule.alt_target_type == org.edranor.leverframe.domain.engine.TargetType.LEVER) {
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

/**
 * Safely deletes a block from the tab configuration and shifts subsequent block indices down.
 * Updates all schematic elements and removes or updates any rules referencing the block.
 *
 * @param tab The tab configuration to modify.
 * @param index The index of the block to delete.
 * @return A new [JsonTab] reflecting the deletion.
 */
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
            if (rule.target_type == org.edranor.leverframe.domain.engine.TargetType.BLOCK) {
                if (rule.target == index) return@mapNotNull null
                else if (rule.target > index) newRule = newRule.copy(target = rule.target - 1)
            }
            if (rule.alt_target_type == org.edranor.leverframe.domain.engine.TargetType.BLOCK) {
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

/**
 * Safely deletes a lever from the tab configuration and shifts subsequent lever indices down.
 * Updates all schematic elements and removes or updates any rules referencing the lever.
 *
 * @param tab The tab configuration to modify.
 * @param index The index of the lever to delete.
 * @return A new [JsonTab] reflecting the deletion.
 */
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
            if (rule.target_type == org.edranor.leverframe.domain.engine.TargetType.LEVER) {
                if (rule.target == index) return@mapNotNull null
                else if (rule.target > index) newRule = newRule.copy(target = rule.target - 1)
            }
            if (rule.alt_target_type == org.edranor.leverframe.domain.engine.TargetType.LEVER) {
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

/**
 * Generates an initialism short code from a full label string.
 * Used as a default short code when creating new blocks.
 *
 * @param label The full text label.
 * @return A capitalized short code (e.g., "Down Main" -> "DM").
 */
fun generateShortCode(label: String): String {
    return label.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { it.take(1).uppercase() }
}
