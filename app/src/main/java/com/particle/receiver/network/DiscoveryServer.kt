package com.particle.receiver.network

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Listens on port 9877 for DISCOVER packets from sender devices.
 * Responds with an ACK containing the receiver's own IP.
 * Also sends periodic HEARTBEATs back to known senders.
 */
class DiscoveryServer(
    private val onSenderFound: (ip: String) -> Unit,
    private val onSenderLost: (ip: String) -> Unit,
    private val discoveryPort: Int = 9877
) {
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null

    // Known senders: ip → last seen timestamp
    private val knownSenders = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val HEARTBEAT_INTERVAL = 500L
    private val SENDER_TIMEOUT     = 3000L

    fun start(localIp: String) {
        val multicastGroup = InetAddress.getByName("239.255.0.1")
        val multicastSocket = java.net.MulticastSocket(discoveryPort).apply {
            joinGroup(multicastGroup)
            reuseAddress = true
        }
        socket = multicastSocket
        android.util.Log.d("DiscoveryServer", "Joined multicast group 239.255.0.1 on port $discoveryPort")

        // Listen for DISCOVER and HEARTBEAT packets
        scope.launch {
            val buf    = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            while (isActive) {
                try {
                    socket!!.receive(packet)
                    val json    = JSONObject(String(packet.data, 0, packet.length, Charsets.UTF_8))
                    val senderIp = packet.address.hostAddress ?: continue

                    when (json.optString("type")) {
                        "DISCOVER" -> {
                            android.util.Log.d("DiscoveryServer", "DISCOVER from $senderIp")
                            val wasKnown = knownSenders.containsKey(senderIp)
                            knownSenders[senderIp] = System.currentTimeMillis()
                            if (!wasKnown) onSenderFound(senderIp)

                            // Send ACK back to sender
                            val ack = JSONObject().apply {
                                put("type", "ACK")
                                put("senderIp", localIp)
                            }.toString().toByteArray(Charsets.UTF_8)
                            val ackPacket = DatagramPacket(
                                ack, ack.size,
                                InetAddress.getByName(senderIp),
                                discoveryPort
                            )
                            socket?.send(ackPacket)
                            android.util.Log.d("DiscoveryServer", "ACK sent to $senderIp")
                        }
                        "HEARTBEAT" -> {
                            knownSenders[senderIp] = System.currentTimeMillis()
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) android.util.Log.w("DiscoveryServer", "Error: ${e.message}")
                }
            }
        }

        // Send HEARTBEATs back to all known senders
        scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL)
                val heartbeat = """{"type":"HEARTBEAT"}""".toByteArray(Charsets.UTF_8)
                knownSenders.keys.forEach { ip ->
                    try {
                        val p = DatagramPacket(
                            heartbeat, heartbeat.size,
                            InetAddress.getByName(ip),
                            discoveryPort
                        )
                        socket?.send(p)
                    } catch (e: Exception) {
                        android.util.Log.w("DiscoveryServer", "Heartbeat to $ip failed: ${e.message}")
                    }
                }
            }
        }

        // Monitor sender timeouts
        scope.launch {
            while (isActive) {
                delay(SENDER_TIMEOUT)
                val now = System.currentTimeMillis()
                val lost = knownSenders.entries
                    .filter { now - it.value > SENDER_TIMEOUT }
                    .map { it.key }
                lost.forEach { ip ->
                    android.util.Log.d("DiscoveryServer", "Sender lost: $ip")
                    knownSenders.remove(ip)
                    onSenderLost(ip)
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
        socket?.close()
        socket = null
    }
}