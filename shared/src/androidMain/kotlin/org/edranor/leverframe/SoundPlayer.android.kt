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
 * Android implementation of the SoundPlayer using Android's native SoundPool API
 * for low-latency playback of lever frame sound effects.
 */
package org.edranor.leverframe

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class AndroidSoundPlayer(context: Context) : SoundPlayer {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val clankId: Int
    private val lockId: Int
    private val thudId: Int
    private val alarmId: Int
    private val dingId: Int
    private val dingDoubleId: Int

    init {
        val packageName = context.packageName
        fun loadSound(name: String): Int {
            val resId = context.resources.getIdentifier(name, "raw", packageName)
            return if (resId != 0) soundPool.load(context, resId, 1) else 0
        }
        clankId = loadSound("clank")
        lockId = loadSound("lock")
        thudId = loadSound("thud")
        alarmId = loadSound("alarm")
        dingId = loadSound("ding")
        dingDoubleId = loadSound("ding_double")
    }

    override fun playClank() { if (clankId != 0) soundPool.play(clankId, 1f, 1f, 1, 0, 1f) }
    override fun playLock() { if (lockId != 0) soundPool.play(lockId, 1f, 1f, 1, 0, 1f) }
    override fun playThud() { if (thudId != 0) soundPool.play(thudId, 1f, 1f, 1, 0, 1f) }
    override fun playAlarm() { if (alarmId != 0) soundPool.play(alarmId, 1f, 1f, 1, 0, 1f) }
    override fun playDing() { if (dingId != 0) soundPool.play(dingId, 1f, 1f, 1, 0, 1f) }
    override fun playDoubleDing() { if (dingDoubleId != 0) soundPool.play(dingDoubleId, 1f, 1f, 1, 0, 1f) }
}

@Composable
actual fun rememberSoundPlayer(): SoundPlayer {
    val context = LocalContext.current
    return remember(context) { AndroidSoundPlayer(context) }
}
