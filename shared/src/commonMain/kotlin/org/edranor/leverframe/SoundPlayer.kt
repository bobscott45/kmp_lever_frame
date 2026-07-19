package org.edranor.leverframe

import androidx.compose.runtime.Composable

interface SoundPlayer {
    fun playClank()
    fun playClick()
    fun playThud()
}

@Composable
expect fun rememberSoundPlayer(): SoundPlayer
