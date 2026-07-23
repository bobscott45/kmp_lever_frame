import re

def patch_file(filepath, replacements):
    with open(filepath, 'r') as f:
        content = f.read()
    for old, new in replacements:
        if old not in content:
            print(f"WARNING: could not find '{old}' in {filepath}")
        content = content.replace(old, new)
    with open(filepath, 'w') as f:
        f.write(content)

# 1. AppViewModel.kt
avm_replacements = [
    (
        '''        val leverStates = parsedTabs.map { (tabName, tabDef) ->
            BooleanArray(tabDef.levers.size) { false }
        }
        val manualLocks = parsedTabs.map { (tabName, tabDef) ->
            BooleanArray(tabDef.levers.size) { false }
        }
        val blockStates = parsedTabs.map { (tabName, tabDef) ->
            BooleanArray(tabDef.blocks.size) { true }
        }
        
        // Restore from disk if configured
        val storedStates = configRepo.loadSavedLeverStates()
        if (configRepo.currentConfig.restore_last_state && storedStates != null && storedStates.isNotEmpty()) {
            storedStates.forEachIndexed { tabIdx, states ->
                if (tabIdx < leverStates.size) {
                    val tabLeversCount = leverStates[tabIdx].size
                    val copyCount = minOf(tabLeversCount, states.size)
                    states.copyInto(leverStates[tabIdx], 0, 0, copyCount)
                }
            }
        }''',
        '''        val frames = parsedTabs.mapIndexed { tabIdx, (_, tabDef) ->
            DomainFrame(
                id = tabIdx,
                levers = tabDef.levers.mapIndexed { i, _ -> DomainLever(i, false, false) },
                blocks = tabDef.blocks.mapIndexed { i, _ -> DomainBlock(i, true) }
            )
        }.toMutableList()

        // Restore from disk if configured
        val storedStates = configRepo.loadSavedLeverStates()
        if (configRepo.currentConfig.restore_last_state && storedStates != null && storedStates.isNotEmpty()) {
            frames.forEachIndexed { tabIdx, frame ->
                if (tabIdx < storedStates.size) {
                    val states = storedStates[tabIdx]
                    val updatedLevers = frame.levers.mapIndexed { i, l -> 
                        if (i < states.size) l.copy(isReversed = states[i]) else l 
                    }
                    frames[tabIdx] = frame.copy(levers = updatedLevers)
                }
            }
        }'''
    ),
    (
        '''        _domainState.update {
            it.copy(
                leverStates = leverStates,
                manualLocks = manualLocks,
                blockStates = blockStates
            )
        }''',
        '''        _domainState.update {
            it.copy(frames = frames.toList())
        }'''
    ),
    (
        '''                if (tabIdx < domain.leverStates.size) {
                    val statesForTab = domain.leverStates[tabIdx]
                    tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                        if (leverIdx < statesForTab.size && leverDef.lcc_enabled && config.config.lcc_enabled) {
                            val isReversed = statesForTab[leverIdx]''',
        '''                if (tabIdx < domain.frames.size) {
                    val statesForTab = domain.frames[tabIdx].levers
                    tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                        if (leverIdx < statesForTab.size && leverDef.lcc_enabled && config.config.lcc_enabled) {
                            val isReversed = statesForTab[leverIdx].isReversed'''
    ),
    (
        '''    fun toggleLever(tabIndex: Int, leverIndex: Int, targetState: Boolean) {
        val configState = _configState.value
        if (configState.tabs.isEmpty()) return

        val tabDef = configState.tabs[tabIndex].second
        if (leverIndex !in tabDef.levers.indices) return

        var didChange = false
        var newlyReversed = false

        _domainState.update { currentDomain ->
            val currentStates = currentDomain.leverStates[tabIndex]
            val leverState = currentStates[leverIndex]
            
            if (leverState == targetState) return@update currentDomain
            
            val newStates = LeverFramePolicy.attemptToggle(tabDef, currentStates, currentDomain.blockStates[tabIndex], leverIndex, targetState)
            if (newStates != null) {
                val updatedAllStates = currentDomain.leverStates.toMutableList()
                updatedAllStates[tabIndex] = newStates
                
                didChange = true
                newlyReversed = targetState
                
                val uiState = _uiState.value
                val conflicts = Interlocking.getConflictingLevers(
                    configState.tabs[uiState.selectedTabIndex].second,
                    updatedAllStates[uiState.selectedTabIndex],
                    currentDomain.blockStates[uiState.selectedTabIndex]
                )
                
                currentDomain.copy(leverStates = updatedAllStates, conflictingLevers = conflicts)
            } else {
                currentDomain
            }
        }''',
        '''    fun toggleLever(tabIndex: Int, leverIndex: Int, targetState: Boolean) {
        val configState = _configState.value
        if (configState.tabs.isEmpty()) return

        val tabDef = configState.tabs[tabIndex].second
        if (leverIndex !in tabDef.levers.indices) return

        var didChange = false
        var newlyReversed = false

        _domainState.update { currentDomain ->
            val frame = currentDomain.frames[tabIndex]
            val currentStates = frame.levers
            val leverState = currentStates[leverIndex].isReversed
            
            if (leverState == targetState) return@update currentDomain
            
            val newStates = LeverFramePolicy.attemptToggle(tabDef, currentStates, frame.blocks, leverIndex, targetState)
            if (newStates != null) {
                val updatedFrames = currentDomain.frames.toMutableList()
                updatedFrames[tabIndex] = frame.copy(levers = newStates)
                
                didChange = true
                newlyReversed = targetState
                
                val uiState = _uiState.value
                val conflicts = Interlocking.getConflictingLevers(
                    configState.tabs[uiState.selectedTabIndex].second,
                    updatedFrames[uiState.selectedTabIndex].levers,
                    updatedFrames[uiState.selectedTabIndex].blocks
                )
                
                currentDomain.copy(frames = updatedFrames, conflictingLevers = conflicts)
            } else {
                currentDomain
            }
        }'''
    ),
    (
        '''    fun toggleManualLock(tabIndex: Int, leverIndex: Int) {
        _domainState.update { currentDomain ->
            val updatedLocks = currentDomain.manualLocks.toMutableList()
            val tabLocks = updatedLocks[tabIndex].copyOf()
            tabLocks[leverIndex] = !tabLocks[leverIndex]
            updatedLocks[tabIndex] = tabLocks
            currentDomain.copy(manualLocks = updatedLocks)
        }
    }''',
        '''    fun toggleManualLock(tabIndex: Int, leverIndex: Int) {
        _domainState.update { currentDomain ->
            val updatedFrames = currentDomain.frames.toMutableList()
            val frame = updatedFrames[tabIndex]
            val updatedLevers = frame.levers.toMutableList()
            updatedLevers[leverIndex] = updatedLevers[leverIndex].copy(isManuallyLocked = !updatedLevers[leverIndex].isManuallyLocked)
            updatedFrames[tabIndex] = frame.copy(levers = updatedLevers)
            currentDomain.copy(frames = updatedFrames)
        }
    }'''
    ),
    (
        '''            if (tabIndex in currentDomain.blockStates.indices && blockIndex in currentDomain.blockStates[tabIndex].indices) {
                val newBlockStates = currentDomain.blockStates.map { it.copyOf() }.toMutableList()
                newBlockStates[tabIndex][blockIndex] = !newBlockStates[tabIndex][blockIndex]
                
                val newLeverStates = currentDomain.leverStates.map { it.copyOf() }.toMutableList()
                val tabDef = configState.tabs[tabIndex].second
                
                // Evaluate auto-reversers (cascade until steady state)
                var reverserChanged: Boolean
                do {
                    reverserChanged = false
                    val currentConflicts = Interlocking.getConflictingLevers(tabDef, newLeverStates[tabIndex], newBlockStates[tabIndex])
                    
                    tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                        if (leverDef.autoReverser && newLeverStates[tabIndex][leverIdx]) {
                            if (leverIdx in currentConflicts) {
                                newLeverStates[tabIndex][leverIdx] = false // Force to NORMAL
                                reverserChanged = true
                                if (leverDef.lcc_event_normal.isNotBlank()) {
                                    outgoingEvents.add(leverDef.lcc_event_normal)
                                }
                            }
                        }
                    }
                } while(reverserChanged)
                
                val conflicts = if (configState.tabs.isNotEmpty()) {
                    Interlocking.getConflictingLevers(
                        configState.tabs[uiState.selectedTabIndex].second,
                        newLeverStates[uiState.selectedTabIndex],
                        newBlockStates[uiState.selectedTabIndex]
                    )
                } else emptyList()
                
                didChange = true
                currentDomain.copy(
                    blockStates = newBlockStates,
                    leverStates = newLeverStates,
                    conflictingLevers = conflicts
                )
            } else {
                currentDomain
            }''',
        '''            if (tabIndex in currentDomain.frames.indices && blockIndex in currentDomain.frames[tabIndex].blocks.indices) {
                val updatedFrames = currentDomain.frames.toMutableList()
                val frame = updatedFrames[tabIndex]
                
                val newBlocks = frame.blocks.toMutableList()
                newBlocks[blockIndex] = newBlocks[blockIndex].copy(isOccupied = !newBlocks[blockIndex].isOccupied)
                
                val newLevers = frame.levers.toMutableList()
                val tabDef = configState.tabs[tabIndex].second
                
                // Evaluate auto-reversers (cascade until steady state)
                var reverserChanged: Boolean
                do {
                    reverserChanged = false
                    val currentConflicts = Interlocking.getConflictingLevers(tabDef, newLevers, newBlocks)
                    
                    tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                        if (leverDef.autoReverser && newLevers[leverIdx].isReversed) {
                            if (leverIdx in currentConflicts) {
                                newLevers[leverIdx] = newLevers[leverIdx].copy(isReversed = false) // Force to NORMAL
                                reverserChanged = true
                                if (leverDef.lcc_event_normal.isNotBlank()) {
                                    outgoingEvents.add(leverDef.lcc_event_normal)
                                }
                            }
                        }
                    }
                } while(reverserChanged)
                
                updatedFrames[tabIndex] = frame.copy(blocks = newBlocks, levers = newLevers)
                
                val conflicts = if (configState.tabs.isNotEmpty()) {
                    Interlocking.getConflictingLevers(
                        configState.tabs[uiState.selectedTabIndex].second,
                        updatedFrames[uiState.selectedTabIndex].levers,
                        updatedFrames[uiState.selectedTabIndex].blocks
                    )
                } else emptyList()
                
                didChange = true
                currentDomain.copy(
                    frames = updatedFrames,
                    conflictingLevers = conflicts
                )
            } else {
                currentDomain
            }'''
    ),
    (
        '''        _domainState.update { currentDomain ->
            val configState = _configState.value
            val conflicts = if (configState.tabs.isNotEmpty()) {
                Interlocking.getConflictingLevers(
                    configState.tabs[index].second,
                    currentDomain.leverStates[index],
                    currentDomain.blockStates[index]
                )
            } else emptyList()
            currentDomain.copy(conflictingLevers = conflicts)
        }''',
        '''        _domainState.update { currentDomain ->
            val configState = _configState.value
            val conflicts = if (configState.tabs.isNotEmpty() && index in currentDomain.frames.indices) {
                Interlocking.getConflictingLevers(
                    configState.tabs[index].second,
                    currentDomain.frames[index].levers,
                    currentDomain.frames[index].blocks
                )
            } else emptyList()
            currentDomain.copy(conflictingLevers = conflicts)
        }'''
    )
]

patch_file('shared/src/commonMain/kotlin/org/edranor/leverframe/AppViewModel.kt', avm_replacements)
