package com.wu.nfccardskin.core

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File
import java.io.FileNotFoundException
import androidx.core.graphics.createBitmap

/**
 * `content://com.wu.nfccardskin.provider`
 *  - `images/<name>`: read-only gallery image for the wallet's Glide (no root needed: the wallet
 *    holds QUERY_ALL_PACKAGES so it can resolve this provider).
 *  - `test/card.png`: generated test card face (test mode).
 *  - call `get_config` / `report` / `hello`: used by the hook.
 * Only this app and the wallet are served.
 */
class SkinProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        checkCaller()
        val repo = Repository.init(requireNotNull(context))
        return when (method) {
            Constants.CALL_GET_CONFIG -> Bundle().apply { putString(Constants.EXTRA_JSON, repo.buildConfig().toJson()) }
            Constants.CALL_REPORT -> {
                val e = extras ?: return null
                val url = e.getString(Constants.EXTRA_URL) ?: return null
                repo.onReport(url, e.getString(Constants.EXTRA_TITLE), e.getString(Constants.EXTRA_SOURCE).orEmpty())
                null
            }
            Constants.CALL_HELLO -> {
                repo.onHello(
                    extras?.getString(Constants.EXTRA_VERSION).orEmpty(),
                    extras?.getString(Constants.EXTRA_PROCESS).orEmpty(),
                    extras?.getInt(Constants.EXTRA_HOOKS) ?: 0,
                )
                null
            }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        checkCaller()
        if (mode != "r") throw SecurityException("read-only")
        val segments = uri.pathSegments
        val file = when (segments.size) {
            2 if segments[0] == "images" ->
                Repository.init(requireNotNull(context)).images.find(segments[1])

            2 if segments[0] == "test" -> testImage()
            else -> null
        } ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    private fun checkCaller() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return
        val ctx = requireNotNull(context)
        val walletUid = runCatching { ctx.packageManager.getPackageUid(Constants.WALLET_PACKAGE, 0) }.getOrNull()
        if (walletUid != null && uid == walletUid) return
        throw SecurityException("caller $uid not allowed")
    }

    private fun testImage(): File {
        val ctx = requireNotNull(context)
        val f = File(ctx.cacheDir, "test_card.png")
        if (f.isFile) return f
        val w = Constants.OUTPUT_WIDTH
        val h = Constants.OUTPUT_HEIGHT
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        // Test artwork only, not UI: a fixed brand-free gradient with a label.
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF2F6BFF.toInt(), 0xFF00C2A8.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = h / 8f; isFakeBoldText = true }
        c.drawText("NFC Card Skin", w * 0.08f, h * 0.55f, text)
        text.textSize = h / 16f
        text.isFakeBoldText = false
        c.drawText("TEST MODE", w * 0.08f, h * 0.68f, text)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f
    }
}
