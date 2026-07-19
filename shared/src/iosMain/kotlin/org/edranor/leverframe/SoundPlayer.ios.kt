@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.edranor.leverframe

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
