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
package org.edranor.leverframe

fun main() {
    val testConfig = JsonConfig(
        node_id = "05.01.01.01.03.01",
        tabs = listOf(
            JsonTab(
                name = "Test",
                levers = listOf(
                    JsonLever(
                        lcc_event_normal = "05.01.01.01.03.01.00.01"
                    )
                )
            )
        )
    )
    
    val str = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), testConfig)
    println("SERIALIZED: $str")
    val parsed = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(str)
    println("PARSED: $parsed")
}
