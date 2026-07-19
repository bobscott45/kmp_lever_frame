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
    private val clickId: Int
    private val thudId: Int
    private val alarmId: Int

    init {
        val packageName = context.packageName
        fun loadSound(name: String): Int {
            val resId = context.resources.getIdentifier(name, "raw", packageName)
            return if (resId != 0) soundPool.load(context, resId, 1) else 0
        }
        clankId = loadSound("clank")
        clickId = loadSound("click")
        thudId = loadSound("thud")
        alarmId = loadSound("alarm")
    }

    override fun playClank() { if (clankId != 0) soundPool.play(clankId, 1f, 1f, 1, 0, 1f) }
    override fun playClick() { if (clickId != 0) soundPool.play(clickId, 1f, 1f, 1, 0, 1f) }
    override fun playThud() { if (thudId != 0) soundPool.play(thudId, 1f, 1f, 1, 0, 1f) }
    override fun playAlarm() { if (alarmId != 0) soundPool.play(alarmId, 1f, 1f, 1, 0, 1f) }
}

@Composable
actual fun rememberSoundPlayer(): SoundPlayer {
    val context = LocalContext.current
    return remember(context) { AndroidSoundPlayer(context) }
}
