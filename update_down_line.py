import json
import re

with open('shared/src/commonMain/kotlin/org/edranor/leverframe/ConfigManager.kt', 'r') as f:
    content = f.read()

match = re.search(r'val defaultPrototypicalConfigJson = """(.*?)"""', content, re.DOTALL)
if not match:
    print("Could not find defaultPrototypicalConfigJson")
    exit(1)

json_str = match.group(1).replace('\n', '\\n')
config = json.loads(json_str)

nj = config['tabs'][0]

# Remove DOWN HOME (index 7)
old_down_home = nj['levers'].pop(7)

# Update new DOWN HOME (index 6, was DOWN ADVANCED)
nj['levers'][6]['label'] = "DOWN\nHOME"
nj['levers'][6]['interlocking'] = old_down_home['interlocking']

# Update DOWN DISTANT (now index 7, was 8)
for rule in nj['levers'][7].get('interlocking', []):
    if rule.get('target_type', 'LEVER') == 'LEVER' and rule.get('target') == 7:
        rule['target'] = 6

# Update Schematic Elements for y=2 (Down Line)
for elem in nj['schematic_elements']:
    if elem.get('y') == 2:
        x = elem.get('x')
        if x == 5:
            elem['linked_lever'] = 7  # DOWN DISTANT moved to index 7
            # block should be 5
        elif x == 4:
            elem['type'] = "STRAIGHT_H"
            if 'linked_lever' in elem:
                del elem['linked_lever']
            elem['linked_block'] = 5
        elif x == 3:
            elem['linked_block'] = 5
        elif x == 2:
            elem['linked_block'] = 5
        elif x == 1:
            # Keeps type SIGNAL_LEFT, linked_lever 6, linked_block 4
            pass
        elif x == 0:
            # Keeps type STRAIGHT_H, linked_block 4
            pass

new_json_str = json.dumps(config)
new_content = content[:match.start(1)] + new_json_str + content[match.end(1):]

with open('shared/src/commonMain/kotlin/org/edranor/leverframe/ConfigManager.kt', 'w') as f:
    f.write(new_content)

print("Successfully updated ConfigManager.kt")
