package com.wu.nfccardskin.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Card face marks (交通联合 / 互联互通 logos…) drawn over a picture, as in the original DIY module.
 * Built-in PNGs live in assets/card_logo (from the DIY module, 960 x 606, transparent); users can
 * import their own transparent PNGs into files/logos.
 */
@Immutable
data class CardLogo(val id: String, val label: String, val builtIn: Boolean)

class LogoStore(private val context: Context) {

    private val dir = File(context.filesDir, "logos").apply { mkdirs() }

    fun list(): List<CardLogo> {
        val builtIn = (context.assets.list(ASSET_DIR) ?: emptyArray()).sorted().map {
            CardLogo(id = "asset:$it", label = BUILT_IN_LABELS[it] ?: it.substringBeforeLast('.'), builtIn = true)
        }
        val user = (dir.listFiles() ?: emptyArray()).filter { it.isFile }.sortedBy { it.lastModified() }.map {
            CardLogo(id = "file:${it.name}", label = it.name.substringBeforeLast('.'), builtIn = false)
        }
        return builtIn + user
    }

    fun load(id: String): Bitmap? = try {
        when {
            id.startsWith("asset:") -> context.assets.open("$ASSET_DIR/${id.removePrefix("asset:")}").use { BitmapFactory.decodeStream(it) }
            id.startsWith("file:") -> {
                val name = id.removePrefix("file:")
                if (ImageStore.isSafeName(name)) BitmapFactory.decodeFile(File(dir, name).absolutePath) else null
            }
            else -> null
        }
    } catch (_: Throwable) {
        null
    }

    /** Imports a transparent PNG (any size; it is stretched over the whole card face). */
    fun import(uri: Uri): CardLogo {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            val longSide = maxOf(info.size.width, info.size.height)
            if (longSide > 2000) {
                val s = 2000f / longSide
                d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        var name = "logo-${System.currentTimeMillis()}.png"
        while (File(dir, name).exists()) name = "logo-${System.currentTimeMillis() + 1}.png"
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return CardLogo("file:$name", name.substringBeforeLast('.'), builtIn = false)
    }

    fun delete(id: String) {
        if (!id.startsWith("file:")) return
        val name = id.removePrefix("file:")
        if (ImageStore.isSafeName(name)) File(dir, name).delete()
    }

    companion object {
        private const val ASSET_DIR = "card_logo"
        private val BUILT_IN_LABELS = mapOf(
            "jingjinji.png" to "京津冀互联互通卡",
            "shanghai.png" to "上海公共交通卡 交通联合版",
            "qvanchengtong.png" to "泉城通",
            "yinchuan.png" to "银川交通一卡通",
        )
    }
}
