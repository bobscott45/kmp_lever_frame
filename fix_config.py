lines = open("shared/src/commonMain/kotlin/org/edranor/leverframe/ConfigurationScreen.kt").readlines()

start_del = -1
for i, line in enumerate(lines):
    if "private fun JsonConfig.withoutRules(): JsonConfig {" in line:
        start_del = i
        break
if start_del != -1:
    del lines[start_del:]

start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if "Column(modifier = Modifier.fillMaxSize()) {" in line and lines[i-1].strip() == "} else {":
        start_idx = i
    if "Text(\"Reset ALL Frames to Factory Defaults\", textAlign = androidx.compose.ui.text.style.TextAlign.Center)" in line:
        end_idx = i + 3 # up to the closing brace of the button, then the column's closing brace is at i+4
        break

if start_idx != -1 and end_idx != -1:
    replacement = """                    FrameSetupView(
                        config = config,
                        selectedFrameIndex = selectedFrameIndex,
                        onSelectedFrameIndexChange = { selectedFrameIndex = it },
                        selectedFrameConfigTab = selectedFrameConfigTab,
                        onSelectedFrameConfigTabChange = { selectedFrameConfigTab = it },
                        onConfigChange = { config = it },
                        onEditLever = { editingLeverIndex = it },
                        onEditBlock = { editingBlockIndex = it },
                        onShowFramesResetWarning = { showFramesResetWarning = true }
                    )
"""
    new_lines = lines[:start_idx] + [replacement] + lines[end_idx:]
    open("shared/src/commonMain/kotlin/org/edranor/leverframe/ConfigurationScreen.kt", "w").writelines(new_lines)
    print("Success")
else:
    print(f"Failed to find match: start_idx={start_idx}, end_idx={end_idx}")
