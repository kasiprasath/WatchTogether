package com.watchtogether.debug

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Transparent debug overlay that shows real-time errors, warnings,
 * and key events on top of the player screen in TEST mode.
 * Automatically hidden in LIVE mode.
 */
class DebugOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private val logContainer: LinearLayout
    private val entries = CopyOnWriteArrayList<String>()
    private var maxEntries = MAX_VISIBLE_ENTRIES

    private val logListener: (LogEntry) -> Unit = { entry ->
        if (shouldShowEntry(entry)) {
            post { addLogLine(entry) }
        }
    }

    private val modeListener: (DebugConfig.Mode) -> Unit = { mode ->
        post { visibility = if (mode == DebugConfig.Mode.TEST) VISIBLE else GONE }
    }

    init {
        setBackgroundColor(Color.parseColor("#AA000000"))
        val padding = dpToPx(6)
        setPadding(padding, padding, padding, padding)

        logContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(logContainer)

        // Header
        addTextLine("[DEBUG OVERLAY - TEST MODE]", Color.parseColor("#FF9800"))

        visibility = if (DebugConfig.isTestMode()) VISIBLE else GONE
    }

    fun attach() {
        val app = context.applicationContext as? com.watchtogether.app.WatchTogetherApp
        app?.fileLogger?.addListener(logListener)
        DebugConfig.addModeChangeListener(modeListener)
    }

    fun detach() {
        val app = context.applicationContext as? com.watchtogether.app.WatchTogetherApp
        app?.fileLogger?.removeListener(logListener)
        DebugConfig.removeModeChangeListener(modeListener)
    }

    fun addEvent(message: String, color: Int = Color.WHITE) {
        post { addTextLine(message, color) }
    }

    fun addError(message: String) {
        addEvent("ERROR: $message", Color.parseColor("#FF5252"))
    }

    fun addWarning(message: String) {
        addEvent("WARN: $message", Color.parseColor("#FFD740"))
    }

    fun addInfo(message: String) {
        addEvent("INFO: $message", Color.parseColor("#69F0AE"))
    }

    private fun shouldShowEntry(entry: LogEntry): Boolean {
        return when (entry.level) {
            LogEntry.Level.ERROR, LogEntry.Level.CRASH -> true
            LogEntry.Level.WARN -> true
            LogEntry.Level.INFO -> entry.tag in setOf(
                LogTag.EXOPLAYER, LogTag.STREAM_SERVER,
                LogTag.SOCKET, LogTag.PLAYER_SYNC, LogTag.WIFI_DIRECT
            )
            LogEntry.Level.DEBUG -> false
        }
    }

    private fun addLogLine(entry: LogEntry) {
        val color = when (entry.level) {
            LogEntry.Level.ERROR, LogEntry.Level.CRASH -> Color.parseColor("#FF5252")
            LogEntry.Level.WARN -> Color.parseColor("#FFD740")
            LogEntry.Level.INFO -> Color.parseColor("#69F0AE")
            LogEntry.Level.DEBUG -> Color.parseColor("#B0BEC5")
        }
        val text = "${entry.level.label}/${entry.tag.value}: ${entry.message}"
        addTextLine(text, color)
        if (entry.throwable != null) {
            val trace = entry.throwable
            val shortTrace = trace.lines().take(5).joinToString("\n")
            addTextLine(shortTrace, Color.parseColor("#EF9A9A"))
        }
    }

    private fun addTextLine(text: String, color: Int) {
        entries.add(text)
        while (entries.size > maxEntries) {
            entries.removeAt(0)
            if (logContainer.childCount > 1) {
                logContainer.removeViewAt(1)
            }
        }

        val tv = TextView(context).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        logContainer.addView(tv)
        post { fullScroll(FOCUS_DOWN) }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val MAX_VISIBLE_ENTRIES = 30
    }
}
