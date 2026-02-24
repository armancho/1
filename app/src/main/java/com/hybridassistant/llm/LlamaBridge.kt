package com.hybridassistant.llm

object LlamaBridge {
    init {
        runCatching { System.loadLibrary("hybridassistant_jni") }
    }

    external fun nativeHealthCheck(): String

    fun health(): String = runCatching { nativeHealthCheck() }
        .getOrElse { "llama.cpp JNI not loaded: ${it.message}" }
}
