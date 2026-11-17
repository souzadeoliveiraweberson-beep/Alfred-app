package com.example.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.R
import com.example.notifications.NotificationChannels
import com.example.ui.navigation.AlfredDeepLinkParser
import com.example.ui.navigation.ROUTE_ALFRED

class AlfredVoiceTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_alfred_voice_label)
        tile.contentDescription = getString(R.string.tile_alfred_voice_description)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(R.string.tile_alfred_voice_subtitle)
        }
        tile.state = Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_alfred_tile)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val action = { launchVoiceChat() }
        if (isLocked) {
            unlockAndRun(action)
        } else {
            action()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchVoiceChat() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("alfred://chat?voice=true")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationChannels.EXTRA_NAV_ROUTE, ROUTE_ALFRED)
            putExtra(AlfredDeepLinkParser.EXTRA_AUTO_VOICE, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                TILE_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    companion object {
        private const val TILE_REQUEST_CODE = 82_001

        fun canRequestAddTile(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        fun requestAddTile(
            context: Context,
            onResult: (String) -> Unit,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                onResult("Adicione o atalho manualmente editando o Painel Rápido do Android.")
                return
            }
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            if (statusBarManager == null) {
                onResult("Serviço do Painel Rápido indisponível neste aparelho.")
                return
            }
            statusBarManager.requestAddTileService(
                ComponentName(context, AlfredVoiceTileService::class.java),
                context.getString(R.string.tile_alfred_voice_label),
                Icon.createWithResource(context, R.drawable.ic_alfred_tile),
                context.mainExecutor,
            ) { resultCode ->
                val message = when (resultCode) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                        "Atalho \"Falar com Alfred\" adicionado ao Painel Rápido."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        "O atalho \"Falar com Alfred\" já está no Painel Rápido."
                    else ->
                        "Não foi possível adicionar automaticamente. Você pode fixá-lo pelo editor do Painel Rápido."
                }
                onResult(message)
            }
        }
    }
}
