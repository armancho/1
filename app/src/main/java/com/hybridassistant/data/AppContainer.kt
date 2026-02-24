package com.hybridassistant.data

import android.content.Context
import com.hybridassistant.device.DeviceActionExecutor
import com.hybridassistant.download.ModelDownloadManager
import com.hybridassistant.llm.LlmEngine
import com.hybridassistant.voice.VoiceAssistant

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val repository = AssistantRepository(appContext)
    val llmEngine = LlmEngine()
    val deviceActionExecutor = DeviceActionExecutor(appContext)
    val voiceAssistant = VoiceAssistant(appContext)
    val downloadManager = ModelDownloadManager(appContext)
}
