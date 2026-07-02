/*
 * Copyright (C) 2026 Robert Scott
 *
 * This file is part of LeverFrame.
 *
 * LeverFrame is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LeverFrame is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LeverFrame.  If not, see <https://www.gnu.org/licenses/>.
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
        assertEquals(8, northJunction.second.levers.size, "North Junction should have 8 levers")
        
        val upDistant = northJunction.second.levers[0]
        assertEquals(LeverType.DISTANT_SIGNAL, upDistant.type)
        assertEquals(1, upDistant.conditions.size)
        assertEquals(1, upDistant.conditions[0].targetLeverIndex)
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
