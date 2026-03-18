//package com.particle.receiver.audio
//
//import android.content.Context
//import android.media.AudioAttributes
//import android.media.SoundPool
//
//class SoundEngine(context: Context) {
//
//    private val pool: SoundPool = SoundPool.Builder()
//        .setMaxStreams(16)
//        .setAudioAttributes(
//            AudioAttributes.Builder()
//                .setUsage(AudioAttributes.USAGE_GAME)
//                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//                .build()
//        ).build()
//
//    private val soundIds = mutableMapOf<Int, Int>()
//
//    fun load(context: Context, resId: Int) {
//        soundIds[resId] = pool.load(context, resId, 1)
//    }
//
//    fun play(resId: Int, volume: Float, pitch: Float = 1.0f) {
//        val sid = soundIds[resId] ?: return
//        pool.play(sid, volume, volume, 1, 0, pitch.coerceIn(0.5f, 2.0f))
//    }
//
//    fun release() = pool.release()
//}

package com.particle.receiver.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.particle.receiver.R

class SoundEngine(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val soundIds = mutableMapOf<Int, Int>()

    init {
        listOf(
            R.raw.guitar_e2, R.raw.guitar_a2, R.raw.guitar_d3, R.raw.guitar_g3,
            R.raw.guitar_b3, R.raw.guitar_e4, R.raw.guitar_a4, R.raw.guitar_d5,
            R.raw.bass_e1, R.raw.bass_a1, R.raw.bass_d2, R.raw.bass_g2,
            R.raw.drum_kick, R.raw.drum_snare, R.raw.drum_hihat_closed,
            R.raw.drum_hihat_open, R.raw.drum_crash, R.raw.drum_ride,
            R.raw.drum_tom_hi, R.raw.drum_tom_lo
        ).forEach { resId -> soundIds[resId] = pool.load(context, resId, 1) }
    }

    fun play(resId: Int, volume: Float, pitch: Float = 1.0f) {
        val sid = soundIds[resId] ?: run {
            android.util.Log.w("SoundEngine", "Sample not loaded for resId $resId")
            return
        }
        pool.play(sid, volume, volume, 1, 0, pitch.coerceIn(0.5f, 2.0f))
    }

    fun release() = pool.release()
}