package com.watchtogether.debug

import kotlinx.coroutines.CoroutineExceptionHandler
import timber.log.Timber

class CrashHandler(private val fileLogger: FileLogger) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Timber.tag(LogTag.CRASH.value).e(throwable, "Uncaught exception in thread ${thread.name}")
            fileLogger.logCrash(thread, throwable)
        } catch (e: Exception) {
            // Avoid secondary crash during logging
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        fun createCoroutineExceptionHandler(fileLogger: FileLogger): CoroutineExceptionHandler {
            return CoroutineExceptionHandler { context, throwable ->
                val name = context.toString()
                Timber.tag(LogTag.CRASH.value).e(throwable, "Coroutine exception in $name")
                fileLogger.log(
                    LogEntry(
                        level = LogEntry.Level.ERROR,
                        tag = LogTag.CRASH,
                        message = "Coroutine exception in $name: ${throwable.message}",
                        throwable = throwable.stackTraceToString()
                    )
                )
            }
        }
    }
}
