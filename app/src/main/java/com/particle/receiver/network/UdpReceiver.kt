package com.particle.receiver.network

import com.particle.receiver.data.TouchEvent
import com.particle.receiver.network.TouchEventSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens on port 9876 for touch event packets from known senders.
 * Tracks per-sender connection state and packet stats.
 */
class UdpReceiver(private val port: Int = 9876) {

    val events = Channel<TouchEvent>(Channel.BUFFERED)

    enum class ConnectionState { SEARCHING, LIVE, IDLE }

    data class DeviceStats(
        var ip: String = "—",
        var packetsReceived: Long = 0L,
        var lastPacketMs: Long = 0L,
        var state: ConnectionState = ConnectionState.SEARCHING
    )

    val devices = ConcurrentHashMap<String, DeviceStats>()

    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null

    private val IDLE_TIMEOUT = 2000L

    fun start() {
        socket = DatagramSocket(port).apply { reuseAddress = true }
        android.util.Log.d("UdpReceiver", "Listening on port $port")

        // Receive touch event packets
        scope.launch {
            val buf    = ByteArray(4096)
            val packet = DatagramPacket(buf, buf.size)
            while (isActive) {
                try {
                    socket!!.receive(packet)
                    val json  = String(packet.data, 0, packet.length, Charsets.UTF_8)

                    // Ignore heartbeat packets — handled by DiscoveryServer
                    if (json.contains("HEARTBEAT")) continue

                    val event = TouchEventSerializer.fromJson(json)
                    android.util.Log.d("UdpReceiver", "Received event type=${event.type} pointer=${event.pointerId}")
                    events.trySend(event)

                    val stats = devices.getOrPut(event.deviceId) { DeviceStats() }
                    stats.ip              = packet.address.hostAddress ?: "?"
                    stats.packetsReceived++
                    stats.lastPacketMs    = System.currentTimeMillis()
                    stats.state           = ConnectionState.LIVE

                    android.util.Log.d("UdpReceiver", "Packet from ${stats.ip}")
                } catch (e: Exception) {
                    if (isActive) android.util.Log.w("UdpReceiver", "Receive error: ${e.message}")
                }
            }
        }

        // Monitor per-device idle state
        scope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                devices.values.forEach { stats ->
                    stats.state = when {
                        now - stats.lastPacketMs < IDLE_TIMEOUT -> ConnectionState.LIVE
                        else -> ConnectionState.IDLE
                    }
                }
            }
        }
    }

    fun registerSender(ip: String) {
        android.util.Log.d("UdpReceiver", "Sender registered: $ip")
    }

    fun unregisterSender(ip: String) {
        android.util.Log.d("UdpReceiver", "Sender unregistered: $ip")
        devices.entries.removeIf { it.value.ip == ip }
    }

    fun stop() {
        scope.cancel()
        events.close()
        socket?.close()
        socket = null
    }
}