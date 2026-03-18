package com.particle.receiver.audio

import android.content.Context
import com.particle.receiver.data.TouchEvent
import org.json.JSONObject

class InstrumentRouter(
    private val context: Context,
    private val engine: SoundEngine
) {
    private val routes  = mutableMapOf<String, Instrument>()
    private var default: Instrument = GuitarInstrument(engine, context)

    private val guitar = GuitarInstrument(engine, context)
    private val bass   = BassInstrument(engine)
    private val drums  = DrumInstrument(engine)

    init { reload() }

    fun route(event: TouchEvent) {
        val instrument = routes[event.deviceId] ?: default
        instrument.handle(event)
    }

    fun reload() {
        routes.clear()
        try {
            val json   = context.assets.open("instruments.json")
                .bufferedReader().use { it.readText() }
            val root   = JSONObject(json)
            val arr    = root.getJSONArray("assignments")
            val defStr = root.optString("default", "guitar")
            default    = instrumentFor(defStr)
            for (i in 0 until arr.length()) {
                val obj      = arr.getJSONObject(i)
                val deviceId = obj.getString("deviceId")
                val name     = obj.getString("instrument")
                routes[deviceId] = instrumentFor(name)
            }
        } catch (e: Exception) {
            android.util.Log.w("InstrumentRouter", "Could not load instruments.json: ${e.message}")
        }
    }

    private fun instrumentFor(name: String): Instrument = when (name.lowercase()) {
        "bass"  -> bass
        "drums" -> drums
        else    -> guitar
    }
}