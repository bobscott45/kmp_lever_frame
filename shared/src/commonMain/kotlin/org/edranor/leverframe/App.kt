/*
 * Copyright (C) 2026 Robert Scott
 *
 * This file is part of LeverFrame.
 *
 * This project is dual-licensed to balance open-source collaboration with 
 * ecosystem compatibility:
 *
 * * Source Code: The source code in this repository is licensed under the 
 *   GNU General Public License v3 (GPLv3). You are free to copy, modify, 
 *   and self-compile the code, provided any distributions remain open-source 
 *   under the same terms.
 * * Compiled Binaries & Storefronts: As the sole copyright owner of this 
 *   codebase, the author reserves the right to distribute compiled binaries 
 *   (such as on the Apple App Store, Google Play, or other platforms) under 
 *   separate, proprietary, or storefront-specific licenses.
 *
 * Note: If you wish to contribute code to this project via a Pull Request, you 
 * agree to grant the author a non-exclusive, perpetual license to distribute 
 * your contributions under both the GPLv3 and our storefront distribution licenses.
 */
package org.edranor.leverframe

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay


@Composable
@Preview
fun App() {
    KeepScreenOn(keepOn = true)
    
    var isInputBlocked by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isInputBlocked = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isInputBlocked) {
        if (isInputBlocked) {
            delay(500)
            isInputBlocked = false
        }
    }

    val customColorScheme = darkColorScheme(
        primary = LeverFrameTheme.Colors.Brass,
        onPrimary = Color.Black,
        secondary = LeverFrameTheme.Colors.Brass,
        onSecondary = Color.Black,
        tertiary = LeverFrameTheme.Colors.Brass,
        onTertiary = Color.Black
    )
    
    val defaultTypography = Typography()
    val customTypography = Typography(
        bodyLarge = defaultTypography.bodyLarge.copy(fontSize = 14.sp),
        bodyMedium = defaultTypography.bodyMedium.copy(fontSize = 12.sp),
        titleLarge = defaultTypography.titleLarge.copy(fontSize = 18.sp),
        titleMedium = defaultTypography.titleMedium.copy(fontSize = 14.sp),
        titleSmall = defaultTypography.titleSmall.copy(fontSize = 12.sp),
        labelLarge = defaultTypography.labelLarge.copy(fontSize = 12.sp)
    )

    MaterialTheme(colorScheme = customColorScheme, typography = customTypography) {
        Box(modifier = Modifier.fillMaxSize()) {
            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel { AppViewModel() }
            val state by viewModel.uiState.collectAsState()
            val actualSoundPlayer = rememberSoundPlayer()
            val soundPlayer = remember(actualSoundPlayer, state.config.enable_sound) {
                object : SoundPlayer {
                    override fun playClank() { if (state.config.enable_sound) actualSoundPlayer.playClank() }
                    override fun playLock() { if (state.config.enable_sound) actualSoundPlayer.playLock() }
                    override fun playThud() { if (state.config.enable_sound) actualSoundPlayer.playThud() }
                    override fun playAlarm() { if (state.config.enable_sound) actualSoundPlayer.playAlarm() }
                    override fun playDing() { if (state.config.enable_sound) actualSoundPlayer.playDing() }
                    override fun playDoubleDing() { if (state.config.enable_sound) actualSoundPlayer.playDoubleDing() }
                }
            }

            if (state.configMode != ConfigMode.NONE) {
                ConfigurationScreen(
                    initialConfig = state.config,
                    initialMode = state.configMode,
                    onUpdateSystemConfig = viewModel::updateSystemConfig,
                    onClose = viewModel::exitConfigMode
                )
            } else {
                LaunchedEffect(state.conflictingLevers) {
                    if (state.conflictingLevers.isNotEmpty()) {
                        delay(700)
                        soundPlayer.playAlarm()
                    }
                }

                var previousBlocks by remember { mutableStateOf<List<BooleanArray>?>(null) }
                LaunchedEffect(state.blockStates) {
                    val currentBlocks = state.blockStates
                    val prevBlocks = previousBlocks
                    if (prevBlocks != null && prevBlocks.size == currentBlocks.size) {
                        var becameOccupied = false
                        var becameFree = false
                        for (i in currentBlocks.indices) {
                            val currArr = currentBlocks[i]
                            val prevArr = prevBlocks[i]
                            if (currArr.size == prevArr.size) {
                                for (j in currArr.indices) {
                                    if (currArr[j] && !prevArr[j]) becameOccupied = true
                                    if (!currArr[j] && prevArr[j]) becameFree = true
                                }
                            }
                        }
                        if (becameOccupied) {
                            soundPlayer.playDoubleDing()
                        } else if (becameFree) {
                            soundPlayer.playDing()
                        }
                    }
                    previousBlocks = currentBlocks.map { it.copyOf() }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LeverFrameTheme.Colors.DarkSurface)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TopMenuBar(state, viewModel)
                    ErrorBanners(
                        errorMessage = state.errorMessage,
                        networkError = state.networkError,
                        onDismissNetworkError = viewModel::dismissNetworkError
                    )
                    BlockShelfGroup(state, viewModel)
                    LeverTrackGroup(state, viewModel, soundPlayer)
                }

                if (state.isStatusMode) {
                    if (state.statusLeverIndex == null) {
                        SystemStatusScreen(
                            config = state.config,
                            networkStatus = state.networkStatus,
                            onUpdateSystemConfig = viewModel::updateSystemConfig,
                            onClose = viewModel::exitStatusMode
                        )
                    } else {
                        val index = state.statusLeverIndex!!
                        val leverDef = state.tabs[state.selectedTabIndex].second.levers[index]
                        
                        LeverStatusScreen(
                            leverIndex = index,
                            leverDef = leverDef,
                            leverStates = state.leverStates[state.selectedTabIndex],
                            blockStates = state.blockStates.getOrNull(state.selectedTabIndex) ?: BooleanArray(0),
                            onClose = viewModel::dismissStatusLever,
                            onLccEnabledChange = { checked ->
                                viewModel.setLeverLccEnabled(state.selectedTabIndex, index, checked)
                            }
                        )
                    }
                }
            }

            // Input blocking overlay
            if (isInputBlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                )
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
    scale: Float = 1f,
    widthScale: Float = scale,
    soundPlayer: SoundPlayer,
    onLabelClick: () -> Unit,
    onToggle: () -> Unit,
    onToggleLock: () -> Unit
) {
    val typeColor = when (leverDef.type) {
        LeverType.HOME_SIGNAL -> LeverFrameTheme.Colors.HomeSignal
        LeverType.DISTANT_SIGNAL -> LeverFrameTheme.Colors.DistantSignal
        LeverType.POINTS -> LeverFrameTheme.Colors.Points
        LeverType.FACING_POINTS -> LeverFrameTheme.Colors.FacingPoints
        LeverType.BROWN -> LeverFrameTheme.Colors.Brown
        LeverType.GREEN -> LeverFrameTheme.Colors.Green
        LeverType.SPARE -> LeverFrameTheme.Colors.Spare
    }

    val (upText, downText) = when (leverDef.type) {
        LeverType.HOME_SIGNAL, LeverType.DISTANT_SIGNAL -> "ON" to "OFF"
        else -> "NORMAL" to "THROWN"
    }

    val scope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }
    var pinFlash by remember { mutableStateOf(false) }
    val pinColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (pinFlash) Color(0xFFFFFFFF) else Color(0xFFcc3333),
        animationSpec = tween(durationMillis = 150),
        label = "pinColor"
    )

    // Plate Background
    Column(
        modifier = Modifier
            .width(96.dp * widthScale)
            .fillMaxHeight()
            .background(Color(0xFF1a1a1a))
            .border(
                width = 2.dp,
                color = Color(0xFF333333),
                shape = RoundedCornerShape(topStart = 4.dp)
            )
            .padding(vertical = 10.dp * scale, horizontal = 4.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header (Brass Plate + Color bar)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((labelLines * labelLineHeight).dp * widthScale) + (12.dp * widthScale))
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
                    fontSize = 9.sp,
                    lineHeight = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp * scale))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp * scale)
                    .clip(RoundedCornerShape(2.dp))
                    .background(typeColor)
                    .then(if (typeColor == Color(0xFF000000)) Modifier.border(1.dp, Color(0xFFAAAAAA), RoundedCornerShape(2.dp)) else Modifier)
            )
        }

        // Switch Container
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp * scale)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    text = upText,
                    color = if (!isReversed) Color(0xFFFFFFFF) else Color(0xFF888888),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                if (leverDef.type == LeverType.HOME_SIGNAL || leverDef.type == LeverType.DISTANT_SIGNAL) {
                    Spacer(modifier = Modifier.width(4.dp * scale))
                    val color = if (!isReversed) {
                        if (leverDef.type == LeverType.HOME_SIGNAL) Color(0xFFFF4444) else Color(0xFFFFCC00)
                    } else Color(0xFF333333)
                    Box(modifier = Modifier.size(8.dp * scale).clip(androidx.compose.foundation.shape.CircleShape).background(color).border(0.5.dp, Color.Black, androidx.compose.foundation.shape.CircleShape))
                }
            }
            Spacer(modifier = Modifier.height(2.dp * scale))
            
            // Switch Track
            Box(
                modifier = Modifier
                    .width(60.dp * widthScale)
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
                            soundPlayer.playThud()
                            scope.launch {
                                launch {
                                    shakeOffset.animateTo(
                                        targetValue = LeverFrameTheme.Animation.ShakeOffsetTarget,
                                        animationSpec = repeatable(
                                            iterations = 6,
                                            animation = tween(durationMillis = LeverFrameTheme.Animation.ShakeDurationMs, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        )
                                    )
                                    shakeOffset.snapTo(0f)
                                }
                                launch {
                                    repeat(3) {
                                        pinFlash = true
                                        delay(150)
                                        pinFlash = false
                                        delay(150)
                                    }
                                }
                            }
                        }
                        if (!isSystemLocked && !isManuallyLocked) {
                            soundPlayer.playClank()
                            onToggle() 
                        } 
                    }
            ) {
                // Knob
                val positionRatio by animateFloatAsState(
                    targetValue = if (isReversed) 1f else 0f,
                    animationSpec = spring(dampingRatio = LeverFrameTheme.Animation.LeverSpringDamping, stiffness = LeverFrameTheme.Animation.LeverSpringStiffness),
                    label = "positionRatio"
                )
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val trackHeight = maxHeight
                    val knobSize = 52.dp * scale
                    val padding = 4.dp * scale
                    
                    val physicalRatio = when {
                        positionRatio > 1f -> 1f - (positionRatio - 1f)
                        positionRatio < 0f -> -positionRatio
                        else -> positionRatio
                    }
                    
                    val offset = padding + (trackHeight - knobSize - padding * 2) * physicalRatio

                    Box(
                        modifier = Modifier
                            .size(knobSize)
                            .align(Alignment.TopCenter)
                            .offset(y = offset, x = shakeOffset.value.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(typeColor)
                            .then(if (typeColor == Color(0xFF000000)) Modifier.border(2.dp, Color(0xFFAAAAAA), androidx.compose.foundation.shape.CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (leverDef.autoReverser) {
                            val textColor = if (typeColor == LeverFrameTheme.Colors.DistantSignal || typeColor == LeverFrameTheme.Colors.Spare) Color.Black else Color.White.copy(alpha = 0.8f)
                            Text(
                                text = "A",
                                color = textColor,
                                fontSize = (16 * scale).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Locking Pin
                    if (isSystemLocked || isManuallyLocked) {
                        val pinHeight = 8.dp * scale
                        val pinOffsetY = (trackHeight - pinHeight) / 2
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = pinOffsetY)
                                .width(24.dp * scale)
                                .height(pinHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(pinColor)
                                .border(1.dp, Color(0xFFdddddd), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp * scale))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    text = downText,
                    color = if (isReversed) Color(0xFFFFFFFF) else Color(0xFF888888),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                if (leverDef.type == LeverType.HOME_SIGNAL || leverDef.type == LeverType.DISTANT_SIGNAL) {
                    Spacer(modifier = Modifier.width(4.dp * scale))
                    val color = if (isReversed) Color(0xFF44FF44) else Color(0xFF333333)
                    Box(modifier = Modifier.size(8.dp * scale).clip(androidx.compose.foundation.shape.CircleShape).background(color).border(0.5.dp, Color.Black, androidx.compose.foundation.shape.CircleShape))
                }
            }
        }

        // Collar Button
        val (collarText, collarBg, collarFg) = when {
            isAlarmed -> Triple("ALARM", Color(0xFFFFA500), Color.Black)
            isManuallyLocked -> Triple("LOCKED", Color(0xFFcc3333), Color.White)
            isSystemLocked -> Triple("INTERLOCK", Color(0xFF252525), Color(0xFFaaaaaa))
            else -> Triple("UNLOCKED", Color(0xFF252525), Color.White)
        }

        Button(
            onClick = {
                soundPlayer.playLock()
                onToggleLock()
            },
            colors = ButtonDefaults.buttonColors(containerColor = collarBg),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp * scale),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(collarText, color = collarFg, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TopMenuBar(
    state: LeverFrameUiState,
    viewModel: AppViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainTabRow(state = state, onTabSelected = viewModel::tabSelected)
        
        Box(modifier = Modifier.padding(start = 16.dp)) {
            var menuExpanded by remember { mutableStateOf(false) }
            IconButton(onClick = { menuExpanded = true }) {
                Text("⋮", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("System Status", fontSize = 14.sp) },
                    onClick = {
                        viewModel.enterStatusMode()
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("System Settings", fontSize = 14.sp) },
                    onClick = { 
                        viewModel.enterConfigMode(ConfigMode.SYSTEM)
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Frame Configuration", fontSize = 14.sp) },
                    onClick = { 
                        viewModel.enterConfigMode(ConfigMode.FRAMES)
                        menuExpanded = false
                    }
                )
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text("Import Configuration", fontSize = 14.sp) },
                    onClick = { 
                        if (isFilePickerAvailable) {
                            importConfigurationFile { json ->
                                if (json != null) {
                                    try {
                                        val importedConfig = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(json)
                                        viewModel.updateSystemConfig(importedConfig)
                                    } catch (e: Exception) {
                                        println("Failed to import: ${e.message}")
                                    }
                                }
                            }
                        } else {
                            showImportDialog = true
                        }
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Export Configuration", fontSize = 14.sp) },
                    onClick = { 
                        if (isFilePickerAvailable) {
                            try {
                                val jsonString = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), state.config)
                                exportConfigurationFile(jsonString)
                            } catch (e: Exception) {
                                println("Failed to export: ${e.message}")
                            }
                        } else {
                            showExportDialog = true
                        }
                        menuExpanded = false
                    }
                )
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Configuration") },
            text = {
                val jsonString = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), state.config)
                OutlinedTextField(
                    value = jsonString,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val jsonString = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), state.config)
                    clipboardManager.setText(AnnotatedString(jsonString))
                    showExportDialog = false
                }) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Close") }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Configuration") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("Paste JSON here") },
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val importedConfig = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(importText)
                        viewModel.updateSystemConfig(importedConfig)
                        showImportDialog = false
                        importText = ""
                    } catch (e: Exception) {
                        println("Failed to import: ${e.message}")
                    }
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportDialog = false
                    importText = ""
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun RowScope.MainTabRow(
    state: LeverFrameUiState,
    onTabSelected: (Int) -> Unit
) {
    PrimaryTabRow(
        selectedTabIndex = state.selectedTabIndex,
        containerColor = Color(0xFF1a1a1a),
        contentColor = Color.White,
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
    ) {
        state.tabs.forEachIndexed { index, pair ->
            Tab(
                selected = state.selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = { Text(pair.first, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (state.selectedTabIndex == index) LeverFrameTheme.Colors.Brass else LeverFrameTheme.Colors.TabUnselected) }
            )
        }
    }
}

