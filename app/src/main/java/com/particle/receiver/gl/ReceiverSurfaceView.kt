package com.particle.receiver.gl

import android.content.Context
import android.opengl.GLSurfaceView
import com.particle.receiver.network.UdpReceiver
import com.particle.receiver.particle.RemoteParticleEmitter
import com.particle.receiver.audio.InstrumentRouter
import com.particle.receiver.particle.ParticleSystem
import com.particle.receiver.gl.ParticleRenderer

class ReceiverSurfaceView(
    context: Context,
    private val udpReceiver: UdpReceiver,
    private val router: InstrumentRouter,
    screenW: Float,
    screenH: Float
) : GLSurfaceView(context) {

    private val particleSystem = ParticleSystem(20_000)
    private val remoteEmitter: RemoteParticleEmitter

    init {
        setEGLContextClientVersion(2)
        setRenderer(ParticleRenderer(particleSystem))
        renderMode = RENDERMODE_CONTINUOUSLY

        remoteEmitter = RemoteParticleEmitter(
            receiver = udpReceiver,
            system   = particleSystem,
            router   = router,
            screenW  = screenW,
            screenH  = screenH,
            glQueue  = { runnable -> queueEvent(runnable) }
        )
        remoteEmitter.start()
    }

    fun release() = remoteEmitter.stop()
}