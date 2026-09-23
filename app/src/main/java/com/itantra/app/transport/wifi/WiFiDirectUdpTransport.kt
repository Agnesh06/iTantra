package com.itantra.app.transport.wifi

import android.content.Context
import android.net.wifi.p2p.*
import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import com.itantra.app.transport.diagnostics.LossSimulator
import com.itantra.app.transport.fec.FecEngine
import com.itantra.app.transport.protocol.*
import com.itantra.app.transport.throttle.TokenBucketLimiter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED, DISCOVERING, CONNECTING, CONNECTED, FAILED
}

class WiFiDirectUdpTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    val tokenBucketLimiter: TokenBucketLimiter,
    val lossSimulator: LossSimulator,
    private val onMessageReceived: (AssembledMessage) -> Unit,
    private val onMessageFailed: (messageId: Long, reason: String) -> Unit
) {
    companion object {
        const val DEFAULT_PORT = 49152
        const val MAX_PORT = 49160
        const val MAX_UDP_DATAGRAM_SIZE = 1400 // Safe MTU size
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var socket: DatagramSocket? = null
    private var boundPort: Int = DEFAULT_PORT
    private var peerAddress: InetAddress? = null
    private var peerPort: Int = DEFAULT_PORT

    private val localDeviceId = UUID.randomUUID().toString()

    private var receiveJob: Job? = null

    val ackRetryManager = AckRetryManager(
        scope = scope,
        onRetransmitGroup = { pendingGroup ->
            retransmitFrameGroup(pendingGroup)
        },
        onGroupFailed = { messageId, frameGroupId ->
            onMessageFailed(messageId, "Retry exhausted for group $frameGroupId")
        }
    )

    val messageAssembler = MessageAssembler(
        scope = scope,
        onSendAck = { messageId, ackPayload ->
            sendAck(messageId, ackPayload)
        },
        onMessageComplete = { assembledMessage ->
            onMessageReceived(assembledMessage)
        },
        onIncompleteTimeout = { messageId ->
            onMessageFailed(messageId, "Inactivity timeout (15s)")
        }
    )

    /**
     * Binds UDP DatagramSocket to the first available port in 49152..49160.
     */
    fun bindSocket(): Int {
        socket?.close()
        for (port in DEFAULT_PORT..MAX_PORT) {
            try {
                val s = DatagramSocket(port)
                socket = s
                boundPort = port
                AppLogger.i("Transport", "Bound UDP socket on port $port", category = LogCategory.NETWORK)
                startListening()
                return port
            } catch (_: SocketException) {
                // Try next port in range
            }
        }
        throw IllegalStateException("Failed to bind UDP socket in range $DEFAULT_PORT..$MAX_PORT")
    }

    fun setPeerEndpoint(address: InetAddress, port: Int) {
        peerAddress = address
        peerPort = port
        _connectionState.value = ConnectionState.CONNECTED
        AppLogger.i("Transport", "Peer endpoint configured: $address:$port", category = LogCategory.NETWORK)
    }

    fun sendHello() {
        val targetAddr = peerAddress ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val helloPayload = HelloPayload(deviceId = localDeviceId, advertisedUdpPort = boundPort)
                val rawPayload = helloPayload.toByteArray()
                val header = PacketHeader(
                    version = PacketConstants.CURRENT_VERSION,
                    type = PacketConstants.TYPE_HELLO,
                    flags = 0,
                    messageId = 0L,
                    seq = 0,
                    total = 1,
                    payloadLen = rawPayload.size
                )
                val rawBytes = PacketSerializer.serialize(header, rawPayload)
                val dPacket = DatagramPacket(rawBytes, rawBytes.size, targetAddr, peerPort)
                socket?.send(dPacket)
                AppLogger.i("Transport", "Sent HELLO to peer with port $boundPort", category = LogCategory.NETWORK)
            } catch (e: Exception) {
                AppLogger.e("Transport", "Failed to send HELLO", e, category = LogCategory.NETWORK)
            }
        }
    }

    /**
     * Packetizes logical message, applies FEC parity frames, throttles via Token Bucket,
     * and transmits datagrams over UDP socket.
     */
    suspend fun sendMessage(
        messageId: Long,
        payloadBytes: ByteArray,
        alertFlag: Boolean,
        hasConfidence: Boolean,
        translationUsed: Boolean
    ) = withContext(Dispatchers.IO) {
        val targetAddr = peerAddress
        if (targetAddr == null) {
            onMessageFailed(messageId, "Peer not connected")
            return@withContext
        }

        // Chunk payload into DATA frames
        val maxChunk = MAX_UDP_DATAGRAM_SIZE - PacketConstants.HEADER_TOTAL_SIZE
        val chunks = payloadBytes.toList().chunked(maxChunk).map { it.toByteArray() }
        val totalDataFrames = chunks.size

        var flags: Byte = 0
        if (alertFlag) flags = (flags.toInt() or PacketConstants.FLAG_ALERT.toInt()).toByte()
        if (hasConfidence) flags = (flags.toInt() or PacketConstants.FLAG_CONFIDENCE_PRESENT.toInt()).toByte()
        if (translationUsed) flags = (flags.toInt() or PacketConstants.FLAG_TRANSLATION_USED.toInt()).toByte()

        val dataPackets = chunks.mapIndexed { seq, chunk ->
            val header = PacketHeader(
                version = PacketConstants.CURRENT_VERSION,
                type = PacketConstants.TYPE_DATA,
                flags = flags,
                messageId = messageId,
                seq = seq,
                total = totalDataFrames,
                payloadLen = chunk.size
            )
            Packet(header, chunk, 0L)
        }

        // Group into frame groups of up to 3 frames and generate XOR parity
        val groups = dataPackets.chunked(FecEngine.GROUP_SIZE)
        for ((groupIndex, groupDataFrames) in groups.withIndex()) {
            val parityFrame = FecEngine.createParityFrame(
                messageId = messageId,
                frameGroupId = groupIndex,
                totalDataFrames = totalDataFrames,
                flags = flags,
                dataPackets = groupDataFrames
            )

            val allFramesInGroup = groupDataFrames + parityFrame

            // Register frame-group with ACK and Selective Retry Manager
            ackRetryManager.registerGroupSent(
                messageId = messageId,
                frameGroupId = groupIndex,
                frames = allFramesInGroup,
                rateBps = tokenBucketLimiter.rateBps
            )

            // Transmit frames through rate limiter and loss simulator
            for (frame in allFramesInGroup) {
                sendFrame(frame, targetAddr, peerPort)
            }
        }
    }

    private suspend fun retransmitFrameGroup(pendingGroup: PendingGroup) = withContext(Dispatchers.IO) {
        val targetAddr = peerAddress ?: return@withContext
        for (frame in pendingGroup.frames) {
            sendFrame(frame, targetAddr, peerPort)
        }
    }

    private suspend fun sendFrame(frame: Packet, targetAddr: InetAddress, port: Int) {
        val rawBytes = PacketSerializer.serialize(frame.header, frame.payload)

        // Enforce software rate limiting
        tokenBucketLimiter.acquire(rawBytes.size, frame.payload.size)

        // Loss Simulator: Drops ONLY DATA and FEC packets
        if (lossSimulator.shouldDrop(frame.header.type)) {
            AppLogger.w(
                "Transport",
                "Simulated drop of frame type=${frame.header.type} seq=${frame.header.seq}",
                category = LogCategory.NETWORK
            )
            return // Dropped frame does NOT go to socket
        }

        try {
            val datagram = DatagramPacket(rawBytes, rawBytes.size, targetAddr, port)
            socket?.send(datagram)
        } catch (e: Exception) {
            AppLogger.e("Transport", "UDP send error: ${e.message}", e, category = LogCategory.NETWORK)
        }
    }

    private suspend fun sendAck(messageId: Long, ackPayload: AckPayload) = withContext(Dispatchers.IO) {
        val targetAddr = peerAddress ?: return@withContext
        val payloadBytes = ackPayload.toByteArray()
        val header = PacketHeader(
            version = PacketConstants.CURRENT_VERSION,
            type = PacketConstants.TYPE_ACK,
            flags = 0,
            messageId = messageId,
            seq = ackPayload.frameGroupId,
            total = 1,
            payloadLen = payloadBytes.size
        )
        val rawBytes = PacketSerializer.serialize(header, payloadBytes)
        // Control packets are never dropped and not throttled
        try {
            val datagram = DatagramPacket(rawBytes, rawBytes.size, targetAddr, peerPort)
            socket?.send(datagram)
            AppLogger.i("Transport", "Sent ACK for msg=$messageId group=${ackPayload.frameGroupId}", category = LogCategory.NETWORK)
        } catch (e: Exception) {
            AppLogger.e("Transport", "Failed to send ACK: ${e.message}", e, category = LogCategory.NETWORK)
        }
    }

    private fun startListening() {
        receiveJob?.cancel()
        receiveJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            while (isActive) {
                try {
                    val datagram = DatagramPacket(buffer, buffer.size)
                    val s = socket ?: break
                    s.receive(datagram)

                    val receivedBytes = datagram.data.copyOfRange(0, datagram.length)
                    handleIncomingRawDatagram(receivedBytes, datagram.address, datagram.port)
                } catch (e: Exception) {
                    if (!isActive) break
                    AppLogger.w("Transport", "UDP receive exception: ${e.message}", e, category = LogCategory.NETWORK)
                }
            }
        }
    }

    private fun handleIncomingRawDatagram(raw: ByteArray, senderAddr: InetAddress, senderPort: Int) {
        try {
            val packet = PacketSerializer.deserialize(raw)
            when (packet.header.type) {
                PacketConstants.TYPE_HELLO -> {
                    val hello = HelloPayload.fromByteArray(packet.payload)
                    setPeerEndpoint(senderAddr, hello.advertisedUdpPort)
                    AppLogger.i("Transport", "Received HELLO from ${hello.deviceId} at $senderAddr:${hello.advertisedUdpPort}", category = LogCategory.NETWORK)
                }
                PacketConstants.TYPE_ACK -> {
                    val ack = AckPayload.fromByteArray(packet.payload)
                    ackRetryManager.onAckReceived(packet.header.messageId, ack.frameGroupId)
                }
                PacketConstants.TYPE_DATA, PacketConstants.TYPE_FEC -> {
                    messageAssembler.handleIncomingPacket(packet)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Transport", "Failed to process incoming datagram: ${e.message}", e, category = LogCategory.NETWORK)
        }
    }

    fun disconnect() {
        receiveJob?.cancel()
        socket?.close()
        socket = null
        peerAddress = null
        ackRetryManager.clear()
        messageAssembler.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
