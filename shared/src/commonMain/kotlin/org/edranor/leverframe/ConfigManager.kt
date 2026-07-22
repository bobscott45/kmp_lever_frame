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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

interface AppConfigRepository {
    var currentConfig: JsonConfig
    suspend fun initConfig()
    fun toJsonString(): String
    fun parseConfig(jsonString: String): List<Pair<String, TabDef>>
    suspend fun loadSavedLeverStates(): List<BooleanArray>?
    suspend fun saveCurrentLeverStates(states: List<BooleanArray>)
    suspend fun saveConfig(newConfig: JsonConfig)
}

@Serializable
data class JsonConfig(
    val node_id: String = "05.01.01.01.03.01",
    val node_name: String = "Kotlin Lever Frame",
    val jmri_hub_ip: String = "",
    val wifi_ssid: String = "",
    val wifi_password: String = "",
    val wifi_station_password: String = "",
    val conflict_policy: Int = 2,
    val display_sleep_timeout_ms: Int = 60000,
    val restore_last_state: Boolean = true,
    val lcc_master: Boolean = true,
    val enable_sound: Boolean = true,
    val tabs: List<JsonTab> = emptyList()
)

@Serializable
data class JsonTab(
    val name: String,
    val label_lines: Int = 2,
    val label_line_height: Int = 18,
    val block_layout: String = "HORIZONTAL",
    val block_label_size: Int = 8,
    val show_lever_numbers: Boolean = true,
    val show_block_numbers: Boolean = false,
    val use_short_codes: Boolean = false,
    val use_short_codes_in_indicators: Boolean = false,
    val schematic_grid_size: Int = 40,
    val levers: List<JsonLever> = emptyList(),
    val blocks: List<JsonBlock> = emptyList(),
    val schematic_elements: List<JsonSchematicElement> = emptyList()
)

@Serializable
data class JsonSchematicElement(
    val type: String,
    val x: Int,
    val y: Int,
    val linked_lever: Int = -1,
    val linked_lever_2: Int = -1,
    val linked_block: String = ""
)

@Serializable
data class JsonBlock(
    val label: String = "",
    val short_code: String = "",
    val lcc_event_occupied: String = "",
    val lcc_event_empty: String = ""
)

@Serializable
data class JsonLever(
    val label: String = "",
    val type: String = "SPARE",
    val lcc_event_normal: String = "",
    val lcc_event_reversed: String = "",
    val lcc_enabled: Boolean = true,
    val auto_reverser: Boolean = false,
    val interlocking: List<JsonInterlocking> = emptyList()
)

@Serializable
data class JsonInterlocking(
    val target: Int,
    val state: String,
    val target_type: String = "LEVER",
    val alt_target: Int = -1,
    val alt_state: String = "NORMAL",
    val alt_target_type: String = "LEVER"
)

@Serializable
data class LeverStatesData(
    val tabs: List<List<Boolean>>
)

object ConfigManager : AppConfigRepository {

