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

# NetworkEventProcessor.kt
nep_replacements = [
    (
        '''val newLeverStates = currentDomain.leverStates.map { it.copyOf() }.toMutableList()
            val newBlockStates = currentDomain.blockStates.map { it.copyOf() }.toMutableList()''',
        '''val newFrames = currentDomain.frames.toMutableList()'''
    ),
    (
        '''                val currState = newLeverStates[tabIdx][leverIdx]
                if (currState != attemptState) {
                    val isValid = Interlocking.evaluate(tabDef, newLeverStates[tabIdx], currentDomain.blockStates[tabIdx], leverIdx, attemptState)
                    if (isValid) {
                        newLeverStates[tabIdx][leverIdx] = attemptState
                        stateChanged = true
                    }
                }''',
        '''                val frame = newFrames[tabIdx]
                val currState = frame.levers[leverIdx].isReversed
                if (currState != attemptState) {
                    val isValid = Interlocking.evaluate(tabDef, frame.levers, frame.blocks, leverIdx, attemptState)
                    if (isValid) {
                        val newLevers = frame.levers.toMutableList()
                        newLevers[leverIdx] = newLevers[leverIdx].copy(isReversed = attemptState)
                        newFrames[tabIdx] = frame.copy(levers = newLevers)
                        stateChanged = true
                    }
                }'''
    ),
    (
        '''                if (newBlockStates[tabIdx][blockIdx] != attemptBlockState) {
                    newBlockStates[tabIdx][blockIdx] = attemptBlockState
                    stateChanged = true
                }''',
        '''                val frame = newFrames[tabIdx]
                if (frame.blocks[blockIdx].isOccupied != attemptBlockState) {
                    val newBlocks = frame.blocks.toMutableList()
                    newBlocks[blockIdx] = newBlocks[blockIdx].copy(isOccupied = attemptBlockState)
                    newFrames[tabIdx] = frame.copy(blocks = newBlocks)
                    stateChanged = true
                }'''
    ),
    (
        '''            var reverserChanged: Boolean
            do {
                reverserChanged = false
                val currentConflicts = Interlocking.getConflictingLevers(tabDef, newLeverStates[tabIdx], newBlockStates[tabIdx])
                
                tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                    if (leverDef.autoReverser && newLeverStates[tabIdx][leverIdx]) {
                        if (leverIdx in currentConflicts) {
                            newLeverStates[tabIdx][leverIdx] = false // Force to NORMAL
                            reverserChanged = true
                            if (leverDef.lcc_event_normal.isNotBlank()) {
                                outgoingEvents.add(leverDef.lcc_event_normal)
                            }
                        }
                    }
                }
            } while(reverserChanged)''',
        '''            var reverserChanged: Boolean
            do {
                val frame = newFrames[tabIdx]
                reverserChanged = false
                val currentConflicts = Interlocking.getConflictingLevers(tabDef, frame.levers, frame.blocks)
                
                tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                    if (leverDef.autoReverser && frame.levers[leverIdx].isReversed) {
                        if (leverIdx in currentConflicts) {
                            val newLevers = newFrames[tabIdx].levers.toMutableList()
                            newLevers[leverIdx] = newLevers[leverIdx].copy(isReversed = false) // Force to NORMAL
                            newFrames[tabIdx] = newFrames[tabIdx].copy(levers = newLevers)
                            reverserChanged = true
                            if (leverDef.lcc_event_normal.isNotBlank()) {
                                outgoingEvents.add(leverDef.lcc_event_normal)
                            }
                        }
                    }
                }
            } while(reverserChanged)'''
    ),
    (
        '''            if (stateChanged && newBlockStates !== currentDomain.blockStates) {
                stateToReturn = stateToReturn.copy(blockStates = newBlockStates)
            }
            if (stateChanged && newLeverStates !== currentDomain.leverStates) {
                val conflicts = if (configState.tabs.isNotEmpty()) {
                    Interlocking.getConflictingLevers(
                        configState.tabs[uiState.selectedTabIndex].second,
                        newLeverStates[uiState.selectedTabIndex],
                        newBlockStates[uiState.selectedTabIndex]
                    )
                } else emptyList()
                stateToReturn = stateToReturn.copy(leverStates = newLeverStates, conflictingLevers = conflicts)
            }''',
        '''            if (stateChanged && newFrames !== currentDomain.frames) {
                val conflicts = if (configState.tabs.isNotEmpty() && uiState.selectedTabIndex in newFrames.indices) {
                    Interlocking.getConflictingLevers(
                        configState.tabs[uiState.selectedTabIndex].second,
                        newFrames[uiState.selectedTabIndex].levers,
                        newFrames[uiState.selectedTabIndex].blocks
                    )
                } else emptyList()
                stateToReturn = stateToReturn.copy(frames = newFrames, conflictingLevers = conflicts)
            }'''
    )
]

patch_file('shared/src/commonMain/kotlin/org/edranor/leverframe/NetworkEventProcessor.kt', nep_replacements)

# PersistenceService.kt
ps_replacements = [
    (
        '''            val statesToSave = domainStateFlow.value.leverStates.map { it.copyOf() }''',
        '''            val statesToSave = domainStateFlow.value.frames.map { f -> f.levers.map { it.isReversed }.toBooleanArray() }'''
    )
]
patch_file('shared/src/commonMain/kotlin/org/edranor/leverframe/PersistenceService.kt', ps_replacements)

# LeverFramePolicy.kt
lfp_replacements = [
    (
        '''    fun attemptToggle(tabDef: TabDef, states: BooleanArray, blockStates: BooleanArray, leverIndex: Int, target: Boolean): BooleanArray? {
        val isValid = Interlocking.evaluate(tabDef, states, blockStates, leverIndex, target)
        if (isValid) {
            val newStates = states.copyOf()
            newStates[leverIndex] = target
            return newStates
        }
        return null
    }''',
        '''    fun attemptToggle(tabDef: TabDef, levers: List<DomainLever>, blocks: List<DomainBlock>, leverIndex: Int, target: Boolean): List<DomainLever>? {
        val isValid = Interlocking.evaluate(tabDef, levers, blocks, leverIndex, target)
        if (isValid) {
            val newLevers = levers.toMutableList()
            newLevers[leverIndex] = newLevers[leverIndex].copy(isReversed = target)
            return newLevers
        }
        return null
    }'''
    )
]
patch_file('shared/src/commonMain/kotlin/org/edranor/leverframe/LeverFramePolicy.kt', lfp_replacements)

