package com.hybridassistant.device

sealed class DeviceCommand(val label: String) {
    data class OpenUrl(val url: String) : DeviceCommand("Открыть ссылку: $url")
    data class WebSearch(val query: String) : DeviceCommand("Поиск: $query")
    data class OpenApp(val appName: String) : DeviceCommand("Открыть приложение: $appName")
    data class SearchYoutube(val query: String) : DeviceCommand("YouTube поиск: $query")
    data class SearchPlayMarket(val query: String) : DeviceCommand("Play Market поиск: $query")
    data class ClickByText(val text: String) : DeviceCommand("Нажать элемент с текстом: $text")
}
