import re
import os

# 1. LeverFrameState.kt
with open('shared/src/commonMain/kotlin/org/edranor/leverframe/LeverFrameState.kt', 'r') as f:
    content = f.read()

domain_classes = """data class DomainState(
    val frames: List<DomainFrame> = emptyList(),
    val conflictingLevers: List<Int> = emptyList()
)

data class DomainFrame(
    val id: Int,
    val levers: List<DomainLever>,
    val blocks: List<DomainBlock>
)

data class DomainLever(
    val id: Int,
    val isReversed: Boolean,
    val isManuallyLocked: Boolean = false
)

data class DomainBlock(
    val id: Int,
    val isOccupied: Boolean
)"""

content = re.sub(
    r'data class DomainState\([\s\S]*?\n\)',
    domain_classes,
    content
)

with open('shared/src/commonMain/kotlin/org/edranor/leverframe/LeverFrameState.kt', 'w') as f:
    f.write(content)

# 2. Interlocking.kt
with open('shared/src/commonMain/kotlin/org/edranor/leverframe/Interlocking.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'fun evaluate(\n        tab: TabDef,\n        states: BooleanArray,\n        blockStates: BooleanArray,',
    'fun evaluate(\n        tab: TabDef,\n        levers: List<DomainLever>,\n        blocks: List<DomainBlock>,'
)
content = content.replace(
    'fun getConflictingLevers(tab: TabDef, states: BooleanArray, blockStates: BooleanArray): List<Int>',
    'fun getConflictingLevers(tab: TabDef, levers: List<DomainLever>, blocks: List<DomainBlock>): List<Int>'
)

content = re.sub(
    r'val newStates = states\.copyOf\(\)\s*newStates\[leverIndex\] = attemptingState',
    r'val newLevers = levers.map { if (it.id == leverIndex) it.copy(isReversed = attemptingState) else it }',
    content
)

content = content.replace('states, blockStates', 'levers, blocks')
content = content.replace('newStates, blockStates', 'newLevers, blocks')

content = re.sub(
    r'if \(states\[i\]\) \{',
    r'if (levers[i].isReversed) {',
    content
)

content = re.sub(
    r'states\.getOrNull\(condition\.targetIndex\) \?\: false',
    r'levers.getOrNull(condition.targetIndex)?.isReversed ?: false',
    content
)
content = re.sub(
    r'blockStates\.getOrNull\(condition\.targetIndex\) \?\: false',
    r'blocks.getOrNull(condition.targetIndex)?.isOccupied ?: false',
    content
)
content = re.sub(
    r'states\.getOrNull\(condition\.altTargetIndex\) \?\: false',
    r'levers.getOrNull(condition.altTargetIndex)?.isReversed ?: false',
    content
)
content = re.sub(
    r'blockStates\.getOrNull\(condition\.altTargetIndex\) \?\: false',
    r'blocks.getOrNull(condition.altTargetIndex)?.isOccupied ?: false',
    content
)

with open('shared/src/commonMain/kotlin/org/edranor/leverframe/Interlocking.kt', 'w') as f:
    f.write(content)

# 3. InterlockingTest.kt
with open('shared/src/commonTest/kotlin/org/edranor/leverframe/InterlockingTest.kt', 'r') as f:
    content = f.read()

content = content.replace('val states = booleanArrayOf(', 'val levers = createLevers(')
content = content.replace('val states1 = booleanArrayOf(', 'val levers1 = createLevers(')
content = content.replace('val states2 = booleanArrayOf(', 'val levers2 = createLevers(')
content = content.replace('val states3 = booleanArrayOf(', 'val levers3 = createLevers(')
content = content.replace('val blockStatesEmpty = booleanArrayOf(false)', 'val blocksEmpty = createBlocks(false)')
content = content.replace('val blockStatesOccupied = booleanArrayOf(true)', 'val blocksOccupied = createBlocks(true)')
content = content.replace('states[0] = true', 'val leversMod = createLevers(true, false)')

# fix usages of states array replacement
content = content.replace('states1, booleanArrayOf()', 'levers1, emptyList()')
content = content.replace('states2, booleanArrayOf()', 'levers2, emptyList()')
content = content.replace('states3, booleanArrayOf()', 'levers3, emptyList()')
content = content.replace('states, booleanArrayOf()', 'levers, emptyList()')
content = content.replace('states, blockStatesEmpty', 'levers, blocksEmpty')
content = content.replace('states, blockStatesOccupied', 'levers, blocksOccupied')

# Since states array is immutable list of objects now, states[0] = true logic needs a new list
content = content.replace('assertFalse(Interlocking.evaluate(tab, states, booleanArrayOf(), 1, true))', 
                          'assertFalse(Interlocking.evaluate(tab, leversMod ?: levers, emptyList(), 1, true))')
content = content.replace('assertTrue(Interlocking.evaluate(tab, states, booleanArrayOf(), 0, false))', 
                          'assertTrue(Interlocking.evaluate(tab, leversMod ?: levers, emptyList(), 0, false))')

helpers = """
    private fun createLevers(vararg reversed: Boolean): List<DomainLever> = reversed.mapIndexed { i, b -> DomainLever(i, b) }
    private fun createBlocks(vararg occupied: Boolean): List<DomainBlock> = occupied.mapIndexed { i, b -> DomainBlock(i, b) }
"""
content = re.sub(r'class InterlockingTest \{', 'class InterlockingTest {' + helpers, content)

with open('shared/src/commonTest/kotlin/org/edranor/leverframe/InterlockingTest.kt', 'w') as f:
    f.write(content)

