package com.particle.receiver.network

import android.util.Log
import com.particle.receiver.data.TouchEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class BroadcastServer(
    private val port: Int = 9876,
    private val broadcastIp: String = "255.255.255.255"
) {
    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channel = Channel<TouchEvent>(Channel.BUFFERED)

    private var socket: DatagramSocket? = null
    private var target: InetAddress?    = null

    fun start() {
        socket = DatagramSocket().apply {
            broadcast = true
            reuseAddress = true
        }
        target = InetAddress.getByName(broadcastIp)
        scope.launch { for (e in channel) send(e) }
    }

    fun enqueue(e: TouchEvent) { channel.trySend(e) }

    private fun send(e: TouchEvent) {
        try {
            val bytes = TouchEventSerializer.toJson(e).toByteArray(Charsets.UTF_8)
            socket?.send(DatagramPacket(bytes, bytes.size, target, port))
        } catch (ex: Exception) {
            Log.w("BroadcastServer", "UDP send failed: ${ex.message}")
        }
    }

    fun stop() {
        scope.cancel()
        channel.close()
        socket?.close()
        socket = null
    }
}