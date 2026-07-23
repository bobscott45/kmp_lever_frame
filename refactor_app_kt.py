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

app_replacements = [
    (
        '''                LaunchedEffect(domainState.blockStates) {
                    val currentBlocks = domainState.blockStates''',
        '''                LaunchedEffect(domainState.frames) {
                    val currentBlocks = domainState.frames.map { it.blocks.map { b -> b.isOccupied }.toBooleanArray() }'''
    ),
    (
        '''                                                leverStates = domainState.leverStates[uiState.selectedTabIndex],
                                                blockStates = domainState.blockStates.getOrNull(uiState.selectedTabIndex) ?: BooleanArray(0),''',
        '''                                                levers = domainState.frames.getOrNull(uiState.selectedTabIndex)?.levers ?: emptyList(),
                                                blocks = domainState.frames.getOrNull(uiState.selectedTabIndex)?.blocks ?: emptyList(),'''
    ),
    (
        '''                                            leverStates = domainState.leverStates[uiState.selectedTabIndex],
                                            blockStates = domainState.blockStates.getOrNull(uiState.selectedTabIndex) ?: BooleanArray(0),''',
        '''                                            levers = domainState.frames.getOrNull(uiState.selectedTabIndex)?.levers ?: emptyList(),
                                            blocks = domainState.frames.getOrNull(uiState.selectedTabIndex)?.blocks ?: emptyList(),'''
    ),
    (
        '''                                leverStates = domainState.leverStates[uiState.selectedTabIndex],
                                blockStates = domainState.blockStates.getOrNull(uiState.selectedTabIndex) ?: BooleanArray(0),''',
        '''                                levers = domainState.frames.getOrNull(uiState.selectedTabIndex)?.levers ?: emptyList(),
                                blocks = domainState.frames.getOrNull(uiState.selectedTabIndex)?.blocks ?: emptyList(),'''
    ),
    (
        '''        val leverStates = domainState.leverStates.getOrNull(uiState.selectedTabIndex)
        val manualLocks = domainState.manualLocks.getOrNull(uiState.selectedTabIndex)
        
        if (leverStates != null && manualLocks != null) {''',
        '''        val levers = domainState.frames.getOrNull(uiState.selectedTabIndex)?.levers
        
        if (levers != null) {'''
    ),
    (
        '''                    currentTabDef.levers.forEachIndexed { index, leverDef ->
                        val isReversed = leverStates[index]
                        val isManuallyLocked = manualLocks[index]
                        val isSystemLocked = !Interlocking.evaluate(currentTabDef, leverStates, domainState.blockStates.getOrNull(uiState.selectedTabIndex) ?: BooleanArray(0), index, !isReversed)''',
        '''                    currentTabDef.levers.forEachIndexed { index, leverDef ->
                        val isReversed = levers[index].isReversed
                        val isManuallyLocked = levers[index].isManuallyLocked
                        val blocks = domainState.frames.getOrNull(uiState.selectedTabIndex)?.blocks ?: emptyList()
                        val isSystemLocked = !Interlocking.evaluate(currentTabDef, levers, blocks, index, !isReversed)'''
    ),
    (
        '''        val blockStates = domainState.blockStates.getOrNull(uiState.selectedTabIndex)
        
        if (blockStates != null && currentTabDef.blocks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentTabDef.blocks.forEachIndexed { index, blockDef ->
                    val isOccupied = blockStates[index]''',
        '''        val blocks = domainState.frames.getOrNull(uiState.selectedTabIndex)?.blocks
        
        if (blocks != null && currentTabDef.blocks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentTabDef.blocks.forEachIndexed { index, blockDef ->
                    val isOccupied = blocks[index].isOccupied'''
    )
]

patch_file('shared/src/commonMain/kotlin/org/edranor/leverframe/App.kt', app_replacements)
