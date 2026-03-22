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
    private lateinit var noteGridView: NoteGridView
    private val hudHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        udpReceiver      = UdpReceiver(port = 9876).also { it.start() }
        soundEngine      = SoundEngine(this)
        instrumentRouter = InstrumentRouter(this, soundEngine)

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

        noteGridView = NoteGridView(this)

        // Wire one-shot note trigger to grid highlight
        instrumentRouter.onOneShotNoteTriggered = { normX, normY ->
            runOnUiThread {
                noteGridView.chromaticMode = false
                noteGridView.highlightOneShotCell(normX, normY)
            }
        }

        surfaceView = ReceiverSurfaceView(
            context         = this,
            udpReceiver     = udpReceiver,
            router          = instrumentRouter,
            screenW         = sw,
            screenH         = sh,
            onNoteTriggered = { normX, normY, deviceId ->
                val gridType = instrumentRouter.gridTypeFor(deviceId)
                if (gridType != InstrumentRouter.GridType.NONE) {
                    runOnUiThread {
                        when (gridType) {
                            InstrumentRouter.GridType.CHROMATIC -> {
                                noteGridView.chromaticMode = true
                                noteGridView.bassMode      = false
                                noteGridView.drumMode      = false
                                noteGridView.highlightCell(normX, normY)
                            }
                            InstrumentRouter.GridType.GUITAR_ONESHOT -> {
                                noteGridView.chromaticMode = false
                                noteGridView.bassMode      = false
                                noteGridView.drumMode      = false
                                noteGridView.highlightOneShotCell(normX, normY)
                            }
                            InstrumentRouter.GridType.BASS_ONESHOT -> {
                                noteGridView.chromaticMode = false
                                noteGridView.bassMode      = true
                                noteGridView.drumMode      = false
                                noteGridView.highlightBassCell(normX, normY)
                            }
                            InstrumentRouter.GridType.DRUM -> {
                                noteGridView.chromaticMode = false
                                noteGridView.bassMode      = false
                                noteGridView.drumMode      = true
                                noteGridView.highlightDrumCell(normX, normY)
                            }
                            InstrumentRouter.GridType.NONE -> { }
                        }
                    }
                }
            }
        )

        val root = FrameLayout(this).apply {
            addView(surfaceView)
            addView(noteGridView)
            statusText = TextView(context).apply {
                setTextColor(android.graphics.Color.argb(200, 255, 255, 255))
                textSize  = 11f
                typeface  = android.graphics.Typeface.MONOSPACE
                setPadding(24, 24, 24, 24)
            }
            addView(statusText)
        }
        setContentView(root)

        noteGridView.visibility = View.GONE
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
                updateGridVisibility()
                hudHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun updateGridVisibility() {
        val anyGridDevice = udpReceiver.devices.keys.any {
            instrumentRouter.gridTypeFor(it) != InstrumentRouter.GridType.NONE
        }
        noteGridView.visibility = if (anyGridDevice) View.VISIBLE else View.GONE
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
        instrumentRouter.releaseAll()
        soundEngine.release()
    }
}