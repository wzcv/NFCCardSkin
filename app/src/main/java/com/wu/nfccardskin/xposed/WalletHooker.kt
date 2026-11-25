package com.wu.nfccardskin.xposed

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import com.wu.nfccardskin.core.UrlKey
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Hook points in the ColorOS wallet (com.finshell.wallet 5.52, see /jadx). The wallet keeps its
 * FinUI widget names unobfuscated, so the card-face hooks are found by name; the Glide layer
 * below them is obfuscated and is found by signature instead.
 *
 * Card face sources (record the URL + card name for the module UI, and replace it):
 *  - `com.finshell.finui.widget.atomic.card.FinUINfcCardFaceView#showCardImg(String url, String title, boolean, int)`
 *    and `#setCardIconAndName(String url, String title, boolean, int)`: card package, home carousel, detail.
 *  - `com.finshell.finui.widget.atomic.FinBigCardFaceView#setCardFaceUrl(String)`: transit card detail.
 *  - `com.finshell.finui.widget.atomic.card.FinCustomCardView#setCardFaceUrl(String)`.
 *
 * Sinks (replace only, nothing recorded):
 *  - `com.finshell.finui.widget.imageview.CircleNetworkImageView`: every instance method taking a String
 *    first and returning a Glide RequestBuilder (A0 / x0 / y0 in 5.52). All setImageUrl / e1 / g1 / Z0
 *    paths go through them, e.g. `NestNfcHeaderCardAdapter` calls `setImageUrl(nfcCard.getCardImg())`.
 *  - `com.finshell.finui.utils.CardCoverSettingUtil$Companion#fetchBrightness*` / `analyze*`: the wallet
 *    samples the card face to choose black / white text, so it must sample the replacement.
 *  - optional (setting): Glide `RequestManager.load(String|Object)` for any other view.
 */
