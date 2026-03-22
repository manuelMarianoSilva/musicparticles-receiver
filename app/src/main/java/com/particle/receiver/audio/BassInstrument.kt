package com.particle.receiver.audio

import android.os.Handler
import android.os.Looper
import com.particle.receiver.data.TouchEvent
import com.particle.receiver.data.TouchEventType
import kotlin.math.*

class BassInstrument(
    private val engine: SoundEngine,
    val continuous: Boolean = false,
    private val synthEngine: SynthEngine? = null,
    private val onNoteTriggered: ((normX: Float, normY: Float) -> Unit)? = null
) : Instrument {

    private val samples = listOf<Pair<Int, Int>>(
        Pair(com.particle.receiver.R.raw.bass_e1,  0),
        Pair(com.particle.receiver.R.raw.bass_a1,  5),
        Pair(com.particle.receiver.R.raw.bass_d2, 10),
        Pair(com.particle.receiver.R.raw.bass_g2, 15)
    )

    private val lastTriggerMs = mutableMapOf<Int, Long>()
    private val DEBOUNCE_MS   = 200L
    private val lastY         = mutableMapOf<Int, Float>()
    private val activePointers = mutableSetOf<Int>()

    override fun handle(event: TouchEvent) {
        if (continuous && synthEngine != null) handleContinuous(event)
        else handleOneShot(event)
    }

    // ── One-shot mode ────────────────────────────────────────────────────────

    private fun handleOneShot(event: TouchEvent) {
        when (event.type) {
            TouchEventType.TOUCH_DOWN -> {
                onNoteTriggered?.invoke(event.x, event.y)
                val (resId, pitch) = resolveFromGrid(event.x, event.y)
                engine.play(resId, event.pressure.coerceIn(0.5f, 1.0f), pitch)
            }
            TouchEventType.TOUCH_MOVE -> {
                val now   = System.currentTimeMillis()
                val last  = lastTriggerMs[event.pointerId] ?: 0L
                val speed = sqrt(event.velocityX * event.velocityX +
                        event.velocityY * event.velocityY)
                if (now - last >= DEBOUNCE_MS && speed > 0.005f) {
                    val (resId, pitch) = resolveFromGrid(event.x, event.y)
                    engine.play(resId, (speed * 60f).coerceIn(0.3f, 0.85f), pitch)
                    lastTriggerMs[event.pointerId] = now
                }
            }
            TouchEventType.TOUCH_BURST -> {
                val (resId, pitch) = resolveFromGrid(event.x, event.y)
                engine.play(resId, 0.9f, pitch * 0.5f)
            }
            TouchEventType.TOUCH_UP -> lastTriggerMs.remove(event.pointerId)
        }
    }

    private fun resolveFromGrid(normX: Float, normY: Float): Pair<Int, Float> {
        val col = if (normX < 0.5f) 0 else 1
        val row = if (normY < 0.5f) 0 else 1
        // Grid layout matches NoteGridView.BASS_GRID exactly:
        // row 0: D2 (col 0), G2 (col 1)
        // row 1: E1 (col 0), A1 (col 1)
        val resId = when {
            row == 0 && col == 0 -> com.particle.receiver.R.raw.bass_d2
            row == 0 && col == 1 -> com.particle.receiver.R.raw.bass_g2
            row == 1 && col == 0 -> com.particle.receiver.R.raw.bass_e1
            else                 -> com.particle.receiver.R.raw.bass_a1
        }
        return Pair(resId, 1.0f)
    }

    // ── Continuous mode ──────────────────────────────────────────────────────

    private fun handleContinuous(event: TouchEvent) {
        val synth = synthEngine ?: return
        when (event.type) {
            TouchEventType.TOUCH_DOWN -> {
                if (!activePointers.contains(event.pointerId)) {
                    activePointers.add(event.pointerId)
                    lastY[event.pointerId] = event.y
                    synth.noteOn(
                        event.pointerId,
                        event.x,
                        event.y,
                        event.pressure.coerceIn(0.3f, 1.0f)
                    )
                }
            }
            TouchEventType.TOUCH_MOVE -> {
                val previousY = lastY[event.pointerId] ?: event.y
                val deltaY    = event.y - previousY
                lastY[event.pointerId] = event.y
                synth.noteUpdate(event.pointerId, deltaY, 0.8f)
            }
            TouchEventType.TOUCH_BURST -> synth.pulse(event.pointerId)
            TouchEventType.TOUCH_UP    -> {
                activePointers.remove(event.pointerId)
                lastY.remove(event.pointerId)
                synth.noteOff(event.pointerId)
            }
        }
    }
}