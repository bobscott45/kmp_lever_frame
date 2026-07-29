import os
import subprocess
import re

mapping = {
    # ui.theme
    "Theme.kt": "ui.theme",
    
    # ui.components
    "LeverComponent.kt": "ui.components",
    "SoundPlayer.kt": "ui.components",
    "SoundPlayer.android.kt": "ui.components",
    "SoundPlayer.ios.kt": "ui.components",
    "SoundPlayer.jvm.kt": "ui.components",
    "FilePicker.kt": "ui.components",
    "FilePicker.android.kt": "ui.components",
    "FilePicker.ios.kt": "ui.components",
    "FilePicker.jvm.kt": "ui.components",
    
    # ui.screens.main
    "MainScreenViews.kt": "ui.screens.main",
    "LeverStatusScreen.kt": "ui.screens.main",
    "SystemStatusScreen.kt": "ui.screens.main",
    
    # ui.screens.schematic
    "SchematicScreen.kt": "ui.screens.schematic",
    "SchematicDrawingExtensions.kt": "ui.screens.schematic",
    
    # ui.screens.editor
    "ConfigurationScreen.kt": "ui.screens.editor",
    "ConfigurationFrameViews.kt": "ui.screens.editor",
    "SystemSettingsViews.kt": "ui.screens.editor",
    "LeverDetailScreen.kt": "ui.screens.editor",
    "BlockDetailScreen.kt": "ui.screens.editor",
    "ClauseBuilderView.kt": "ui.screens.editor",
    "AlternativeRuleViews.kt": "ui.screens.editor",
    "SchematicEditorScreen.kt": "ui.screens.editor",
    "SchematicElementEditorDialog.kt": "ui.screens.editor",
    
    # domain.engine
    "Interlocking.kt": "domain.engine",
    "RuleValidator.kt": "domain.engine",
    "NxRoutingEngine.kt": "domain.engine",
    "InterlockingTest.kt": "domain.engine",
    "RuleValidatorTest.kt": "domain.engine",
    "NxRoutingEngineTest.kt": "domain.engine",
    
    # domain.models
    "LeverFrameState.kt": "domain.models",
    "LeverFramePolicy.kt": "domain.models",
    "LeverFramePolicyTest.kt": "domain.models",
    
    # domain.parser
    "Ast.kt": "domain.parser",
    "FormulaParser.kt": "domain.parser",
    "FormulaParserTest.kt": "domain.parser",
    
    # config
    "ConfigManager.kt": "config",
    "ConfigurationMutators.kt": "config",
    "ConfigManagerTest.kt": "config",
    
    # network
    "LccNode.kt": "network",
    "NetworkEventProcessor.kt": "network",
    "NetworkEventProcessorTest.kt": "network",
    
    # services
    "InterlockingService.kt": "services",
    "ConfigurationService.kt": "services",
    "NxRoutingService.kt": "services",
    "PersistenceService.kt": "services",
    "PersistenceServiceTest.kt": "services",
    
    # di
    "AppModule.kt": "di"
}

all_new_packages = set(mapping.values())
import_block = "\n".join([f"import org.edranor.leverframe.{pkg}.*" for pkg in all_new_packages]) + "\n"

def process_all_files():
    # Find all .kt files in shared/src
    result = subprocess.run(["find", "shared/src", "-type", "f", "-name", "*.kt"], capture_output=True, text=True)
    files = result.stdout.splitlines()
    
    # First pass: collect moves
    moves = []
    for filepath in files:
        if "org/edranor/leverframe" not in filepath:
            continue
            
        filename = os.path.basename(filepath)
        if filename in mapping:
            sub_pkg = mapping[filename]
            # Replace org/edranor/leverframe with org/edranor/leverframe/sub_pkg
            # But handle cases where it's already in di or services correctly if it exists
            # Actually, easiest way is to find the base directory up to org/edranor/leverframe
            parts = filepath.split("org/edranor/leverframe/")
            base_dir = parts[0] + "org/edranor/leverframe/"
            
            # The current file might be in a subfolder already (like services or di)
            # We strip that out to get just the filename part
            new_dir = os.path.join(base_dir, sub_pkg.replace(".", "/"))
            new_filepath = os.path.join(new_dir, filename)
            
            if filepath != new_filepath:
                moves.append((filepath, new_filepath, sub_pkg))

    # Execute moves
    for old_path, new_path, sub_pkg in moves:
        os.makedirs(os.path.dirname(new_path), exist_ok=True)
        subprocess.run(["git", "mv", old_path, new_path])
        print(f"Moved {old_path} -> {new_path}")

    # Second pass: update contents of ALL kotlin files in org/edranor/leverframe
    result = subprocess.run(["find", "shared/src", "-type", "f", "-name", "*.kt"], capture_output=True, text=True)
    all_files = result.stdout.splitlines()
    
    for filepath in all_files:
        if "org/edranor/leverframe" not in filepath:
            continue
            
        filename = os.path.basename(filepath)
        
        with open(filepath, 'r') as f:
            content = f.read()
            
        # Update package declaration
        if filename in mapping:
            sub_pkg = mapping[filename]
            new_package_decl = f"package org.edranor.leverframe.{sub_pkg}"
            # Regex to replace any package org.edranor.leverframe.* 
            content = re.sub(r'package\s+org\.edranor\.leverframe[a-zA-Z0-9_.]*', new_package_decl, content)
        else:
            # Files staying in root package
            content = re.sub(r'package\s+org\.edranor\.leverframe[a-zA-Z0-9_.]*', 'package org.edranor.leverframe', content)

        # Add wildcard imports just after the package declaration
        if "package org.edranor.leverframe" in content:
            # We split by package declaration and insert imports
            parts = re.split(r'(package\s+org\.edranor\.leverframe[a-zA-Z0-9_.]*\n)', content, maxsplit=1)
            if len(parts) == 3:
                # Add import block if not already present
                if "import org.edranor.leverframe.ui.theme.*" not in parts[2]:
                    content = parts[0] + parts[1] + "\n" + import_block + parts[2]
            
        with open(filepath, 'w') as f:
            f.write(content)

process_all_files()
