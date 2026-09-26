package com.watchtogether.network.sync

import com.watchtogether.data.model.SyncMessage
import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.Socket

internal sealed class WsFrame {
    data class Text(val text: String) : WsFrame()
    data class Ping(val payload: ByteArray) : WsFrame()
    object Pong : WsFrame()
    object Close : WsFrame()
    data class Other(val opcode: Int) : WsFrame()
}

class SyncClient {

    private var socket: Socket? = null
    private var readerJob: Job? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _incomingMessages = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SyncMessage> = _incomingMessages.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var hostAddress: String = ""
    private var port: Int = SyncServer.DEFAULT_PORT
    private var shouldReconnect = false
    private var reconnectAttempts = 0

    fun connect(address: String, syncPort: Int = SyncServer.DEFAULT_PORT) {
        hostAddress = address
        port = syncPort
        shouldReconnect = true
        reconnectAttempts = 0
        performConnect()
    }

    private fun performConnect() {
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                val tcpSocket = Socket(hostAddress, port)
                tcpSocket.keepAlive = true
                tcpSocket.soTimeout = 0 // No read timeout - heartbeat keeps connection alive
                socket = tcpSocket

                val outputStream = tcpSocket.getOutputStream()
                val inputStream = tcpSocket.getInputStream()

                // WebSocket handshake
                val key = android.util.Base64.encodeToString(
                    ByteArray(16).also { java.security.SecureRandom().nextBytes(it) },
                    android.util.Base64.NO_WRAP
                )
                val handshake = "GET / HTTP/1.1\r\n" +
                        "Host: $hostAddress:$port\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: $key\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n"

                outputStream.write(handshake.toByteArray())
                outputStream.flush()

                // Read handshake response byte-by-byte to avoid BufferedReader
                // consuming WebSocket frame data into its internal buffer
                readHttpResponseHeaders(inputStream)

                _isConnected.value = true
                reconnectAttempts = 0
                AppLogger.d(LogTag.SOCKET, "Connected to sync server at $hostAddress:$port")

                // Read loop for WebSocket frames
                while (_isConnected.value) {
                    try {
                        when (val frame = readWebSocketFrame(inputStream)) {
                            null, WsFrame.Close -> {
                                AppLogger.d(LogTag.SOCKET, "Server closed connection")
                                break
                            }
                            is WsFrame.Text -> SyncMessage.fromJson(frame.text)?.let {
                                _incomingMessages.emit(it)
                            }
                            is WsFrame.Ping -> sendPong(frame.payload)
                            WsFrame.Pong -> {}
                            is WsFrame.Other -> {
                                AppLogger.w(
                                    LogTag.SOCKET,
                                    "Ignoring unexpected opcode ${frame.opcode}"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (_isConnected.value) {
                            AppLogger.e(LogTag.SOCKET, "FLOW BREAK: Frame read failed - connection may be closed", e)
                            break
                        }
                    }
                }
            } catch (e: java.net.ConnectException) {
                AppLogger.e(LogTag.SOCKET, "FLOW BREAK: Cannot reach host at $hostAddress:$port - connection refused", e)
            } catch (e: java.net.SocketTimeoutException) {
                AppLogger.e(LogTag.SOCKET, "FLOW BREAK: Connection to $hostAddress:$port timed out", e)
            } catch (e: java.net.UnknownHostException) {
                AppLogger.e(LogTag.SOCKET, "FLOW BREAK: Unknown host $hostAddress", e)
            } catch (e: Exception) {
                AppLogger.e(LogTag.SOCKET, "FLOW BREAK: Sync connection failed to $hostAddress:$port", e)
            } finally {
                _isConnected.value = false
                cleanup()
                if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    scheduleReconnect()
                } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                    AppLogger.w(LogTag.SOCKET, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached, giving up")
                    shouldReconnect = false
                }
            }
        }
    }

    private fun readHttpResponseHeaders(input: InputStream) {
        var state = 0 // Looking for \r\n\r\n sequence
        while (true) {
            val b = input.read()
            if (b == -1) return
            val c = b.toChar()
            state = when {
                c == '\r' && (state == 0 || state == 2) -> state + 1
                c == '\n' && state == 1 -> 2
                c == '\n' && state == 3 -> return // Found \r\n\r\n
                else -> 0
            }
        }
    }

    fun sendMessage(message: SyncMessage) {
        scope.launch {
            sendMessageBlocking(message)
        }
    }

    private fun sendMessageBlocking(message: SyncMessage) {
        try {
            val json = message.toJson()
            val payload = json.toByteArray(Charsets.UTF_8)
            val frame = createWebSocketFrame(payload)
            socket?.getOutputStream()?.let { os ->
                os.write(frame)
                os.flush()
            }
        } catch (e: Exception) {
            AppLogger.e(LogTag.SOCKET, "FLOW BREAK: Failed to send sync message ${message.javaClass.simpleName}", e)
        }
    }

