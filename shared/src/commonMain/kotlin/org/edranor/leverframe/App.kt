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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.gestures.detectDragGestures
import kotlinx.coroutines.delay


import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import org.edranor.leverframe.di.appModule
import org.koin.core.context.startKoin

var koinStarted = false

@Composable
@Preview
fun App() {
    if (!koinStarted) {
        startKoin {
            modules(appModule)
        }
        koinStarted = true
    }
    KoinContext {
        AppContent()
    }
}

@Composable
fun AppContent() {
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
            val viewModel = koinViewModel<AppViewModel>()
            val domainState by viewModel.domainState.collectAsState()
            val configState by viewModel.configState.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            val actualSoundPlayer = rememberSoundPlayer()
            val soundPlayer = remember(actualSoundPlayer, configState.config.enable_sound) {
                object : SoundPlayer {
                    override fun playClank() { if (configState.config.enable_sound) actualSoundPlayer.playClank() }
                    override fun playLock() { if (configState.config.enable_sound) actualSoundPlayer.playLock() }
                    override fun playThud() { if (configState.config.enable_sound) actualSoundPlayer.playThud() }
                    override fun playAlarm() { if (configState.config.enable_sound) actualSoundPlayer.playAlarm() }
                    override fun playDing() { if (configState.config.enable_sound) actualSoundPlayer.playDing() }
                    override fun playDoubleDing() { if (configState.config.enable_sound) actualSoundPlayer.playDoubleDing() }
                }
            }

            if (uiState.configMode != ConfigMode.NONE) {
                ConfigurationScreen(
                    initialConfig = configState.config,
                    initialMode = uiState.configMode,
                    initialSelectedFrameIndex = uiState.initialEditFrameIndex ?: 0,
                    initialEditingLeverIndex = uiState.initialEditLeverIndex,
                    onUpdateSystemConfig = { cfg, rulesOnly, clearStates -> viewModel.updateSystemConfig(cfg, rulesOnly, clearStates) },
                    onClose = viewModel::exitConfigMode
                )
            } else {
                ConflictSoundEffectHandler(domainState, soundPlayer)
                BlockSoundEffectHandler(domainState, soundPlayer)

                NavContent(
                    domainState = domainState,
                    configState = configState,
                    uiState = uiState,
                    viewModel = viewModel,
                    soundPlayer = soundPlayer
                )

                if (uiState.isStatusMode) {
                    if (uiState.statusLeverIndex == null) {
                        SystemStatusScreen(
                            config = configState.config,
                            networkStatus = uiState.networkStatus,
                            onClose = viewModel::exitStatusMode
                        )
                    } else {
                        val index = uiState.statusLeverIndex!!
                        val tabDef = configState.tabs.getOrNull(uiState.selectedTabIndex)?.second
                        val leverDef = tabDef?.levers?.getOrNull(index)
                        
                        if (leverDef == null) {
                            viewModel.dismissStatusLever()
                        } else {
                            LeverStatusScreen(
                                leverIndex = index,
                                leverDef = leverDef,
                                levers = domainState.frames.getOrNull(uiState.selectedTabIndex)?.levers ?: emptyList(),
                                blocks = domainState.frames.getOrNull(uiState.selectedTabIndex)?.blocks ?: emptyList(),
                                onClose = viewModel::dismissStatusLever,
                                onEditConfig = {
                                    viewModel.enterConfigMode(ConfigMode.FRAMES, frameIndex = uiState.selectedTabIndex, leverIndex = index)
                                },
                                onLccEnabledChange = { checked ->
                                    viewModel.setLeverLccEnabled(uiState.selectedTabIndex, index, checked)
                                }
                            )
                        }
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
fun TopMenuBar(
    configState: ConfigState,
    uiState: TransientUiState,
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
        MainTabRow(configState = configState, uiState = uiState, onTabSelected = viewModel::tabSelected)
        
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
                        viewModel.enterConfigMode(ConfigMode.FRAMES, frameIndex = uiState.selectedTabIndex)
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
                                val jsonString = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), configState.config)
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
                val jsonString = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), configState.config)
                OutlinedTextField(
                    value = jsonString,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val jsonString = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), configState.config)
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
    configState: ConfigState,
    uiState: TransientUiState,
    onTabSelected: (Int) -> Unit
) {
    if (configState.tabs.isNotEmpty()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = uiState.selectedTabIndex,
            containerColor = Color(0xFF1a1a1a),
            contentColor = Color.White,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
            edgePadding = 0.dp
        ) {
            configState.tabs.forEachIndexed { index, pair ->
                Tab(
                    selected = uiState.selectedTabIndex == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(pair.first, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (uiState.selectedTabIndex == index) LeverFrameTheme.Colors.Brass else LeverFrameTheme.Colors.TabUnselected) }
                )
            }
        }
    } else {
        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1a1a1a)))
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
    domainState: DomainState,
    configState: ConfigState,
    uiState: TransientUiState,
    viewModel: AppViewModel,
    soundPlayer: SoundPlayer
) {
    if (configState.tabs.isNotEmpty() && uiState.selectedTabIndex < configState.tabs.size) {
        val currentTabDef = configState.tabs[uiState.selectedTabIndex].second
        val levers = domainState.frames.getOrNull(uiState.selectedTabIndex)?.levers
        
        if (levers != null) {
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
                        val isReversed = levers[index].isReversed
                        val isManuallyLocked = levers[index].isManuallyLocked
                        val blocks = domainState.frames.getOrNull(uiState.selectedTabIndex)?.blocks ?: emptyList()
                        val isSystemLocked = !Interlocking.evaluate(currentTabDef, levers, blocks, index, !isReversed)
                        val isAlarmed = index in domainState.conflictingLevers
    
                        LeverComponent(
                            leverIndex = index,
                            showLeverNumber = currentTabDef.showLeverNumbers,
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
                                viewModel.toggleLever(uiState.selectedTabIndex, index)
                            },
                            onToggleLock = {
                                viewModel.toggleManualLock(uiState.selectedTabIndex, index)
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
    domainState: DomainState,
    configState: ConfigState,
    uiState: TransientUiState,
    viewModel: AppViewModel
) {
    if (configState.tabs.isNotEmpty() && uiState.selectedTabIndex < configState.tabs.size) {
        val currentTabDef = configState.tabs[uiState.selectedTabIndex].second
        val blocks = domainState.frames.getOrNull(uiState.selectedTabIndex)?.blocks
        if (blocks != null && currentTabDef.blocks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentTabDef.blocks.forEachIndexed { index, blockDef ->
                    val isOccupied = if (index < blocks.size) blocks[index].isOccupied else true
                    val baseLabel = if (currentTabDef.useShortCodesInIndicators && blockDef.shortCode.isNotBlank()) blockDef.shortCode else blockDef.label
                    val labelText = if (currentTabDef.showBlockNumbers) "${index + 1} $baseLabel" else baseLabel
                    BlockIndicator(
                        label = labelText, 
                        isOccupied = isOccupied,
                        layout = currentTabDef.blockLayout,
                        fontSize = currentTabDef.blockLabelSize,
                        onToggle = { viewModel.toggleBlockState(uiState.selectedTabIndex, index) }
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

@Composable
fun ConflictSoundEffectHandler(domainState: DomainState, soundPlayer: SoundPlayer) {
    LaunchedEffect(domainState.conflictingLevers) {
        if (domainState.conflictingLevers.isNotEmpty()) {
            delay(700)
            soundPlayer.playAlarm()
        }
    }
}

@Composable
fun BlockSoundEffectHandler(domainState: DomainState, soundPlayer: SoundPlayer) {
    var previousBlocks by remember { mutableStateOf<List<BooleanArray>?>(null) }
    LaunchedEffect(domainState.frames) {
        val currentBlocks = domainState.frames.map { it.blocks.map { b -> b.isOccupied }.toBooleanArray() }
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
}


@Composable
private fun NavContent(
    domainState: DomainState,
    configState: ConfigState,
    uiState: TransientUiState,
    viewModel: AppViewModel,
    soundPlayer: SoundPlayer
) {
    var isSchematicVisiblePortrait by rememberSaveable { mutableStateOf(true) }
                var isSchematicVisibleLandscape by rememberSaveable { mutableStateOf(true) }
                var dragLandscapeWeight by remember(configState.config.schematic_weight_landscape) { mutableStateOf(configState.config.schematic_weight_landscape) }
                var dragPortraitWeight by remember(configState.config.schematic_weight_portrait) { mutableStateOf(configState.config.schematic_weight_portrait) }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LeverFrameTheme.Colors.DarkSurface)
                        .padding(16.dp)
                ) {
                    val parentMaxWidth = maxWidth
                    val parentMaxHeight = maxHeight
                    val isLandscapeCompact = maxWidth > maxHeight && maxHeight < 600.dp
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!isLandscapeCompact) {
                            TopMenuBar(configState, uiState, viewModel)
                            ErrorBanners(
                                errorMessage = uiState.errorMessage,
                                networkError = uiState.networkError,
                                onDismissNetworkError = viewModel::dismissNetworkError
                            )
                        }
                        
                        if (isLandscapeCompact) {
                            Row(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                                if (configState.tabs.isNotEmpty() && uiState.selectedTabIndex < configState.tabs.size) {
                                    val currentTabDef = configState.tabs[uiState.selectedTabIndex].second
                                    if (currentTabDef.schematicElements.isNotEmpty()) {
                                        val schematicWeight by animateFloatAsState(if (isSchematicVisibleLandscape) dragLandscapeWeight else 0.0f)
                                        if (schematicWeight > 0.01f) {
                                            SchematicScreen(
                                                tabDef = currentTabDef,
                                                levers = domainState.frames.getOrNull(uiState.selectedTabIndex)?.levers ?: emptyList(),
                                                blocks = domainState.frames.getOrNull(uiState.selectedTabIndex)?.blocks ?: emptyList(),
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(schematicWeight)
                                                    .padding(end = 4.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(24.dp)
                                                .pointerInput(parentMaxWidth) {
                                                    detectDragGestures(
                                                        onDragEnd = {
                                                            viewModel.saveLayoutWeights(dragLandscapeWeight, dragPortraitWeight)
                                                        }
                                                    ) { change, dragAmount ->
                                                        change.consume()
                                                        val dragFraction = dragAmount.x / parentMaxWidth.toPx()
                                                        dragLandscapeWeight = (dragLandscapeWeight + dragFraction).coerceIn(0.1f, 0.9f)
                                                    }
                                                }
                                                .clickable { isSchematicVisibleLandscape = !isSchematicVisibleLandscape },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(modifier = Modifier.fillMaxHeight().width(2.dp).background(Color.Gray.copy(alpha=0.5f)))
                                            androidx.compose.material3.Surface(
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                                color = LeverFrameTheme.Colors.DarkSurface,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha=0.5f)),
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(if (isSchematicVisibleLandscape) "◀" else "▶", color = Color.LightGray, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                val leversWeight by animateFloatAsState(if (isSchematicVisibleLandscape) (1f - dragLandscapeWeight) else 1.0f)
                                Column(modifier = Modifier.weight(leversWeight).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    TopMenuBar(configState, uiState, viewModel)
                                    ErrorBanners(
                                        errorMessage = uiState.errorMessage,
                                        networkError = uiState.networkError,
                                        onDismissNetworkError = viewModel::dismissNetworkError
                                    )
                                    BlockShelfGroup(domainState, configState, uiState, viewModel)
                                    LeverTrackGroup(domainState, configState, uiState, viewModel, soundPlayer)
                                }
                            }
                        } else {
                            if (configState.tabs.isNotEmpty() && uiState.selectedTabIndex < configState.tabs.size) {
                                val currentTabDef = configState.tabs[uiState.selectedTabIndex].second
                                if (currentTabDef.schematicElements.isNotEmpty()) {
                                    val schematicWeight by animateFloatAsState(if (isSchematicVisiblePortrait) dragPortraitWeight else 0.0f)
                                    if (schematicWeight > 0.01f) {
                                        SchematicScreen(
                                            tabDef = currentTabDef,
                                            levers = domainState.frames.getOrNull(uiState.selectedTabIndex)?.levers ?: emptyList(),
                                            blocks = domainState.frames.getOrNull(uiState.selectedTabIndex)?.blocks ?: emptyList(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(schematicWeight)
                                                .padding(top = 8.dp, bottom = 4.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                            .pointerInput(parentMaxHeight) {
                                                detectDragGestures(
                                                    onDragEnd = {
                                                        viewModel.saveLayoutWeights(dragLandscapeWeight, dragPortraitWeight)
                                                    }
                                                ) { change, dragAmount ->
                                                    change.consume()
                                                    val dragFraction = dragAmount.y / parentMaxHeight.toPx()
                                                    dragPortraitWeight = (dragPortraitWeight + dragFraction).coerceIn(0.1f, 0.9f)
                                                }
                                            }
                                            .clickable { isSchematicVisiblePortrait = !isSchematicVisiblePortrait },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.Gray.copy(alpha=0.5f)))
                                        androidx.compose.material3.Surface(
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            color = LeverFrameTheme.Colors.DarkSurface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha=0.5f)),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(if (isSchematicVisiblePortrait) "▲" else "▼", color = Color.LightGray, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            val leversWeight by animateFloatAsState(if (isSchematicVisiblePortrait) (1f - dragPortraitWeight) else 1.0f)
                            Column(modifier = Modifier.weight(leversWeight).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                BlockShelfGroup(domainState, configState, uiState, viewModel)
                                LeverTrackGroup(domainState, configState, uiState, viewModel, soundPlayer)
                            }
                        }
                    }
                }
}
