package com.bragbuddy.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Turns a captured image (a camera or gallery [Uri]) into a base64 `data:` URL small enough for
 * Groq vision. Groq caps a base64 image at **4 MB**; base64 inflates bytes by ~33%, so we target
 * ≤3 MB of JPEG and downscale the longest side to [MAX_DIMEN] (plenty for OCR, far under Groq's
 * 33 MP cap). Pure Android framework — no image-loading dependency.
 *
 * Privacy: the image is only ever held in memory here and sent to Groq to read online — it is not
 * retained by us. The **one** exception is the offline image-scan queue (M2): when a scan can't reach
 * Groq (offline / transport failure) the downscaled JPEG is written to an app-private queue file via
 * [compressToFile] so the capture isn't lost, then [OfflineRecovery] reads it with [fileToDataUrl]
 * when the network returns and **deletes it right after** — never backed up, device-local only.
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
        val jpeg = toJpegBytes(context, uri) ?: return null
        return "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
    }

    /** The shared decode → downscale → JPEG-compress-under-cap step. Null if the image can't be
     *  decoded. Does blocking I/O + bitmap work; call off the main thread. */
    fun toJpegBytes(context: Context, uri: Uri): ByteArray? {
        val bitmap = runCatching { decodeScaled(context, uri) }.getOrNull() ?: return null
        return try {
            compressUnder(bitmap, TARGET_BYTES)
        } catch (t: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Offline queue (M2): downscale + compress [uri] and write the JPEG bytes to [dest]. Returns true
     * on success. The written file is a valid, size-capped JPEG ready to re-send with [fileToDataUrl].
     * Call off the main thread.
     */
    fun compressToFile(context: Context, uri: Uri, dest: File): Boolean {
        val jpeg = toJpegBytes(context, uri) ?: return false
        return runCatching { dest.writeBytes(jpeg); dest.length() > 0 }.getOrDefault(false)
    }

    /**
     * Offline queue (M2): write an already-built `data:image/jpeg;base64,…` [dataUrl] straight to
     * [dest] (decode the base64 back to the compressed JPEG bytes — no second decode/re-compress of the
     * source, and the source [Uri] need not still be readable). Returns true on success.
     */
    fun dataUrlToFile(dataUrl: String, dest: File): Boolean {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return false
        return runCatching {
            val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.NO_WRAP)
            if (bytes.isEmpty()) return false
            dest.writeBytes(bytes)
            dest.length() > 0
        }.getOrDefault(false)
    }

    /** Offline queue (M2): re-read a queued JPEG [file] into a `data:` URL for Groq vision. Null if
     *  the file is missing/empty/unreadable. Call off the main thread. */
    fun fileToDataUrl(file: File): String? {
        if (!file.exists() || file.length() == 0L) return null
        val bytes = runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
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
