package com.hybridassistant.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.hybridassistant.data.Speaker
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

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) input = text
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
                    appContainer.repository.addMessage(ChatMessage(speaker = Speaker.USER, text = prompt))
                    val result = appContainer.llmEngine.generateResponse(prompt, state.selectedModel)
                    result.deviceCommand?.let(appContainer.deviceActionExecutor::execute)
                    appContainer.repository.addMessage(ChatMessage(speaker = Speaker.ASSISTANT, text = result.answer))
                    appContainer.voiceAssistant.speak(result.answer)
                })

                1 -> ModelsTab(state, onDownload = { model ->
                    scope.launch {
                        val id = appContainer.downloadManager.enqueueModelDownload(model)
                        appContainer.repository.markModelDownload(model.id, id)
                        appContainer.repository.installModel(model.id)
                    }
                }, onSelect = { id ->
                    scope.launch { appContainer.repository.selectModel(id) }
                }, onDelete = { model ->
                    scope.launch {
                        model.downloadId?.let(appContainer.downloadManager::removeDownload)
                        appContainer.repository.removeModel(model.id)
                    }
                })

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
        modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
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
            modifier = Modifier.weight(1f),
            placeholder = { Text("Введите команду") }
        )
        IconButton(onClick = onVoice) { Icon(Icons.Default.Mic, contentDescription = "Voice") }
        Button(onClick = onSend) { Text("Send") }
    }
}

@Composable
private fun ModelsTab(
    state: AssistantState,
    onDownload: (com.hybridassistant.data.LlmModel) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (com.hybridassistant.data.LlmModel) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.models) { model ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Text("Размер: ${model.sizeLabel}")
                    Text("URL: ${model.downloadUrl}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!model.installed) {
                            Button(onClick = { onDownload(model) }) { Text("Скачать") }
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
