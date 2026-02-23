package com.hybridassistant.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.hybridassistant.data.LlmModel

class ModelDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueueModelDownload(model: LlmModel): Long {
        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
            .setTitle("Downloading ${model.name}")
            .setDescription("HybridAssistant model download")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                "models/${model.id}.gguf"
            )

        return downloadManager.enqueue(request)
    }

    fun removeDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
    }
}
