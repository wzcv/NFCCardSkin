package com.wu.nfccardskin.core

import org.json.JSONObject

/** A user-defined hook point (dev option), same semantics as the original DIY module. */
data class CustomHook(
    val className: String = "",
    val methodName: String = "",
    /** "" = no parameters, "String", or a fully-qualified parameter class. */
    val argClass: String = "String",
    /** Read the URL from argument 0 (otherwise from the return value). */
    val loadUrlFromArg: Boolean = true,
    /** Write the replacement back into argument 0 and call the original (otherwise return it). */
    val replaceUrlToArg: Boolean = true,
) {
    val isSet: Boolean get() = className.isNotBlank() && methodName.isNotBlank()
}

/**
 * What the hook needs to know, as one JSON document. The app builds it, pushes it to LSPosed remote
 * preferences and also serves it from [SkinProvider] (`get_config`) as a fallback.
 *
 * Mapping values are final URIs the wallet's Glide can load: `content://…/images/<name>?v=<ts>` for
 * gallery images (the `v` busts Glide's cache when an image is replaced) or an http(s) URL.
 */
data class SkinConfig(
    val enabled: Boolean = true,
    val testMode: Boolean = false,
    val debug: Boolean = false,
    /** Also replace in every Glide `RequestManager.load(String)` call (fallback for unknown views). */
    val glideFallback: Boolean = false,
    /** Record every network image the wallet loads, not only card face views (dev option). */
    val recordAll: Boolean = false,
    val customHook: CustomHook = CustomHook(),
    val mappings: Map<String, String> = emptyMap(),
    val version: Long = 0L,
) {
    /** Lookup by normalised URL, built once per config. */
    private val normalized: Map<String, String> by lazy {
        val out = HashMap<String, String>()
        for ((k, v) in mappings) out.putIfAbsent(UrlKey.normalize(k), v)
        out
    }

    /** The replacement for [url], or null to keep the original card face. */
    fun replacementFor(url: String): String? {
        if (!enabled || url.isEmpty() || UrlKey.isOwnUrl(url)) return null
        if (testMode) return Constants.TEST_IMAGE_URI
        mappings[url]?.let { return it }
        return normalized[UrlKey.normalize(url)]
    }

    fun toJson(): String {
        val map = JSONObject()
        for ((k, v) in mappings) map.put(k, v)
        val hook = JSONObject()
            .put("class", customHook.className)
            .put("method", customHook.methodName)
            .put("arg", customHook.argClass)
            .put("fromArg", customHook.loadUrlFromArg)
            .put("toArg", customHook.replaceUrlToArg)
        return JSONObject()
            .put("v", 1)
            .put("enabled", enabled)
            .put("test", testMode)
            .put("debug", debug)
            .put("glide", glideFallback)
            .put("recordAll", recordAll)
            .put("hook", hook)
            .put("ts", version)
            .put("map", map)
            .toString()
    }

    companion object {
        val EMPTY = SkinConfig(enabled = false)

        fun fromJson(json: String?): SkinConfig? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                val m = o.optJSONObject("map")
                val mappings = HashMap<String, String>()
                if (m != null) {
                    val keys = m.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = m.optString(k)
                        if (k.isNotEmpty() && v.isNotEmpty()) mappings[k] = v
                    }
                }
                val h = o.optJSONObject("hook")
                SkinConfig(
                    enabled = o.optBoolean("enabled", true),
                    testMode = o.optBoolean("test", false),
                    debug = o.optBoolean("debug", false),
                    glideFallback = o.optBoolean("glide", false),
                    recordAll = o.optBoolean("recordAll", false),
                    customHook = if (h == null) {
                        CustomHook()
                    } else {
                        CustomHook(
                            className = h.optString("class"),
                            methodName = h.optString("method"),
                            argClass = h.optString("arg", "String"),
                            loadUrlFromArg = h.optBoolean("fromArg", true),
                            replaceUrlToArg = h.optBoolean("toArg", true),
                        )
                    },
                    mappings = mappings,
                    version = o.optLong("ts", 0L),
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}

/** URL helpers shared by app and hook. */
object UrlKey {
    private val SIZE_SEGMENT = Regex("/w\\d+h\\d+(?=[/?#]|$)")
    private val SIZE_SUFFIX = Regex("[_@-]\\d{2,4}x\\d{2,4}(?=\\.(png|jpe?g|webp)$)", RegexOption.IGNORE_CASE)

    /**
     * Card face URLs can come back with a different query (signature, timestamp, resize
     * parameters) between wallet sessions. Matching on scheme+host+path keeps a mapping alive.
     */
    fun normalize(url: String): String {
        var s = url.substringBefore('#').substringBefore('?')
        s = s.replaceFirst("http://", "https://")
        s = SIZE_SEGMENT.replace(s, "")
        s = SIZE_SUFFIX.replace(s, "")
        return s.trimEnd('/').lowercase()
    }

    fun isOwnUrl(url: String): Boolean = url.startsWith("content://${Constants.AUTHORITY}")

    fun isHttp(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")

    /** Only real network images are worth listing as wallet card faces. */
    fun isCardCandidate(url: String): Boolean = isHttp(url) && !isOwnUrl(url)
}
