package com.bragbuddy.app.data.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records mic audio to an `.m4a` (AAC/MP4) file for cloud transcription (Groq/OpenAI Whisper accept
 * m4a). Also exposes [maxAmplitude] so the capture UI can draw a live waveform. Main-thread driven.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** Start recording to a fresh temp file in the cache dir; returns that file. */
    @Suppress("DEPRECATION")
    fun start(): File {
        releaseInternal()
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.m4a")
        outputFile = file
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        recorder = r
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        return file
    }

    /** Peak amplitude since the last call (0..32767) — for the waveform. */
    fun maxAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    /** Stop and finalize; returns the recorded file (or null if nothing/too short). Ownership of
     *  the file transfers to the caller — [outputFile] is cleared so a later [cancel] can only ever
     *  delete an in-progress recording, never a finished take being retained for retry/queueing. */
    fun stop(): File? {
        val file = outputFile
        outputFile = null
        runCatching { recorder?.stop() }
        releaseInternal()
        return file?.takeIf { it.exists() && it.length() > 0 }
    }

    /** Abort without keeping the file (no-op on an already-stopped take — see [stop]). */
    fun cancel() {
        runCatching { recorder?.stop() }
        releaseInternal()
        outputFile?.delete()
        outputFile = null
    }

    private fun releaseInternal() {
        recorder?.let { runCatching { it.reset() }; runCatching { it.release() } }
        recorder = null
    }
}
