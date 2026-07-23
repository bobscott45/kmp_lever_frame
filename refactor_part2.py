import re

# AppViewModel.kt
with open('shared/src/commonMain/kotlin/org/edranor/leverframe/AppViewModel.kt', 'r') as f:
    content = f.read()

# Replace initialization of domain state
new_domain_init = """        val frames = parsedTabs.mapIndexed { tabIdx, (tabName, tabDef) ->
            DomainFrame(
                id = tabIdx,
                levers = tabDef.levers.mapIndexed { i, _ -> DomainLever(i, false, false) },
                blocks = tabDef.blocks.mapIndexed { i, _ -> DomainBlock(i, true) }
            )
        }
        
        // Restore from disk if configured
        val storedStates = configRepo.loadSavedLeverStates()
        if (configRepo.currentConfig.restore_last_state && storedStates != null && storedStates.isNotEmpty()) {
            frames.forEachIndexed { tabIdx, frame ->
                if (tabIdx < storedStates.size) {
                    val states = storedStates[tabIdx]
                    val updatedLevers = frame.levers.mapIndexed { i, l -> 
                        if (i < states.size) l.copy(isReversed = states[i]) else l 
                    }
                    frames[tabIdx] = frame.copy(levers = updatedLevers) // wait, frames is immutable list. Needs to be mutable.
                }
            }
        }"""

# Corrected domain init
new_domain_init = """        val frames = parsedTabs.mapIndexed { tabIdx, (tabName, tabDef) ->
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
        }"""

content = re.sub(
    r'val leverStates = parsedTabs\.map \{[\s\S]*?val blockStates = parsedTabs\.map \{[\s\S]*?\}',
    new_domain_init,
    content
)

content = re.sub(
    r'it\.copy\(\s*leverStates = leverStates,\s*manualLocks = manualLocks,\s*blockStates = blockStates\s*\)',
    r'it.copy(frames = frames.toList())',
    content
)

content = content.replace(
    'if (tabIdx < domain.leverStates.size) {',
    'if (tabIdx < domain.frames.size) {'
)
content = content.replace(
    'val statesForTab = domain.leverStates[tabIdx]',
    'val statesForTab = domain.frames[tabIdx].levers'
)
content = content.replace(
    'val isReversed = statesForTab[leverIdx]',
    'val isReversed = statesForTab[leverIdx].isReversed'
)

# toggleLever
content = content.replace(
    'val currentStates = currentDomain.leverStates[tabIndex]',
    'val currentStates = currentDomain.frames[tabIndex].levers'
)
content = content.replace(
    'val leverState = currentStates[leverIndex]',
    'val leverState = currentStates[leverIndex].isReversed'
)
content = content.replace(
    'val newStates = LeverFramePolicy.attemptToggle(tabDef, currentStates, currentDomain.blockStates[tabIndex], leverIndex, targetState)',
    'val newStates = LeverFramePolicy.attemptToggle(tabDef, currentStates, currentDomain.frames[tabIndex].blocks, leverIndex, targetState)'
)
content = content.replace(
    'val updatedAllStates = currentDomain.leverStates.toMutableList()',
    'val updatedFrames = currentDomain.frames.toMutableList()'
)
content = content.replace(
    'updatedAllStates[tabIndex] = newStates',
    'updatedFrames[tabIndex] = updatedFrames[tabIndex].copy(levers = newStates)'
)
content = content.replace(
    'updatedAllStates[uiState.selectedTabIndex]',
    'updatedFrames[uiState.selectedTabIndex].levers'
)
content = content.replace(
    'currentDomain.blockStates[uiState.selectedTabIndex]',
    'updatedFrames[uiState.selectedTabIndex].blocks'
)
content = content.replace(
    'currentDomain.copy(leverStates = updatedAllStates, conflictingLevers = conflicts)',
    'currentDomain.copy(frames = updatedFrames, conflictingLevers = conflicts)'
)

# toggleManualLock
content = content.replace(
    'val updatedLocks = currentDomain.manualLocks.toMutableList()\n            val tabLocks = updatedLocks[tabIndex].copyOf()\n            tabLocks[leverIndex] = !tabLocks[leverIndex]\n            updatedLocks[tabIndex] = tabLocks\n            currentDomain.copy(manualLocks = updatedLocks)',
    'val updatedFrames = currentDomain.frames.toMutableList()\n            val frame = updatedFrames[tabIndex]\n            val updatedLevers = frame.levers.toMutableList()\n            updatedLevers[leverIndex] = updatedLevers[leverIndex].copy(isManuallyLocked = !updatedLevers[leverIndex].isManuallyLocked)\n            updatedFrames[tabIndex] = frame.copy(levers = updatedLevers)\n            currentDomain.copy(frames = updatedFrames)'
)

# toggleBlockState
content = content.replace(
    'if (tabIndex in currentDomain.blockStates.indices && blockIndex in currentDomain.blockStates[tabIndex].indices) {',
    'if (tabIndex in currentDomain.frames.indices && blockIndex in currentDomain.frames[tabIndex].blocks.indices) {'
)
content = content.replace(
    'val newBlockStates = currentDomain.blockStates.map { it.copyOf() }.toMutableList()\n                newBlockStates[tabIndex][blockIndex] = !newBlockStates[tabIndex][blockIndex]',
    'val updatedFrames = currentDomain.frames.toMutableList()\n                val frame = updatedFrames[tabIndex]\n                val newBlocks = frame.blocks.toMutableList()\n                newBlocks[blockIndex] = newBlocks[blockIndex].copy(isOccupied = !newBlocks[blockIndex].isOccupied)'
)
content = content.replace(
    'val newLeverStates = currentDomain.leverStates.map { it.copyOf() }.toMutableList()',
    'val newLevers = frame.levers.toMutableList()'
)
content = content.replace(
    'val currentConflicts = Interlocking.getConflictingLevers(tabDef, newLeverStates[tabIndex], newBlockStates[tabIndex])',
    'val currentConflicts = Interlocking.getConflictingLevers(tabDef, newLevers, newBlocks)'
)
content = content.replace(
    'if (leverDef.autoReverser && newLeverStates[tabIndex][leverIdx]) {',
    'if (leverDef.autoReverser && newLevers[leverIdx].isReversed) {'
)
content = content.replace(
    'newLeverStates[tabIdx][leverIdx] = false',
    'newLevers[leverIdx] = newLevers[leverIdx].copy(isReversed = false)'
)
content = content.replace(
    'newLeverStates[uiState.selectedTabIndex]',
    '(if (uiState.selectedTabIndex == tabIndex) newLevers else currentDomain.frames[uiState.selectedTabIndex].levers)'
)
content = content.replace(
    'newBlockStates[uiState.selectedTabIndex]',
    '(if (uiState.selectedTabIndex == tabIndex) newBlocks else currentDomain.frames[uiState.selectedTabIndex].blocks)'
)
content = content.replace(
    'currentDomain.copy(\n                    blockStates = newBlockStates,\n                    leverStates = newLeverStates,\n                    conflictingLevers = conflicts\n                )',
    'updatedFrames[tabIndex] = frame.copy(blocks = newBlocks, levers = newLevers)\n                currentDomain.copy(frames = updatedFrames, conflictingLevers = conflicts)'
)

# tabSelected
content = content.replace(
    'currentDomain.leverStates[index]',
    'currentDomain.frames[index].levers'
)
content = content.replace(
    'currentDomain.blockStates[index]',
    'currentDomain.frames[index].blocks'
)

with open('shared/src/commonMain/kotlin/org/edranor/leverframe/AppViewModel.kt', 'w') as f:
    f.write(content)
