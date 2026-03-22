package com.particle.receiver.audio

import kotlin.math.*

object WaveGenerator {

    /** Sine wave */
    fun sine(phase: Float): Float = sin(phase * 2f * PI.toFloat())

    /** Sawtooth wave */
    fun sawtooth(phase: Float): Float = 2f * (phase - floor(phase + 0.5f))

    /** Square wave */
    fun square(phase: Float): Float = if (phase % 1f < 0.5f) 1f else -1f

    /** Triangle wave */
    fun triangle(phase: Float): Float = 2f * abs(2f * (phase - floor(phase + 0.5f))) - 1f

    /** Guitar-like tone */
    fun guitar(phase: Float, ratio: Float = 0.7f): Float =
        sawtooth(phase) * ratio + sine(phase) * (1f - ratio)

    /** Bass tone */
    fun bass(phase: Float): Float =
        sine(phase) * 0.8f + sine(phase * 2f) * 0.2f

    /** Sync waveform */
    fun sync(slavePhase: Float, subPhase: Float = slavePhase * 0.5f): Float {
        val saw  = sawtooth(slavePhase)
        val saw2 = sawtooth(slavePhase * 1.003f)
        val sub  = sine(subPhase) * 0.3f
        return (saw * 0.5f + saw2 * 0.5f) * 0.7f + sub
    }

    // ── Note frequency table ─────────────────────────────────────────────────

    // All 12 notes across 3 octaves (C2–B4), indexed 0–35
    // Row 0 = octave 2 (low), Row 1 = octave 3 (mid), Row 2 = octave 4 (high)
    private val NOTE_FREQUENCIES = arrayOf(
        // Octave 2
        floatArrayOf(65.41f, 69.30f, 73.42f, 77.78f, 82.41f, 87.31f,
            92.50f, 98.00f, 103.83f, 110.00f, 116.54f, 123.47f),
        // Octave 3
        floatArrayOf(130.81f, 138.59f, 146.83f, 155.56f, 164.81f, 174.61f,
            185.00f, 196.00f, 207.65f, 220.00f, 233.08f, 246.94f),
        // Octave 4
        floatArrayOf(261.63f, 277.18f, 293.66f, 311.13f, 329.63f, 349.23f,
            369.99f, 392.00f, 415.30f, 440.00f, 466.16f, 493.88f)
    )

    private val NOTE_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    /**
     * Maps (x, y) screen position to a frequency.
     * y < 0.33  → octave 4 (high)
     * y 0.33–0.66 → octave 3 (mid)
     * y > 0.66  → octave 2 (low)
     * x 0.0–1.0 → C to B (12 semitones)
     */
    fun xyToFrequency(x: Float, y: Float): Float {
        val octave = when {
            y < 0.33f -> 2   // top = high
            y < 0.66f -> 1   // middle
            else      -> 0   // bottom = low
        }
        val noteIndex = (x * 12f).toInt().coerceIn(0, 11)
        return NOTE_FREQUENCIES[octave][noteIndex]
    }

    /**
     * Returns the note name for a given (x, y) position.
     * Useful for HUD display.
     */
    fun xyToNoteName(x: Float, y: Float): String {
        val octave = when {
            y < 0.33f -> 4
            y < 0.66f -> 3
            else      -> 2
        }
        val noteIndex = (x * 12f).toInt().coerceIn(0, 11)
        return "${NOTE_NAMES[noteIndex]}$octave"
    }

    /**
     * Legacy single-axis mapping — kept for bass instrument.
     */
    fun xToFrequency(x: Float, minHz: Float, maxHz: Float): Float =
        minHz * (maxHz / minHz).pow(x.coerceIn(0f, 1f))

    /**
     * Map y delta to pitch bend multiplier.
     */
    fun yToSyncMultiplier(y: Float): Float =
        1f + (1f - y.coerceIn(0f, 1f)) * 6f
}