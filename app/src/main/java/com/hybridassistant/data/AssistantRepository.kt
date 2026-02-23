package com.hybridassistant.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "assistant_settings")

class AssistantRepository(private val context: Context) {

    private val chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val installedModelsKey = stringPreferencesKey("installed_models")
    private val selectedModelKey = stringPreferencesKey("selected_model")
    private val modelDownloadsKey = stringPreferencesKey("model_downloads")

    private val catalog = listOf(
        LlmModel("llama3_8b", "Llama 3 8B", "4.7 GB", "https://huggingface.co/example/llama3-8b/resolve/main/model.gguf"),
        LlmModel("llama3_70b", "Llama 3 70B", "40 GB", "https://huggingface.co/example/llama3-70b/resolve/main/model.gguf"),
        LlmModel("mistral_7b", "Mistral 7B", "4.1 GB", "https://huggingface.co/example/mistral-7b/resolve/main/model.gguf"),
        LlmModel("mixtral_8x7b", "Mixtral 8x7B", "26 GB", "https://huggingface.co/example/mixtral-8x7b/resolve/main/model.gguf"),
        LlmModel("phi3_mini", "Phi-3 Mini", "2.2 GB", "https://huggingface.co/example/phi3-mini/resolve/main/model.gguf"),
        LlmModel("gemma2_9b", "Gemma 2 9B", "5.3 GB", "https://huggingface.co/example/gemma2-9b/resolve/main/model.gguf"),
        LlmModel("qwen2_7b", "Qwen2 7B", "4.8 GB", "https://huggingface.co/example/qwen2-7b/resolve/main/model.gguf"),
        LlmModel("yi_9b", "Yi 9B", "5.5 GB", "https://huggingface.co/example/yi-9b/resolve/main/model.gguf"),
        LlmModel("deepseek_7b", "DeepSeek 7B", "4.5 GB", "https://huggingface.co/example/deepseek-7b/resolve/main/model.gguf"),
        LlmModel("openchat_7b", "OpenChat 7B", "4.2 GB", "https://huggingface.co/example/openchat-7b/resolve/main/model.gguf")
    )

    val state: Flow<AssistantState> = combine(
        context.dataStore.data,
        chatMessages
    ) { pref, messages ->
        val installed = pref[installedModelsKey]
            .orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .toSet()

        val downloads = parseDownloads(pref[modelDownloadsKey].orEmpty())

        val models = catalog.map { model ->
            model.copy(
                installed = model.id in installed,
                downloadId = downloads[model.id]
            )
        }

        AssistantState(
            models = models,
            selectedModel = pref[selectedModelKey],
            chatMessages = messages
        )
    }

    suspend fun markModelDownload(modelId: String, downloadId: Long) {
        context.dataStore.edit { pref ->
            val map = parseDownloads(pref[modelDownloadsKey].orEmpty()).toMutableMap()
            map[modelId] = downloadId
            pref[modelDownloadsKey] = map.entries.joinToString(";") { "${it.key}:${it.value}" }
        }
    }

    suspend fun installModel(id: String) {
        context.dataStore.edit { pref ->
            val existing = pref[installedModelsKey].orEmpty().split(',').filter { it.isNotBlank() }.toMutableSet()
            existing.add(id)
            pref[installedModelsKey] = existing.joinToString(",")
            if (pref[selectedModelKey].isNullOrBlank()) pref[selectedModelKey] = id
        }
    }

    suspend fun removeModel(id: String) {
        context.dataStore.edit { pref ->
            val existing = pref[installedModelsKey].orEmpty().split(',').filter { it.isNotBlank() }.toMutableSet()
            existing.remove(id)
            pref[installedModelsKey] = existing.joinToString(",")
            if (pref[selectedModelKey] == id) pref[selectedModelKey] = existing.firstOrNull().orEmpty()
            val map = parseDownloads(pref[modelDownloadsKey].orEmpty()).toMutableMap()
            map.remove(id)
            pref[modelDownloadsKey] = map.entries.joinToString(";") { "${it.key}:${it.value}" }
        }
    }

    suspend fun selectModel(id: String) {
        context.dataStore.edit { pref -> pref[selectedModelKey] = id }
    }

    fun addMessage(message: ChatMessage) {
        chatMessages.value = chatMessages.value + message
    }

    private fun parseDownloads(raw: String): Map<String, Long> {
        return raw.split(';').mapNotNull { token ->
            val parts = token.split(':')
            if (parts.size != 2) null else parts[0] to parts[1].toLongOrNull()
        }.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
    }
}

data class AssistantState(
    val models: List<LlmModel> = emptyList(),
    val selectedModel: String? = null,
    val chatMessages: List<ChatMessage> = emptyList()
)
