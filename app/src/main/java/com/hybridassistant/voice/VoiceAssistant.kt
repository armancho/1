package com.hybridassistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAssistant(context: Context) {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru")
            }
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_reply")
    }
}
