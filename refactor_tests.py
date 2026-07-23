import re

filepath = 'shared/src/commonTest/kotlin/org/edranor/leverframe/AppViewModelTest.kt'
with open(filepath, 'r') as f:
    content = f.read()

# I will replace all instances of `.leverStates[0][...]` with `.frames[0].levers[...].isReversed`
content = re.sub(r'\.leverStates\[(\d+)\]\[(\d+)\]', r'.frames[\1].levers[\2].isReversed', content)
content = re.sub(r'\.manualLocks\[(\d+)\]\[(\d+)\]', r'.frames[\1].levers[\2].isManuallyLocked', content)
content = re.sub(r'\.blockStates\[(\d+)\]\[(\d+)\]', r'.frames[\1].blocks[\2].isOccupied', content)

with open(filepath, 'w') as f:
    f.write(content)

filepath_it = 'shared/src/commonTest/kotlin/org/edranor/leverframe/InterlockingTest.kt'
with open(filepath_it, 'r') as f:
    content_it = f.read()

# I need to replace `val levers = listOf(\n            LeverDef` with `val leverDefs = listOf(\n            LeverDef`
content_it = content_it.replace('val levers = listOf(\n            LeverDef', 'val leverDefs = listOf(\n            LeverDef')
# and replace `val levers = listOf(` with `val leverDefs = listOf(` when followed by `LeverDef`
# Wait, let's just use regex to match `val levers = listOf(` where the next line has `LeverDef`
content_it = re.sub(r'val levers = listOf\(\s*LeverDef', r'val leverDefs = listOf(\n            LeverDef', content_it)

# And then we need to replace `Interlocking.evaluate(TabDef(levers),` with `Interlocking.evaluate(TabDef(leverDefs),`
content_it = content_it.replace('TabDef(levers)', 'TabDef(leverDefs)')
content_it = content_it.replace('TabDef(levers = levers', 'TabDef(levers = leverDefs')

with open(filepath_it, 'w') as f:
    f.write(content_it)
