package com.particle.receiver.gl

import android.content.Context
import android.opengl.GLSurfaceView
import com.particle.receiver.audio.InstrumentRouter
import com.particle.receiver.network.UdpReceiver
import com.particle.receiver.particle.ParticleSystem
import com.particle.receiver.particle.RemoteParticleEmitter

class ReceiverSurfaceView(
    context: Context,
    private val udpReceiver: UdpReceiver,
    private val router: InstrumentRouter,
    screenW: Float,
    screenH: Float,
    onNoteTriggered: ((normX: Float, normY: Float, deviceId: String) -> Unit)? = null
) : GLSurfaceView(context) {

    private val particleSystem = ParticleSystem(40_000)
    private val remoteEmitter: RemoteParticleEmitter

    init {
        setEGLContextClientVersion(2)
        setRenderer(ParticleRenderer(particleSystem))
        renderMode = RENDERMODE_CONTINUOUSLY

        remoteEmitter = RemoteParticleEmitter(
            receiver          = udpReceiver,
            system            = particleSystem,
            router            = router,
            screenW           = screenW,
            screenH           = screenH,
            glQueue           = { runnable -> queueEvent(runnable) },
            onNoteTriggered   = onNoteTriggered
        )
        remoteEmitter.start()
    }

    fun release() = remoteEmitter.stop()
}