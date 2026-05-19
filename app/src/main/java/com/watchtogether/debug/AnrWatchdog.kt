package com.watchtogether.debug

import android.os.Handler
import android.os.Looper

class AnrWatchdog(private val fileLogger: FileLogger) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchThread: Thread? = null
    @Volatile
    private var running = false
    @Volatile
    private var responded = false

    fun start() {
        if (running) return
        running = true
        watchThread = Thread({
            while (running) {
                responded = false
                mainHandler.post { responded = true }
                try {
                    Thread.sleep(CHECK_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    break
                }
                if (!responded && running) {
                    val mainThread = Looper.getMainLooper().thread
                    val stackTrace = mainThread.stackTrace
                        .joinToString("\n    at ") { it.toString() }
                    val message = "ANR detected! Main thread blocked for >${CHECK_INTERVAL_MS}ms\n    at $stackTrace"
                    AppLogger.e(LogTag.ANR, message)
                }
            }
        }, "AnrWatchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        watchThread?.interrupt()
        watchThread = null
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 5000L
    }
}
