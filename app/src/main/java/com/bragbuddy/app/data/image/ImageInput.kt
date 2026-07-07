package com.bragbuddy.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Turns a captured image (a camera or gallery [Uri]) into a base64 `data:` URL small enough for
 * Groq vision. Groq caps a base64 image at **4 MB**; base64 inflates bytes by ~33%, so we target
 * ≤3 MB of JPEG and downscale the longest side to [MAX_DIMEN] (plenty for OCR, far under Groq's
 * 33 MP cap). Pure Android framework — no image-loading dependency.
 *
 * Privacy: the image is only ever held in memory here and sent to Groq to read — it is **never
 * stored** (consistent with audio; the offline queue keeps clips, images are online-only).
 */
object ImageInput {
    private const val MAX_DIMEN = 1600          // longest side in px
    private const val TARGET_BYTES = 2_600_000  // JPEG bytes before base64 (~33% inflation → ~3.5 MB < 4 MB)
    private const val MIN_QUALITY = 40

    /**
     * Read [uri], downscale + JPEG-compress under the size cap, and return a
     * `data:image/jpeg;base64,…` URL — or null if the image can't be decoded (unsupported / corrupt /
     * unreadable stream). Does blocking I/O and bitmap work; call off the main thread.
     */
    fun toDataUrl(context: Context, uri: Uri): String? {
        val bitmap = runCatching { decodeScaled(context, uri) }.getOrNull() ?: return null
        return try {
            val jpeg = compressUnder(bitmap, TARGET_BYTES)
            "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        } catch (t: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** Decode [uri] downscaled so its longest side is ~[MAX_DIMEN] px, using a bounds pass first so a
     *  huge photo is never fully loaded into memory. Returns null if it can't be decoded. */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > MAX_DIMEN * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val longestNow = maxOf(decoded.width, decoded.height)
        if (longestNow <= MAX_DIMEN) return decoded
        val scale = MAX_DIMEN.toFloat() / longestNow
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /** JPEG-encode, dropping quality until the bytes fit [targetBytes] (or quality bottoms out). */
    private fun compressUnder(bitmap: Bitmap, targetBytes: Int): ByteArray {
        var quality = 90
        var bytes = bitmap.toJpeg(quality)
        while (bytes.size > targetBytes && quality > MIN_QUALITY) {
            quality -= 15
            bytes = bitmap.toJpeg(quality)
        }
        return bytes
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().use { compress(Bitmap.CompressFormat.JPEG, quality, it); it.toByteArray() }
}
