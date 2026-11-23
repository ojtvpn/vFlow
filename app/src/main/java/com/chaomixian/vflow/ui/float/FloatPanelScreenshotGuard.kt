package com.chaomixian.vflow.ui.float

import android.content.Context
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps the workflows float panel out of screenshots.
 *
 * Two independent user options (both on by default):
 *  - [KEY_HIDE_FROM_OWN_CAPTURE]: hide the panel while the app itself captures the screen.
 *  - [KEY_HIDE_FROM_SYSTEM_SCREENSHOT]: mark the panel windows FLAG_SECURE so system
 *    screenshots / screen recordings do not show it.
 */
object FloatPanelScreenshotGuard {
    const val PREFS_NAME = "vFlowPrefs"
    const val KEY_HIDE_FROM_OWN_CAPTURE = "float_panel_hide_from_own_capture"
    const val KEY_HIDE_FROM_SYSTEM_SCREENSHOT = "float_panel_hide_from_system_screenshot"

    private const val HIDE_SETTLE_MS = 150L

    private val views = CopyOnWriteArrayList<WeakReference<View>>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isHideFromOwnCapture(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_FROM_OWN_CAPTURE, true)

    fun isHideFromSystemScreenshot(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_FROM_SYSTEM_SCREENSHOT, true)

    fun setHideFromOwnCapture(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_FROM_OWN_CAPTURE, value).apply()
    }

    fun setHideFromSystemScreenshot(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_FROM_SYSTEM_SCREENSHOT, value).apply()
    }

    /** Adds FLAG_SECURE to [params] when the system-screenshot option is on. */
    fun applySecureFlag(context: Context, params: WindowManager.LayoutParams) {
        params.flags = if (isHideFromSystemScreenshot(context)) {
            params.flags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }
    }

    fun register(view: View) {
        views.removeAll { it.get() == null || it.get() === view }
        views.add(WeakReference(view))
    }

    fun unregister(view: View) {
        views.removeAll { it.get() == null || it.get() === view }
    }

    /**
     * Runs [block] with the float panel hidden (if the own-capture option is on),
     * restoring each view's previous visibility afterwards.
     */
    suspend fun <T> hiddenDuringCapture(context: Context, block: suspend () -> T): T {
        if (!isHideFromOwnCapture(context)) return block()
        val hidden = withContext(Dispatchers.Main) {
            val changed = mutableListOf<Pair<View, Int>>()
            for (ref in views) {
                val v = ref.get() ?: continue
                if (v.isAttachedToWindow && v.visibility == View.VISIBLE) {
                    changed += v to v.visibility
                    v.visibility = View.INVISIBLE
                }
            }
            changed
        }
        if (hidden.isEmpty()) return block()
        delay(HIDE_SETTLE_MS)
        try {
            return block()
        } finally {
            withContext(Dispatchers.Main) {
                for ((v, vis) in hidden) {
                    if (v.isAttachedToWindow && v.visibility == View.INVISIBLE) v.visibility = vis
                }
            }
        }
    }
}
