package com.watchtogether.network.server

import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * TCP proxy that tunnels ExoPlayer HTTP requests from localhost to the host's
 * video server via raw TCP sockets. This bypasses Android's default network
 * routing which may send HTTP requests over mobile data instead of the
 * Wi-Fi Direct P2P interface. Raw TCP sockets connect directly to the peer IP.
 */
class StreamProxy(
    private val hostAddress: String,
    private val hostPort: Int
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var running = false

    fun start(): Int {
        serverSocket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val localPort = serverSocket!!.localPort
        running = true
        scope.launch {
            while (running) {
                try {
                    val client = serverSocket?.accept() ?: break
                    launch { proxyConnection(client) }
                } catch (e: Exception) {
                    if (running) {
                        AppLogger.e(LogTag.STREAM_SERVER, "Stream proxy accept error", e)
                    }
                }
            }
        }
        AppLogger.i(LogTag.STREAM_SERVER, "Stream proxy started on localhost:$localPort -> $hostAddress:$hostPort")
        return localPort
    }

    private suspend fun proxyConnection(clientSocket: Socket) {
        var hostSocket: Socket? = null
        try {
            hostSocket = Socket()
            hostSocket.connect(java.net.InetSocketAddress(hostAddress, hostPort), 10000)

            val clientToHost = scope.launch {
                try {
                    pipe(clientSocket.getInputStream(), hostSocket.getOutputStream())
                } catch (_: Exception) {
                } finally {
                    try { hostSocket.shutdownOutput() } catch (_: Exception) {}
                }
            }

            val hostToClient = scope.launch {
                try {
                    pipe(hostSocket.getInputStream(), clientSocket.getOutputStream())
                } catch (_: Exception) {
                } finally {
                    try { clientSocket.shutdownOutput() } catch (_: Exception) {}
                }
            }

            clientToHost.join()
            hostToClient.join()
        } catch (e: Exception) {
            AppLogger.w(LogTag.STREAM_SERVER, "Stream proxy connection error to $hostAddress:$hostPort", e)
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
            try { hostSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(32 * 1024)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            output.flush()
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        scope.cancel()
        AppLogger.d(LogTag.STREAM_SERVER, "Stream proxy stopped")
    }
}
