import os
import subprocess
import re

result = subprocess.run(["find", "shared/src", "-type", "f", "-name", "*.kt"], capture_output=True, text=True)
files = result.stdout.splitlines()

for filepath in files:
    with open(filepath, 'r') as f:
        lines = f.readlines()
        
    new_lines = []
    changed = False
    for line in lines:
        # Match explicit imports in org.edranor.leverframe (but not openlcb, and not wildcards)
        if line.startswith("import org.edranor.leverframe.") and not line.strip().endswith(".*"):
            changed = True
            continue # drop it
        
        # Also fix the weird nested class bug. ConfigManager.kt has `RestoreOverride` which is actually `PersistenceService.RestoreOverride`.
        # Since PersistenceService moved, if it's imported via wildcard, nested classes might need qualification or explicit import.
        
        new_lines.append(line)
        
    if changed:
        with open(filepath, 'w') as f:
            f.writelines(new_lines)

