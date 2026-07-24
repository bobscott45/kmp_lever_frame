package org.edranor.leverframe

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

data class AstEvaluationResult(val isSatisfied: Boolean, val involvedLevers: Set<Int> = emptySet())

@Serializable
sealed class AstNode {
    abstract fun toFormulaString(): String
    abstract fun evaluate(levers: List<DomainLever>, blocks: List<DomainBlock>): AstEvaluationResult
    abstract fun collectAllLevers(): Set<Int>
}

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

@Serializable
@SerialName("BLOCK")
data class BlockStateNode(val blockIndex: Int, val requiredOccupied: Boolean) : AstNode() {
    override fun toFormulaString(): String = "B${blockIndex + 1}:${if(requiredOccupied) "O" else "E"}"
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
        val mainNode = if (condition.target_type == "BLOCK") {
            BlockStateNode(condition.target, condition.state == "OCCUPIED")
        } else {
            LeverStateNode(condition.target, condition.state == "REVERSED")
        }

        if (condition.alt_target != -1) {
            val altNode = if (condition.alt_target_type == "BLOCK") {
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

