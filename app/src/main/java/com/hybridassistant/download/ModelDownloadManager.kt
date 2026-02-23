package com.hybridassistant.download

import android.content.Context
import com.hybridassistant.data.LlmModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class ModelDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun startOrResumeDownload(
        model: LlmModel,
        onProgress: (Int, Boolean) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (activeJobs[model.id]?.isActive == true) return

        val job = scope.launch {
            val file = modelFile(model.id)
            file.parentFile?.mkdirs()

            var attempts = 0
            while (isActive) {
                try {
                    downloadOnce(model, file, onProgress)
                    onProgress(100, false)
                    onComplete()
                    break
                } catch (e: IOException) {
                    attempts += 1
                    onProgress(progressFromFile(file, -1L), false)
                    if (attempts >= 8) {
                        onError("Ошибка сети: ${e.message}. Нажмите 'Продолжить' для дозагрузки.")
                        break
                    }
                    delay(1500L * attempts)
                    onProgress(progressFromFile(file, -1L), true)
                }
            }
        }

        activeJobs[model.id] = job
        job.invokeOnCompletion { activeJobs.remove(model.id) }
    }

    fun removeDownload(model: LlmModel) {
        activeJobs.remove(model.id)?.cancel()
        modelFile(model.id).takeIf { it.exists() }?.delete()
    }

    private fun downloadOnce(
        model: LlmModel,
        file: File,
        onProgress: (Int, Boolean) -> Unit
    ) {
        val downloaded = if (file.exists()) file.length() else 0L

        val requestBuilder = Request.Builder().url(model.downloadUrl)
        if (downloaded > 0L) {
            requestBuilder.addHeader("Range", "bytes=$downloaded-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Пустой ответ сервера")
            val contentLength = body.contentLength()
            val totalLength = if (contentLength > 0) downloaded + contentLength else -1L

            body.byteStream().use { input ->
                FileOutputStream(file, downloaded > 0).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var current = downloaded

                    onProgress(progressFromPair(current, totalLength), true)
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        current += read
                        onProgress(progressFromPair(current, totalLength), true)
                    }
                    output.flush()
                }
            }
        }
    }

    private fun modelFile(modelId: String): File =
        File(appContext.filesDir, "models/$modelId.gguf")

    private fun progressFromFile(file: File, total: Long): Int =
        progressFromPair(if (file.exists()) file.length() else 0L, total)

    private fun progressFromPair(current: Long, total: Long): Int {
        if (total <= 0L) return if (current > 0) 1 else 0
        return ((current * 100L) / total).toInt().coerceIn(0, 100)
    }
}
