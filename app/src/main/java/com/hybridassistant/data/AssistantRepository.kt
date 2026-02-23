package com.hybridassistant.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

private val Context.dataStore by preferencesDataStore(name = "assistant_settings")

class AssistantRepository(private val context: Context) {

    private val chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val downloadingModels = MutableStateFlow<Set<String>>(emptySet())

    private val installedModelsKey = stringPreferencesKey("installed_models")
    private val selectedModelKey = stringPreferencesKey("selected_model")
    private val modelDownloadsKey = stringPreferencesKey("model_downloads")
    private val openRouterApiKeyKey = stringPreferencesKey("openrouter_api_key")
    private val cloudModelKey = stringPreferencesKey("cloud_model")

    private val catalog = listOf(
        LlmModel("llama3.1_8b", "Llama 3.1 8B Instruct Q4", "~4.7 GB", "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf?download=true"),
        LlmModel("mistral_7b_v03", "Mistral 7B Instruct v0.3 Q4", "~4.4 GB", "https://huggingface.co/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf?download=true"),
        LlmModel("qwen2.5_7b", "Qwen2.5 7B Instruct Q4", "~4.8 GB", "https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF/resolve/main/Qwen2.5-7B-Instruct-Q4_K_M.gguf?download=true"),
        LlmModel("gemma2_9b", "Gemma 2 9B Instruct Q4", "~5.4 GB", "https://huggingface.co/bartowski/gemma-2-9b-it-GGUF/resolve/main/gemma-2-9b-it-Q4_K_M.gguf?download=true"),
        LlmModel("phi3_medium", "Phi-3 Medium 14B Q4", "~8.2 GB", "https://huggingface.co/bartowski/Phi-3-medium-4k-instruct-GGUF/resolve/main/Phi-3-medium-4k-instruct-Q4_K_M.gguf?download=true"),
        LlmModel("deepseek_7b", "DeepSeek LLM 7B Chat Q4", "~4.6 GB", "https://huggingface.co/TheBloke/deepseek-llm-7b-chat-GGUF/resolve/main/deepseek-llm-7b-chat.Q4_K_M.gguf?download=true"),
        LlmModel("openchat_7b", "OpenChat 3.5 7B Q4", "~4.3 GB", "https://huggingface.co/TheBloke/openchat_3.5-GGUF/resolve/main/openchat_3.5.Q4_K_M.gguf?download=true"),
        LlmModel("yi_9b", "Yi 9B Chat Q4", "~5.6 GB", "https://huggingface.co/TheBloke/Yi-1.5-9B-Chat-GGUF/resolve/main/yi-1.5-9b-chat.Q4_K_M.gguf?download=true"),
        LlmModel("mixtral_8x7b", "Mixtral 8x7B Instruct Q4", "~26 GB", "https://huggingface.co/TheBloke/Mixtral-8x7B-Instruct-v0.1-GGUF/resolve/main/mixtral-8x7b-instruct-v0.1.Q4_K_M.gguf?download=true"),
        LlmModel("llama3.1_70b", "Llama 3.1 70B Instruct Q4", "~40 GB", "https://huggingface.co/bartowski/Meta-Llama-3.1-70B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-70B-Instruct-Q4_K_M.gguf?download=true")
    )

    val state: Flow<AssistantState> = combine(
        context.dataStore.data,
        chatMessages,
        downloadProgress,
        downloadingModels
    ) { pref, messages, progress, downloading ->
        val installed = pref[installedModelsKey].orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        val downloads = parseDownloads(pref[modelDownloadsKey].orEmpty())

        val models = catalog.map { model ->
            model.copy(
                installed = model.id in installed,
                downloadId = downloads[model.id],
                downloadProgress = progress[model.id] ?: if (model.id in installed) 100 else 0,
                isDownloading = model.id in downloading
            )
        }

        AssistantState(
            models = models,
            selectedModel = pref[selectedModelKey],
            chatMessages = messages,
            openRouterApiKey = pref[openRouterApiKeyKey].orEmpty(),
            cloudModel = pref[cloudModelKey].orEmpty().ifBlank { "openai/gpt-4o-mini" }
        )
    }

    suspend fun markModelDownload(modelId: String, downloadId: Long) {
        context.dataStore.edit { pref ->
            val map = parseDownloads(pref[modelDownloadsKey].orEmpty()).toMutableMap()
            map[modelId] = downloadId
            pref[modelDownloadsKey] = map.entries.joinToString(";") { "${it.key}:${it.value}" }
        }
        downloadingModels.value = downloadingModels.value + modelId
        updateDownloadProgress(modelId, 1, true)
    }

    fun updateDownloadProgress(modelId: String, progress: Int, downloading: Boolean) {
        downloadProgress.value = downloadProgress.value.toMutableMap().apply { put(modelId, progress.coerceIn(0, 100)) }
        downloadingModels.value = if (downloading) downloadingModels.value + modelId else downloadingModels.value - modelId
    }

    suspend fun installModel(id: String) {
        context.dataStore.edit { pref ->
            val existing = pref[installedModelsKey].orEmpty().split(',').filter { it.isNotBlank() }.toMutableSet()
            existing.add(id)
            pref[installedModelsKey] = existing.joinToString(",")
            if (pref[selectedModelKey].isNullOrBlank()) pref[selectedModelKey] = id
        }
        updateDownloadProgress(id, 100, false)
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
        updateDownloadProgress(id, 0, false)
    }

    suspend fun selectModel(id: String) {
        context.dataStore.edit { pref -> pref[selectedModelKey] = id }
    }

    suspend fun saveOpenRouterApiKey(value: String) {
        context.dataStore.edit { pref -> pref[openRouterApiKeyKey] = value }
    }

    suspend fun saveCloudModel(value: String) {
        context.dataStore.edit { pref -> pref[cloudModelKey] = value }
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
    val chatMessages: List<ChatMessage> = emptyList(),
    val openRouterApiKey: String = "",
    val cloudModel: String = "openai/gpt-4o-mini"
)
