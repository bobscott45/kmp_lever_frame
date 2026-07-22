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
def swap_blocks(tab, idx_a, idx_b):
    blocks = tab['blocks']
    blocks[idx_a], blocks[idx_b] = blocks[idx_b], blocks[idx_a]
    
    # Update schematic elements
    for elem in tab.get('schematic_elements', []):
        if elem.get('linked_block') == idx_a:
            elem['linked_block'] = idx_b
        elif elem.get('linked_block') == idx_b:
            elem['linked_block'] = idx_a

    # Update interlocking rules
    for lever in tab.get('levers', []):
        for rule in lever.get('interlocking', []):
            if rule.get('target_type') == 'BLOCK':
                if rule.get('target') == idx_a:
                    rule['target'] = idx_b
                elif rule.get('target') == idx_b:
                    rule['target'] = idx_a
            if rule.get('alt_target_type') == 'BLOCK':
                if rule.get('alt_target') == idx_a:
                    rule['alt_target'] = idx_b
                elif rule.get('alt_target') == idx_b:
                    rule['alt_target'] = idx_a

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

def move_block(tab, from_idx, to_idx):
    if from_idx == to_idx: return
    step = 1 if to_idx > from_idx else -1
    for i in range(from_idx, to_idx, step):
        swap_blocks(tab, i, i + step)

def move_lever(tab, from_idx, to_idx):
    if from_idx == to_idx: return
    step = 1 if to_idx > from_idx else -1
    for i in range(from_idx, to_idx, step):
        swap_levers(tab, i, i + step)

# North Junction
nj = config['tabs'][0]
# Current blocks: 0: UP APPROACH (UA), 1: UP MAIN (UM), 2: TO YARD (TY), 3: DOWN APPROACH (DA), 4: DOWN MAIN (DM), 5: UP MAIN AHEAD (UMA)
# Target: put UMA between UA and TY.
# Since UA is 0, UM is 1, TY is 2. UMA should go between UA (0) and TY (2)? Wait, UM is 1. Does "between UA and TY" mean index 1 (pushing UM down) or after UM?
# "put nort jctn UMA block index between UA and TY."
# UA is 0, TY is 2. So UM is at 1. If UMA goes between UA and TY, maybe they want UA, UM, UMA, TY?
# Wait, "between UA and TY". The current order is UA (0), UM (1), TY (2).
# Wait, maybe they mean between UM and TY?
# Let's assume UA, UM, UMA, TY, DA, DM. So UMA (5) moves to index 2.
move_block(nj, 5, 2)

# "reverse indexes of DA and DM"
# DA and DM are the last two blocks.
# If UMA moved to 2, then TY is 3, DA is 4, DM is 5.
# Swapping DA and DM means swapping 4 and 5.
swap_blocks(nj, 4, 5)

# South Box
sb = config['tabs'][1]
# Current levers: 
# 0: SHUNT AHEAD
# 1: YARD CROSSOVER
# 2: SIDING EXIT
# 3: YARD HOME
# 4: SIDING HOME
# 5: YARD DISTANT

# Target order:
# 1. YARD DISTANT (was 5)
# 2. YARD HOME (was 3)
# 3. SIDING HOME (was 4)
# 4. YARD CROSSOVER (was 1)
# 5. SHUNT AHEAD (was 0)
# 6. SIDING EXIT (was 2)

# Let's move them to their target positions one by one.
# Current: 0:SA, 1:YC, 2:SE, 3:YH, 4:SH, 5:YD
# Move YD (5) to 0 -> YD, SA, YC, SE, YH, SH
move_lever(sb, 5, 0)
# Now: 0:YD, 1:SA, 2:YC, 3:SE, 4:YH, 5:SH
# Want YH at 1. YH is currently 4.
move_lever(sb, 4, 1)
# Now: 0:YD, 1:YH, 2:SA, 3:YC, 4:SE, 5:SH
# Want SH at 2. SH is currently 5.
move_lever(sb, 5, 2)
# Now: 0:YD, 1:YH, 2:SH, 3:SA, 4:YC, 5:SE
# Want YC at 3. YC is currently 4.
move_lever(sb, 4, 3)
# Now: 0:YD, 1:YH, 2:SH, 3:YC, 4:SA, 5:SE
# Want SA at 4. It is currently at 4.
# Want SE at 5. It is currently at 5.
# Order is now: YARD DISTANT, YARD HOME, SIDING HOME, YARD CROSSOVER, SHUNT AHEAD, SIDING EXIT.

new_json_str = json.dumps(config)
new_content = content[:match.start(1)] + new_json_str + content[match.end(1):]

with open('shared/src/commonMain/kotlin/org/edranor/leverframe/ConfigManager.kt', 'w') as f:
    f.write(new_content)

print("Successfully updated ConfigManager.kt")
