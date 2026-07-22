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

for tab_idx, tab in enumerate(config['tabs']):
    lever_prefix = "11" if tab_idx == 0 else "21"
    block_prefix = "12" if tab_idx == 0 else "22"
    
    for i, lever in enumerate(tab['levers']):
        lever_num = i + 1
        normal_hex = f"{lever_prefix}.{lever_num:02X}"
        reversed_hex = f"{lever_prefix}.{(lever_num + 0x80):02X}"
        lever['lcc_event_normal'] = normal_hex
        lever['lcc_event_reversed'] = reversed_hex
        
    for i, block in enumerate(tab['blocks']):
        block_num = i + 1
        empty_hex = f"{block_prefix}.{block_num:02X}"
        occupied_hex = f"{block_prefix}.{(block_num + 0x80):02X}"
        block['lcc_event_empty'] = empty_hex
        block['lcc_event_occupied'] = occupied_hex

new_json_str = json.dumps(config)
new_content = content[:match.start(1)] + new_json_str + content[match.end(1):]

with open('shared/src/commonMain/kotlin/org/edranor/leverframe/ConfigManager.kt', 'w') as f:
    f.write(new_content)

print("Successfully updated ConfigManager.kt with LCC Event defaults")
