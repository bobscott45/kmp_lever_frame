package org.example.project

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch

val BrassColor = Color(0xFFD4AF37)
val PaleBlue = Color(0xFF8CA8C4)

@Composable
@Preview
fun App() {
    val customColorScheme = darkColorScheme(
        primary = BrassColor,
        onPrimary = Color.Black,
        secondary = BrassColor,
        onSecondary = Color.Black,
        tertiary = BrassColor,
        onTertiary = Color.Black
    )
    MaterialTheme(colorScheme = customColorScheme) {
        var isConfigMode by remember { mutableStateOf(false) }
        var isStatusMode by remember { mutableStateOf(false) }
        var statusLeverIndex by remember { mutableStateOf<Int?>(null) }
        var configVersion by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            LccNode.initialize()
        }

        // Force recreation of tabs if we return from config mode AND saved changes
        var tabs by remember { mutableStateOf(ConfigManager.parseConfig(ConfigManager.toJsonString())) }
        
        LaunchedEffect(configVersion) {
            tabs = ConfigManager.parseConfig(ConfigManager.toJsonString())
        }

        var selectedTabIndex by remember { mutableStateOf(0) }
        
        // Hold states and locks for all tabs
        val allLeverStates = remember(configVersion) {
            val defaultStates = tabs.map { BooleanArray(it.second.levers.size) { false } }.toTypedArray()
            
            if (ConfigManager.currentConfig.restore_last_state && configVersion == 0) {
                val savedStates = ConfigManager.loadSavedLeverStates()
                if (savedStates != null && savedStates.size == tabs.size && savedStates.map { it.size } == defaultStates.map { it.size }.toList()) {
                    mutableStateListOf(*savedStates.toTypedArray())
                } else {
                    mutableStateListOf(*defaultStates)
                }
            } else {
                mutableStateListOf(*defaultStates)
            }
        }
        val allManualLocks = remember(configVersion) {
            mutableStateListOf(*tabs.map { BooleanArray(it.second.levers.size) { false } }.toTypedArray())
        }
        
        LaunchedEffect(allLeverStates.toList()) {
            if (ConfigManager.currentConfig.restore_last_state) {
                ConfigManager.saveCurrentLeverStates(allLeverStates.toList())
            }
        }
        
        var errorMessage by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(configVersion) {
            LccNode.externalEvents.collect { hexEventId ->
                try {
                    if (!ConfigManager.currentConfig.lcc_master) return@collect
                    tabs.forEachIndexed { tabIdx, tabPair ->
                        val tabDef = tabPair.second
                        tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                            if (!leverDef.lcc_enabled) return@forEachIndexed
                            var attemptState: Boolean? = null
                            if (leverDef.lcc_event_normal.isNotBlank()) {
                                val normalHex = LccNode.parseEventId(leverDef.lcc_event_normal)
                                if (normalHex == hexEventId) attemptState = false
                            }
                            if (leverDef.lcc_event_reversed.isNotBlank()) {
                                val reversedHex = LccNode.parseEventId(leverDef.lcc_event_reversed)
                                if (reversedHex == hexEventId) attemptState = true
                            }
                            
                            if (attemptState != null) {
                                println("Matched event $hexEventId to Lever $leverIdx (Attempt State: $attemptState)")
                                val leverStates = allLeverStates[tabIdx]
                                if (leverStates[leverIdx] != attemptState) {
                                    val policy = ConfigManager.currentConfig.conflict_policy
                                    val isValid = Interlocking.evaluate(tabDef, leverStates, leverIdx, attemptState)
                                    
                                    println("Lever $leverIdx state change: policy=$policy, isValid=$isValid")
                                    
                                    if (policy == 1 && !isValid) {
                                        println("External event ignored due to STRICT policy")
                                    } else {
                                        val newStates = leverStates.clone()
                                        newStates[leverIdx] = attemptState
                                        allLeverStates[tabIdx] = newStates
                                        println("Applied external event: lever $leverIdx to $attemptState (Policy $policy)")
                                    }
                                } else {
                                    println("Lever $leverIdx is already in state $attemptState")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Error processing external event: ${e.message}")
                }
            }
        }

        if (isConfigMode) {
            ConfigurationScreen(
                onClose = { isConfigMode = false },
                onSave = { 
                    configVersion++
                    isConfigMode = false 
                }
            )
        } else if (isStatusMode) {
            SystemStatusScreen(
                onClose = { isStatusMode = false }
            )
        } else if (statusLeverIndex != null) {
            val index = statusLeverIndex!!
            val currentTabDef = tabs[selectedTabIndex].second
            val leverDef = currentTabDef.levers[index]
            
            LeverStatusScreen(
                leverIndex = index,
                leverDef = leverDef,
                onClose = { statusLeverIndex = null },
                onLccEnabledChange = { checked ->
                    val newTabsJson = ConfigManager.currentConfig.tabs.toMutableList()
                    val currentTabJson = newTabsJson[selectedTabIndex].copy()
                    val newLeversJson = currentTabJson.levers.toMutableList()
                    newLeversJson[index] = newLeversJson[index].copy(lcc_enabled = checked)
                    val newConfig = ConfigManager.currentConfig.copy(tabs = newTabsJson.apply { set(selectedTabIndex, currentTabJson.copy(levers = newLeversJson)) })
                    ConfigManager.currentConfig = newConfig
                    saveConfigToFile(ConfigManager.toJsonString())
                    tabs = ConfigManager.parseConfig(ConfigManager.toJsonString())
                }
            )
        } else {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color(0xFF1a1a1a),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    ) {
                        tabs.forEachIndexed { index, pair ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { 
                                    selectedTabIndex = index 
                                    errorMessage = null // clear error when switching
                                },
                                text = { Text(pair.first, fontWeight = FontWeight.Bold, color = if (selectedTabIndex == index) BrassColor else Color.White) }
                            )
                        }
                    }
                    
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Text("⋮", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("System Status") },
                                onClick = {
                                    isStatusMode = true
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Configure") },
                                onClick = { 
                                    isConfigMode = true
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                val currentTabDef = tabs[selectedTabIndex].second
                val leverStates = allLeverStates[selectedTabIndex]
                val manualLocks = allManualLocks[selectedTabIndex]
                val policy = ConfigManager.currentConfig.conflict_policy
                val conflictingLevers = if (policy == 3) Interlocking.getConflictingLevers(currentTabDef, leverStates) else emptyList()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    currentTabDef.levers.forEachIndexed { index, leverDef ->
                        val isReversed = leverStates[index]
                        val isManuallyLocked = manualLocks[index]
                        val isSystemLocked = !Interlocking.evaluate(currentTabDef, leverStates, index, !isReversed)
                        val isAlarmed = index in conflictingLevers

                        LeverComponent(
                            leverDef = leverDef,
                            labelLines = currentTabDef.labelLines,
                            labelLineHeight = currentTabDef.labelLineHeight,
                            isReversed = isReversed,
                            isManuallyLocked = isManuallyLocked,
                            isSystemLocked = isSystemLocked,
                            isAlarmed = isAlarmed,
                            onLabelClick = {
                                statusLeverIndex = index
                            },
                            onToggle = {
                                if (isManuallyLocked) {
                                    errorMessage = "Lever ${index + 1} is manually locked!"
                                    return@LeverComponent
                                }
                                
                                val attemptingState = !isReversed
                                if (Interlocking.evaluate(currentTabDef, leverStates, index, attemptingState)) {
                                    val newStates = leverStates.clone()
                                    newStates[index] = attemptingState
                                    allLeverStates[selectedTabIndex] = newStates
                                    errorMessage = null
                                    
                                    if (ConfigManager.currentConfig.lcc_master && leverDef.lcc_enabled) {
                                        val eventStr = if (attemptingState) leverDef.lcc_event_reversed else leverDef.lcc_event_normal
                                        LccNode.produceEvent(eventStr)
                                    }
                                } else {
                                    errorMessage = "Interlocking: Lever ${index + 1} is system locked!"
                                }
                            },
                            onToggleLock = {
                                val newLocks = manualLocks.clone()
                                newLocks[index] = !isManuallyLocked
                                allManualLocks[selectedTabIndex] = newLocks
                                errorMessage = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LeverComponent(
    leverDef: LeverDef,
    labelLines: Int,
    labelLineHeight: Int,
    isReversed: Boolean,
    isManuallyLocked: Boolean,
    isSystemLocked: Boolean,
    isAlarmed: Boolean,
    onLabelClick: () -> Unit,
    onToggle: () -> Unit,
    onToggleLock: () -> Unit
) {
    val typeColor = when (leverDef.type) {
        LeverType.HOME_SIGNAL -> Color(0xFF8f2727)
        LeverType.DISTANT_SIGNAL -> Color(0xFFb08817)
        LeverType.POINTS -> Color(0xFF000000)
        LeverType.FACING_POINTS -> Color(0xFF2b58b5)
        LeverType.BROWN -> Color(0xFF5C4033)
        LeverType.GREEN -> Color(0xFF228B22)
        LeverType.SPARE -> Color(0xFFb8b8b8)
    }

    val (upText, downText) = when (leverDef.type) {
        LeverType.HOME_SIGNAL, LeverType.DISTANT_SIGNAL -> "ON" to "OFF"
        else -> "NORMAL" to "THROWN"
    }

    val scope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    // Plate Background
    Column(
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight()
            .background(Color(0xFF1a1a1a))
            .border(
                width = 2.dp,
                color = Color(0xFF333333),
                shape = RoundedCornerShape(topStart = 4.dp)
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header (Brass Plate + Color bar)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((labelLines * labelLineHeight).dp + 12.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFB59410),
                                Color(0xFFFBE473),
                                Color(0xFFD4AF37),
                                Color(0xFF8A6B0A)
                            )
                        )
                    )
                    .border(2.dp, Color(0xFF5c421a))
                    .clickable { onLabelClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = leverDef.label,
                    color = Color(0xFF1A1500),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = labelLineHeight.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(typeColor)
                    .then(if (typeColor == Color(0xFF000000)) Modifier.border(1.dp, Color(0xFFAAAAAA), RoundedCornerShape(2.dp)) else Modifier)
            )
        }

        // Switch Container
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).padding(vertical = 16.dp)
        ) {
            Text(
                text = upText,
                color = if (!isReversed) Color(0xFFFFFFFF) else Color(0xFF888888),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Switch Track
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF000000), Color(0xFF1a1a1a), Color(0xFF000000))
                        )
                    )
                    .border(2.dp, Color(0xFF2b2b2b), RoundedCornerShape(6.dp))
                    .clickable { 
                        if (isSystemLocked || isManuallyLocked) {
                            scope.launch {
                                shakeOffset.animateTo(
                                    targetValue = 8f,
                                    animationSpec = repeatable(
                                        iterations = 6,
                                        animation = tween(durationMillis = 40, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                shakeOffset.snapTo(0f)
                            }
                        }
                        onToggle() 
                    }
            ) {
                // Knob
                val positionRatio by animateFloatAsState(
                    targetValue = if (isReversed) 1f else 0f,
                    animationSpec = tween(durationMillis = 250),
                    label = "positionRatio"
                )
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val trackHeight = maxHeight
                    val knobSize = 52.dp
                    val padding = 4.dp
                    val offset = padding + (trackHeight - knobSize - padding * 2) * positionRatio

                    Box(
                        modifier = Modifier
                            .size(knobSize)
                            .align(Alignment.TopCenter)
                            .offset(y = offset, x = shakeOffset.value.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(typeColor)
                            .then(if (typeColor == Color(0xFF000000)) Modifier.border(2.dp, Color(0xFFAAAAAA), androidx.compose.foundation.shape.CircleShape) else Modifier)
                    )

                    // Locking Pin
                    if (isSystemLocked || isManuallyLocked) {
                        val pinHeight = 8.dp
                        val pinOffsetY = (trackHeight - pinHeight) / 2
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = pinOffsetY)
                                .width(24.dp)
                                .height(pinHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFcc3333))
                                .border(1.dp, Color(0xFFdddddd), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = downText,
                color = if (isReversed) Color(0xFFFFFFFF) else Color(0xFF888888),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Collar Button
        val (collarText, collarBg, collarFg) = when {
            isAlarmed -> Triple("ALARM", Color(0xFFFFA500), Color.Black)
            isManuallyLocked -> Triple("LOCKED", Color(0xFFcc3333), Color.White)
            isSystemLocked -> Triple("INTERLOCK", Color(0xFF252525), Color(0xFFaaaaaa))
            else -> Triple("UNLOCKED", Color(0xFF252525), Color.White)
        }

        Button(
            onClick = onToggleLock,
            colors = ButtonDefaults.buttonColors(containerColor = collarBg),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(collarText, color = collarFg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}