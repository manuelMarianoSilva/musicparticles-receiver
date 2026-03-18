package com.particle.receiver.audio

import android.os.Handler
import android.os.Looper
import com.particle.receiver.data.TouchEvent
import com.particle.receiver.data.TouchEventType
import kotlin.math.*

class GuitarInstrument(private val engine: SoundEngine, private val context: android.content.Context) : Instrument {

    private val pentatonic = intArrayOf(0, 3, 5, 7, 10, 12, 15, 17, 19, 22, 24, 27)

    private val samples = listOf<Pair<Int, Int>>(
        Pair(com.particle.receiver.R.raw.guitar_e2,  0),
        Pair(com.particle.receiver.R.raw.guitar_a2,  5),
        Pair(com.particle.receiver.R.raw.guitar_d3, 10),
        Pair(com.particle.receiver.R.raw.guitar_g3, 15),
        Pair(com.particle.receiver.R.raw.guitar_b3, 19),
        Pair(com.particle.receiver.R.raw.guitar_e4, 24),
        Pair(com.particle.receiver.R.raw.guitar_a4, 29),
        Pair(com.particle.receiver.R.raw.guitar_d5, 34)
    )

    private val lastTriggerMs = mutableMapOf<Int, Long>()
    private val DEBOUNCE_MS = 120L

    override fun handle(event: TouchEvent) {
        when (event.type) {
            TouchEventType.TOUCH_DOWN  -> playNote(event, event.pressure.coerceIn(0.4f, 1.0f))
            TouchEventType.TOUCH_MOVE  -> {
                val now = System.currentTimeMillis()
                val last = lastTriggerMs[event.pointerId] ?: 0L
                val speed = sqrt(event.velocityX * event.velocityX + event.velocityY * event.velocityY)
                if (now - last >= DEBOUNCE_MS && speed > 0.003f) {
                    playNote(event, (speed * 80f).coerceIn(0.2f, 0.9f))
                    lastTriggerMs[event.pointerId] = now
                }
            }
            TouchEventType.TOUCH_BURST -> strum(event)
            TouchEventType.TOUCH_UP    -> lastTriggerMs.remove(event.pointerId)
        }
    }

    private fun playNote(event: TouchEvent, volume: Float) {
        val (resId, pitch) = resolve(event.x)
        engine.play(resId, volume, pitch)
    }

    private fun strum(event: TouchEvent) {
        val baseIndex = (event.x * pentatonic.size).toInt().coerceIn(0, pentatonic.size - 3)
        listOf(0, 1, 2).forEachIndexed { i, offset ->
            Handler(Looper.getMainLooper()).postDelayed({
                val semitone = pentatonic[baseIndex + offset]
                val (resId, pitch) = sampleForSemitone(semitone)
                engine.play(resId, 0.7f, pitch)
            }, i * 40L)
        }
    }

    private fun resolve(normX: Float): Pair<Int, Float> {
        val semitone = pentatonic[(normX * pentatonic.size).toInt().coerceIn(0, pentatonic.size - 1)]
        return sampleForSemitone(semitone)
    }

    private fun sampleForSemitone(semitone: Int): Pair<Int, Float> {
        val best = samples.minByOrNull { abs(it.second - semitone) }!!
        val diff = semitone - best.second
        val rate = 2f.pow(diff / 12f)
        return Pair(best.first, rate)
    }
}