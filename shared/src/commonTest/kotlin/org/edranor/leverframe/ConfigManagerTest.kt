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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigManagerTest {

    @Test
    fun testParsePrototypicalConfig() {
        // Given the default json string
        val defaultJson = ConfigManager.defaultPrototypicalConfigJson

        // When parsed
        val parsedTabs = ConfigManager.parseConfig(defaultJson)

        // Then
        assertEquals(2, parsedTabs.size, "Should parse two tabs")
        
        val northJunction = parsedTabs[0]
        assertEquals("North Junction", northJunction.first)
        assertEquals(9, northJunction.second.levers.size, "North Junction should have 9 levers")
        
        val upDistant = northJunction.second.levers[0]
        assertEquals(LeverType.DISTANT_SIGNAL, upDistant.type)
        assertEquals(1, upDistant.conditions.size)
        assertEquals(1, upDistant.conditions[0].targetIndex)
        assertTrue(upDistant.conditions[0].requiredState) // "REVERSED" translates to true
        
        val southBox = parsedTabs[1]
        assertEquals("South Box", southBox.first)
        assertEquals(4, southBox.second.levers.size, "South Box should have 4 levers")
    }

    @Test
    fun testParseInvalidTypeDefaultsToSpare() {
        val json = """
            {
                "tabs": [
                    {
                        "name": "Test",
                        "levers": [
                            {
                                "label": "Unknown",
                                "type": "INVALID_TYPE_BLAH",
                                "interlocking": []
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val parsed = ConfigManager.parseConfig(json)
        assertEquals(1, parsed.size)
        assertEquals(LeverType.SPARE, parsed[0].second.levers[0].type)
    }
}
