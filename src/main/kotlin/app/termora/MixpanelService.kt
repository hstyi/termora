package app.termora

import app.termora.database.DatabaseManager
import com.formdev.flatlaf.util.SystemInfo
import com.mixpanel.mixpanelapi.ClientDelivery
import com.mixpanel.mixpanelapi.MessageBuilder
import com.mixpanel.mixpanelapi.MixpanelAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.SystemUtils
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.*

internal class MixpanelService private constructor() {
    companion object {
        private val log = LoggerFactory.getLogger(MixpanelService::class.java)

        fun getInstance(): MixpanelService {
            return ApplicationScope.forApplicationScope().getOrCreate(MixpanelService::class) { MixpanelService() }
        }
    }

    fun push(event: String, extras: Map<String, String> = emptyMap()) {
        swingCoroutineScope.launch(Dispatchers.IO) {
            try {
                val properties = JSONObject()
                for (entry in extras) {
                    properties.put(entry.key, entry.value)
                }
                properties.put("os", SystemUtils.OS_NAME)
                if (SystemInfo.isLinux) {
                    properties.put("platform", "Linux")
                } else if (SystemInfo.isWindows) {
                    properties.put("platform", "Windows")
                } else if (SystemInfo.isMacOS) {
                    properties.put("platform", "macOS")
                }
                properties.put("version", Application.getVersion())
                properties.put("language", Locale.getDefault().toString())
                val message = MessageBuilder("0871335f59ee6d0eb246b008a20f9d1c")
                    .event(getAnalyticsUserID(), event, properties)
                val delivery = ClientDelivery()
                delivery.addMessage(message)
                val endpoints = listOf(
                    "https://api-eu.mixpanel.com",
                    "https://api-in.mixpanel.com",
                    "https://api.mixpanel.com",
                    "http://api.mixpanel.com",
                )
                for (endpoint in endpoints) {
                    try {
                        MixpanelAPI(
                            "$endpoint/track",
                            "$endpoint/engage",
                            "$endpoint/groups"
                        ).deliver(delivery, true)
                        break
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                        continue
                    }
                }
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }
    }


    private fun getAnalyticsUserID(): String {
        val properties = DatabaseManager.getInstance().properties
        var id = properties.getString("AnalyticsUserID")
        if (id.isNullOrBlank()) {
            id = randomUUID()
            properties.putString("AnalyticsUserID", id)
        }
        return id
    }

}