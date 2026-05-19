package com.watchtogether.network.sync

import com.watchtogether.data.model.SyncMessage
import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.LogTag
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

class SyncServer(port: Int = DEFAULT_PORT) : NanoWSD(port) {

    private val connectedClients = mutableListOf<SyncWebSocket>()
    private val _incomingMessages = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SyncMessage> = _incomingMessages.asSharedFlow()

    private val _clientConnected = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    val clientConnected: SharedFlow<Boolean> = _clientConnected.asSharedFlow()

    private var heartbeatTimer: Timer? = null

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        val ws = SyncWebSocket(handshake)
        AppLogger.d(LogTag.SOCKET, "New WebSocket connection from ${handshake.remoteIpAddress}")
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
                    AppLogger.w(LogTag.SOCKET, "FLOW BREAK: Broadcast failed - client disconnected during send", e)
                    iterator.remove()
                }
            }
        }
    }

    fun startServer() {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            startHeartbeat()
            AppLogger.i(LogTag.SOCKET, "Sync WebSocket server started on port $listeningPort")
        } catch (e: IOException) {
            AppLogger.e(LogTag.SOCKET, "FLOW BREAK: Sync server failed to start on port $DEFAULT_PORT - port may be in use", e)
            throw e
        }
    }

    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = Timer("SyncHeartbeat", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        broadcastMessage(SyncMessage.Heartbeat(System.currentTimeMillis()))
                    } catch (e: Exception) {
                        AppLogger.w(LogTag.SOCKET, "Heartbeat failed", e)
                    }
                }
            }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS)
        }
    }

    fun stopServer() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
        synchronized(connectedClients) {
            connectedClients.forEach { client ->
                try {
                    client.close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "Server stopping", false)
                } catch (e: Exception) {
                    AppLogger.w(LogTag.SOCKET, "Error closing client", e)
                }
            }
            connectedClients.clear()
        }
        stop()
        AppLogger.d(LogTag.SOCKET, "Sync server stopped")
    }

    private fun relayToOthers(json: String, sender: SyncWebSocket) {
        synchronized(connectedClients) {
            val iterator = connectedClients.iterator()
            while (iterator.hasNext()) {
                val client = iterator.next()
                if (client !== sender) {
                    try {
                        client.send(json)
                    } catch (e: IOException) {
                        AppLogger.w(LogTag.SOCKET, "Relay failed - client disconnected", e)
                        iterator.remove()
                    }
                }
            }
        }
    }

    val clientCount: Int
        get() = synchronized(connectedClients) { connectedClients.size }

    inner class SyncWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            synchronized(connectedClients) {
                connectedClients.add(this)
            }
            _clientConnected.tryEmit(true)
            AppLogger.d(LogTag.SOCKET, "Client connected. Total: ${connectedClients.size}")
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            synchronized(connectedClients) {
                connectedClients.remove(this)
            }
            _clientConnected.tryEmit(false)
            AppLogger.d(LogTag.SOCKET, "Client disconnected. Total: ${connectedClients.size}")
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            val text = message.textPayload ?: return
            val syncMessage = SyncMessage.fromJson(text)
            if (syncMessage != null) {
                AppLogger.d(LogTag.SOCKET, "Viewer sync received: ${syncMessage.javaClass.simpleName}")
                _incomingMessages.tryEmit(syncMessage)
                if (syncMessage !is SyncMessage.Disconnect) {
                    relayToOthers(text, this)
                }
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame?) {}

        override fun onException(exception: IOException) {
            synchronized(connectedClients) {
                connectedClients.remove(this)
            }
            AppLogger.e(LogTag.SOCKET, "FLOW BREAK: WebSocket client error - ${exception.message}", exception)
        }
    }

    companion object {
        const val DEFAULT_PORT = 8081
        private const val SOCKET_READ_TIMEOUT = 0 // No timeout - heartbeat keeps connection alive
        private const val HEARTBEAT_INTERVAL_MS = 15000L
    }
}
