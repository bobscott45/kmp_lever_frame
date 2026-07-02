/*
 * Copyright (C) 2026 Robert Scott
 *
 * This file is part of LeverFrame.
 *
 * LeverFrame is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LeverFrame is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LeverFrame.  If not, see <https://www.gnu.org/licenses/>.
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

            if (state.isConfigMode) {
                ConfigurationScreen(
                    initialConfig = state.config,
                    onUpdateSystemConfig = viewModel::updateSystemConfig,
                    onClose = viewModel::exitConfigMode
                )
            } else {
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
                    LeverTrackGroup(state, viewModel)
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
            modifier = Modifier.weight(1f).padding(vertical = 16.dp * scale)
        ) {
            Text(
                text = upText,
                color = if (!isReversed) Color(0xFFFFFFFF) else Color(0xFF888888),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp * scale))
            
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
                            scope.launch {
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
                        }
                        onToggle() 
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
                            .then(if (typeColor == Color(0xFF000000)) Modifier.border(2.dp, Color(0xFFAAAAAA), androidx.compose.foundation.shape.CircleShape) else Modifier)
                    )

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
                                .background(Color(0xFFcc3333))
                                .border(1.dp, Color(0xFFdddddd), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp * scale))
            Text(
                text = downText,
                color = if (isReversed) Color(0xFFFFFFFF) else Color(0xFF888888),
                fontSize = 8.sp,
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
                    text = { Text("Configure", fontSize = 14.sp) },
                    onClick = { 
                        viewModel.enterConfigMode()
                        menuExpanded = false
                    }
                )
            }
        }
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
    viewModel: AppViewModel
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
                        val isSystemLocked = !Interlocking.evaluate(currentTabDef, leverStates, index, !isReversed)
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