class WalletHooker(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
    /** The wallet context handed over by a hot reload (Application.attach will not run again). */
    private val initialContext: Context? = null,
) {
    val config = HookConfig(module)

    @Volatile var installed = 0
        private set

    /**
     * Ids of the hooks this generation installed (API 102). Hooking an executable again with the same
     * id replaces the old hook atomically, which is how a hot-reloaded generation takes over.
     */
    val installedIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val hasHookIds: Boolean = module.apiVersion >= XposedInterface.API_102

    @Volatile private var contextReady = false

    fun start() {
        config.init()
        hookApplication()
        hookCardFaceViews()
        hookCircleNetworkImageView()
        hookBrightnessUtil()
        val cfg = config.current()
        if (cfg.glideFallback) hookGlideRequestManager()
        if (cfg.customHook.isSet) hookCustom(cfg.customHook)
        initialContext?.let {
            contextReady = true
            onContext(it)
        }
    }

    /**
     * State for the next generation (API 102 hot reload). Only objects of the wallet / framework, never
     * of this module's class loader, may cross generations.
     */
    fun saveState(): Any = hashMapOf<String, Any?>(
        STATE_CLASS_LOADER to classLoader,
        STATE_CONTEXT to config.context,
    )

    fun close() = config.close()

    // ---------------------------------------------------------------- context

    private fun hookApplication() {
        val attach = try {
            Application::class.java.getDeclaredMethod("attach", Context::class.java)
        } catch (t: Throwable) {
            XLog.w("Application.attach not found", t)
            return
        }
        try {
            hookWith(attach,
                object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        if (!contextReady) {
                            contextReady = true
                            try {
                                onContext(chain.getArg(0) as Context)
                            } catch (t: Throwable) {
                                XLog.w("context capture failed", t)
                            }
                        }
                        return result
                    }
                },
            )
        } catch (t: Throwable) {
            XLog.e("hook Application.attach failed", t)
        }
    }

    private fun onContext(ctx: Context) {
        config.attachContext(ctx)
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (_: Throwable) {
            "?"
        }
        // Config only reachable through the provider (LSPosed service never bound): late custom hook.
        val cfg = config.current()
        if (!customInstalled && cfg.customHook.isSet) hookCustom(cfg.customHook)
        config.hello(version, Application.getProcessName() ?: "?", installed)
    }

    // ---------------------------------------------------------------- custom hook point (dev option)

    @Volatile private var customInstalled = false

    @Synchronized
    private fun hookCustom(h: com.wu.nfccardskin.core.CustomHook) {
        if (customInstalled) return
        customInstalled = true
        val c = findClass(h.className.trim()) ?: return
        val argTypes: Array<Class<*>> = when (val a = h.argClass.trim()) {
            "" -> emptyArray()
            "String", "java.lang.String" -> arrayOf(String::class.java)
            else -> arrayOf(findClass(a) ?: return)
        }
        val m = try {
            c.getDeclaredMethod(h.methodName.trim(), *argTypes)
        } catch (t: Throwable) {
            XLog.e("custom hook: ${h.className}#${h.methodName}(${h.argClass}) not found", t)
            return
        }
        val label = "custom ${c.simpleName}#${m.name}"
        val argCount = m.parameterTypes.size
        try {
            hookWith(
                m,
                tag = "custom",
                hooker =
                object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        if (h.loadUrlFromArg && argCount > 0) {
                            val url = chain.getArg(0) as? String ?: return chain.proceed()
                            val r = try {
                                if (UrlKey.isCardCandidate(url)) config.report(url, null, "custom")
                                replacementFor(url)
                            } catch (t: Throwable) {
                                XLog.e("$label: module error", t)
                                null
                            } ?: return chain.proceed()
                            return if (h.replaceUrlToArg) chain.proceed(argsWith(chain, argCount, 0, r)) else r
                        }
                        val result = chain.proceed()
                        val url = result as? String ?: return result
                        return try {
                            if (UrlKey.isCardCandidate(url)) config.report(url, null, "custom")
                            replacementFor(url) ?: result
                        } catch (t: Throwable) {
                            XLog.e("$label: module error", t)
                            result
                        }
                    }
                },
            )
            installed++
            XLog.i("hooked $label (fromArg=${h.loadUrlFromArg}, toArg=${h.replaceUrlToArg})")
        } catch (t: Throwable) {
            XLog.e("hook failed: $label", t)
        }
    }

    // ---------------------------------------------------------------- card face views

    private fun hookCardFaceViews() {
        val nfcFace = findClass("com.finshell.finui.widget.atomic.card.FinUINfcCardFaceView")
        if (nfcFace != null) {
            for (m in nfcFace.declaredMethods) {
                val p = m.parameterTypes
                val isTarget = (m.name == "showCardImg" || m.name == "setCardIconAndName") &&
                    p.size == 4 && p[0] == String::class.java && p[1] == String::class.java
                if (!isTarget) continue // the shorter showCardImg overloads delegate to the 4-arg one
                hookArgs(m, "FinUINfcCardFaceView#${m.name}") { chain, n ->
                    replaceUrlArg(chain, n, index = 0, title = chain.getArg(1) as? String, source = "nfc")
                }
            }
        }
        for (cls in listOf(
            "com.finshell.finui.widget.atomic.FinBigCardFaceView",
            "com.finshell.finui.widget.atomic.card.FinCustomCardView",
        )) {
            val c = findClass(cls) ?: continue
            val m = try {
                c.getDeclaredMethod("setCardFaceUrl", String::class.java)
            } catch (_: Throwable) {
                XLog.w("${c.simpleName}#setCardFaceUrl not found")
                null
            } ?: continue
            hookArgs(m, "${c.simpleName}#setCardFaceUrl") { chain, n ->
                replaceUrlArg(chain, n, index = 0, title = null, source = c.simpleName)
            }
        }
    }

    /** Records [index] as a card face and returns the replaced argument array, or null to keep it. */
    private fun replaceUrlArg(
        chain: XposedInterface.Chain,
        argCount: Int,
        index: Int,
        title: String?,
        source: String,
    ): Array<Any?>? {
        val url = chain.getArg(index) as? String
        if (url.isNullOrEmpty()) return null
        if (UrlKey.isCardCandidate(url)) config.report(url, title?.takeIf { it.isNotBlank() }, source)
        val replacement = replacementFor(url) ?: return null
        return argsWith(chain, argCount, index, replacement)
    }

    // ---------------------------------------------------------------- image view sink

    private fun hookCircleNetworkImageView() {
        val c = findClass("com.finshell.finui.widget.imageview.CircleNetworkImageView") ?: return
        val setCompat = try {
            c.getMethod("setCompatibleSignature", Boolean::class.javaPrimitiveType)
        } catch (_: Throwable) {
            null
        }
        var count = 0
        for (m in c.declaredMethods) {
            if (Modifier.isStatic(m.modifiers) || m.isSynthetic) continue
            val p = m.parameterTypes
            if (p.isEmpty() || p[0] != String::class.java) continue
            if (!m.returnType.name.startsWith("com.bumptech.glide.")) continue
            count++
            hookArgs(m, "CircleNetworkImageView#${m.name}") { chain, n ->
                val url = chain.getArg(0) as? String
                // Dev option: list every network image, for cards shown by views we don't know.
                if (url != null && config.current().recordAll && UrlKey.isCardCandidate(url)) {
                    config.report(url, null, "image")
                }
                val replacement = url?.let { replacementFor(it) }
                if (replacement == null) {
                    null
                } else {
                    // compatibleSignature wraps the model in a GlideUrl, which cannot load content://.
                    if (!UrlKey.isHttp(replacement)) {
                        try {
                            setCompat?.invoke(chain.thisObject, false)
                        } catch (_: Throwable) {
                        }
                    }
                    argsWith(chain, n, 0, replacement)
                }
            }
        }
        if (count == 0) XLog.w("CircleNetworkImageView: no Glide builder method matched")
    }

    private fun hookBrightnessUtil() {
        val c = findClass("com.finshell.finui.utils.CardCoverSettingUtil\$Companion") ?: return
        for (m in c.declaredMethods) {
            if (Modifier.isStatic(m.modifiers) || m.isSynthetic) continue
            if (!(m.name.startsWith("fetchBrightness") || m.name.startsWith("analyze"))) continue
            val idx = m.parameterTypes.indexOfFirst { it == String::class.java }
            if (idx < 0) continue
            hookArgs(m, "CardCoverSettingUtil#${m.name}") { chain, n ->
                val url = chain.getArg(idx) as? String
                val replacement = url?.let { replacementFor(it) }
                if (replacement == null) null else argsWith(chain, n, idx, replacement)
            }
        }
    }

    /** RequestManager is obfuscated: it is the type of RequestBuilder's ComponentCallbacks2 field. */
    private fun hookGlideRequestManager() {
        val cnv = findClass("com.finshell.finui.widget.imageview.CircleNetworkImageView") ?: return
        val builder = cnv.declaredMethods.firstOrNull {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java &&
                it.returnType.name.startsWith("com.bumptech.glide.")
        }?.returnType ?: return
        var rm: Class<*>? = null
        var k: Class<*>? = builder
        while (k != null && rm == null) {
            rm = k.declaredFields.map { it.type }.firstOrNull {
                it.name.startsWith("com.bumptech.glide.") && ComponentCallbacks2::class.java.isAssignableFrom(it)
            }
            k = k.superclass
        }
        val requestManager = rm ?: run {
            XLog.w("Glide RequestManager not found")
            return
        }
        for (m in requestManager.declaredMethods) {
            val p = m.parameterTypes
            if (p.size != 1 || !builder.isAssignableFrom(m.returnType)) continue
            if (p[0] != String::class.java && p[0] != Any::class.java) continue
            hookArgs(m, "RequestManager#${m.name}(${p[0].simpleName})") { chain, n ->
                val url = chain.getArg(0) as? String
                val replacement = url?.let { replacementFor(it) }
                if (replacement == null) null else argsWith(chain, n, 0, replacement)
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun replacementFor(url: String): String? {
        val r = config.current().replacementFor(url)
        if (r != null) XLog.d("replace $url -> $r")
        return r
    }

    private fun argsWith(chain: XposedInterface.Chain, argCount: Int, index: Int, value: Any?): Array<Any?> =
        Array(argCount) { i -> if (i == index) value else chain.getArg(i) }

    /** module.hook(…).intercept(…) with a stable hook id on API 102 frameworks. */
    private fun hookWith(
        method: Method,
        hooker: XposedInterface.Hooker,
        tag: String = "builtin",
    ): XposedInterface.HookHandle {
        val builder = module.hook(method)
        // The tag keeps a custom hook point from replacing a built-in hook on the same method.
        val id = if (hasHookIds) hookId(method, tag) else null
        if (id != null) builder.setId(id)
        val handle = builder.intercept(hooker)
        if (id != null) installedIds += id
        return handle
    }

    private fun findClass(name: String): Class<*>? = try {
        Class.forName(name, false, classLoader)
    } catch (t: Throwable) {
        XLog.w("class not found: $name")
        null
    }

    /**
     * Installs a hook whose [prepare] returns replacement arguments (or null to keep the call as
     * is). Module errors are contained in [prepare]; exceptions of the original method propagate
     * unchanged and the original is never invoked twice.
     */
    private fun hookArgs(
        method: Method,
        label: String,
        prepare: (chain: XposedInterface.Chain, argCount: Int) -> Array<Any?>?,
    ) {
        val argCount = method.parameterTypes.size
        try {
            hookWith(method,
                object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val args = try {
                            prepare(chain, argCount)
                        } catch (t: Throwable) {
                            XLog.e("$label: module error, keeping original call", t)
                            null
                        }
                        return if (args == null) chain.proceed() else chain.proceed(args)
                    }
                },
            )
            installed++
            XLog.i("hooked $label")
        } catch (t: Throwable) {
            XLog.e("hook failed: $label", t)
        }
    }
}

const val STATE_CLASS_LOADER = "classLoader"
const val STATE_CONTEXT = "context"

/** Stable across module generations: one hook per executable. */
fun hookId(method: Method, tag: String): String =
    "nfcskin:$tag:" + method.declaringClass.name + "#" + method.name +
        method.parameterTypes.joinToString(",", "(", ")") { it.name }
