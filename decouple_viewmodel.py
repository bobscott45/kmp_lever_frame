import re

def decouple_viewmodel(path):
    with open(path, 'r') as f:
        content = f.read()

    # 1. Remove the saveStateTrigger declaration
    content = re.sub(r'private val saveStateTrigger = MutableSharedFlow<Unit>\([\s\S]*?DROP_OLDEST\s*\)\s*', '', content)

    # 2. Add properties for the new services right after the UI states
    services = """
    private val persistenceService = PersistenceService(configRepo, viewModelScope, domainState)
    private val eventProcessor = NetworkEventProcessor(lccClient, configRepo)
"""
    content = re.sub(r'(val uiState: StateFlow<TransientUiState> = [^)]+\)\n)', r'\1' + services, content)

    # 3. Remove the init block saveStateTrigger logic
    content = re.sub(r'@OptIn\(kotlinx\.coroutines\.FlowPreview::class\)\s*viewModelScope\.launch \{\s*saveStateTrigger\.debounce\(500\)\.collect \{\s*if \(configRepo\.currentConfig\.restore_last_state\) \{\s*val statesToSave = _domainState\.value\.leverStates\.map \{ it\.copyOf\(\) \}\s*configRepo\.saveCurrentLeverStates\(statesToSave\)\s*\}\s*\}\s*\}', '', content)

    # 4. Replace persistStatesIfEnabled body
    content = re.sub(r'private fun persistStatesIfEnabled\(\) \{\s*if \(configRepo\.currentConfig\.restore_last_state\) \{\s*saveStateTrigger\.tryEmit\(Unit\)\s*\}\s*\}', 'private fun persistStatesIfEnabled() {\n        persistenceService.triggerSave()\n    }', content)

    # 5. Replace handleExternalEvent
    new_handle_external = """private fun handleExternalEvent(hexEventId: String) {
        var result: EventProcessorResult? = null
        
        _domainState.update { currentDomain ->
            result = eventProcessor.processEvent(hexEventId, currentDomain, _configState.value, _uiState.value)
            result!!.newState
        }
        
        if (result!!.didChange) {
            persistStatesIfEnabled()
        }
        
        result!!.outgoingEvents.forEach { eventStr ->
            lccClient.produceEvent(eventStr)
        }
    }"""
    content = re.sub(r'private fun handleExternalEvent\(hexEventId: String\) \{[\s\S]*?\}\s*fun tabSelected\(', new_handle_external + '\n\n    fun tabSelected(', content)

    with open(path, 'w') as f:
        f.write(content)

decouple_viewmodel('shared/src/commonMain/kotlin/org/edranor/leverframe/AppViewModel.kt')
