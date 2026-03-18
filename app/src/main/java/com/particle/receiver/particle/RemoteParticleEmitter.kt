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
    private val glQueue: (Runnable) -> Unit
) {
    private val emitter    = ParticleEmitter(system)
    private val scope      = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val deviceHues = ConcurrentHashMap<String, Float>()

    private fun hueForDevice(deviceId: String): Float =
        deviceHues.getOrPut(deviceId) {
            kotlin.math.abs(deviceId.hashCode().toFloat()) % 360f
        }

    fun start() {
        scope.launch {
            for (event in receiver.events) {
                val x   = event.x  * screenW
                val y   = event.y  * screenH
                val vx  = event.velocityX * screenW
                val vy  = event.velocityY * screenH
                val hue = hueForDevice(event.deviceId)

                router.route(event)

                glQueue {
                    when (event.type) {
                        TouchEventType.TOUCH_DOWN  -> emitter.onTouchDown(x, y, event.pressure, hue)
                        TouchEventType.TOUCH_MOVE  -> emitter.onTouchMove(x, y, vx, vy, hue)
                        TouchEventType.TOUCH_UP    -> emitter.onTouchUp(x, y, event.holdDurationMs)
                        TouchEventType.TOUCH_BURST -> emitter.onBurst(x, y, event.holdDurationMs, hue)
                    }
                }
            }
        }
    }

    fun stop() = scope.cancel()
}