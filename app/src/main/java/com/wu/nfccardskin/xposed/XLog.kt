package com.wu.nfccardskin.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule

/** Hook-side log: logcat + LSPosed log page, tag "NfcCardSkin". */
object XLog {
    const val TAG = "NfcCardSkin"

    @Volatile private var module: XposedModule? = null

    @Volatile var debug: Boolean = false

    fun attach(module: XposedModule) {
        this.module = module
    }

    fun d(msg: String) {
        if (debug) write(Log.DEBUG, msg, null)
    }

    fun i(msg: String) = write(Log.INFO, msg, null)

    fun w(msg: String, tr: Throwable? = null) = write(Log.WARN, msg, tr)

    fun e(msg: String, tr: Throwable? = null) = write(Log.ERROR, msg, tr)

    private fun write(priority: Int, msg: String, tr: Throwable?) {
        try {
            Log.println(priority, TAG, if (tr == null) msg else "$msg: ${Log.getStackTraceString(tr)}")
        } catch (_: Throwable) {
        }
        val m = module ?: return
        try {
            if (tr == null) m.log(priority, TAG, msg) else m.log(priority, TAG, msg, tr)
        } catch (_: Throwable) {
        }
    }
}
