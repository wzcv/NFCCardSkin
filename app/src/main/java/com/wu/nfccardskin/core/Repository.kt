package com.wu.nfccardskin.core

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import com.wu.nfccardskin.core.catalog.CatalogItem
import com.wu.nfccardskin.core.catalog.OnlineCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

/**
 * Single source of truth of the module app. Everything lives in one SharedPreferences file
 * ("store"); every change that affects the hook rebuilds the config and pushes it to LSPosed.
 *
 * Called from the UI and from [SkinProvider] binder threads, hence the lock.
 */
class Repository private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("store", Context.MODE_PRIVATE)
    val images = ImageStore(context)
    val logos = LogoStore(context)
    val catalog = OnlineCatalog(context, images)
    private val lock = Any()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // ------------------------------------------------------------------ hook reports

    fun onReport(url: String, title: String?, source: String) {
        if (!UrlKey.isCardCandidate(url)) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val cards = _state.value.cards.toMutableList()
            val i = cards.indexOfFirst { it.url == url }
            if (i >= 0) {
                val old = cards[i]
                // A card-face view beats a generic image report for the source / title.
                val betterSource = if (old.source == SOURCE_IMAGE && source != SOURCE_IMAGE) source else old.source
                val newTitle = title ?: old.title
                if (old.title == newTitle && old.source == betterSource && now - old.lastSeen < 60_000L) return
                cards[i] = old.copy(title = newTitle, source = betterSource, lastSeen = now)
            } else {
                cards += SeenCard(url, title, source, now, now)
            }
            commit(publish = false) { it.copy(cards = cards) }
        }
    }

    fun onHello(version: String, process: String, hooks: Int) {
        synchronized(lock) {
            commit(publish = false) { it.copy(hookStatus = HookStatus(version, process, hooks, System.currentTimeMillis())) }
        }
    }

    // ------------------------------------------------------------------ edits

    fun setTarget(url: String, target: SkinTarget?) = synchronized(lock) {
        commit { s ->
            val m = s.mappings.toMutableMap()
            if (target == null) m.remove(url) else m[url] = target.encode()
            s.copy(mappings = m)
        }
    }

    fun saveImageFor(url: String?, bitmap: Bitmap): String {
        val name = images.save(bitmap)
        synchronized(lock) {
            commit(publish = url != null) { s ->
                val m = s.mappings.toMutableMap()
                if (url != null) m[url] = SkinTarget.Image(name).encode()
                s.copy(mappings = m, images = images.list())
            }
        }
        return name
    }

    /**
     * Stores an online library image in the gallery (centre crop, the user's rounded-corner
     * setting) and maps it to [url] when given. Returns the gallery file name.
     */
    suspend fun saveOnline(item: CatalogItem, url: String?): String {
        val round = _state.value.settings.roundCorners
        val name = catalog.saveToGallery(item) { ImageStore.finish(it, null, round) }
        synchronized(lock) {
            commit(publish = url != null) { s ->
                val m = s.mappings.toMutableMap()
                if (url != null) m[url] = SkinTarget.Image(name).encode()
                s.copy(mappings = m, images = images.list())
            }
        }
        return name
    }

    /** Deletes a gallery image and every mapping that pointed at it (no dangling mappings). */
    fun deleteImage(name: String) = synchronized(lock) {
        images.delete(name)
        val encoded = SkinTarget.Image(name).encode()
        commit { s -> s.copy(mappings = s.mappings.filterValues { it != encoded }, images = images.list()) }
    }

    fun setHidden(url: String, hidden: Boolean) = synchronized(lock) {
        commit(publish = false) { s -> s.copy(hidden = if (hidden) s.hidden + url else s.hidden - url) }
    }

    fun setAlias(url: String, alias: String?) = synchronized(lock) {
        commit(publish = false) { s ->
            val a = s.aliases.toMutableMap()
            if (alias.isNullOrBlank()) a.remove(url) else a[url] = alias.trim()
            s.copy(aliases = a)
        }
    }

    fun forget(url: String) = synchronized(lock) {
        commit { s ->
            s.copy(
                cards = s.cards.filterNot { it.url == url },
                hidden = s.hidden - url,
                mappings = s.mappings - url,
                aliases = s.aliases - url,
            )
        }
    }

    /** "清空记录": forgets cards that have no replacement (they come back when the wallet shows them). */
    fun clearUnmappedCards() = synchronized(lock) {
        commit(publish = false) { s -> s.copy(cards = s.cards.filter { it.url in s.mappings }) }
    }

    fun resetAllMappings() = synchronized(lock) {
        commit { s -> s.copy(mappings = emptyMap()) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = synchronized(lock) {
        commit { s -> s.copy(settings = transform(s.settings)) }
    }

    /** Re-reads everything from disk (pull to refresh): picks up reports written by other processes. */
    fun reload() = synchronized(lock) {
        _state.value = load()
    }

    fun refreshImages() = synchronized(lock) {
        commit(publish = false) { s -> s.copy(images = images.list()) }
    }

    // ------------------------------------------------------------------ hook config

    fun buildConfig(): SkinConfig {
        val s = _state.value
        val resolved = HashMap<String, String>()
        for ((url, value) in s.mappings) {
            when (val t = SkinTarget.decode(value)) {
                is SkinTarget.Image -> {
                    val file = images.find(t.name) ?: continue // missing file: keep the original face
                    resolved[url] = Constants.IMAGE_URI_PREFIX + t.name + "?v=" + file.lastModified()
                }
                is SkinTarget.Url -> resolved[url] = t.url
                null -> Unit
            }
        }
        val st = s.settings
        return SkinConfig(
            enabled = st.enabled,
            testMode = st.testMode,
            debug = st.debug,
            glideFallback = st.glideFallback,
            recordAll = st.devMode && st.recordAll,
            customHook = if (st.devMode) st.customHook else CustomHook(),
            mappings = resolved,
            version = prefs.getLong(KEY_VERSION, 0L),
        )
    }

    fun publish() {
        RemoteBridge.push(buildConfig().toJson())
    }

    // ------------------------------------------------------------------ persistence

    private fun commit(publish: Boolean = true, transform: (UiState) -> UiState) {
        val next = transform(_state.value)
        _state.value = next
        save(next, bumpVersion = publish)
        if (publish) publish()
    }

    private fun save(s: UiState, bumpVersion: Boolean) {
        val cards = JSONArray()
        for ((url, title, source, firstSeen, lastSeen) in s.cards) {
            cards.put(
                JSONObject().put("url", url).put("title", title ?: "").put("source", source)
                    .put("first", firstSeen).put("last", lastSeen),
            )
        }
        val st = s.settings
        val h = st.customHook
        prefs.edit {
            putString(KEY_CARDS, cards.toString())
            putString(KEY_MAPPINGS, JSONObject(s.mappings).toString())
            putString(KEY_ALIASES, JSONObject(s.aliases).toString())
            putStringSet(KEY_HIDDEN, s.hidden)
            putBoolean("enabled", st.enabled)
            putBoolean("test_mode", st.testMode)
            putBoolean("debug", st.debug)
            putBoolean("glide_fallback", st.glideFallback)
            putBoolean("record_all", st.recordAll)
            putBoolean("dev_mode", st.devMode)
            putString("hook_class", h.className)
            putString("hook_method", h.methodName)
            putString("hook_arg", h.argClass)
            putBoolean("hook_from_arg", h.loadUrlFromArg)
            putBoolean("hook_to_arg", h.replaceUrlToArg)
            putInt("theme_mode", st.themeMode)
            putBoolean("blur", st.blur)
            putBoolean("round_corners", st.roundCorners)
            putBoolean("first_run", st.firstRun)
            putBoolean("catalog_proxy", st.catalogProxy)
            s.hookStatus?.let {
                putString(KEY_HOOK,
                    JSONObject().put("ver", it.walletVersion).put("proc", it.process)
                        .put("hooks", it.hooks).put("at", it.at).toString()
                )
            }
            if (bumpVersion) putLong(KEY_VERSION, System.currentTimeMillis())
        }
    }

    private fun readMap(key: String): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString(key, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun load(): UiState {
        val cards = runCatching {
            val a = JSONArray(prefs.getString(KEY_CARDS, "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                SeenCard(
                    url = o.getString("url"),
                    title = o.optString("title").ifBlank { null },
                    source = o.optString("source"),
                    firstSeen = o.optLong("first"),
                    lastSeen = o.optLong("last"),
                )
            }
        }.getOrDefault(emptyList())
        val hook = runCatching {
            val o = JSONObject(prefs.getString(KEY_HOOK, null) ?: return@runCatching null)
            HookStatus(o.optString("ver"), o.optString("proc"), o.optInt("hooks"), o.optLong("at"))
        }.getOrNull()
        return UiState(
            cards = cards,
            mappings = readMap(KEY_MAPPINGS),
            hidden = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet(),
            aliases = readMap(KEY_ALIASES),
            images = images.list(),
            settings = AppSettings(
                enabled = prefs.getBoolean("enabled", true),
                testMode = prefs.getBoolean("test_mode", false),
                debug = prefs.getBoolean("debug", false),
                glideFallback = prefs.getBoolean("glide_fallback", false),
                recordAll = prefs.getBoolean("record_all", false),
                devMode = prefs.getBoolean("dev_mode", false),
                customHook = CustomHook(
                    className = prefs.getString("hook_class", "").orEmpty(),
                    methodName = prefs.getString("hook_method", "").orEmpty(),
                    argClass = prefs.getString("hook_arg", "String") ?: "String",
                    loadUrlFromArg = prefs.getBoolean("hook_from_arg", true),
                    replaceUrlToArg = prefs.getBoolean("hook_to_arg", true),
                ),
                themeMode = prefs.getInt("theme_mode", 0),
                blur = prefs.getBoolean("blur", true),
                roundCorners = prefs.getBoolean("round_corners", false),
                firstRun = prefs.getBoolean("first_run", true),
                catalogProxy = prefs.getBoolean("catalog_proxy", true),
            ),
            hookStatus = hook,
        )
    }

    companion object {
        const val SOURCE_IMAGE = "image"
        private const val KEY_CARDS = "cards"
        private const val KEY_MAPPINGS = "mappings"
        private const val KEY_ALIASES = "aliases"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_HOOK = "hook_status"
        private const val KEY_VERSION = "config_version"

        @Volatile private var instance: Repository? = null

        fun init(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }

        fun get(): Repository = checkNotNull(instance) { "Repository.init not called" }
    }
}
