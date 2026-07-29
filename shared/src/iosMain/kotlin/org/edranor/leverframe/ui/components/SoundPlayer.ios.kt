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
 * iOS implementation of the SoundPlayer using AVFoundation (AVAudioPlayer)
 * to play standard .wav resources bundled in the iOS application.
 */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
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
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

class IosSoundPlayer : SoundPlayer {
    
    private var clankPlayer: AVAudioPlayer? = null
    private var lockPlayer: AVAudioPlayer? = null
    private var thudPlayer: AVAudioPlayer? = null
    private var alarmPlayer: AVAudioPlayer? = null
    private var dingPlayer: AVAudioPlayer? = null
    private var dingDoublePlayer: AVAudioPlayer? = null
    
    init {
        clankPlayer = loadPlayer("clank")
        lockPlayer = loadPlayer("lock")
        thudPlayer = loadPlayer("thud")
        alarmPlayer = loadPlayer("alarm")
        dingPlayer = loadPlayer("ding")
        dingDoublePlayer = loadPlayer("ding_double")
    }

    private fun loadPlayer(name: String): AVAudioPlayer? {
        val path = NSBundle.mainBundle.pathForResource(name, ofType = "wav") ?: return null
        val url = NSURL.fileURLWithPath(path)
        return AVAudioPlayer(contentsOfURL = url, error = null)?.apply { prepareToPlay() }
    }

    override fun playClank() { clankPlayer?.apply { currentTime = 0.0; play() } }
    override fun playLock() { lockPlayer?.apply { currentTime = 0.0; play() } }
    override fun playThud() { thudPlayer?.apply { currentTime = 0.0; play() } }
    override fun playAlarm() { alarmPlayer?.apply { currentTime = 0.0; play() } }
    override fun playDing() { dingPlayer?.apply { currentTime = 0.0; play() } }
    override fun playDoubleDing() { dingDoublePlayer?.apply { currentTime = 0.0; play() } }
}

@Composable
actual fun rememberSoundPlayer(): SoundPlayer {
    return remember { IosSoundPlayer() }
}
