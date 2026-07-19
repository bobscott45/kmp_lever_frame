package org.edranor.leverframe

import androidx.compose.runtime.Composable

interface SoundPlayer {
    fun playClank()
    fun playClick()
    fun playThud()
    fun playAlarm()
    fun playDing()
    fun playDoubleDing()
}

@Composable
expect fun rememberSoundPlayer(): SoundPlayer
