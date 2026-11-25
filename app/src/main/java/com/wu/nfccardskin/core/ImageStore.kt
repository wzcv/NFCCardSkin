package com.wu.nfccardskin.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

/** Module gallery in `files/images`, plus decoding / cropping helpers. */
class ImageStore(private val context: Context) {

    val dir: File = File(context.filesDir, "images").apply { mkdirs() }

    fun list(): List<GalleryImage> = (dir.listFiles() ?: emptyArray())
        .filter { it.isFile && isSafeName(it.name) }
        .sortedByDescending { it.lastModified() }
        .map { GalleryImage(it.name, it.absolutePath, it.lastModified(), it.length()) }

    fun find(name: String): File? {
        if (!isSafeName(name)) return null
        val f = File(dir, name)
        return if (f.isFile) f else null
    }

    fun delete(name: String): Boolean = find(name)?.delete() ?: false

    /** Saves [bitmap] as PNG and returns the new file name. */
    fun save(bitmap: Bitmap): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        var name = "card-$stamp.png"
        var i = 1
        while (File(dir, name).exists()) name = "card-$stamp-${i++}.png"
        val tmp = File(dir, "$name.tmp")
        tmp.outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encode failed" }
        }
        check(tmp.renameTo(File(dir, name))) { "rename failed" }
        return name
    }

    companion object {
        fun isSafeName(name: String): Boolean =
            name.isNotEmpty() && name.length <= 128 && !name.contains('/') && !name.contains('\\') &&
                !name.contains("..") && !name.endsWith(".tmp") && name.none { it.code < 0x20 }

        /**
         * Decodes [uri] with the long side capped at [maxSide] (ImageDecoder applies EXIF rotation
         * and samples while decoding, so a 50 MP photo never lands in memory at full size).
         */
        fun decode(context: Context, uri: Uri, maxSide: Int = 2600): Bitmap {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxSide.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }

        /**
         * Crops [crop] (in [src] pixels) and scales it to [outW] x [outH]. Large reductions are done
         * in halving steps with bilinear filtering, which avoids the aliasing of a single big
         * `createScaledBitmap` (the original DIY module's main quality problem).
         */
        fun cropAndScale(src: Bitmap, crop: RectF, outW: Int, outH: Int): Bitmap {
            val r = Rect(
                crop.left.roundToInt().coerceIn(0, src.width - 1),
                crop.top.roundToInt().coerceIn(0, src.height - 1),
                crop.right.roundToInt().coerceIn(1, src.width),
                crop.bottom.roundToInt().coerceIn(1, src.height),
            )
            var cur = Bitmap.createBitmap(src, r.left, r.top, r.width().coerceAtLeast(1), r.height().coerceAtLeast(1))
            while (cur.width >= outW * 2 && cur.height >= outH * 2) {
                val next = cur.scale(cur.width / 2, cur.height / 2)
                if (cur !== src) cur.recycle()
                cur = next
            }
            val out = createBitmap(outW, outH)
            val m = Matrix().apply { setScale(outW.toFloat() / cur.width, outH.toFloat() / cur.height) }
            Canvas(out).drawBitmap(cur, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
            if (cur !== src) cur.recycle()
            return out
        }

        /**
         * Finishes a card face in place: optional rounded corners (radius scaled from the DIY
         * module's 40 px at 960 px width) and an optional logo stretched over the whole face.
         */
        fun finish(face: Bitmap, logo: Bitmap?, roundCorners: Boolean): Bitmap {
            var out = if (face.isMutable) face else face.copy(Bitmap.Config.ARGB_8888, true)
            if (roundCorners) {
                val r = out.width * 40f / 960f
                val rounded = createBitmap(out.width, out.height)
                val c = Canvas(rounded)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                c.drawRoundRect(RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), r, r, p)
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                c.drawBitmap(out, 0f, 0f, p)
                out.recycle()
                out = rounded
            }
            if (logo != null) {
                val m = Matrix().apply { setScale(out.width.toFloat() / logo.width, out.height.toFloat() / logo.height) }
                Canvas(out).drawBitmap(logo, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            return out
        }
    }
}
