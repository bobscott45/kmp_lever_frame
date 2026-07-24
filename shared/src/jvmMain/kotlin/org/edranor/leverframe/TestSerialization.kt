package org.edranor.leverframe

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

fun main() {
    val node: AstNode = AndNode(listOf(
        LeverStateNode(1, true),
        BlockStateNode(2, false)
    ))
    val json = Json { encodeDefaults = true }
    val encoded = json.encodeToString(node)
    println("Encoded: $encoded")
    val decoded = json.decodeFromString<AstNode>(encoded)
    println("Decoded: $decoded")
}
