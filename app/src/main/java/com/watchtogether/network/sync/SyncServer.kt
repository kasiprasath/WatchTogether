package com.watchtogether.network.sync

import android.util.Log
import com.watchtogether.data.model.SyncMessage
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException

class SyncServer(port: Int = DEFAULT_PORT) : NanoWSD(port) {

    private val connectedClients = mutableListOf<SyncWebSocket>()
    private val _incomingMessages = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SyncMessage> = _incomingMessages.asSharedFlow()

    private val _clientConnected = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    val clientConnected: SharedFlow<Boolean> = _clientConnected.asSharedFlow()

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        val ws = SyncWebSocket(handshake)
        Log.d(TAG, "New WebSocket connection from ${handshake.remoteIpAddress}")
        return ws
    }

    fun broadcastMessage(message: SyncMessage) {
        val json = message.toJson()
        synchronized(connectedClients) {
            val iterator = connectedClients.iterator()
            while (iterator.hasNext()) {
                val client = iterator.next()
                try {
                    client.send(json)
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to send to client, removing", e)
                    iterator.remove()
                }
            }
        }
    }

    fun startServer() {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "Sync server started on port $listeningPort")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start sync server", e)
        }
    }

    fun stopServer() {
        synchronized(connectedClients) {
            connectedClients.forEach { client ->
                try {
                    client.close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "Server stopping", false)
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing client", e)
                }
            }
            connectedClients.clear()
        }
        stop()
        Log.d(TAG, "Sync server stopped")
    }

    val clientCount: Int
        get() = synchronized(connectedClients) { connectedClients.size }

    inner class SyncWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            synchronized(connectedClients) {
                connectedClients.add(this)
            }
            _clientConnected.tryEmit(true)
            Log.d(TAG, "Client connected. Total: ${connectedClients.size}")
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            synchronized(connectedClients) {
                connectedClients.remove(this)
            }
            _clientConnected.tryEmit(false)
            Log.d(TAG, "Client disconnected. Total: ${connectedClients.size}")
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            val text = message.textPayload ?: return
            val syncMessage = SyncMessage.fromJson(text)
            if (syncMessage != null) {
                _incomingMessages.tryEmit(syncMessage)
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame?) {}

        override fun onException(exception: IOException) {
            synchronized(connectedClients) {
                connectedClients.remove(this)
            }
            Log.w(TAG, "WebSocket exception", exception)
        }
    }

    companion object {
        private const val TAG = "SyncServer"
        const val DEFAULT_PORT = 8081
        private const val SOCKET_READ_TIMEOUT = 30000
    }
}
