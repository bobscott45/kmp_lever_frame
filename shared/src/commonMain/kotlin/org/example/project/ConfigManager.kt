package org.example.project

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
    val tabs: List<JsonTab> = emptyList()
)

@Serializable
data class JsonTab(
    val name: String,
    val label_lines: Int = 2,
    val label_line_height: Int = 18,
    val levers: List<JsonLever> = emptyList()
)

@Serializable
data class JsonLever(
    val label: String = "",
    val type: String = "SPARE",
    val lcc_event_normal: String = "",
    val lcc_event_reversed: String = "",
    val lcc_enabled: Boolean = true,
    val interlocking: List<JsonInterlocking> = emptyList()
)

@Serializable
data class JsonInterlocking(
    val target: Int,
    val state: String,
    val alt_target: Int = -1,
    val alt_state: String = "NORMAL"
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

    val defaultPrototypicalConfigJson = """{"wifi_ssid": "", "wifi_password": "signalman", "wifi_station_password": "", "conflict_policy": 2, "display_sleep_timeout_ms": 60000, "restore_last_state": true, "lcc_master": true, "tabs": [{"name": "North Junction", "label_lines": 2, "label_line_height": 18, "levers": [{"label": "UP\nDISTANT", "type": "DISTANT_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 1, "state": "REVERSED", "alt_target": 4, "alt_state": "REVERSED"}]}, {"label": "UP MAIN\nHOME", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 3, "state": "NORMAL", "alt_target": -1, "alt_state": "NORMAL"}, {"target": 2, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}]}, {"label": "FPL FOR\nPOINTS 4", "type": "FACING_POINTS", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": []}, {"label": "JUNCTION\nPOINTS", "type": "POINTS", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 2, "state": "NORMAL", "alt_target": -1, "alt_state": "NORMAL"}]}, {"label": "UP BRANCH\nHOME", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 3, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}, {"target": 2, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}]}, {"label": "SPARE", "type": "SPARE", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": []}, {"label": "DOWN\nADVANCED", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": []}, {"label": "DOWN\nHOME", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 6, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}]}]}, {"name": "South Box", "label_lines": 2, "label_line_height": 18, "levers": [{"label": "SHUNT\nAHEAD", "type": "HOME_SIGNAL", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 1, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}]}, {"label": "YARD\nCROSSOVER", "type": "POINTS", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": [{"target": 2, "state": "REVERSED", "alt_target": -1, "alt_state": "NORMAL"}]}, {"label": "FRAME\nRELEASE", "type": "FACING_POINTS", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": []}, {"label": "SPARE", "type": "SPARE", "lcc_event_normal": "", "lcc_event_reversed": "", "lcc_enabled": true, "interlocking": []}]}]}"""

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
                    InterlockingCondition(
                        targetLeverIndex = condition.target,
                        requiredState = condition.state == "REVERSED",
                        altTargetLeverIndex = condition.alt_target,
                        altRequiredState = condition.alt_state == "REVERSED"
                    )
                }
                
                LeverDef(
                    conditions = conditions,
                    type = type,
                    label = jsonLever.label,
                    lcc_event_normal = jsonLever.lcc_event_normal,
                    lcc_event_reversed = jsonLever.lcc_event_reversed,
                    lcc_enabled = jsonLever.lcc_enabled
                )
            }
            jsonTab.name to TabDef(levers, jsonTab.label_lines, jsonTab.label_line_height)
        }
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
