package org.edranor.leverframe

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FakeConfigRepository : AppConfigRepository {
    override var currentConfig: JsonConfig = JsonConfig()
    
    var initCalled = false
    var saveCalled = false
    
    override suspend fun initConfig() {
        initCalled = true
    }
    
    override fun toJsonString(): String = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), currentConfig)
    
    override fun parseConfig(jsonString: String): List<Pair<String, TabDef>> {
        return ConfigManager.parseConfig(jsonString)
    }
    
    var savedLeverStates: SavedStatesData? = null
    override suspend fun loadSavedStates(): SavedStatesData? = savedLeverStates
    
    override suspend fun saveCurrentStates(states: SavedStatesData) {
        savedLeverStates = states
    }

    override suspend fun clearSavedStates() {
        savedLeverStates = null
    }
    
    override suspend fun saveConfig(newConfig: JsonConfig) {
        currentConfig = newConfig
        saveCalled = true
    }
}

class FakeLccClient : LccNetworkClient {
    private val _events = MutableSharedFlow<String>()
    override val externalEvents: SharedFlow<String> = _events.asSharedFlow()
    private val _connectionStatus = kotlinx.coroutines.flow.MutableStateFlow("Disconnected")
    override val connectionStatus: kotlinx.coroutines.flow.StateFlow<String> = _connectionStatus
    private val _connectionErrors = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    override val connectionErrors: SharedFlow<String> = _connectionErrors.asSharedFlow()
    
    val producedEvents = mutableListOf<String>()
    var initCalled = false
    var disconnectCalled = false

    override fun initialize() {
        initCalled = true
        disconnectCalled = false
    }

    override fun disconnect() {
        disconnectCalled = true
    }

    override fun identifyProducer(eventIdStr: String) {
        // No-op for test
    }
    
    override fun produceEvent(eventIdStr: String) {
        producedEvents.add(eventIdStr)
    }

    override fun parseEventId(eventIdStr: String): String {
        return eventIdStr.replace(".", "").padEnd(16, '0').uppercase()
    }
    
    suspend fun emitEvent(event: String) {
        _events.emit(event)
    }

    suspend fun emitConnectionError(error: String) {
        _connectionErrors.emit(error)
    }
}
