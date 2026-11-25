package com.wu.nfccardskin.xposed

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import com.wu.nfccardskin.core.Constants
import com.wu.nfccardskin.core.SkinConfig
import io.github.libxposed.api.XposedModule
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Config inside the wallet process.
 *
 * 1. LSPosed remote preferences (`config/json`): synchronous, pushed live by the framework, works on
 *    cold start before the wallet has a Context. Read-only in the hooked process.
 * 2. Fallback: `SkinProvider.call("get_config")` once a Context exists, refreshed at most every 5 s
 *    on a background thread (used when the module app never reached the LSPosed service).
 *
 * Reports (card faces seen, hook heartbeat) always go to the provider, off the UI thread.
 */
class HookConfig(private val module: XposedModule) {

    @Volatile private var remote: SharedPreferences? = null
    @Volatile private var remoteJson: String? = null
    @Volatile private var remoteConfig: SkinConfig? = null

    @Volatile private var providerConfig: SkinConfig? = null
    @Volatile private var providerFetchedAt = 0L
    private val fetching = AtomicBoolean(false)

    /** The wallet's application context, once Application.attach ran (or handed over by a hot reload). */
    @Volatile var context: Context? = null
        private set
    // The idle thread times out, so an old module generation is not kept alive after a hot reload.
    private val io = ThreadPoolExecutor(1, 1, 10L, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
        Thread(r, "NfcCardSkin-io").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }
    private val reported = HashMap<String, String>()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ -> readRemote(prefs) }

    fun init() {
        try {
            val prefs = module.getRemotePreferences(Constants.REMOTE_GROUP)
            remote = prefs
            readRemote(prefs)
            prefs.registerOnSharedPreferenceChangeListener(listener)
        } catch (t: Throwable) {
            XLog.w("remote preferences unavailable, will use provider", t)
        }
    }

    private fun readRemote(prefs: SharedPreferences) {
        try {
            val json = prefs.getString(Constants.REMOTE_KEY_JSON, null)
            if (json != remoteJson) {
                remoteJson = json
                remoteConfig = SkinConfig.fromJson(json)
                XLog.debug = current().debug
                XLog.i("config from LSPosed: ${remoteConfig?.mappings?.size ?: 0} mappings")
            }
        } catch (t: Throwable) {
            XLog.w("read remote config failed", t)
        }
    }

    /** Drops the framework listener before a hot reload, so the old generation can be collected. */
    fun close() {
        try {
            remote?.unregisterOnSharedPreferenceChangeListener(listener)
        } catch (_: Throwable) {
        }
    }

    fun attachContext(ctx: Context) {
        if (context != null) return
        context = ctx.applicationContext ?: ctx
        refreshFromProvider(force = true)
    }

    /** The config to use right now. Never blocks. */
    fun current(): SkinConfig {
        val r = remoteConfig
        val p = providerConfig
        if (System.currentTimeMillis() - providerFetchedAt > 5_000L) refreshFromProvider(force = false)
        return when {
            r != null && p != null -> if (p.version > r.version) p else r
            r != null -> r
            p != null -> p
            else -> SkinConfig.EMPTY
        }
    }

    private fun refreshFromProvider(force: Boolean) {
        val ctx = context ?: return
        if (!force && System.currentTimeMillis() - providerFetchedAt < 5_000L) return
        if (!fetching.compareAndSet(false, true)) return
        io.execute {
            try {
                val out = ctx.contentResolver.call(providerUri(), Constants.CALL_GET_CONFIG, null, null)
                val cfg = SkinConfig.fromJson(out?.getString(Constants.EXTRA_JSON))
                if (cfg != null) {
                    providerConfig = cfg
                    XLog.debug = current().debug
                }
            } catch (t: Throwable) {
                XLog.d("provider config unavailable: $t")
            } finally {
                providerFetchedAt = System.currentTimeMillis()
                fetching.set(false)
            }
        }
    }

    /** Tell the module app about a card face URL (once per URL / title per process). */
    fun report(url: String, title: String?, source: String) {
        val ctx = context ?: return
        val key = title.orEmpty()
        synchronized(reported) {
            if (reported[url] == key) return
            reported[url] = key
        }
        io.execute {
            try {
                val extras = Bundle().apply {
                    putString(Constants.EXTRA_URL, url)
                    putString(Constants.EXTRA_TITLE, title)
                    putString(Constants.EXTRA_SOURCE, source)
                }
                ctx.contentResolver.call(providerUri(), Constants.CALL_REPORT, null, extras)
            } catch (t: Throwable) {
                synchronized(reported) { reported.remove(url) }
                XLog.d("report failed: $t")
            }
        }
    }

    fun hello(version: String, process: String, hooks: Int) {
        val ctx = context ?: return
        io.execute {
            try {
                val extras = Bundle().apply {
                    putString(Constants.EXTRA_VERSION, version)
                    putString(Constants.EXTRA_PROCESS, process)
                    putInt(Constants.EXTRA_HOOKS, hooks)
                }
                ctx.contentResolver.call(providerUri(), Constants.CALL_HELLO, null, extras)
            } catch (t: Throwable) {
                XLog.d("hello failed: $t")
            }
        }
    }

    private fun providerUri(): Uri = Uri.parse("content://${Constants.AUTHORITY}")
}
