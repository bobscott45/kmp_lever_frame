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
/**
 * The main Compose application entry point.
 * Manages the top-level UI architecture, screen navigation (Configuration vs. Status vs. Frame),
 * and dynamic scaling based on the user's viewport and configuration settings.
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

/**
 * Application root entry point. Bootstraps the Koin dependency injection container
 * and wraps the main content in the Koin context.
 * 
 * @param runtimeUiScale Optional scaling factor applied to the entire Compose UI hierarchy.
 */
@Composable
@Preview
fun App(runtimeUiScale: Float = 1.0f) {
    if (!koinStarted) {
        startKoin {
            modules(appModule)
        }
        koinStarted = true
    }
    KoinContext {
        AppContent(runtimeUiScale)
    }
}

@Composable
fun AppContent(runtimeUiScale: Float) {
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
            
            val currentDensity = LocalDensity.current
            val scale = if (configState.config.ui_scale > 0.0f) configState.config.ui_scale else runtimeUiScale
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density * scale,
                    fontScale = currentDensity.fontScale
                )
            ) {
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
            } // Close CompositionLocalProvider

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

