package com.hybridassistant.llm

import com.hybridassistant.device.DeviceCommand

class LlmEngine {

    fun generateResponse(prompt: String, selectedModel: String?): LlmResult {
        val lower = prompt.lowercase()

        val command = when {
            lower.startsWith("open ") || lower.startsWith("открой ") -> DeviceCommand.OpenApp(lower.substringAfter(' ').trim())
            lower.contains("youtube") || lower.contains("ютуб") -> DeviceCommand.SearchYoutube(prompt)
            lower.contains("play market") || lower.contains("плей маркет") -> DeviceCommand.SearchPlayMarket(prompt)
            lower.contains("http://") || lower.contains("https://") -> DeviceCommand.OpenUrl(prompt.substringAfterLast(' '))
            lower.contains("найди") || lower.contains("search") -> DeviceCommand.WebSearch(prompt)
            lower.contains("нажми") || lower.contains("click") -> DeviceCommand.ClickByText(prompt.substringAfter(' ').trim())
            else -> null
        }

        if (command != null) {
            return LlmResult("Выполняю: ${command.label}", command)
        }

        val model = selectedModel ?: "no-model"
        return LlmResult("[$model] ${LlamaBridge.health()} | Запрос: $prompt")
    }
}

data class LlmResult(
    val answer: String,
    val deviceCommand: DeviceCommand? = null
)
