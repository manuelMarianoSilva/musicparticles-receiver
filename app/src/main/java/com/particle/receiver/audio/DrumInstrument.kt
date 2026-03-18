package com.particle.receiver.audio

import android.os.Handler
import android.os.Looper
import com.particle.receiver.data.TouchEvent
import com.particle.receiver.data.TouchEventType

class DrumInstrument(private val engine: SoundEngine) : Instrument {

    override fun handle(event: TouchEvent) {
        when (event.type) {
            TouchEventType.TOUCH_DOWN  -> hit(event, event.pressure.coerceIn(0.4f, 1.0f))
            TouchEventType.TOUCH_BURST -> roll(event)
            TouchEventType.TOUCH_MOVE,
            TouchEventType.TOUCH_UP    -> { }
        }
    }

    private fun hit(event: TouchEvent, volume: Float) {
        engine.play(zoneFor(event.x, event.y), volume)
    }

    private fun roll(event: TouchEvent) {
        val resId = zoneFor(event.x, event.y)
        listOf(0L, 80L, 160L).forEach { delay ->
            Handler(Looper.getMainLooper()).postDelayed({
                engine.play(resId, 0.7f)
            }, delay)
        }
    }

    private fun zoneFor(x: Float, y: Float): Int = when {
        y < 0.33f -> if (x < 0.5f) com.particle.receiver.R.raw.drum_hihat_closed
        else           com.particle.receiver.R.raw.drum_crash
        y < 0.66f -> if (x < 0.5f) com.particle.receiver.R.raw.drum_tom_hi
        else           com.particle.receiver.R.raw.drum_ride
        y < 0.85f -> when {
            x < 0.25f -> com.particle.receiver.R.raw.drum_tom_lo
            x < 0.75f -> com.particle.receiver.R.raw.drum_snare
            else       -> com.particle.receiver.R.raw.drum_tom_lo
        }
        else       -> if (x < 0.5f) com.particle.receiver.R.raw.drum_hihat_open
        else           com.particle.receiver.R.raw.drum_kick
    }
}