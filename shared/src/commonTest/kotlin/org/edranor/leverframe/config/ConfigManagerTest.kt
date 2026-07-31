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
 * Unit tests verifying the JSON configuration loading, parsing, and migration logic.
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
        assertEquals(1, upDistant.conditions[0].targetIndex)
        assertTrue(upDistant.conditions[0].requiredState) // "REVERSED" translates to true
        
        val southBox = parsedTabs[1]
        assertEquals("South Box", southBox.first)
        assertEquals(6, southBox.second.levers.size, "South Box should have 6 levers")
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

    @Test
    fun testLccEventIdsAreProperlyPrefixed() {
        val json = """
            {
                "node_id": "05.01.01.01.03.01",
                "tabs": [
                    {
                        "name": "Test",
                        "levers": [
                            {
                                "label": "L1",
                                "type": "SPARE",
                                "lcc_event_normal": "11.01",
                                "lcc_event_reversed": "05.01.01.01.03.01.11.02"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val parsed = ConfigManager.parseConfig(json)
        val lever = parsed[0].second.levers[0]
        
        // Both the short suffix and the fully-qualified event ID in the JSON 
        // should end up correctly prefixed in the parsed LeverDef.
        assertEquals("05.01.01.01.03.01.11.01", lever.lcc_event_normal)
        assertEquals("05.01.01.01.03.01.11.02", lever.lcc_event_reversed)
    }

    @Test
    fun testLandscapeSchematicPositionSerializationAndFallback() {
        val json = """
            {
                "landscape_schematic_position": "TOP"
            }
        """.trimIndent()
        val config = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(json)
        assertEquals(LandscapeSchematicPosition.TOP, config.landscape_schematic_position)

        val invalidJson = """
            {
                "landscape_schematic_position": "UNKNOWN_POS"
            }
        """.trimIndent()
        val configFallback = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(invalidJson)
        assertEquals(LandscapeSchematicPosition.SIDE_BY_SIDE, configFallback.landscape_schematic_position)
    }

    @Test
    fun testLandscapeSchematicPositionIsExcludedFromHardwareResetCheck() {
        val initial = JsonConfig(landscape_schematic_position = LandscapeSchematicPosition.SIDE_BY_SIDE)
        val modified = initial.copy(landscape_schematic_position = LandscapeSchematicPosition.TOP)

        assertEquals(
            initial.withoutUiAndRules(),
            modified.withoutUiAndRules(),
            "Changing landscape_schematic_position should be treated as a silent UI update"
        )
    }
}
