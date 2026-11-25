package com.wu.nfccardskin.xposed

import android.content.Context
import com.wu.nfccardskin.core.Constants
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * libxposed API 102 entry (`META-INF/xposed/java_init.list`, the only Java entry, which hot reload
 * requires). Scope: `com.finshell.wallet` only.
 *
 * Hooks are installed as early as possible (onPackageLoaded) so the first screen of card faces is
 * already replaced; onPackageReady is a fallback when the early install found nothing.
 *
 * Hot reload (API 102, `autoHotReload=true` in module.prop): when the module app is updated, the
 * framework asks the running generation ([onHotReloading]), which hands the wallet class loader and
 * context over; the new generation ([onHotReloaded]) re-hooks with the same hook ids, replacing the
 * old hooks atomically, so new code is live without restarting the wallet. On an API 101 framework
 * these callbacks are simply never called.
 */
class HookEntry : XposedModule() {

    @Volatile private var hooker: WalletHooker? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        XLog.attach(this)
        XLog.i("module loaded in ${param.processName}, framework=$frameworkName $frameworkVersion api=$apiVersion")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != Constants.WALLET_PACKAGE) return
        XLog.attach(this)
        install(param.defaultClassLoader, "packageLoaded")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != Constants.WALLET_PACKAGE) return
        val existing = hooker
        if (existing != null && existing.installed > 0) return
        XLog.attach(this)
        install(param.classLoader, "packageReady")
    }

    @Synchronized
    private fun install(classLoader: ClassLoader, stage: String) {
        val existing = hooker
        if (existing != null && existing.installed > 0) return
        try {
            val h = WalletHooker(this, classLoader)
            h.start()
            hooker = h
            XLog.i("$stage: installed ${h.installed} hooks")
        } catch (t: Throwable) {
            // The module must never take the wallet down.
            XLog.e("$stage: install failed", t)
        }
    }

    // ---------------------------------------------------------------- hot reload (API 102)

    /** Old generation: agree to reload and hand the wallet objects over. */
    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        val h = hooker
        return try {
            param.setSavedInstanceState(h?.saveState())
            h?.close()
            XLog.i("hot reloading (hooks=${h?.installed ?: 0})")
            true
        } catch (t: Throwable) {
            XLog.e("hot reloading refused", t)
            false
        }
    }

    /** New generation: re-hook with the same ids (atomic replace), drop old hooks we no longer install. */
    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        XLog.attach(this)
        val old = param.oldHookHandles
        val state = param.savedInstanceState as? Map<*, *>
        val classLoader = state?.get(STATE_CLASS_LOADER) as? ClassLoader ?: walletClassLoaderOf(old)
        val context = state?.get(STATE_CONTEXT) as? Context
        if (classLoader == null) {
            // Not a wallet process, or nothing was hooked: just clean up.
            old.forEach { runCatching { it.unhook() } }
            XLog.i("hot reloaded in ${param.processName}: nothing to re-hook")
            return
        }
        val h = try {
            WalletHooker(this, classLoader, initialContext = context).also { it.start() }
        } catch (t: Throwable) {
            XLog.e("hot reload: re-hook failed", t)
            null
        }
        hooker = h
        val kept = h?.installedIds.orEmpty()
        var dropped = 0
        for (handle in old) {
            val id = runCatching { handle.id }.getOrNull()
            if (id != null && id in kept) continue // already replaced by the new generation
            runCatching { handle.unhook() }
            dropped++
        }
        XLog.i("hot reloaded in ${param.processName}: hooks=${h?.installed ?: 0}, dropped $dropped old hooks")
    }

    private fun walletClassLoaderOf(handles: List<XposedInterface.HookHandle>): ClassLoader? =
        handles.firstNotNullOfOrNull { handle ->
            runCatching { handle.executable.declaringClass }.getOrNull()
                ?.takeIf { it.name.startsWith("com.finshell.") }
                ?.classLoader
        }
}
