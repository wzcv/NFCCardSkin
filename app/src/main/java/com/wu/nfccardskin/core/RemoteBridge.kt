package com.wu.nfccardskin.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import androidx.core.content.edit

/**
 * Module app <-> LSPosed (libxposed service 102). Writes the hook config into the framework's remote
 * preferences (read by the hook without any IPC to this app), manages the wallet scope and, on API
 * 102 frameworks, lists the hooked wallet processes and hot-reloads stale ones.
 */
object RemoteBridge {
    private const val TAG = "NfcCardSkin"

    @Volatile private var service: XposedService? = null
    @Volatile private var pendingJson: String? = null
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(ServiceState())
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    fun init(onBind: () -> Unit) {
        try {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    this@RemoteBridge.service = service
                    refreshState()
                    pendingJson?.let { write(service, it) }
                    onBind()
                }

                override fun onServiceDied(service: XposedService) {
                    this@RemoteBridge.service = null
                    _state.value = ServiceState()
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "XposedService unavailable", t)
        }
    }

    fun push(json: String) {
        pendingJson = json
        service?.let { write(it, json) }
    }

    private fun write(service: XposedService, json: String) {
        try {
            service.getRemotePreferences(Constants.REMOTE_GROUP).edit {
                putString(Constants.REMOTE_KEY_JSON, json)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "write remote config failed", t)
        }
    }

    fun refreshState() {
        val s = service ?: run {
            _state.value = ServiceState()
            return
        }
        val api = runCatching { s.apiVersion }.getOrDefault(0)
        val framework = runCatching { "${s.frameworkName} ${s.frameworkVersion}" }.getOrDefault("LSPosed")
        val inScope = runCatching { s.scope.contains(Constants.WALLET_PACKAGE) }.getOrNull()
        val targets = if (api >= XposedService.API_102) {
            runCatching { s.runningTargets.map { it.toProcess() } }
                .onFailure { Log.w(TAG, "getRunningTargets failed", it) }
                .getOrNull()
        } else {
            null
        }
        _state.value = ServiceState(
            connected = true,
            framework = "$framework · API $api",
            apiVersion = api,
            walletInScope = inScope,
            targets = targets,
        )
    }

    /** Asks LSPosed to add the wallet to the module scope (the manager shows a confirmation). */
    fun requestWalletScope(): Boolean {
        val s = service ?: return false
        return try {
            s.requestScope(listOf(Constants.WALLET_PACKAGE), object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    main.post { refreshState() }
                }
            })
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestScope failed", t)
            false
        }
    }

    /**
     * Hot-reloads every wallet process still running an older module build (API 102). [onDone] gets
     * (succeeded, failed, first failure message) on the main thread; false = nothing was started.
     */
    fun hotReloadStale(onDone: (Int, Int, String?) -> Unit): Boolean {
        val s = service ?: return false
        if (runCatching { s.apiVersion }.getOrDefault(0) < XposedService.API_102) return false
        val stale = runCatching { s.runningTargets }.getOrNull()
            ?.filter { it.state == HookedTarget.State.STALE || it.state == HookedTarget.State.FAILED }
            .orEmpty()
        if (stale.isEmpty()) return false
        val remaining = AtomicInteger(stale.size)
        val ok = AtomicInteger(0)
        val fail = AtomicInteger(0)
        val message = AtomicReference<String?>(null)
        val finishOne = {
            if (remaining.decrementAndGet() == 0) {
                main.post {
                    refreshState()
                    onDone(ok.get(), fail.get(), message.get())
                }
            }
        }
        for (target in stale) {
            try {
                s.hotReloadModule(target, null) { _, result ->
                    if (result.status() == HotReloadResult.Status.SUCCEEDED) {
                        ok.incrementAndGet()
                    } else {
                        fail.incrementAndGet()
                        message.compareAndSet(null, result.message() ?: result.status().name)
                    }
                    finishOne()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "hotReloadModule failed for ${target.processName}", t)
                fail.incrementAndGet()
                message.compareAndSet(null, t.message ?: t.javaClass.simpleName)
                finishOne()
            }
        }
        main.post { refreshState() } // show RELOADING
        return true
    }

    private fun HookedTarget.toProcess() = WalletProcess(processName = processName, pid = pid, state = state.name)
}
