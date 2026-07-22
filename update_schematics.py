import re

def update_file(filepath, is_editor):
    with open(filepath, 'r') as f:
        content = f.read()

    # Rename TURNOUT_RIGHT to TURNOUT_LEFT
    if is_editor:
        turnout_right_pattern = r'("TURNOUT_RIGHT" -> \{.*?\})'
        match = re.search(turnout_right_pattern, content, re.DOTALL)
        if match:
            turnout_left_str = match.group(1).replace('"TURNOUT_RIGHT"', '"TURNOUT_LEFT"')
            turnout_right_str = turnout_left_str.replace('"TURNOUT_LEFT"', '"TURNOUT_RIGHT"').replace('py - gridSizeY / 2', 'py + gridSizeY * 1.5f').replace('py + gridSizeY * 0.15f', 'py + gridSizeY * 0.85f')
            content = content.replace(match.group(1), turnout_left_str + '\n                        ' + turnout_right_str)
            
        bracket_pattern = r'("BRACKET_SIGNAL" -> \{.*?\})'
        match = re.search(bracket_pattern, content, re.DOTALL)
        if match:
            bracket_left_str = match.group(1).replace('"BRACKET_SIGNAL"', '"BRACKET_SIGNAL_LEFT"')
            # add rotate to branch arrow
            branch_arrow_pattern = r'(val arrowWidth2.*?\n)(.*?)(drawLine\(arrowColor2.*?)(if \(element\.linked_lever_2)'
            
            def repl_arrow(m):
                return m.group(1) + m.group(2) + "                            rotate(-45f, Offset(cx2, cy2)) {\n                                " + m.group(3).replace('                            drawLine', 'drawLine').replace('\n                            ', '\n                                ') + "                            }\n                            " + m.group(4)
            bracket_left_str = re.sub(branch_arrow_pattern, repl_arrow, bracket_left_str, flags=re.DOTALL)
            
            bracket_right_str = bracket_left_str.replace('"BRACKET_SIGNAL_LEFT"', '"BRACKET_SIGNAL_RIGHT"').replace('py + gridSizeY * 0.15f', 'py + gridSizeY * 0.85f').replace('py - gridSizeY * 0.4f', 'py + gridSizeY * 1.25f').replace('rotate(-45f', 'rotate(45f')
            
            content = content.replace(match.group(1), bracket_left_str + '\n                        ' + bracket_right_str)
            
        content = content.replace('"BRACKET_SIGNAL"', '"BRACKET_SIGNAL_LEFT", "BRACKET_SIGNAL_RIGHT"').replace('"TURNOUT_RIGHT"', '"TURNOUT_LEFT", "TURNOUT_RIGHT"')

    else:
        # SchematicScreen
        turnout_right_pattern = r'("TURNOUT_RIGHT" -> \{.*?\n                        \})'
        match = re.search(turnout_right_pattern, content, re.DOTALL)
        if match:
            turnout_left_str = match.group(1).replace('"TURNOUT_RIGHT"', '"TURNOUT_LEFT"')
            turnout_right_str = turnout_left_str.replace('"TURNOUT_LEFT"', '"TURNOUT_RIGHT"').replace('py - gridSizeY / 2', 'py + gridSizeY * 1.5f').replace('element.y - 1', 'element.y + 1').replace('py + gridSizeY * 0.15f', 'py + gridSizeY * 0.85f')
            content = content.replace(match.group(1), turnout_left_str + '\n                        ' + turnout_right_str)
            
        bracket_pattern = r'("BRACKET_SIGNAL" -> \{.*?\n                        \})'
        match = re.search(bracket_pattern, content, re.DOTALL)
        if match:
            bracket_left_str = match.group(1).replace('"BRACKET_SIGNAL"', '"BRACKET_SIGNAL_LEFT"')
            # add rotate to branch arrow
            branch_arrow_pattern = r'(val arrowWidth2.*?\n)(.*?)(drawLine\(arrowColor2.*?)(drawText\(\n)'
            
            def repl_arrow(m):
                return m.group(1) + m.group(2) + "                            rotate(-45f, Offset(cx2, cy2)) {\n                                " + m.group(3).replace('                            drawLine', 'drawLine').replace('\n                            ', '\n                                ') + "                            }\n                            " + m.group(4)
            bracket_left_str = re.sub(branch_arrow_pattern, repl_arrow, bracket_left_str, flags=re.DOTALL)
            
            bracket_right_str = bracket_left_str.replace('"BRACKET_SIGNAL_LEFT"', '"BRACKET_SIGNAL_RIGHT"').replace('py + gridSizeY * 0.15f', 'py + gridSizeY * 0.85f').replace('py - gridSizeY * 0.4f', 'py + gridSizeY * 1.25f').replace('rotate(-45f', 'rotate(45f')
            
            content = content.replace(match.group(1), bracket_left_str + '\n                        ' + bracket_right_str)
            
    with open(filepath, 'w') as f:
        f.write(content)

update_file('shared/src/commonMain/kotlin/org/edranor/leverframe/SchematicEditorScreen.kt', True)
update_file('shared/src/commonMain/kotlin/org/edranor/leverframe/SchematicScreen.kt', False)