    private fun sendPong(payload: ByteArray) {
        try {
            socket?.getOutputStream()?.let { os ->
                os.write(createWebSocketFrame(payload, 0xA))
                os.flush()
            }
        } catch (e: Exception) {
            AppLogger.e(LogTag.SOCKET, "FLOW BREAK: Failed to send pong", e)
        }
    }

    private fun createWebSocketFrame(payload: ByteArray, opcode: Int = 0x1): ByteArray {
        val mask = ByteArray(4).also { java.security.SecureRandom().nextBytes(it) }
        val frame: ByteArray

        val len = payload.size
        frame = when {
            len < 126 -> {
                val f = ByteArray(6 + len)
                f[0] = (0x80 or opcode).toByte()
                f[1] = (0x80 or len).toByte() // masked + length
                System.arraycopy(mask, 0, f, 2, 4)
                for (i in payload.indices) {
                    f[6 + i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
                f
            }
            len < 65536 -> {
                val f = ByteArray(8 + len)
                f[0] = (0x80 or opcode).toByte()
                f[1] = (0x80 or 126).toByte()
                f[2] = (len shr 8).toByte()
                f[3] = (len and 0xFF).toByte()
                System.arraycopy(mask, 0, f, 4, 4)
                for (i in payload.indices) {
                    f[8 + i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
                f
            }
            else -> {
                val f = ByteArray(14 + len)
                f[0] = (0x80 or opcode).toByte()
                f[1] = (0x80 or 127).toByte()
                for (i in 0 until 8) {
                    f[2 + i] = (len.toLong() shr (56 - i * 8) and 0xFF).toByte()
                }
                System.arraycopy(mask, 0, f, 10, 4)
                for (i in payload.indices) {
                    f[14 + i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
                f
            }
        }
        return frame
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempts++
            val backoffDelay = RECONNECT_DELAY * reconnectAttempts
            AppLogger.d(LogTag.SOCKET, "Reconnect attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS in ${backoffDelay}ms")
            delay(backoffDelay)
            if (shouldReconnect) {
                performConnect()
            }
        }
    }

    fun disconnect() {
        // Send disconnect before signalling the reader loop to exit,
        // otherwise the reader's finally block may close the socket first
        try {
            val sendThread = Thread { sendMessageBlocking(SyncMessage.Disconnect) }
            sendThread.start()
            sendThread.join(DISCONNECT_SEND_TIMEOUT_MS)
        } catch (e: Exception) {
            AppLogger.w(LogTag.SOCKET, "Failed to send disconnect message", e)
        }
        shouldReconnect = false
        _isConnected.value = false
        cleanup()
    }

    private fun cleanup() {
        try {
            readerJob?.cancel()
            reconnectJob?.cancel()
            socket?.close()
        } catch (e: Exception) {
            AppLogger.w(LogTag.SOCKET, "Cleanup error", e)
        }
        socket = null
    }

    companion object {
        private const val RECONNECT_DELAY = 2000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val DISCONNECT_SEND_TIMEOUT_MS = 100L

        internal fun readFully(input: InputStream, buf: ByteArray): Boolean {
            var offset = 0
            while (offset < buf.size) {
                val count = input.read(buf, offset, buf.size - offset)
                if (count == -1) return false
                offset += count
            }
            return true
        }

        internal fun readWebSocketFrame(input: InputStream): WsFrame? {
            val firstByte = input.read()
            if (firstByte == -1) return null
            val opcode = firstByte and 0x0F

            val secondByte = input.read()
            if (secondByte == -1) return null
            val isMasked = (secondByte and 0x80) != 0
            var payloadLength = (secondByte and 0x7F).toLong()

            when (payloadLength) {
                126L -> {
                    val extendedLength = ByteArray(2)
                    if (!readFully(input, extendedLength)) return null
                    payloadLength = (
                        ((extendedLength[0].toInt() and 0xFF) shl 8) or
                            (extendedLength[1].toInt() and 0xFF)
                        ).toLong()
                }
                127L -> {
                    val extendedLength = ByteArray(8)
                    if (!readFully(input, extendedLength)) return null
                    payloadLength = 0L
                    for (byte in extendedLength) {
                        payloadLength = (payloadLength shl 8) or
                                (byte.toInt() and 0xFF).toLong()
                    }
                    if (payloadLength < 0) return null
                }
            }

            val mask = if (isMasked) {
                ByteArray(4).also {
                    if (!readFully(input, it)) return null
                }
            } else {
                null
            }
            if (payloadLength > Int.MAX_VALUE) return null

            val payload = ByteArray(payloadLength.toInt())
            if (!readFully(input, payload)) return null

            if (mask != null) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
            }

            return when (opcode) {
                0x1 -> WsFrame.Text(String(payload, Charsets.UTF_8))
                0x8 -> WsFrame.Close
                0x9 -> WsFrame.Ping(payload)
                0xA -> WsFrame.Pong
                else -> WsFrame.Other(opcode)
            }
        }
    }
}
