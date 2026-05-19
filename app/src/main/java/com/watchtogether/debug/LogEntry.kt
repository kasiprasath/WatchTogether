package com.watchtogether.debug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: Level,
    val tag: LogTag,
    val message: String,
    val threadName: String = Thread.currentThread().name,
    val throwable: String? = null
) {
    enum class Level(val label: String) {
        DEBUG("D"),
        INFO("I"),
        WARN("W"),
        ERROR("E"),
        CRASH("CRASH")
    }

    fun format(): String {
        val time = DATE_FORMAT.format(Date(timestamp))
        val base = "$time ${level.label}/${tag.value} [$threadName]: $message"
        return if (throwable != null) "$base\n$throwable" else base
    }

    companion object {
        private val DATE_FORMAT get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
}

enum class LogTag(val value: String) {
    WIFI_DIRECT("WIFI_DIRECT"),
    STREAM_SERVER("STREAM_SERVER"),
    EXOPLAYER("EXOPLAYER"),
    SOCKET("SOCKET"),
    PLAYER_SYNC("PLAYER_SYNC"),
    STORAGE("STORAGE"),
    UI("UI"),
    CRASH("CRASH"),
    ANR("ANR"),
    GENERAL("GENERAL")
}
