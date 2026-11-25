/*
 * Adapted from cardface-miuix (https://github.com/MiamBao/cardface-miuix, GPL-3.0):
 * the hi.my-api.cn query format, image host checks and cache approach.
 */
package com.wu.nfccardskin.core.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.wu.nfccardskin.core.Constants
import com.wu.nfccardskin.core.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Collator
import java.util.Locale
import androidx.core.content.edit

/**
 * The online card face library at https://hi.my-api.cn.
 * `GET /api/?resource=<r>&page=<n>&page_size=100` returns `{code, data: {list, total_pages}}`:
 *  - `face_ctu`: transit cards — `id, city, title, filename, source`
 *  - `face_ctu_origin`: IP collaborations — `productId, ipName, productName, productImagePath`
 *  - `face`: bank cards — `id, description, image, source, issuer.native_name`
 * Images only come from https://storage.my-api.cn/static/images/. They are always downloaded,
 * checked and stored in the module gallery: the wallet is never given a remote URL.
 */
enum class CatalogCategory(val resource: String, val webPath: String) {
    TRANSIT("face_ctu", "face-ctu"),
    IP("face_ctu_origin", "face-ctu-origin"),
    BANK("face", "face"),
}

@Immutable
data class CatalogItem(
    val id: String,
    val category: CatalogCategory,
    val title: String,
    /** City / IP name / bank: also the group shown as a chip. */
    val group: String,
    val source: String,
    val imageUrl: String,
) {
    val key: String get() = "${category.resource}:$id"
}

class OnlineCatalog(context: Context, private val images: ImageStore) {

    private val app = context.applicationContext
    private val cacheDir = File(app.cacheDir, "catalog").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("catalog", Context.MODE_PRIVATE)
    private val memory = HashMap<CatalogCategory, List<CatalogItem>>()
    private val mutex = Mutex()
    private val collator: Collator = Collator.getInstance(Locale.CHINA)

    /** Whole category, sorted by group (pinyin) then title. Cached on disk for 7 days. */
    suspend fun load(category: CatalogCategory, force: Boolean = false): List<CatalogItem> {
        mutex.withLock {
            if (!force) memory[category]?.let { return it }
            val file = File(cacheDir, "${category.resource}.json")
            if (!force) {
                readCache(file)?.let {
                    memory[category] = it
                    return it
                }
            }
            val fetched = withContext(Dispatchers.IO) { fetchAll(category) }
            val sorted = fetched
                .distinctBy { it.imageUrl }
                .sortedWith { a, b ->
                    val g = collator.compare(a.group, b.group)
                    if (g != 0) g else collator.compare(a.title, b.title)
                }
            memory[category] = sorted
            runCatching { writeCache(file, sorted) }
            return sorted
        }
    }

    /** Groups of a category with their sizes, largest first. */
    fun groups(items: List<CatalogItem>): List<Pair<String, Int>> =
        items.filter { it.group.isNotBlank() }.groupingBy { it.group }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

