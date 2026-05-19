package com.watchtogether.debug

import timber.log.Timber

object AppLogger {

    private var fileLogger: FileLogger? = null

    fun init(fileLogger: FileLogger) {
        this.fileLogger = fileLogger
    }

    fun d(tag: LogTag, message: String) {
        Timber.tag(tag.value).d(message)
        fileLogger?.log(LogEntry(level = LogEntry.Level.DEBUG, tag = tag, message = message))
    }

    fun i(tag: LogTag, message: String) {
        Timber.tag(tag.value).i(message)
        fileLogger?.log(LogEntry(level = LogEntry.Level.INFO, tag = tag, message = message))
    }

    fun w(tag: LogTag, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag(tag.value).w(throwable, message)
        } else {
            Timber.tag(tag.value).w(message)
        }
        fileLogger?.log(
            LogEntry(
                level = LogEntry.Level.WARN,
                tag = tag,
                message = message,
                throwable = throwable?.stackTraceToString()
            )
        )
    }

    fun e(tag: LogTag, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag(tag.value).e(throwable, message)
        } else {
            Timber.tag(tag.value).e(message)
        }
        fileLogger?.log(
            LogEntry(
                level = LogEntry.Level.ERROR,
                tag = tag,
                message = message,
                throwable = throwable?.stackTraceToString()
            )
        )
    }
}
