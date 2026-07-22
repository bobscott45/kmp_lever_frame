import json
import re

with open('shared/src/commonMain/kotlin/org/edranor/leverframe/ConfigManager.kt', 'r') as f:
    content = f.read()

# Extract the JSON string
match = re.search(r'val defaultPrototypicalConfigJson = """(.*?)"""', content, re.DOTALL)
if not match:
    print("Could not find defaultPrototypicalConfigJson")
    exit(1)

json_str = match.group(1).replace('\n', '\\n')
config = json.loads(json_str)

# HELPER FUNCTIONS
def swap_levers(tab, idx_a, idx_b):
    levers = tab['levers']
    levers[idx_a], levers[idx_b] = levers[idx_b], levers[idx_a]
    
    # Update schematic elements
    for elem in tab.get('schematic_elements', []):
        if elem.get('linked_lever') == idx_a:
            elem['linked_lever'] = idx_b
        elif elem.get('linked_lever') == idx_b:
            elem['linked_lever'] = idx_a
            
        if elem.get('linked_lever_2') == idx_a:
            elem['linked_lever_2'] = idx_b
        elif elem.get('linked_lever_2') == idx_b:
            elem['linked_lever_2'] = idx_a

    # Update interlocking rules
    for lever in tab.get('levers', []):
        for rule in lever.get('interlocking', []):
            if rule.get('target_type', 'LEVER') == 'LEVER':
                if rule.get('target') == idx_a:
                    rule['target'] = idx_b
                elif rule.get('target') == idx_b:
                    rule['target'] = idx_a
            if rule.get('alt_target_type', 'LEVER') == 'LEVER':
                if rule.get('alt_target') == idx_a:
                    rule['alt_target'] = idx_b
                elif rule.get('alt_target') == idx_b:
                    rule['alt_target'] = idx_a

def move_lever(tab, from_idx, to_idx):
    if from_idx == to_idx: return
    step = 1 if to_idx > from_idx else -1
    for i in range(from_idx, to_idx, step):
        swap_levers(tab, i, i + step)

# North Junction
nj = config['tabs'][0]

# Current levers: 
# 0: UP DISTANT
# 1: UP MAIN HOME
# 2: FPL FOR POINTS 4
# 3: JUNCTION POINTS
# 4: TO YARD HOME
# Target: move TO YARD HOME (4) to index 2 (between UP MAIN HOME and FPL).
# Wait, "position 3" usually means 1-indexed 3, which is 0-indexed 2.
# "between up main home (1) and fpl (2)" means we want it to go to index 2.
# So FPL goes to 3, JUNCTION POINTS goes to 4.
# We just need to move_lever from 4 to 2.
move_lever(nj, 4, 2)

new_json_str = json.dumps(config)
new_content = content[:match.start(1)] + new_json_str + content[match.end(1):]

with open('shared/src/commonMain/kotlin/org/edranor/leverframe/ConfigManager.kt', 'w') as f:
    f.write(new_content)

print("Successfully updated ConfigManager.kt")
