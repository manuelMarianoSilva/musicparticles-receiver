package com.particle.receiver.audio

/**
 * Manages SynthVoice instances per active pointer.
 * Supports both simple and sync oscillator modes.
 */
class SynthEngine(private val instrument: SynthInstrumentType) {

    enum class SynthInstrumentType { GUITAR, BASS, SYNC }

    private val activeVoices = java.util.concurrent.ConcurrentHashMap<Int, SynthVoice>()

    private fun createVoice(): SynthVoice = when (instrument) {
        SynthInstrumentType.SYNC   -> SynthVoice(
            waveform    = { phase -> WaveGenerator.sawtooth(phase) },
            syncEnabled = true
        )
        SynthInstrumentType.GUITAR -> SynthVoice(
            waveform    = { phase -> WaveGenerator.guitar(phase) },
            syncEnabled = false
        )
        SynthInstrumentType.BASS   -> SynthVoice(
            waveform    = { phase -> WaveGenerator.bass(phase) },
            syncEnabled = false
        )
    }

    /**
     * Called on TOUCH_DOWN.
     * Maps (x, y) to a note frequency using the 3-octave grid.
     */
    fun noteOn(pointerId: Int, normX: Float, normY: Float, volume: Float) {
        activeVoices[pointerId]?.stop()
        val freq  = WaveGenerator.xyToFrequency(normX, normY)
        val voice = createVoice().apply {
            targetMasterFrequency = freq
            targetSlaveFrequency  = freq
            targetVolume          = 0f
            start()
            setVolume(volume)
        }
        activeVoices[pointerId] = voice
        android.util.Log.d("SynthEngine", "noteOn pointer=$pointerId note=${WaveGenerator.xyToNoteName(normX, normY)} freq=$freq")
    }

    /**
     * Called on TOUCH_MOVE.
     * deltaY controls pitch bend — x is ignored while held.
     */
    fun noteUpdate(pointerId: Int, deltaY: Float, volume: Float) {
        val voice = activeVoices[pointerId] ?: return
        val current = voice.targetSlaveFrequency
        val master  = voice.targetMasterFrequency
        val newSlave = (current * (1f - deltaY * 5f)).coerceIn(
            master * 0.25f,
            master * 4f
        )
        voice.setSlaveFrequency(newSlave)
        voice.setVolume(volume)
    }

    /** Called on TOUCH_BURST — brief volume pulse */
    fun pulse(pointerId: Int) {
        val voice = activeVoices[pointerId] ?: return
        voice.setVolume(1.0f)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            voice.setVolume(0.5f)
        }, 150)
    }

    /** Called on TOUCH_UP — exponential decay release */
    fun noteOff(pointerId: Int) {
        val voice = activeVoices.remove(pointerId) ?: return
        voice.startRelease(800)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            voice.stop()
        }, 900)
    }

    fun releaseAll() {
        activeVoices.values.forEach { it.stop() }
        activeVoices.clear()
    }
}