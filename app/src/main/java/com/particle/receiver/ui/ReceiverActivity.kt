package com.particle.receiver.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.particle.receiver.audio.InstrumentRouter
import com.particle.receiver.audio.SoundEngine
import com.particle.receiver.gl.ReceiverSurfaceView
import com.particle.receiver.network.DiscoveryServer
import com.particle.receiver.network.UdpReceiver
import java.net.NetworkInterface

class ReceiverActivity : AppCompatActivity() {

    private lateinit var surfaceView: ReceiverSurfaceView
    private lateinit var udpReceiver: UdpReceiver
    private lateinit var discoveryServer: DiscoveryServer
    private lateinit var soundEngine: SoundEngine
    private lateinit var instrumentRouter: InstrumentRouter
    private lateinit var statusText: TextView
    private val hudHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("ReceiverActivity", "onCreate called")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        val dm = resources.displayMetrics
        val sw = dm.widthPixels.toFloat()
        val sh = dm.heightPixels.toFloat()

        val localIp = getLocalIpAddress() ?: "0.0.0.0"
        android.util.Log.d("ReceiverActivity", "Local IP: $localIp")

        udpReceiver = UdpReceiver(port = 9876).also { it.start() }

        discoveryServer = DiscoveryServer(
            onSenderFound = { ip ->
                android.util.Log.d("ReceiverActivity", "Sender found: $ip")
                udpReceiver.registerSender(ip)
            },
            onSenderLost = { ip ->
                android.util.Log.d("ReceiverActivity", "Sender lost: $ip")
                udpReceiver.unregisterSender(ip)
            }
        )
        discoveryServer.start(localIp)

        soundEngine      = SoundEngine(this)
        instrumentRouter = InstrumentRouter(this, soundEngine)
        surfaceView      = ReceiverSurfaceView(this, udpReceiver, instrumentRouter, sw, sh)

        val root = FrameLayout(this).apply {
            addView(surfaceView)
            statusText = TextView(context).apply {
                setTextColor(android.graphics.Color.argb(200, 255, 255, 255))
                textSize  = 11f
                typeface  = android.graphics.Typeface.MONOSPACE
                setPadding(24, 24, 24, 24)
            }
            addView(statusText)
        }
        setContentView(root)

        root.setOnClickListener { toggleHud() }
        startHudUpdates()
    }

    private var hudVisible = true

    private fun toggleHud() {
        hudVisible = !hudVisible
        statusText.visibility = if (hudVisible) View.VISIBLE else View.GONE
    }

    private fun startHudUpdates() {
        hudHandler.post(object : Runnable {
            override fun run() {
                if (hudVisible) updateHud()
                hudHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun updateHud() {
        val now = System.currentTimeMillis()
        if (udpReceiver.devices.isEmpty()) {
            statusText.text = "SEARCHING FOR SENDERS…"
            return
        }
        val sb = StringBuilder()
        udpReceiver.devices.entries.forEachIndexed { i, (deviceId, stats) ->
            val ageSec = (now - stats.lastPacketMs) / 1000L
            val status = when (stats.state) {
                UdpReceiver.ConnectionState.LIVE      -> "● LIVE"
                UdpReceiver.ConnectionState.IDLE      -> "○ IDLE  (${ageSec}s ago)"
                UdpReceiver.ConnectionState.SEARCHING -> "… SEARCHING"
            }
            val shortId = deviceId.takeLast(8)
            sb.appendLine("[$shortId]  $status")
            sb.appendLine("  ip: ${stats.ip}   pkts: ${stats.packetsReceived}")
            if (i < udpReceiver.devices.size - 1) sb.appendLine()
        }
        statusText.text = sb.toString().trimEnd()
    }

    private fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains('.') == true }
                ?.hostAddress
        } catch (e: Exception) {
            android.util.Log.w("ReceiverActivity", "Could not get IP: ${e.message}")
            null
        }
    }

    override fun onResume()  { super.onResume();  surfaceView.onResume() }
    override fun onPause()   { super.onPause();   surfaceView.onPause() }
    override fun onDestroy() {
        super.onDestroy()
        hudHandler.removeCallbacksAndMessages(null)
        surfaceView.release()
        discoveryServer.stop()
        udpReceiver.stop()
        soundEngine.release()
    }
}