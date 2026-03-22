package com.particle.receiver.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.*

class SynthVoice(
    private val waveform: (Float) -> Float,
    private val syncEnabled: Boolean = false
) {
    companion object {
        const val SAMPLE_RATE    = 44100
        const val BUFFER_SAMPLES = 2048
    }

    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile var targetMasterFrequency = 440f
    @Volatile var targetSlaveFrequency  = 440f
    @Volatile var targetVolume          = 0f
    @Volatile var releasing             = false

    private var currentFrequency = 440f
    private var currentVolume    = 0f
    private var phase            = 0f
    private var releaseStep      = 0f

    private val FREQ_SLEW = 0.15f
    private val VOL_SLEW  = 0.02f

    fun start() {
        val minBuf  = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val bufSize = maxOf(minBuf, BUFFER_SAMPLES * 4)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        scope.launch {
            val buffer     = FloatArray(BUFFER_SAMPLES)
            var lastSample = 0f
            while (isActive) {
                currentFrequency += (targetSlaveFrequency - currentFrequency) * FREQ_SLEW

                if (releasing) {
                    currentVolume *= (1f - releaseStep)   // exponential decay
                    if (currentVolume < 0.001f) currentVolume = 0f
                } else {
                    currentVolume += (targetVolume - currentVolume) * VOL_SLEW
                }

                for (i in buffer.indices) {
                    val saw1   = WaveGenerator.sawtooth(phase)
                    val saw2   = WaveGenerator.sawtooth(phase * 1.007f)
                    val sub    = WaveGenerator.sine(phase * 0.5f) * 0.4f
                    val raw    = (saw1 * 0.4f + saw2 * 0.4f + sub) * currentVolume
                    lastSample = lastSample * 0.4f + raw * 0.6f
                    buffer[i]  = lastSample
                    phase      = (phase + currentFrequency / SAMPLE_RATE.toFloat()) % 1f
                }

                if (!isActive) break
                try {
                    audioTrack?.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                } catch (e: Exception) {
                    android.util.Log.w("SynthVoice", "Write failed: ${e.message}")
                    break
                }
            }
        }
    }

    fun setMasterFrequency(hz: Float) {
        targetMasterFrequency = hz.coerceIn(20f, 2000f)
        targetSlaveFrequency  = hz.coerceIn(20f, 2000f)
    }

    fun setSlaveFrequency(hz: Float) {
        targetSlaveFrequency = hz.coerceIn(20f, 8000f)
    }

    fun setVolume(v: Float) {
        targetVolume = v.coerceIn(0f, 1f)
    }

    fun startRelease(durationMs: Int = 800) {
        releasing = true
        // Exponential decay coefficient — smaller = longer tail
        // Tuned so volume reaches near zero within durationMs
        releaseStep = 1f - (0.001f.pow(1f / ((durationMs / 1000f) * SAMPLE_RATE / BUFFER_SAMPLES)))
    }

    fun stop() {
        targetVolume = 0f
        scope.launch {
            delay(600)
            val track = audioTrack
            audioTrack = null
            scope.cancel()
            track?.stop()
            track?.release()
        }
    }
}