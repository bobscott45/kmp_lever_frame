package org.edranor.leverframe.services

import org.edranor.leverframe.ConfigState
import org.edranor.leverframe.ConfigurationRepository
import org.edranor.leverframe.JsonConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConfigurationService(
    private val configRepo: ConfigurationRepository
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _configState = MutableStateFlow(ConfigState())
    val configState: StateFlow<ConfigState> = _configState.asStateFlow()

    suspend fun initialize() {
        configRepo.initConfig()
        loadConfig()
    }

    suspend fun loadConfig() {
        val configStr = configRepo.toJsonString()
        val parsedTabs = configRepo.parseConfig(configStr)
        val initialVersion = _configState.value.configVersion
        
        _configState.update {
            it.copy(
                tabs = parsedTabs,
                configVersion = initialVersion + 1,
                config = configRepo.currentConfig
            )
        }
    }

    fun setLeverLccEnabled(tabIndex: Int, leverIndex: Int, enabled: Boolean) {
        val newTabsJson = configRepo.currentConfig.tabs.toMutableList()
        val currentTabJson = newTabsJson[tabIndex].copy()
        val newLeversJson = currentTabJson.levers.toMutableList()
        newLeversJson[leverIndex] = newLeversJson[leverIndex].copy(lcc_enabled = enabled)
        val newConfig = configRepo.currentConfig.copy(
            tabs = newTabsJson.apply { set(tabIndex, currentTabJson.copy(levers = newLeversJson)) }
        )
        coroutineScope.launch {
            configRepo.saveConfig(newConfig)
            loadConfig()
        }
    }

    fun saveLayoutWeights(landscapeWeight: Float, portraitWeight: Float) {
        val newConfig = configRepo.currentConfig.copy(
            schematic_weight_landscape = landscapeWeight,
            schematic_weight_portrait = portraitWeight
        )
        coroutineScope.launch {
            configRepo.saveConfig(newConfig)
            _configState.update { it.copy(config = newConfig) }
        }
    }

    suspend fun updateSystemConfig(newConfig: JsonConfig, rulesOnly: Boolean = false) {
        configRepo.saveConfig(newConfig)
        if (rulesOnly) {
            val configStr = configRepo.toJsonString()
            val parsedTabs = configRepo.parseConfig(configStr)
            _configState.update { 
                it.copy(
                    tabs = parsedTabs,
                    config = configRepo.currentConfig
                )
            }
        } else {
            loadConfig()
        }
    }
}
