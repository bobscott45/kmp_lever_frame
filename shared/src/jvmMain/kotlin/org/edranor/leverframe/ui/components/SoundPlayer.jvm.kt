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
 * Desktop (JVM) implementation of the SoundPlayer using Java's built-in AudioSystem API
 * to play standard .wav resources bundled in the JVM application.
 */
package org.edranor.leverframe.ui.components
import org.edranor.leverframe.*

import org.edranor.leverframe.network.*
import org.edranor.leverframe.services.*
import org.edranor.leverframe.ui.screens.main.*
import org.edranor.leverframe.ui.components.*
import org.edranor.leverframe.ui.theme.*
import org.edranor.leverframe.di.*
import org.edranor.leverframe.ui.screens.editor.*
import org.edranor.leverframe.domain.models.*
import org.edranor.leverframe.config.*
import org.edranor.leverframe.ui.screens.schematic.*
import org.edranor.leverframe.domain.engine.*
import org.edranor.leverframe.domain.parser.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/**
 * JVM implementation of [SoundPlayer].
 * Uses Java's built-in AudioSystem API to play sound resources from the classpath.
 */
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
    override fun playLock() { playWav("/lock.wav") }
    override fun playThud() { playWav("/thud.wav") }
    override fun playAlarm() { playWav("/alarm.wav") }
    override fun playDing() { playWav("/ding.wav") }
    override fun playDoubleDing() { playWav("/ding_double.wav") }
}

@Composable
actual fun rememberSoundPlayer(): SoundPlayer {
    return remember { JvmSoundPlayer() }
}
