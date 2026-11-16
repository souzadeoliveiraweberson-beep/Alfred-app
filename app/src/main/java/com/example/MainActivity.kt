package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.data.remote.AlfredAiService
import com.example.data.remote.configureAppCheck
import com.example.notifications.NotificationChannels
import com.example.ui.navigation.AlfredApp
import com.example.ui.theme.AlfredTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingNavRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory App Check initialization before Compose UI, ViewModels, or Firebase AI calls
        configureAppCheck(this, intent)
        NotificationChannels.ensureCreated(this)
        pendingNavRoute = extractNavRoute(intent)

        if (BuildConfig.DEBUG) {
            lifecycleScope.launch(Dispatchers.IO) {
                AlfredAiService().pingDebugStartup()
            }
        }

        enableEdgeToEdge()
        setContent {
            AlfredTheme {
                AlfredApp(
                    pendingNavRoute = pendingNavRoute,
                    onConsumeNavRoute = { pendingNavRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractNavRoute(intent)?.let { route ->
            pendingNavRoute = route
        }
    }

    private fun extractNavRoute(intent: Intent?): String? {
        return intent?.getStringExtra(NotificationChannels.EXTRA_NAV_ROUTE)
            ?.takeIf { it.isNotBlank() }
    }
}
