package com.hybridassistant.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hybridassistant.service.AssistantAccessibilityService

class DeviceActionExecutor(private val context: Context) {

    fun execute(command: DeviceCommand) {
        when (command) {
            is DeviceCommand.OpenUrl -> openUrl(command.url)
            is DeviceCommand.WebSearch -> webSearch(command.query)
            is DeviceCommand.OpenApp -> openApp(command.appName)
            is DeviceCommand.SearchYoutube -> searchYoutube(command.query)
            is DeviceCommand.SearchPlayMarket -> searchPlayMarket(command.query)
            is DeviceCommand.ClickByText -> clickByText(command.text)
        }
    }

    private fun openUrl(url: String) {
        val safeUrl = if (url.startsWith("http")) url else "https://$url"
        launch(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)))
    }

    private fun webSearch(query: String) {
        launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query))
    }

    private fun openApp(appName: String) {
        val pm = context.packageManager
        val app = pm.getInstalledApplications(0).firstOrNull {
            pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
        }
        val intent = app?.packageName?.let(pm::getLaunchIntentForPackage)
        if (intent != null) launch(intent)
        else Toast.makeText(context, "Не найдено приложение: $appName", Toast.LENGTH_SHORT).show()
    }

    private fun searchYoutube(query: String) {
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")))
    }

    private fun searchPlayMarket(query: String) {
        try {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}&c=apps")))
        } catch (_: ActivityNotFoundException) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query)}&c=apps")))
        }
    }

    private fun clickByText(text: String) {
        val ok = AssistantAccessibilityService.instance?.clickByText(text) == true
        val message = if (ok) "Нажатие выполнено: $text" else "Не удалось нажать. Включите Accessibility Service."
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
