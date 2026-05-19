package com.watchtogether.debug

import android.content.Context
import android.content.SharedPreferences

/**
 * Runtime configuration for TEST/LIVE operational modes.
 * TEST mode: verbose logging, debug overlays, detailed exception traces on screen.
 * LIVE mode: optimized logging, minimal overhead, crash-safe logging only.
 */
object DebugConfig {

    enum class Mode(val label: String) {
        TEST("TEST"),
        LIVE("LIVE")
    }

    private const val PREFS_NAME = "debug_config"
    private const val KEY_MODE = "operational_mode"

    @Volatile
    private var currentMode: Mode = Mode.TEST

    private var prefs: SharedPreferences? = null
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(Mode) -> Unit>()

    private var isDebugBuild = false

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDebugBuild = context.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Debug builds always use TEST mode
        if (isDebugBuild) {
            currentMode = Mode.TEST
        } else {
            val saved = prefs?.getString(KEY_MODE, Mode.LIVE.name) ?: Mode.LIVE.name
            currentMode = try { Mode.valueOf(saved) } catch (_: Exception) { Mode.LIVE }
        }
    }

    fun getMode(): Mode = currentMode

    fun isTestMode(): Boolean = currentMode == Mode.TEST

    fun setMode(mode: Mode) {
        currentMode = mode
        prefs?.edit()?.putString(KEY_MODE, mode.name)?.apply()
        listeners.forEach { it(mode) }
    }

    fun addModeChangeListener(listener: (Mode) -> Unit) {
        listeners.add(listener)
    }

    fun removeModeChangeListener(listener: (Mode) -> Unit) {
        listeners.remove(listener)
    }
}
