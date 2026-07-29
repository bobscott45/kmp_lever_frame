import os
import subprocess
import re

result = subprocess.run(["find", "shared/src", "-type", "f", "-name", "*.kt"], capture_output=True, text=True)
files = result.stdout.splitlines()

for filepath in files:
    with open(filepath, 'r') as f:
        content = f.read()
    
    if "package org.edranor.leverframe" in content:
        # Check if import org.edranor.leverframe.* is missing
        if "import org.edranor.leverframe.*" not in content:
            # We split by the package declaration (up to the newline)
            parts = re.split(r'(package\s+org\.edranor\.leverframe[a-zA-Z0-9_.]*\n)', content, maxsplit=1)
            if len(parts) == 3:
                content = parts[0] + parts[1] + "import org.edranor.leverframe.*\n" + parts[2]
                with open(filepath, 'w') as f:
                    f.write(content)

