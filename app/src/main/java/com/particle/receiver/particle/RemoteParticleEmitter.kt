package com.particle.receiver.particle

import com.particle.receiver.audio.InstrumentRouter
import com.particle.receiver.data.TouchEventType
import com.particle.receiver.network.UdpReceiver
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class RemoteParticleEmitter(
    private val receiver: UdpReceiver,
    private val system: ParticleSystem,
    private val router: InstrumentRouter,
    private val screenW: Float,
    private val screenH: Float,
    private val glQueue: (Runnable) -> Unit,
    private val onNoteTriggered: ((normX: Float, normY: Float, deviceId: String) -> Unit)? = null
) {
    private val emitter    = ParticleEmitter(system)
    private val scope      = CoroutineScope(
        Dispatchers.IO.limitedParallelism(1) + SupervisorJob()
    )
    private val deviceHues = ConcurrentHashMap<String, Float>()

    private fun hueForDevice(deviceId: String): Float {
        val instrumentName = router.instrumentNameFor(deviceId)
        // Add a small per-device offset within the range so multiple
        // devices of the same instrument type have slightly different shades
        val deviceOffset = (deviceId.hashCode().toFloat().let {
            kotlin.math.abs(it) % 30f
        })
        return when (instrumentName) {
            "drums"          -> 45f  + deviceOffset   // yellows 45–75
            "bass"           -> 220f + deviceOffset   // blues 220–250
            "bass_continuous"-> 200f + deviceOffset   // cyan-blues 200–230
            "sync"           -> 120f + deviceOffset   // greens 120–150
            else             -> 0f   + deviceOffset   // reds 0–30
        }
    }

    fun start() {
        scope.launch {
            for (event in receiver.events) {
                val x   = event.x  * screenW
                val y   = event.y  * screenH
                val vx  = event.velocityX * screenW
                val vy  = event.velocityY * screenH
                val hue = hueForDevice(event.deviceId)

                // Audio
                router.route(event)

                // Note grid highlight on TOUCH_DOWN
                if (event.type == TouchEventType.TOUCH_DOWN) {
                    onNoteTriggered?.invoke(event.x, event.y, event.deviceId)
                }

                // Visuals
                glQueue(Runnable {
                    when (event.type) {
                        TouchEventType.TOUCH_DOWN  -> emitter.onTouchDown(x, y, event.pressure, hue)
                        TouchEventType.TOUCH_MOVE  -> emitter.onTouchMove(x, y, vx, vy, hue)
                        TouchEventType.TOUCH_UP    -> emitter.onTouchUp(x, y, event.holdDurationMs)
                        TouchEventType.TOUCH_BURST -> emitter.onBurst(x, y, event.holdDurationMs, hue)
                    }
                })
            }
        }
    }

    fun stop() = scope.cancel()
}