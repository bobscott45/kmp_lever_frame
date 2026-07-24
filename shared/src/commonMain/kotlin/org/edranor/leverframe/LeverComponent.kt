package org.edranor.leverframe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LeverComponent(
    leverIndex: Int,
    showLeverNumber: Boolean,
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
    val pinColor by animateColorAsState(
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
        LeverBrassPlate(
            label = leverDef.label,
            typeColor = typeColor,
            labelLines = labelLines,
            labelLineHeight = labelLineHeight,
            scale = scale,
            widthScale = widthScale,
            onLabelClick = onLabelClick
        )

        // Switch Container
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp * scale)
        ) {
            LeverStatusIndicator(
                text = upText,
                isReversed = isReversed,
                isActiveWhenReversed = false,
                isSignal = leverDef.type == LeverType.HOME_SIGNAL || leverDef.type == LeverType.DISTANT_SIGNAL,
                isHomeSignal = leverDef.type == LeverType.HOME_SIGNAL,
                scale = scale
            )
            
            Spacer(modifier = Modifier.height(2.dp * scale))
            
            LeverKnobTrack(
                leverIndex = leverIndex,
                showLeverNumber = showLeverNumber,
                autoReverser = leverDef.autoReverser,
                isReversed = isReversed,
                isSystemLocked = isSystemLocked,
                isManuallyLocked = isManuallyLocked,
                typeColor = typeColor,
                pinColor = pinColor,
                shakeOffset = shakeOffset,
                scale = scale,
                widthScale = widthScale,
                onInteract = {
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
                    } else {
                        soundPlayer.playClank()
                        onToggle()
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(2.dp * scale))
            
            LeverStatusIndicator(
                text = downText,
                isReversed = isReversed,
                isActiveWhenReversed = true,
                isSignal = leverDef.type == LeverType.HOME_SIGNAL || leverDef.type == LeverType.DISTANT_SIGNAL,
                isHomeSignal = leverDef.type == LeverType.HOME_SIGNAL,
                scale = scale
            )
        }

        LeverLockCollar(
            isAlarmed = isAlarmed,
            isManuallyLocked = isManuallyLocked,
            isSystemLocked = isSystemLocked,
            scale = scale,
            soundPlayer = soundPlayer,
            onToggleLock = onToggleLock
        )
    }
}

@Composable
private fun LeverBrassPlate(
    label: String,
    typeColor: Color,
    labelLines: Int,
    labelLineHeight: Int,
    scale: Float,
    widthScale: Float,
    onLabelClick: () -> Unit
) {
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
                text = label,
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
}

@Composable
private fun LeverStatusIndicator(
    text: String,
    isReversed: Boolean,
    isActiveWhenReversed: Boolean,
    isSignal: Boolean,
    isHomeSignal: Boolean,
    scale: Float
) {
    val isActive = if (isActiveWhenReversed) isReversed else !isReversed
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(
            text = text,
            color = if (isActive) Color(0xFFFFFFFF) else Color(0xFF888888),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
        if (isSignal) {
            Spacer(modifier = Modifier.width(4.dp * scale))
            val color = if (isActive) {
                if (isActiveWhenReversed) {
                    Color(0xFF44FF44) // Green when OFF
                } else {
                    if (isHomeSignal) Color(0xFFFF4444) else Color(0xFFFFCC00) // Red/Yellow when ON
                }
            } else {
                Color(0xFF333333) // Dark when inactive
            }
            Box(
                modifier = Modifier
                    .size(8.dp * scale)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color)
                    .border(0.5.dp, Color.Black, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

@Composable
private fun ColumnScope.LeverKnobTrack(
    leverIndex: Int,
    showLeverNumber: Boolean,
    autoReverser: Boolean,
    isReversed: Boolean,
    isSystemLocked: Boolean,
    isManuallyLocked: Boolean,
    typeColor: Color,
    pinColor: Color,
    shakeOffset: Animatable<Float, AnimationVector1D>,
    scale: Float,
    widthScale: Float,
    onInteract: () -> Unit
) {
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
            .clickable { onInteract() }
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
            
            val pinCenterY = trackHeight / 2
            val thrownKnobTopY = trackHeight - knobSize - padding
            
            // Track Number
            if (showLeverNumber) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(thrownKnobTopY - pinCenterY)
                        .align(Alignment.TopCenter)
                        .offset(y = pinCenterY),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${leverIndex + 1}",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = (18 * scale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

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
                if (autoReverser) {
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
}

@Composable
private fun LeverLockCollar(
    isAlarmed: Boolean,
    isManuallyLocked: Boolean,
    isSystemLocked: Boolean,
    scale: Float,
    soundPlayer: SoundPlayer,
    onToggleLock: () -> Unit
) {
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
