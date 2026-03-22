package com.particle.receiver.audio

import android.content.Context
import com.particle.receiver.data.TouchEvent
import org.json.JSONObject

class InstrumentRouter(
    private val context: Context,
    private val engine: SoundEngine
) {
    private val routes      = mutableMapOf<String, Instrument>()
    private val gridDevices = mutableSetOf<String>()
    private var default: Instrument = GuitarInstrument(engine, context)

    // One SynthEngine per instrument type — shared across device assignments
    private val guitarSynth = SynthEngine(SynthEngine.SynthInstrumentType.GUITAR)
    private val bassSynth   = SynthEngine(SynthEngine.SynthInstrumentType.BASS)
    private val syncSynth   = SynthEngine(SynthEngine.SynthInstrumentType.SYNC)

    enum class GridType { NONE, CHROMATIC, GUITAR_ONESHOT, BASS_ONESHOT, DRUM }

    // Callback for one-shot note triggers — wired from ReceiverActivity
    var onOneShotNoteTriggered: ((normX: Float, normY: Float) -> Unit)? = null

    init { reload() }

    fun route(event: TouchEvent) {
        val instrument = routes[event.deviceId] ?: default
        instrument.handle(event)
    }

    fun gridTypeFor(deviceId: String): GridType {
        if (!gridDevices.contains(deviceId)) return GridType.NONE
        val instrument = routes[deviceId] ?: return GridType.NONE
        return when {
            instrument is GuitarInstrument && instrument.continuous  -> GridType.CHROMATIC
            instrument is GuitarInstrument && !instrument.continuous -> GridType.GUITAR_ONESHOT
            instrument is BassInstrument   && instrument.continuous  -> GridType.CHROMATIC
            instrument is BassInstrument   && !instrument.continuous -> GridType.BASS_ONESHOT
            instrument is DrumInstrument                             -> GridType.DRUM
            else -> GridType.NONE
        }
    }


    fun shouldShowGrid(deviceId: String): Boolean = gridDevices.contains(deviceId)

    fun hasContinuousInstrument(): Boolean = routes.values.any {
        it is GuitarInstrument || it is BassInstrument
    }

    fun reload() {
        routes.clear()
        gridDevices.clear()
        try {
            val json   = context.assets.open("instruments.json")
                .bufferedReader().use { it.readText() }
            val root   = JSONObject(json)
            val arr    = root.getJSONArray("assignments")
            val defStr = root.optString("default", "guitar")
            default    = instrumentFor(defStr, false)
            for (i in 0 until arr.length()) {
                val obj        = arr.getJSONObject(i)
                val deviceId   = obj.getString("deviceId")
                val name       = obj.getString("instrument")
                val continuous = obj.optBoolean("continuous", false)
                val showGrid   = obj.optBoolean("showGrid", false)
                routes[deviceId] = instrumentFor(name, continuous)
                if (showGrid) gridDevices.add(deviceId) else gridDevices.remove(deviceId)
            }
        } catch (e: Exception) {
            android.util.Log.w("InstrumentRouter", "Could not load instruments.json: ${e.message}")
        }
    }

    fun releaseAll() {
        guitarSynth.releaseAll()
        bassSynth.releaseAll()
        syncSynth.releaseAll()
    }

    private fun instrumentFor(name: String, continuous: Boolean): Instrument = when (name.lowercase()) {
        "bass"  -> BassInstrument(engine, continuous,
            if (continuous) bassSynth else null,
            if (!continuous) onOneShotNoteTriggered else null)
        "drums" -> DrumInstrument(engine, onOneShotNoteTriggered)
        "sync"  -> GuitarInstrument(engine, context, continuous, syncSynth)
        else    -> GuitarInstrument(engine, context, continuous,
            if (continuous) guitarSynth else null,
            if (!continuous) onOneShotNoteTriggered else null)
    }

    fun instrumentNameFor(deviceId: String): String {
        val instrument = routes[deviceId] ?: return "guitar"
        return when (instrument) {
            is DrumInstrument  -> "drums"
            is BassInstrument  -> if (instrument.continuous) "bass_continuous" else "bass"
            is GuitarInstrument -> when {
                instrument.continuous && instrument.synthEngine != null -> "sync"
                else -> "guitar"
            }
            else -> "guitar"
        }
    }
}