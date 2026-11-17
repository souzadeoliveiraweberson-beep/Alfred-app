package com.example.ui.navigation

import android.content.Intent
import android.net.Uri
import com.example.data.model.FinanceScope
import com.example.notifications.NotificationChannels

data class AlfredLaunchAction(
    val route: String? = null,
    val targetScope: FinanceScope? = null,
    val autoStartVoice: Boolean = false,
    val openQuickAdd: Boolean = false,
)

object AlfredDeepLinkParser {

    const val EXTRA_AUTO_VOICE = "alfred_auto_voice"
    const val EXTRA_OPEN_QUICK_ADD = "alfred_open_quick_add"
    const val EXTRA_TARGET_SCOPE = "alfred_target_scope"

    fun parse(intent: Intent?): AlfredLaunchAction? {
        if (intent == null) return null

        val dataUri = intent.data
        if (dataUri != null && dataUri.scheme.equals("alfred", ignoreCase = true)) {
            return parseUri(dataUri)
        }

        val extraRoute = intent.getStringExtra(NotificationChannels.EXTRA_NAV_ROUTE)
            ?.takeIf { it.isNotBlank() }
        val autoVoice = intent.getBooleanExtra(EXTRA_AUTO_VOICE, false)
        val openQuickAdd = intent.getBooleanExtra(EXTRA_OPEN_QUICK_ADD, false)
        val scopeExtra = intent.getStringExtra(EXTRA_TARGET_SCOPE)
            ?.takeIf { it.isNotBlank() }
            ?.let { FinanceScope.fromWire(it) }

        if (extraRoute == null && !autoVoice && !openQuickAdd && scopeExtra == null) {
            return null
        }

        return AlfredLaunchAction(
            route = extraRoute ?: if (autoVoice) ROUTE_ALFRED else null,
            targetScope = scopeExtra,
            autoStartVoice = autoVoice,
            openQuickAdd = openQuickAdd,
        )
    }

    fun parseUri(uri: Uri): AlfredLaunchAction? {
        if (!uri.scheme.equals("alfred", ignoreCase = true)) return null
        val host = uri.host.orEmpty().lowercase()
        val voiceFlag = uri.getQueryParameter("voice").equals("true", ignoreCase = true)
        val scopeParam = uri.getQueryParameter("scope")?.let {
            if (it.equals("BUSINESS", ignoreCase = true) || it.equals("empresa", ignoreCase = true)) {
                FinanceScope.BUSINESS
            } else if (it.equals("PERSONAL", ignoreCase = true) || it.equals("pessoal", ignoreCase = true)) {
                FinanceScope.PERSONAL
            } else null
        }

        return when (host) {
            "chat", "alfred", "voice" -> AlfredLaunchAction(
                route = ROUTE_ALFRED,
                targetScope = scopeParam,
                autoStartVoice = voiceFlag || host == "voice",
            )
            "quick-add", "add", "calculator" -> AlfredLaunchAction(
                route = ROUTE_FINANCES,
                targetScope = scopeParam,
                openQuickAdd = true,
            )
            "business", "empresa" -> AlfredLaunchAction(
                route = ROUTE_BUSINESS,
                targetScope = FinanceScope.BUSINESS,
            )
            "analytics", "metas" -> AlfredLaunchAction(
                route = ROUTE_ANALYTICS,
                targetScope = scopeParam,
            )
            "finances", "financas", "" -> AlfredLaunchAction(
                route = ROUTE_FINANCES,
                targetScope = scopeParam ?: FinanceScope.PERSONAL,
            )
            else -> null
        }
    }
}
