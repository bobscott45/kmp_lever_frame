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
 * Core business service responsible for loading, parsing, updating, and saving
 * the JSON configuration model representing the lever frames.
 */
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
