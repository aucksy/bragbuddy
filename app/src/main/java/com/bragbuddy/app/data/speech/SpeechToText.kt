package com.bragbuddy.app.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Events emitted by [SpeechToText] while listening. */
sealed interface SttEvent {
    data object Ready : SttEvent
    /** Live, not-yet-final text — updates as the user speaks. */
    data class Partial(val text: String) : SttEvent
    /** Microphone loudness in dB (roughly -2..10), for the waveform. */
    data class Rms(val db: Float) : SttEvent
    /** The recognizer's final transcript for this utterance. */
    data class Final(val text: String) : SttEvent
    data object EndOfSpeech : SttEvent
    data class Error(val code: Int, val message: String) : SttEvent
}

/**
 * Thin wrapper over Android's on-device [SpeechRecognizer]. Prefers offline recognition (keeps the
 * audio on the phone, per the privacy rule); if the device can't, the error path lets the UI fall
 * back to typing. Must be created and driven on the main thread.
 */
class SpeechToText(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onEvent: (SttEvent) -> Unit) {
        stopInternal()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        listening = true
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onEvent(SttEvent.Ready)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = onEvent(SttEvent.Rms(rmsdB))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onEvent(SttEvent.EndOfSpeech)
            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { onEvent(SttEvent.Partial(it)) }
            }
            override fun onResults(results: Bundle?) {
                onEvent(SttEvent.Final(firstResult(results).orEmpty()))
            }
            override fun onError(error: Int) {
                listening = false
                onEvent(SttEvent.Error(error, errorMessage(error)))
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            // Don't cut off on a brief pause mid-thought.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        runCatching { r.startListening(intent) }.onFailure {
            listening = false
            onEvent(SttEvent.Error(-1, it.message ?: "Couldn't start listening"))
        }
    }

    /** Ask the recognizer to finalize (delivers onResults). */
    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun cancelAndRelease() = stopInternal()

    private fun stopInternal() {
        listening = false
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything"
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network needed for this device's speech engine"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "On-device language pack unavailable"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy — try again"
        else -> "Speech recognition unavailable"
    }
}
