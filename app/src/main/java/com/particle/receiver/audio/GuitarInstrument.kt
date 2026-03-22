package com.particle.receiver.audio

import android.os.Handler
import android.os.Looper
import com.particle.receiver.data.TouchEvent
import com.particle.receiver.data.TouchEventType
import kotlin.math.*

class GuitarInstrument(
    private val engine: SoundEngine,
    private val context: android.content.Context,
    val continuous: Boolean = false,
    val synthEngine: SynthEngine? = null,
    private val onNoteTriggered: ((normX: Float, normY: Float) -> Unit)? = null
) : Instrument {

    private val pentatonic = intArrayOf(0, 3, 5, 7, 10, 12, 15, 17, 19, 22, 24, 27)

    private val activePointers = mutableSetOf<Int>()

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

    // Continuous mode state
    private val lastY = mutableMapOf<Int, Float>()

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
                engine.play(resId, event.pressure.coerceIn(0.4f, 1.0f), pitch)
            }
            TouchEventType.TOUCH_MOVE -> {
                val now   = System.currentTimeMillis()
                val last  = lastTriggerMs[event.pointerId] ?: 0L
                val speed = sqrt(event.velocityX * event.velocityX +
                        event.velocityY * event.velocityY)
                if (now - last >= DEBOUNCE_MS && speed > 0.003f) {
                    val (resId, pitch) = resolveFromGrid(event.x, event.y)
                    engine.play(resId, (speed * 80f).coerceIn(0.2f, 0.9f), pitch)
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
        val baseCol = if (event.x < 0.5f) 0 else 1
        val baseRow = when {
            event.y < 0.25f -> 0
            event.y < 0.50f -> 1
            event.y < 0.75f -> 2
            else            -> 3
        }
        // Strum upward from current row
        listOf(0, 1, 2).forEachIndexed { i, offset ->
            val targetRow = (baseRow - offset).coerceAtLeast(0)
            Handler(Looper.getMainLooper()).postDelayed({
                val (resId, pitch) = resolveFromGrid(
                    if (baseCol == 0) 0.25f else 0.75f,
                    when (targetRow) {
                        0    -> 0.125f
                        1    -> 0.375f
                        2    -> 0.625f
                        else -> 0.875f
                    }
                )
                engine.play(resId, 0.7f, pitch)
            }, i * 40L)
        }
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
    // ── Shared helpers ───────────────────────────────────────────────────────

    private fun resolve(normX: Float): Pair<Int, Float> {
        val semitone = pentatonic[
            (normX * pentatonic.size).toInt().coerceIn(0, pentatonic.size - 1)
        ]
        return sampleForSemitone(semitone)
    }

    private fun sampleForSemitone(semitone: Int): Pair<Int, Float> {
        val best = samples.minByOrNull { abs(it.second - semitone) }!!
        val diff = semitone - best.second
        val rate = 2f.pow(diff / 12f)
        return Pair(best.first, rate)
    }

    private fun resolveFromGrid(normX: Float, normY: Float): Pair<Int, Float> {
        val col = if (normX < 0.5f) 0 else 1
        val row = when {
            normY < 0.25f -> 0
            normY < 0.50f -> 1
            normY < 0.75f -> 2
            else          -> 3
        }
        // Grid layout matches NoteGridView.ONESHOT_GRID exactly:
        // row 0: A4 (col 0), D5 (col 1)
        // row 1: B3 (col 0), E4 (col 1)
        // row 2: D3 (col 0), G3 (col 1)
        // row 3: E2 (col 0), A2 (col 1)
        val resId = when {
            row == 0 && col == 0 -> com.particle.receiver.R.raw.guitar_a4
            row == 0 && col == 1 -> com.particle.receiver.R.raw.guitar_d5
            row == 1 && col == 0 -> com.particle.receiver.R.raw.guitar_b3
            row == 1 && col == 1 -> com.particle.receiver.R.raw.guitar_e4
            row == 2 && col == 0 -> com.particle.receiver.R.raw.guitar_d3
            row == 2 && col == 1 -> com.particle.receiver.R.raw.guitar_g3
            row == 3 && col == 0 -> com.particle.receiver.R.raw.guitar_e2
            else                 -> com.particle.receiver.R.raw.guitar_a2
        }
        return Pair(resId, 1.0f)  // pitch = 1.0 — no shifting, play sample at natural pitch
    }
}