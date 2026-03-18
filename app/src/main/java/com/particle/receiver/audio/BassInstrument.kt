package com.particle.receiver.audio

import android.os.Handler
import android.os.Looper
import com.particle.receiver.data.TouchEvent
import com.particle.receiver.data.TouchEventType
import kotlin.math.*

class BassInstrument(private val engine: SoundEngine) : Instrument {

    private val samples = listOf<Pair<Int, Int>>(
        Pair(com.particle.receiver.R.raw.bass_e1,  0),
        Pair(com.particle.receiver.R.raw.bass_a1,  5),
        Pair(com.particle.receiver.R.raw.bass_d2, 10),
        Pair(com.particle.receiver.R.raw.bass_g2, 15)
    )

    private val bassPattern = intArrayOf(0, 2, 4, 5, 7, 9, 10, 12)
    private val lastTriggerMs = mutableMapOf<Int, Long>()
    private val DEBOUNCE_MS = 200L

    override fun handle(event: TouchEvent) {
        when (event.type) {
            TouchEventType.TOUCH_DOWN  -> playNote(event, event.pressure.coerceIn(0.5f, 1.0f))
            TouchEventType.TOUCH_MOVE  -> {
                val now = System.currentTimeMillis()
                val last = lastTriggerMs[event.pointerId] ?: 0L
                val speed = sqrt(event.velocityX * event.velocityX + event.velocityY * event.velocityY)
                if (now - last >= DEBOUNCE_MS && speed > 0.005f) {
                    playNote(event, (speed * 60f).coerceIn(0.3f, 0.85f))
                    lastTriggerMs[event.pointerId] = now
                }
            }
            TouchEventType.TOUCH_BURST -> {
                val (resId, pitch) = resolve(event.x)
                engine.play(resId, 0.9f, pitch * 0.5f)
            }
            TouchEventType.TOUCH_UP    -> lastTriggerMs.remove(event.pointerId)
        }
    }

    private fun playNote(event: TouchEvent, volume: Float) {
        val (resId, pitch) = resolve(event.x)
        val yMod = if (event.y > 0.5f) 0.75f else 1.0f
        engine.play(resId, volume * yMod, pitch)
    }

    private fun resolve(normX: Float): Pair<Int, Float> {
        val semitone = bassPattern[(normX * bassPattern.size).toInt().coerceIn(0, bassPattern.size - 1)]
        val best = samples.minByOrNull { abs(it.second - semitone) }!!
        val rate = 2f.pow((semitone - best.second) / 12f)
        return Pair(best.first, rate)
    }
}