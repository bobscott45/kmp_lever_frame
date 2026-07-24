package org.edranor.leverframe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FormulaParserTest {

    @Test
    fun testParseEmptyString() {
        assertNull(FormulaParser.parse(""))
        assertNull(FormulaParser.parse("   "))
    }

    @Test
    fun testParseSimpleLeverNode() {
        val result = FormulaParser.parse("L1:N")
        assertNotNull(result)
        assertTrue(result is LeverStateNode)
        assertEquals(0, result.leverIndex)
        assertEquals(false, result.requiredReversed)

        val result2 = FormulaParser.parse("L5:R")
        assertNotNull(result2)
        assertTrue(result2 is LeverStateNode)
        assertEquals(4, result2.leverIndex)
        assertEquals(true, result2.requiredReversed)
    }

    @Test
    fun testParseSimpleBlockNode() {
        val result = FormulaParser.parse("B1:E")
        assertNotNull(result)
        assertTrue(result is BlockStateNode)
        assertEquals(0, result.blockIndex)
        assertEquals(false, result.requiredOccupied)

        val result2 = FormulaParser.parse("B3:O")
        assertNotNull(result2)
        assertTrue(result2 is BlockStateNode)
        assertEquals(2, result2.blockIndex)
        assertEquals(true, result2.requiredOccupied)
    }

    @Test
    fun testParseAndNode() {
        val result = FormulaParser.parse("L1:N AND B2:O")
        assertNotNull(result)
        assertTrue(result is AndNode)
        assertEquals(2, result.children.size)
        
        val left = result.children[0] as LeverStateNode
        assertEquals(0, left.leverIndex)
        assertEquals(false, left.requiredReversed)

        val right = result.children[1] as BlockStateNode
        assertEquals(1, right.blockIndex)
        assertEquals(true, right.requiredOccupied)
    }

    @Test
    fun testParseOrNode() {
        val result = FormulaParser.parse("L1:N OR B2:O")
        assertNotNull(result)
        assertTrue(result is OrNode)
        assertEquals(2, result.children.size)
    }

    @Test
    fun testParseNotNode() {
        val result = FormulaParser.parse("NOT L2:R")
        assertNotNull(result)
        assertTrue(result is NotNode)
        assertTrue(result.child is LeverStateNode)
        val child = result.child as LeverStateNode
        assertEquals(1, child.leverIndex)
        assertEquals(true, child.requiredReversed)
    }

    @Test
    fun testParseComplexExpression() {
        // (L1:N OR B2:O) AND NOT L3:R
        val result = FormulaParser.parse("(L1:N OR B2:O) AND NOT L3:R")
        assertNotNull(result)
        assertTrue(result is AndNode)
        assertEquals(2, result.children.size)

        val left = result.children[0]
        assertTrue(left is OrNode)
        assertEquals(2, left.children.size)
        assertTrue(left.children[0] is LeverStateNode)
        assertTrue(left.children[1] is BlockStateNode)

        val right = result.children[1]
        assertTrue(right is NotNode)
        assertTrue(right.child is LeverStateNode)
        assertEquals(2, (right.child as LeverStateNode).leverIndex)
    }
    
    @Test
    fun testParseInvalidSyntax() {
        // Missing parenthesis
        assertNull(FormulaParser.parse("(L1:N OR B2:O"))
        // Invalid token
        assertNull(FormulaParser.parse("L1:N XOR B2:O"))
        // Malformed token
        assertNull(FormulaParser.parse("L1:X"))
    }
    
    @Test
    fun testSerialization() {
        val node: AstNode = AndNode(listOf(
            LeverStateNode(1, true),
            BlockStateNode(2, false)
        ))
        
        val json = kotlinx.serialization.json.Json { 
            ignoreUnknownKeys = true 
            isLenient = true
            encodeDefaults = true
        }
        
        val encoded = json.encodeToString(AstNode.serializer(), node)
        val decoded = json.decodeFromString(AstNode.serializer(), encoded)
        
        assertEquals(node, decoded)
        
        // Test within JsonConfig
        val lever = JsonLever(ast_logic = node)
        val config = JsonConfig(tabs = listOf(JsonTab("Test", levers = listOf(lever))))
        
        val configEncoded = json.encodeToString(JsonConfig.serializer(), config)
        val configDecoded = json.decodeFromString(JsonConfig.serializer(), configEncoded)
        
        assertEquals(config.tabs[0].levers[0].ast_logic, configDecoded.tabs[0].levers[0].ast_logic)
    }
}