    fun search(items: List<CatalogItem>, query: String): List<CatalogItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return items.filter { it.title.contains(q, true) || it.group.contains(q, true) || it.source.contains(q, true) }
    }

    // ------------------------------------------------------------------ network

    private suspend fun fetchAll(category: CatalogCategory): List<CatalogItem> = coroutineScope {
        val first = parse(requestJson(pageUrl(category, 1)), category)
        val pages = first.second.coerceIn(1, 80)
        val gate = Semaphore(4)
        val rest = (2..pages).map { p ->
            async(Dispatchers.IO) {
                gate.withPermit { runCatching { parse(requestJson(pageUrl(category, p)), category).first }.getOrDefault(emptyList()) }
            }
        }.awaitAll()
        first.first + rest.flatten()
    }

    private fun pageUrl(category: CatalogCategory, page: Int) =
        "https://$API_HOST/api/?resource=${category.resource}&page=$page&page_size=100"

    private suspend fun requestJson(url: String): String {
        val conn = withContext(Dispatchers.IO) {
            URL(url).openConnection()
        } as HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            check(conn.url.protocol == "https" && conn.url.host == API_HOST) { "unexpected redirect" }
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    check(out.size() + n <= 4 * 1024 * 1024) { "response too large" }
                    out.write(buf, 0, n)
                }
            }
            return withContext(Dispatchers.IO) {
                out.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Returns the items of one page and the total page count. */
    private fun parse(text: String, category: CatalogCategory): Pair<List<CatalogItem>, Int> {
        val root = JSONObject(text)
        check(root.optInt("code") == 200) { root.optString("msg").take(80) }
        val data = root.getJSONObject("data")
        val list = data.getJSONArray("list")
        val items = (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val raw = when (category) {
                CatalogCategory.TRANSIT -> o.optString("filename")
                CatalogCategory.IP -> o.optString("productImagePath")
                CatalogCategory.BANK -> o.optString("image")
            }
            val image = normalizeImageUrl(raw) ?: return@mapNotNull null
            val title = when (category) {
                CatalogCategory.TRANSIT -> o.optString("title")
                CatalogCategory.IP -> o.optString("productName")
                CatalogCategory.BANK -> o.optString("description")
            }.trim().take(120)
            val group = when (category) {
                CatalogCategory.TRANSIT -> o.optString("city")
                CatalogCategory.IP -> o.optString("ipName")
                CatalogCategory.BANK -> o.optJSONObject("issuer")?.optString("native_name").orEmpty()
            }.trim().take(80)
            val id = o.optString(if (category == CatalogCategory.IP) "productId" else "id")
                .takeIf { it.isNotBlank() && it != "null" } ?: sha1(image).take(16)
            CatalogItem(
                id = id,
                category = category,
                title = title,
                group = group,
                source = if (category == CatalogCategory.IP) "" else o.optString("source").trim().take(60),
                imageUrl = image,
            )
        }
        return items to data.optInt("total_pages", 1)
    }

    // ------------------------------------------------------------------ images

    /** Downloads the original to the cache (reused across calls) and returns it. */
    suspend fun download(item: CatalogItem): File = withContext(Dispatchers.IO) {
        val url = normalizeImageUrl(item.imageUrl) ?: error("bad image url")
        val dir = File(cacheDir, "images").apply { mkdirs() }
        val target = File(dir, sha1(url) + ".img")
        if (target.isFile && target.length() > 0) return@withContext target
        val part = File(dir, target.name + ".part")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Referer", "https://$API_HOST/${item.category.webPath}")
            try {
                check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                check(conn.url.protocol == "https" && conn.url.host == IMAGE_HOST) { "unexpected redirect" }
                var done = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            done += n
                            check(done <= MAX_IMAGE_BYTES) { "image too large" }
                            output.write(buf, 0, n)
                        }
                    }
                }
                check(done > 0) { "empty image" }
            } finally {
                conn.disconnect()
            }
            check(part.renameTo(target)) { "rename failed" }
            target
        } finally {
            part.delete()
        }
    }

    /**
     * Downloads [item] and stores it in the gallery at the wallet card size (centre crop to 1.62:1),
     * returning the gallery file name. The same online image is only stored once.
     */
    suspend fun saveToGallery(item: CatalogItem, finish: (Bitmap) -> Bitmap): String {
        val key = "gallery:" + sha1(item.imageUrl)
        prefs.getString(key, null)?.let { name -> if (images.find(name) != null) return name }
        val file = download(item)
        val name = withContext(Dispatchers.Default) {
            val src = ImageStore.decode(app, Uri.fromFile(file))
            try {
                val out = finish(ImageStore.cropAndScale(src, centerCrop(src.width, src.height), Constants.OUTPUT_WIDTH, Constants.OUTPUT_HEIGHT))
                try {
                    images.save(out)
                } finally {
                    out.recycle()
                }
            } finally {
                src.recycle()
            }
        }
        prefs.edit { putString(key, name) }
        return name
    }

    // ------------------------------------------------------------------ favourites

    fun favorites(): List<CatalogItem> = prefs.all.filterKeys { it.startsWith("fav:") }
        .values.mapNotNull { (it as? String)?.let(::decode) }
        .sortedWith { a, b -> collator.compare(a.title, b.title) }

    fun isFavorite(item: CatalogItem): Boolean = prefs.contains("fav:" + item.key)

    fun setFavorite(item: CatalogItem, favorite: Boolean) {
        prefs.edit {
            if (favorite) putString(
                "fav:" + item.key,
                encode(item)
            ) else remove("fav:" + item.key)
        }
    }

    // ------------------------------------------------------------------ cache files

    private fun readCache(file: File): List<CatalogItem>? {
        if (!file.isFile || System.currentTimeMillis() - file.lastModified() > CACHE_TTL_MS) return null
        return runCatching {
            val a = JSONArray(file.readText())
            (0 until a.length()).mapNotNull { decode(a.getString(it)) }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun writeCache(file: File, items: List<CatalogItem>) {
        val a = JSONArray()
        items.forEach { a.put(encode(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(a.toString())
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    private fun encode(i: CatalogItem): String = JSONObject()
        .put("id", i.id).put("c", i.category.name).put("t", i.title).put("g", i.group).put("s", i.source).put("u", i.imageUrl)
        .toString()

    private fun decode(raw: String): CatalogItem? = runCatching {
        val o = JSONObject(raw)
        CatalogItem(
            id = o.getString("id"),
            category = CatalogCategory.valueOf(o.getString("c")),
            title = o.optString("t"),
            group = o.optString("g"),
            source = o.optString("s"),
            imageUrl = normalizeImageUrl(o.getString("u")) ?: return@runCatching null,
        )
    }.getOrNull()

    companion object {
        private const val API_HOST = "hi.my-api.cn"
        private const val IMAGE_HOST = "storage.my-api.cn"
        private const val USER_AGENT = "NfcCardSkin/1.0"
        private const val CACHE_TTL_MS = 7L * 24 * 3600 * 1000
        private const val MAX_IMAGE_BYTES = 16L * 1024 * 1024

        /**
         * Only https://storage.my-api.cn/static/images/… is accepted. Transit / IP paths come with raw
         * Chinese characters, bank paths already percent-encoded: encode only what is not ASCII.
         */
        fun normalizeImageUrl(raw: String): String? = runCatching {
            val s = raw.trim()
            require(s.length in 1..2048 && s.none { it.code < 0x20 })
            val encoded = buildString {
                var i = 0
                while (i < s.length) {
                    val cp = s.codePointAt(i)
                    if (cp < 0x80 && cp != ' '.code) {
                        append(cp.toChar())
                    } else {
                        String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).forEach { append("%%%02X".format(it.toInt() and 0xFF)) }
                    }
                    i += Character.charCount(cp)
                }
            }
            val uri = URI(encoded)
            require(uri.scheme.equals("https", true) && uri.host.equals(IMAGE_HOST, true))
            require(uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) && uri.rawFragment == null)
            require(uri.rawPath.startsWith("/static/images/") && !uri.rawPath.split('/').contains(".."))
            uri.toASCIIString()
        }.getOrNull()

        /** Thumbnail through the wsrv.nl image proxy (small WebP, ~20 KB instead of ~1 MB). */
        fun thumbnailUrl(imageUrl: String, width: Int): String =
            "https://wsrv.nl/?url=" + URLEncoder.encode(imageUrl, "UTF-8") + "&w=$width&output=webp&q=70&we"

        fun centerCrop(w: Int, h: Int): RectF {
            val ratio = Constants.CARD_RATIO
            return if (w.toFloat() / h > ratio) {
                val cw = h * ratio
                RectF((w - cw) / 2f, 0f, (w + cw) / 2f, h.toFloat())
            } else {
                val ch = w / ratio
                RectF(0f, (h - ch) / 2f, w.toFloat(), (h + ch) / 2f)
            }
        }

        fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