@Composable
fun ErrorBanners(
    errorMessage: String?,
    networkError: String?,
    onDismissNetworkError: () -> Unit
) {
    AnimatedVisibility(
        visible = errorMessage != null,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = LeverFrameTheme.Colors.ErrorText,
                modifier = Modifier.padding(bottom = 16.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }

    AnimatedVisibility(
        visible = networkError != null,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        networkError?.let { msg ->
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = LeverFrameTheme.Colors.NetworkErrorBg),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = msg, color = LeverFrameTheme.Colors.NetworkErrorText, fontWeight = FontWeight.Medium)
                    TextButton(onClick = onDismissNetworkError) {
                        Text("Dismiss", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.LeverTrackGroup(
    state: LeverFrameUiState,
    viewModel: AppViewModel,
    soundPlayer: SoundPlayer
) {
    if (state.tabs.isNotEmpty() && state.selectedTabIndex < state.tabs.size) {
        val currentTabDef = state.tabs[state.selectedTabIndex].second
        val leverStates = state.leverStates.getOrNull(state.selectedTabIndex)
        val manualLocks = state.manualLocks.getOrNull(state.selectedTabIndex)
        
        if (leverStates != null && manualLocks != null) {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val heightScale = (maxHeight.value / 600f).coerceIn(0.5f, 1.2f)
                val baseWidthScale = (maxWidth.value / 450f).coerceIn(0.6f, 1.2f)
                val scale = minOf(heightScale, baseWidthScale)
                val leverWidthScale = if (maxWidth > maxHeight) maxOf(scale, 0.75f) else scale
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp * leverWidthScale, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    currentTabDef.levers.forEachIndexed { index, leverDef ->
                        val isReversed = leverStates[index]
                        val isManuallyLocked = manualLocks[index]
                        val isSystemLocked = !Interlocking.evaluate(currentTabDef, leverStates, state.blockStates.getOrNull(state.selectedTabIndex) ?: BooleanArray(0), index, !isReversed)
                        val isAlarmed = index in state.conflictingLevers
    
                        LeverComponent(
                            leverDef = leverDef,
                            labelLines = currentTabDef.labelLines,
                            labelLineHeight = currentTabDef.labelLineHeight,
                            isReversed = isReversed,
                            isManuallyLocked = isManuallyLocked,
                            isSystemLocked = isSystemLocked,
                            isAlarmed = isAlarmed,
                            scale = scale,
                            widthScale = leverWidthScale,
                            soundPlayer = soundPlayer,
                            onLabelClick = {
                                viewModel.leverLabelClicked(index)
                            },
                            onToggle = {
                                viewModel.toggleLever(state.selectedTabIndex, index)
                            },
                            onToggleLock = {
                                viewModel.toggleManualLock(state.selectedTabIndex, index)
                            }
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun BlockShelfGroup(
    state: LeverFrameUiState,
    viewModel: AppViewModel
) {
    if (state.tabs.isNotEmpty() && state.selectedTabIndex < state.tabs.size) {
        val currentTabDef = state.tabs[state.selectedTabIndex].second
        val blockStates = state.blockStates.getOrNull(state.selectedTabIndex)
        if (blockStates != null && currentTabDef.blocks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentTabDef.blocks.forEachIndexed { index, blockDef ->
                    val isOccupied = blockStates[index]
                    BlockIndicator(
                        label = blockDef.label, 
                        isOccupied = isOccupied,
                        layout = currentTabDef.blockLayout,
                        fontSize = currentTabDef.blockLabelSize,
                        onToggle = { viewModel.toggleBlockState(state.selectedTabIndex, index) }
                    )
                }
            }
        }
    }
}

@Composable
fun BlockIndicator(
    label: String, 
    isOccupied: Boolean,
    layout: String,
    fontSize: Int,
    onToggle: () -> Unit
) {
    val ledSize = (fontSize * 1.2f).dp
    val ledSizeVert = (fontSize * 1.6f).dp
    val spacing = (fontSize * 0.6f).dp
    val hPad = (fontSize * 0.6f).dp
    val vPad = (fontSize * 0.2f).dp

    val content = @Composable {
        Text(
            text = label.replace("\n", " "),
            color = Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            modifier = if (layout == "HORIZONTAL") Modifier.padding(end = spacing) else Modifier.padding(bottom = spacing)
        )
        Box(
            modifier = Modifier
                .size(if (layout == "HORIZONTAL") ledSize else ledSizeVert)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (isOccupied) Color(0xFFcc3333) else Color(0xFF33cc33))
                .border(1.dp, Color.Black, androidx.compose.foundation.shape.CircleShape)
        )
    }

    if (layout == "HORIZONTAL") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0xFF4A2511), RoundedCornerShape(4.dp)) // Richer Mahogany
                .border(1.dp, Color(0xFF6B3E26), RoundedCornerShape(4.dp))
                .clickable { onToggle() }
                .padding(horizontal = hPad, vertical = vPad)
        ) {
            content()
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color(0xFF4A2511), RoundedCornerShape(4.dp)) // Richer Mahogany
                .border(1.dp, Color(0xFF6B3E26), RoundedCornerShape(4.dp))
                .clickable { onToggle() }
                .padding(horizontal = hPad, vertical = vPad)
        ) {
            content()
        }
    }
}
