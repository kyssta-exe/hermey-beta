package com.kyssta.hermey.ui.theme

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Completion beep — plays a short positive-tone sound when a message finishes.
 * Uses Android ToneGenerator for zero-dependency tone.
 * ponytail: platform sound, no deps.
 */
@Composable
fun rememberCompletionBeep(context: Context = LocalContext.current): () -> Unit {
    val ctx = context.applicationContext
    return {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val volume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            if (volume > 0) {
                val tg = android.media.ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 100)
            }
        } catch (_: Exception) {}
    }
}
