package com.hybridassistant.data

import java.util.UUID

data class LlmModel(
    val id: String,
    val name: String,
    val sizeLabel: String,
    val downloadUrl: String,
    val installed: Boolean = false,
    val downloadId: Long? = null
)

enum class Speaker {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val speaker: Speaker,
    val text: String
)
