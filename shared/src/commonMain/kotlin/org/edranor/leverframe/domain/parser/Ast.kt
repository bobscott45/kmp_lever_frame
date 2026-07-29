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
 * Defines the Abstract Syntax Tree (AST) used to represent complex interlocking rules.
 * Supports logical AND, OR, and NOT operations combining Lever and Block states, replacing 
 * the legacy flat-list condition structure with fully evaluatable logic nodes.
 */
package org.edranor.leverframe.domain.parser
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Result of an AST evaluation, indicating if the conditions are met and which levers are involved in any failures. */
data class AstEvaluationResult(val isSatisfied: Boolean, val involvedLevers: Set<Int> = emptySet())

/**
 * Base class for all nodes in the Interlocking Abstract Syntax Tree.
 * Subclasses define specific logical operations or state checks.
 */
@Serializable
sealed class AstNode {
    abstract fun toFormulaString(): String
    abstract fun evaluate(levers: List<DomainLever>, blocks: List<DomainBlock>): AstEvaluationResult
    abstract fun collectAllLevers(): Set<Int>
}

/** Logical AND node that requires all of its child conditions to be satisfied. */
@Serializable
@SerialName("AND")
data class AndNode(val children: List<AstNode> = emptyList()) : AstNode() {
    override fun toFormulaString(): String = if (children.isEmpty()) "" else "(" + children.joinToString(" AND ") { it.toFormulaString() } + ")"
    override fun evaluate(levers: List<DomainLever>, blocks: List<DomainBlock>): AstEvaluationResult {
        val results = children.map { it.evaluate(levers, blocks) }
        val isSatisfied = results.all { it.isSatisfied }
        return if (isSatisfied) {
            AstEvaluationResult(true)
        } else {
            val involved = results.filter { !it.isSatisfied }.flatMap { it.involvedLevers }.toSet()
            AstEvaluationResult(false, involved)
        }
    }
    override fun collectAllLevers(): Set<Int> = children.flatMap { it.collectAllLevers() }.toSet()
}

/** Logical OR node that requires at least one of its child conditions to be satisfied. */
@Serializable
@SerialName("OR")
data class OrNode(val children: List<AstNode> = emptyList()) : AstNode() {
    override fun toFormulaString(): String = if (children.isEmpty()) "" else "(" + children.joinToString(" OR ") { it.toFormulaString() } + ")"
    override fun evaluate(levers: List<DomainLever>, blocks: List<DomainBlock>): AstEvaluationResult {
        if (children.isEmpty()) return AstEvaluationResult(true)
        val results = children.map { it.evaluate(levers, blocks) }
        val isSatisfied = results.any { it.isSatisfied }
        return if (isSatisfied) {
            AstEvaluationResult(true)
        } else {
            val involved = results.flatMap { it.involvedLevers }.toSet()
            AstEvaluationResult(false, involved)
        }
    }
    override fun collectAllLevers(): Set<Int> = children.flatMap { it.collectAllLevers() }.toSet()
}

/** Logical NOT node that inverts the evaluation result of its single child condition. */
@Serializable
@SerialName("NOT")
data class NotNode(val child: AstNode) : AstNode() {
    override fun toFormulaString(): String = "NOT " + child.toFormulaString()
    override fun evaluate(levers: List<DomainLever>, blocks: List<DomainBlock>): AstEvaluationResult {
        val childResult = child.evaluate(levers, blocks)
        return if (!childResult.isSatisfied) {
            AstEvaluationResult(true)
        } else {
            AstEvaluationResult(false, child.collectAllLevers())
        }
    }
    override fun collectAllLevers(): Set<Int> = child.collectAllLevers()
}

/** Leaf node that checks if a specific lever is in the required state (Reversed or Normal). */
@Serializable
@SerialName("LEVER")
data class LeverStateNode(val leverIndex: Int, val requiredReversed: Boolean) : AstNode() {
    override fun toFormulaString(): String = "L${leverIndex + 1}:${if(requiredReversed) "R" else "N"}"
    override fun evaluate(levers: List<DomainLever>, blocks: List<DomainBlock>): AstEvaluationResult {
        val isRev = levers.getOrNull(leverIndex)?.isReversed ?: false
        val isSatisfied = isRev == requiredReversed
        return if (isSatisfied) {
            AstEvaluationResult(true)
        } else {
            AstEvaluationResult(false, setOf(leverIndex))
        }
    }
    override fun collectAllLevers(): Set<Int> = setOf(leverIndex)
}

/** Leaf node that checks if a specific track block is in the required state (Occupied or Clear). */
@Serializable
@SerialName("BLOCK")
data class BlockStateNode(val blockIndex: Int, val requiredOccupied: Boolean) : AstNode() {
    override fun toFormulaString(): String = "B${blockIndex + 1}:${if(requiredOccupied) "O" else "C"}"
    override fun evaluate(levers: List<DomainLever>, blocks: List<DomainBlock>): AstEvaluationResult {
        val isOcc = blocks.getOrNull(blockIndex)?.isOccupied ?: false
        return AstEvaluationResult(isOcc == requiredOccupied)
    }
    override fun collectAllLevers(): Set<Int> = emptySet()
}

/**
 * Utility to convert the old flat [InterlockingCondition] structure into the new AST format.
 */
fun List<InterlockingCondition>.toAstNode(): AstNode? {
    if (this.isEmpty()) return null
    
    val conditionsAst = this.map { condition ->
        val mainNode = if (condition.targetType == TargetType.BLOCK) {
            BlockStateNode(condition.targetIndex, condition.requiredState)
        } else {
            LeverStateNode(condition.targetIndex, condition.requiredState)
        }

        if (condition.altTargetIndex != -1) {
            val altNode = if (condition.altTargetType == TargetType.BLOCK) {
                BlockStateNode(condition.altTargetIndex, condition.altRequiredState)
            } else {
                LeverStateNode(condition.altTargetIndex, condition.altRequiredState)
            }
            OrNode(listOf(mainNode, altNode))
        } else {
            mainNode
        }
    }

    return if (conditionsAst.size == 1) {
        conditionsAst.first()
    } else {
        AndNode(conditionsAst)
    }
}

/**
 * Utility to convert the old flat [JsonInterlocking] structure from the config JSON into the new AST format for the UI.
 */
fun migrateJsonInterlockingToAst(list: List<JsonInterlocking>): AstNode? {
    if (list.isEmpty()) return null
    
    val conditionsAst = list.map { condition ->
        val mainNode = if (condition.target_type == TargetType.BLOCK) {
            BlockStateNode(condition.target, condition.state == "OCCUPIED")
        } else {
            LeverStateNode(condition.target, condition.state == "REVERSED")
        }

        if (condition.alt_target != -1) {
            val altNode = if (condition.alt_target_type == TargetType.BLOCK) {
                BlockStateNode(condition.alt_target, condition.alt_state == "OCCUPIED")
            } else {
                LeverStateNode(condition.alt_target, condition.alt_state == "REVERSED")
            }
            OrNode(listOf(mainNode, altNode))
        } else {
            mainNode
        }
    }

    return if (conditionsAst.size == 1) {
        conditionsAst.first()
    } else {
        AndNode(conditionsAst)
    }
}

