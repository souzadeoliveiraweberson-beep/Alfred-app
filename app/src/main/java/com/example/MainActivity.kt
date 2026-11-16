package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.remote.configureAppCheck
import com.example.ui.navigation.AlfredApp
import com.example.ui.theme.AlfredTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory App Check initialization before Compose UI, ViewModels, or Firebase AI calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            AlfredTheme {
                AlfredApp()
            }
        }
    }
}
