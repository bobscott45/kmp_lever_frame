package org.edranor.leverframe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

class JvmSoundPlayer : SoundPlayer {
    private fun playWav(resourcePath: String) {
        try {
            val stream = javaClass.getResourceAsStream(resourcePath) ?: return
            val audioIn = AudioSystem.getAudioInputStream(BufferedInputStream(stream))
            val clip = AudioSystem.getClip()
            clip.open(audioIn)
            clip.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun playClank() { playWav("/clank.wav") }
    override fun playClick() { playWav("/click.wav") }
    override fun playThud() { playWav("/thud.wav") }
    override fun playAlarm() { playWav("/alarm.wav") }
}

@Composable
actual fun rememberSoundPlayer(): SoundPlayer {
    return remember { JvmSoundPlayer() }
}
