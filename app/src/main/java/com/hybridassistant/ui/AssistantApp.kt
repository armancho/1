package com.hybridassistant.ui

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hybridassistant.data.AppContainer
import com.hybridassistant.data.AssistantState
import com.hybridassistant.data.ChatMessage
import com.hybridassistant.data.LlmModel
import com.hybridassistant.data.Speaker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AssistantApp(
    appContainer: AppContainer,
    openAccessibilitySettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state by appContainer.repository.state.collectAsState(initial = AssistantState())

    var selectedTab by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var apiKeyInput by remember(state.openRouterApiKey) { mutableStateOf(state.openRouterApiKey) }
    var cloudModelInput by remember(state.cloudModel) { mutableStateOf(state.cloudModel) }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) input = text
        }
    }

    LaunchedEffect(state.models) {
        while (true) {
            state.models.filter { it.downloadId != null && it.isDownloading }.forEach { model ->
                val progress = appContainer.downloadManager.queryProgress(model.downloadId!!)
                if (progress != null) {
                    when (progress.status) {
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_PAUSED -> {
                            appContainer.repository.updateDownloadProgress(
                                model.id,
                                progress.progress,
                                progress.status != DownloadManager.STATUS_PAUSED || progress.reason == DownloadManager.PAUSED_WAITING_TO_RETRY || progress.reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK
                            )
                        }

                        DownloadManager.STATUS_SUCCESSFUL -> {
                            appContainer.repository.installModel(model.id)
                        }

                        DownloadManager.STATUS_FAILED -> {
                            appContainer.repository.updateDownloadProgress(model.id, progress.progress, false)
                        }
                    }
                }
            }
            delay(1200)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("HybridAssistant") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Чат") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Модели") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Разрешения") })
            }

            when (selectedTab) {
                0 -> ChatTab(state, input, onInput = { input = it }, onVoice = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    }
                    speechLauncher.launch(intent)
                }, onSend = {
                    if (input.isBlank()) return@ChatTab
                    val prompt = input
                    input = ""
                    scope.launch {
                        appContainer.repository.addMessage(ChatMessage(speaker = Speaker.USER, text = prompt))
                        val result = appContainer.llmEngine.generateResponse(
                            prompt = prompt,
                            selectedModel = state.selectedModel,
                            openRouterApiKey = state.openRouterApiKey,
                            cloudModel = state.cloudModel
                        )
                        result.deviceCommand?.let(appContainer.deviceActionExecutor::execute)
                        appContainer.repository.addMessage(ChatMessage(speaker = Speaker.ASSISTANT, text = result.answer))
                        appContainer.voiceAssistant.speak(result.answer)
                    }
                })

                1 -> ModelsTab(
                    state = state,
                    apiKeyInput = apiKeyInput,
                    cloudModelInput = cloudModelInput,
                    onApiKeyInput = { apiKeyInput = it },
                    onCloudModelInput = { cloudModelInput = it },
                    onSaveCloudSettings = {
                        scope.launch {
                            appContainer.repository.saveOpenRouterApiKey(apiKeyInput.trim())
                            appContainer.repository.saveCloudModel(cloudModelInput.trim())
                        }
                    },
                    onDownload = { model ->
                        scope.launch {
                            val id = appContainer.downloadManager.enqueueModelDownload(model)
                            appContainer.repository.markModelDownload(model.id, id)
                        }
                    },
                    onSelect = { id ->
                        scope.launch { appContainer.repository.selectModel(id) }
                    },
                    onDelete = { model ->
                        scope.launch {
                            model.downloadId?.let(appContainer.downloadManager::removeDownload)
                            appContainer.repository.removeModel(model.id)
                        }
                    }
                )

                else -> PermissionsTab(onOpenAccessibility = openAccessibilitySettings)
            }
        }
    }
}

@Composable
private fun ChatTab(
    state: AssistantState,
    input: String,
    onInput: (String) -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.78f).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.chatMessages) { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(if (message.speaker == Speaker.USER) "Вы" else "Ассистент", style = MaterialTheme.typography.labelLarge)
                    Text(message.text)
                    IconButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
            }
        }
    }

    HorizontalDivider()

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(0.75f),
            placeholder = { Text("Введите команду") }
        )
        IconButton(onClick = onVoice) { Icon(Icons.Default.Mic, contentDescription = "Voice") }
        Button(onClick = onSend) { Text("Send") }
    }
}

@Composable
private fun ModelsTab(
    state: AssistantState,
    apiKeyInput: String,
    cloudModelInput: String,
    onApiKeyInput: (String) -> Unit,
    onCloudModelInput: (String) -> Unit,
    onSaveCloudSettings: () -> Unit,
    onDownload: (LlmModel) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (LlmModel) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cloud LLM (реальные ответы)", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = apiKeyInput, onValueChange = onApiKeyInput, label = { Text("OpenRouter API Key") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cloudModelInput, onValueChange = onCloudModelInput, label = { Text("Cloud model (например openai/gpt-4o-mini)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = onSaveCloudSettings) { Text("Сохранить Cloud настройки") }
                }
            }
        }

        items(state.models) { model ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Text("Размер: ${model.sizeLabel}")
                    Text("URL: ${model.downloadUrl}")

                    if (model.isDownloading || (model.downloadProgress in 1..99)) {
                        LinearProgressIndicator(progress = model.downloadProgress / 100f, modifier = Modifier.fillMaxWidth())
                        Text("Прогресс: ${model.downloadProgress}% (при потере интернета загрузка продолжится автоматически через DownloadManager)")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!model.installed) {
                            Button(onClick = { onDownload(model) }) { Text(if (model.isDownloading) "Скачивается..." else "Скачать") }
                        } else {
                            Button(onClick = { onSelect(model.id) }) {
                                Text(if (state.selectedModel == model.id) "Активна" else "Выбрать")
                            }
                            Button(onClick = { onDelete(model) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsTab(onOpenAccessibility: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Для управления кнопками/ссылками в других приложениях включите Accessibility Service.")
        Button(onClick = onOpenAccessibility) { Text("Открыть настройки Accessibility") }
    }
}