    val jsonFormat = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
        encodeDefaults = true
    }

    val defaultPrototypicalConfigJson = """{"wifi_ssid": "", "wifi_password": "signalman", "wifi_station_password": "", "conflict_policy": 2, "display_sleep_timeout_ms": 60000, "restore_last_state": true, "lcc_master": true, "tabs": [{"name": "North Junction", "label_lines": 2, "label_line_height": 18, "use_short_codes": true, "use_short_codes_in_indicators": true, "blocks": [{"label": "UP APPROACH", "short_code": "UA", "lcc_event_occupied": "", "lcc_event_empty": ""}, {"label": "UP MAIN", "short_code": "UM", "lcc_event_occupied": "", "lcc_event_empty": ""}, {"label": "TO YARD", "short_code": "TY", "lcc_event_occupied": "", "lcc_event_empty": ""}, {"label": "DOWN APPROACH", "short_code": "DA", "lcc_event_occupied": "", "lcc_event_empty": ""}, {"label": "DOWN MAIN", "short_code": "DM", "lcc_event_occupied": "", "lcc_event_empty": ""}, {"label": "UP MAIN AHEAD", "short_code": "UMA", "lcc_event_occupied": "", "lcc_event_empty": ""}, {"label": "UP MAIN AHEAD", "short_code": "UMA", "lcc_event_occupied": "", "lcc_event_empty": ""}], "levers": [{"label": "UP\nDISTANT", "type": "DISTANT_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 1, "state": "REVERSED", "alt_target": 4, "alt_state": "REVERSED"}]}, {"label": "UP MAIN\nHOME", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 3, "state": "NORMAL"}, {"target": 2, "state": "REVERSED"}, {"target": 1, "state": "EMPTY", "target_type": "BLOCK"}, {"target": 5, "state": "EMPTY", "target_type": "BLOCK"}]}, {"label": "FPL FOR\nPOINTS 4", "type": "FACING_POINTS", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 1, "state": "EMPTY", "target_type": "BLOCK", "alt_target": -1, "alt_state": "NORMAL", "alt_target_type": "LEVER"}]}, {"label": "JUNCTION\nPOINTS", "type": "POINTS", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 1, "state": "EMPTY", "target_type": "BLOCK", "alt_target": -1, "alt_state": "NORMAL", "alt_target_type": "LEVER"}]}, {"label": "TO YARD\nHOME", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 3, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}, {"target": 2, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}, {"target": 2, "state": "EMPTY", "target_type": "BLOCK", "alt_target": -1, "alt_state": "NORMAL"}, {"target": 1, "state": "EMPTY", "target_type": "BLOCK", "alt_target": -1, "alt_state": "NORMAL", "alt_target_type": "LEVER"}]}, {"label": "SPARE", "type": "SPARE", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": []}, {"label": "DOWN\nADVANCED", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": []}, {"label": "DOWN\nHOME", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 4, "state": "EMPTY", "target_type": "BLOCK", "alt_target": -1, "alt_state": "NORMAL"}]}, {"label": "DOWN\nDISTANT", "type": "DISTANT_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 7, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}]}], "schematic_elements": [{"type": "SIGNAL_RIGHT", "x": 0, "y": 1, "linked_lever": 0, "linked_lever_2": -1, "linked_block": "UP APPROACH"}, {"type": "BRACKET_SIGNAL", "x": 1, "y": 1, "linked_lever": 1, "linked_lever_2": 4, "linked_block": "UP MAIN"}, {"type": "TURNOUT_RIGHT", "x": 2, "y": 1, "linked_lever": 3, "linked_block": "UP MAIN"}, {"type": "STRAIGHT_H", "x": 3, "y": 1, "linked_block": "UP MAIN AHEAD"}, {"type": "STRAIGHT_H", "x": 4, "y": 1, "linked_block": "UP MAIN AHEAD"}, {"type": "STRAIGHT_H", "x": 5, "y": 1, "linked_block": "UP MAIN AHEAD"}, {"type": "STRAIGHT_H", "x": 3, "y": 0, "linked_block": "TO YARD"}, {"type": "STRAIGHT_H", "x": 4, "y": 0, "linked_block": "TO YARD"}, {"type": "STRAIGHT_H", "x": 5, "y": 0, "linked_block": "TO YARD"}, {"type": "STRAIGHT_H", "x": 0, "y": 2, "linked_block": "DOWN MAIN"}, {"type": "SIGNAL_LEFT", "x": 1, "y": 2, "linked_lever": 6, "linked_block": "DOWN MAIN"}, {"type": "STRAIGHT_H", "x": 2, "y": 2, "linked_block": "DOWN MAIN"}, {"type": "STRAIGHT_H", "x": 3, "y": 2, "linked_block": "DOWN MAIN"}, {"type": "SIGNAL_LEFT", "x": 4, "y": 2, "linked_lever": 7, "linked_block": "DOWN MAIN"}, {"type": "SIGNAL_LEFT", "x": 5, "y": 2, "linked_block": "DOWN APPROACH", "linked_lever": 8}]}, {"name": "South Box", "label_lines": 2, "label_line_height": 18, "use_short_codes": true, "use_short_codes_in_indicators": true, "blocks": [{"label": "YARD APPROACH", "short_code": "YA", "lcc_event_occupied": "", "lcc_event_empty": ""}, {"label": "YARD", "short_code": "Y", "lcc_event_occupied": "", "lcc_event_empty": ""}, {"label": "SIDING", "short_code": "S", "lcc_event_occupied": "", "lcc_event_empty": ""}], "levers": [{"label": "SHUNT\nAHEAD", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 1, "state": "NORMAL", "alt_target": -1, "alt_state": "NORMAL"}]}, {"label": "YARD\nCROSSOVER", "type": "POINTS", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 1, "state": "EMPTY", "target_type": "BLOCK"}]}, {"label": "SIDING\nEXIT", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 1, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}]}, {"label": "YARD\nHOME", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 1, "state": "NORMAL"}]}, {"label": "SIDING\nHOME", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 1, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}]}, {"label": "YARD\nDISTANT", "type": "DISTANT_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 3, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}]}], "schematic_elements": [{"type": "SIGNAL_RIGHT", "x": 0, "y": 1, "linked_block": "YARD APPROACH", "linked_lever": 5}, {"type": "BRACKET_SIGNAL", "x": 2, "y": 1, "linked_block": "YARD", "linked_lever": 3, "linked_lever_2": 4}, {"type": "TURNOUT_RIGHT", "x": 3, "y": 1, "linked_lever": 1, "linked_block": "YARD"}, {"type": "STRAIGHT_H", "x": 4, "y": 1, "linked_block": "YARD"}, {"type": "SIGNAL_LEFT", "x": 5, "y": 1, "linked_lever": 0, "linked_block": "YARD"}, {"type": "STRAIGHT_H", "x": 4, "y": 0, "linked_block": "SIDING"}, {"type": "SIGNAL_LEFT", "x": 5, "y": 0, "linked_block": "SIDING", "linked_lever": 2}, {"type": "STRAIGHT_H", "x": 1, "y": 1, "linked_block": "YARD APPROACH"}]}]}"""

    override var currentConfig by mutableStateOf(jsonFormat.decodeFromString<JsonConfig>(defaultPrototypicalConfigJson))

    override suspend fun initConfig() {
        val loadedJson = loadConfigFromFile()
        if (loadedJson != null) {
            try {
                currentConfig = jsonFormat.decodeFromString<JsonConfig>(loadedJson)
            } catch (e: Exception) {
                // fallback to default
            }
        }
    }

    override fun toJsonString(): String = jsonFormat.encodeToString(JsonConfig.serializer(), currentConfig)

    override fun parseConfig(jsonString: String): List<Pair<String, TabDef>> {
        val config = jsonFormat.decodeFromString<JsonConfig>(jsonString)
        
        return config.tabs.map { jsonTab ->
            val levers = jsonTab.levers.map { jsonLever ->
                val type = try {
                    LeverType.valueOf(jsonLever.type)
                } catch (e: Exception) {
                    LeverType.SPARE
                }
                
                val conditions = jsonLever.interlocking.map { condition ->
                    val tType = try { TargetType.valueOf(condition.target_type) } catch (e: Exception) { TargetType.LEVER }
                    val altTType = try { TargetType.valueOf(condition.alt_target_type) } catch (e: Exception) { TargetType.LEVER }
                    
                    InterlockingCondition(
                        targetType = tType,
                        targetIndex = condition.target,
                        requiredState = if (tType == TargetType.BLOCK) condition.state == "OCCUPIED" else condition.state == "REVERSED",
                        altTargetType = altTType,
                        altTargetIndex = condition.alt_target,
                        altRequiredState = if (altTType == TargetType.BLOCK) condition.alt_state == "OCCUPIED" else condition.alt_state == "REVERSED"
                    )
                }
                
                val normalSuffix = extractSuffix(jsonLever.lcc_event_normal, config.node_id)
                val reversedSuffix = extractSuffix(jsonLever.lcc_event_reversed, config.node_id)
                
                LeverDef(
                    conditions = conditions,
                    type = type,
                    label = jsonLever.label,
                    lcc_event_normal = if (normalSuffix.isNotBlank()) "${config.node_id}.$normalSuffix" else "",
                    lcc_event_reversed = if (reversedSuffix.isNotBlank()) "${config.node_id}.$reversedSuffix" else "",
                    lcc_enabled = jsonLever.lcc_enabled,
                    autoReverser = jsonLever.auto_reverser
                )
            }

            val blocks = jsonTab.blocks.map { jsonBlock ->
                val occupiedSuffix = extractSuffix(jsonBlock.lcc_event_occupied, config.node_id)
                val emptySuffix = extractSuffix(jsonBlock.lcc_event_empty, config.node_id)
                
                BlockDef(
                    label = jsonBlock.label,
                    shortCode = jsonBlock.short_code,
                    lcc_event_occupied = if (occupiedSuffix.isNotBlank()) "${config.node_id}.$occupiedSuffix" else "",
                    lcc_event_empty = if (emptySuffix.isNotBlank()) "${config.node_id}.$emptySuffix" else ""
                )
            }
            
            val schematicElements = jsonTab.schematic_elements.map { jsonElem ->
                SchematicElementDef(
                    type = jsonElem.type,
                    x = jsonElem.x,
                    y = jsonElem.y,
                    linkedLever = jsonElem.linked_lever,
                    linkedLever2 = jsonElem.linked_lever_2,
                    linkedBlock = jsonElem.linked_block
                )
            }

            jsonTab.name to TabDef(
                levers = levers,
                labelLines = jsonTab.label_lines,
                labelLineHeight = jsonTab.label_line_height,
                blockLayout = jsonTab.block_layout,
                blockLabelSize = jsonTab.block_label_size,
                showLeverNumbers = jsonTab.show_lever_numbers,
                showBlockNumbers = jsonTab.show_block_numbers,
                useShortCodes = jsonTab.use_short_codes,
                useShortCodesInIndicators = jsonTab.use_short_codes_in_indicators,
                schematicGridSize = jsonTab.schematic_grid_size,
                blocks = blocks,
                schematicElements = schematicElements
            )
        }
    }

    private fun extractSuffix(eventId: String, nodeId: String): String {
        if (eventId.isBlank()) return ""
        val pfx = if (nodeId.isNotBlank()) "$nodeId." else ""
        if (pfx.isNotEmpty() && eventId.startsWith(pfx)) {
            return eventId.removePrefix(pfx)
        }
        val clean = eventId.replace(".", "")
        if (clean.length == 16) {
            return "${clean.substring(12, 14)}.${clean.substring(14, 16)}"
        }
        return eventId
    }

    override suspend fun loadSavedLeverStates(): List<BooleanArray>? {
        val jsonString = loadLeverStatesFromFile() ?: return null
        return try {
            val data = jsonFormat.decodeFromString<LeverStatesData>(jsonString)
            data.tabs.map { it.toBooleanArray() }
        } catch (e: Exception) {
            println("Failed to load saved lever states: ${e.message}")
            null
        }
    }

    override suspend fun saveCurrentLeverStates(states: List<BooleanArray>) {
        try {
            val data = LeverStatesData(states.map { it.toList() })
            val jsonString = jsonFormat.encodeToString(LeverStatesData.serializer(), data)
            saveLeverStatesToFile(jsonString)
        } catch (e: Exception) {
            println("Failed to save lever states: ${e.message}")
        }
    }

    override suspend fun saveConfig(newConfig: JsonConfig) {
        currentConfig = newConfig
        saveConfigToFile(toJsonString())
    }
}
