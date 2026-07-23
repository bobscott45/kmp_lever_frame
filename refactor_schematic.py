import re

filepath = 'shared/src/commonMain/kotlin/org/edranor/leverframe/SchematicScreen.kt'
with open(filepath, 'r') as f:
    content = f.read()

content = content.replace('blockStates.indices', 'blocks.indices')
content = content.replace('blockStates[blockIdx]', 'blocks[blockIdx].isOccupied')
content = content.replace('blockStates[element.linkedBlock]', 'blocks[element.linkedBlock].isOccupied')
content = content.replace('leverStates.indices', 'levers.indices')
content = content.replace('leverStates[element.linkedLever]', 'levers[element.linkedLever].isReversed')
content = content.replace('leverStates[element.linkedLever2]', 'levers[element.linkedLever2].isReversed')

with open(filepath, 'w') as f:
    f.write(content)

filepath_ls = 'shared/src/commonMain/kotlin/org/edranor/leverframe/LeverStatusScreen.kt'
with open(filepath_ls, 'r') as f:
    content_ls = f.read()

# I already replaced the signature. Let's make sure there are no other references.
content_ls = content_ls.replace('leverStates', 'levers')
content_ls = content_ls.replace('blockStates', 'blocks')

with open(filepath_ls, 'w') as f:
    f.write(content_ls)
