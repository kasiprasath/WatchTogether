package com.watchtogether.network.sync

import android.util.Log
import com.watchtogether.data.model.SyncMessage
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
import java.io.PrintWriter
import java.net.Socket

class SyncClient {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
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

    fun connect(address: String, syncPort: Int = SyncServer.DEFAULT_PORT) {
        hostAddress = address
        port = syncPort
        shouldReconnect = true
        performConnect()
    }

    private fun performConnect() {
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                val tcpSocket = Socket(hostAddress, port)
                socket = tcpSocket

                val outputStream = tcpSocket.getOutputStream()
                val inputStream = tcpSocket.getInputStream()

                // WebSocket handshake
                val key = java.util.Base64.getEncoder().encodeToString(
                    ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
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
                Log.d(TAG, "Connected to sync server at $hostAddress:$port")

                // Read loop for WebSocket frames
                while (_isConnected.value) {
                    try {
                        val frame = readWebSocketFrame(inputStream)
                        if (frame != null) {
                            val syncMessage = SyncMessage.fromJson(frame)
                            if (syncMessage != null) {
                                _incomingMessages.emit(syncMessage)
                            }
                        }
                    } catch (e: Exception) {
                        if (_isConnected.value) {
                            Log.w(TAG, "Error reading frame", e)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
            } finally {
                _isConnected.value = false
                cleanup()
                if (shouldReconnect) {
                    scheduleReconnect()
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

    private fun readWebSocketFrame(input: InputStream): String? {
        val firstByte = input.read()
        if (firstByte == -1) return null

        val secondByte = input.read()
        if (secondByte == -1) return null

        val isMasked = (secondByte and 0x80) != 0
        var payloadLength = (secondByte and 0x7F).toLong()

        when {
            payloadLength == 126L -> {
                val b1 = input.read()
                val b2 = input.read()
                payloadLength = ((b1 shl 8) or b2).toLong()
            }
            payloadLength == 127L -> {
                var len = 0L
                for (i in 0 until 8) {
                    len = (len shl 8) or input.read().toLong()
                }
                payloadLength = len
            }
        }

        val mask = if (isMasked) {
            ByteArray(4).also { input.read(it) }
        } else null

        val payload = ByteArray(payloadLength.toInt())
        var read = 0
        while (read < payloadLength) {
            val r = input.read(payload, read, (payloadLength - read).toInt())
            if (r == -1) return null
            read += r
        }

        if (mask != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
        }

        return String(payload, Charsets.UTF_8)
    }

    fun sendMessage(message: SyncMessage) {
        scope.launch {
            try {
                val json = message.toJson()
                val payload = json.toByteArray(Charsets.UTF_8)
                val frame = createWebSocketFrame(payload)
                socket?.getOutputStream()?.let { os ->
                    os.write(frame)
                    os.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
            }
        }
    }

    private fun createWebSocketFrame(payload: ByteArray): ByteArray {
        val mask = ByteArray(4).also { java.security.SecureRandom().nextBytes(it) }
        val frame: ByteArray

        val len = payload.size
        frame = when {
            len < 126 -> {
                val f = ByteArray(6 + len)
                f[0] = 0x81.toByte() // FIN + text
                f[1] = (0x80 or len).toByte() // masked + length
                System.arraycopy(mask, 0, f, 2, 4)
                for (i in payload.indices) {
                    f[6 + i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
                f
            }
            len < 65536 -> {
                val f = ByteArray(8 + len)
                f[0] = 0x81.toByte()
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
                f[0] = 0x81.toByte()
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
            delay(RECONNECT_DELAY)
            if (shouldReconnect) {
                Log.d(TAG, "Attempting reconnect...")
                performConnect()
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        _isConnected.value = false
        cleanup()
    }

    private fun cleanup() {
        try {
            readerJob?.cancel()
            reconnectJob?.cancel()
            writer?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error", e)
        }
        socket = null
        writer = null
    }

    companion object {
        private const val TAG = "SyncClient"
        private const val RECONNECT_DELAY = 3000L
    }
}
