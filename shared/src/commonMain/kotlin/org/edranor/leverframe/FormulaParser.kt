package org.edranor.leverframe

/**
 * A service that parses boolean expression strings into [AstNode] trees.
 * Valid syntax examples:
 * - "L1:N" -> Lever 1 Normal
 * - "B2:O" -> Block 2 Occupied
 * - "L1:N AND B2:O"
 * - "(L1:N OR B2:O) AND NOT L3:R"
 */
object FormulaParser {

    /**
     * Parses a formula string. Returns null if the string is empty or invalid.
     */
    fun parse(formula: String): AstNode? {
        val trimmed = formula.trim()
        if (trimmed.isEmpty()) return null
        
        return try {
            val tokens = tokenize(trimmed)
            val (node, remaining) = parseExpression(tokens)
            if (remaining.isNotEmpty()) {
                null // Syntax error: leftover tokens
            } else {
                node
            }
        } catch (e: Exception) {
            null // Syntax error during parsing
        }
    }

    private fun tokenize(input: String): List<String> {
        val regex = Regex("""\(|\)|\bAND\b|\bOR\b|\bNOT\b|L\d+:[NR]|B\d+:[OE]""")
        // We should also ensure that the entire string is matched, or at least that there are no un-tokenized non-whitespace characters.
        // Actually, a safer way to tokenize is to just split by whitespace and parentheses, then map.
        // But for now, we can check if the matched tokens length (plus spaces) equals the string length.
        // Or simply throw if replacing all matches with "" leaves anything other than whitespace.
        val replaced = regex.replace(input.uppercase(), "")
        if (replaced.trim().isNotEmpty()) {
            throw IllegalArgumentException("Invalid characters in expression")
        }
        
        return regex.findAll(input.uppercase()).map { it.value }.toList()
    }

    private fun parseExpression(tokens: List<String>): Pair<AstNode, List<String>> {
        var (node, remaining) = parseTerm(tokens)
        
        while (remaining.isNotEmpty() && remaining[0] == "OR") {
            val (rightNode, nextRemaining) = parseTerm(remaining.drop(1))
            // Collapse nested ORs
            val newChildren = mutableListOf<AstNode>()
            if (node is OrNode) newChildren.addAll(node.children) else newChildren.add(node)
            if (rightNode is OrNode) newChildren.addAll(rightNode.children) else newChildren.add(rightNode)
            
            node = OrNode(newChildren)
            remaining = nextRemaining
        }
        
        return Pair(node, remaining)
    }

    private fun parseTerm(tokens: List<String>): Pair<AstNode, List<String>> {
        var (node, remaining) = parseFactor(tokens)
        
        while (remaining.isNotEmpty() && remaining[0] == "AND") {
            val (rightNode, nextRemaining) = parseFactor(remaining.drop(1))
            // Collapse nested ANDs
            val newChildren = mutableListOf<AstNode>()
            if (node is AndNode) newChildren.addAll(node.children) else newChildren.add(node)
            if (rightNode is AndNode) newChildren.addAll(rightNode.children) else newChildren.add(rightNode)
            
            node = AndNode(newChildren)
            remaining = nextRemaining
        }
        
        return Pair(node, remaining)
    }

    private fun parseFactor(tokens: List<String>): Pair<AstNode, List<String>> {
        if (tokens.isEmpty()) throw IllegalArgumentException("Unexpected end of expression")
        
        val token = tokens[0]
        
        if (token == "NOT") {
            val (child, remaining) = parseFactor(tokens.drop(1))
            return Pair(NotNode(child), remaining)
        }
        
        if (token == "(") {
            val (node, remaining) = parseExpression(tokens.drop(1))
            if (remaining.isEmpty() || remaining[0] != ")") {
                throw IllegalArgumentException("Missing closing parenthesis")
            }
            return Pair(node, remaining.drop(1))
        }
        
        // Base case: L<num>:<state> or B<num>:<state>
        if (token.startsWith("L")) {
            val parts = token.substring(1).split(":")
            if (parts.size != 2) throw IllegalArgumentException("Invalid lever token: $token")
            val index = (parts[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid lever index")) - 1
            val isReversed = parts[1] == "R"
            return Pair(LeverStateNode(index, isReversed), tokens.drop(1))
        }
        
        if (token.startsWith("B")) {
            val parts = token.substring(1).split(":")
            if (parts.size != 2) throw IllegalArgumentException("Invalid block token: $token")
            val index = (parts[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid block index")) - 1
            val isOccupied = parts[1] == "O"
            return Pair(BlockStateNode(index, isOccupied), tokens.drop(1))
        }
        
        throw IllegalArgumentException("Unexpected token: $token")
    }
}
