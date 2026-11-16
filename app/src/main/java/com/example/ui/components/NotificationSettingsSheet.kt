package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notifications.NotificationSettings
import com.example.ui.theme.GothamHairline
import com.example.ui.theme.GothamHairlineStrong
import com.example.ui.theme.GothamObsidian
import com.example.ui.theme.GothamPearl
import com.example.ui.theme.GothamPureWhite
import com.example.ui.theme.GothamSilver
import com.example.ui.theme.GothamSlate
import com.example.ui.theme.GothamSurface
import com.example.ui.theme.GothamSurfaceElevated
import com.example.ui.theme.GothamVoid
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsSheet(
    settings: NotificationSettings,
    hasSystemPermission: Boolean,
    onRequestPermission: () -> Unit,
    onToggleReminders: (Boolean) -> Unit,
    onToggleOneHourBefore: (Boolean) -> Unit,
    onToggleDailySummary: (Boolean) -> Unit,
    onToggleWeeklySummary: (Boolean) -> Unit,
    onToggleGoals: (Boolean) -> Unit,
    onSelectDailyTime: (Int, Int) -> Unit,
    onAddNickname: (String) -> Unit,
    onRemoveNickname: (String) -> Unit,
    onResetNicknames: () -> Unit,
    onSendTestNotification: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newNickname by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GothamObsidian,
        contentColor = GothamPureWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Notificações do Alfred",
                style = MaterialTheme.typography.headlineMedium,
                color = GothamPureWhite,
            )
            Text(
                text = "Avisos 100% locais com saudação por horário e o jeito direto do Alfred.",
                style = MaterialTheme.typography.bodyMedium,
                color = GothamSilver,
            )

            if (!hasSystemPermission) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GothamSurfaceElevated,
                    border = BorderStroke(1.dp, GothamHairlineStrong),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = GothamPureWhite,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permissão desativada",
                                style = MaterialTheme.typography.titleSmall,
                                color = GothamPureWhite,
                            )
                            Text(
                                text = "Ative as notificações do Android para receber lembretes e resumos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GothamSilver,
                            )
                        }
                        TextButton(onClick = onRequestPermission) {
                            Text("Permitir", color = GothamPureWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Category Toggles
            NotificationToggleRow(
                title = "Lembretes no horário",
                subtitle = "Avisa exatamente na hora marcada do compromisso",
                checked = settings.remindersEnabled,
                onCheckedChange = onToggleReminders,
                testTag = "toggle_reminders_notifications",
            )
            NotificationToggleRow(
                title = "Aviso 1 hora antes",
                subtitle = "Pré-aviso antecipado para não perder o prazo",
                checked = settings.oneHourBeforeEnabled,
                enabled = settings.remindersEnabled,
                onCheckedChange = onToggleOneHourBefore,
                testTag = "toggle_1h_notifications",
            )
            NotificationToggleRow(
                title = "Resumo diário",
                subtitle = "Panorama com gastos do dia e lembretes pendentes",
                checked = settings.dailySummaryEnabled,
                onCheckedChange = onToggleDailySummary,
                testTag = "toggle_daily_summary",
            )

            if (settings.dailySummaryEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val formattedTime = String.format(
                        Locale.US,
                        "%02d:%02d",
                        settings.dailySummaryHour,
                        settings.dailySummaryMinute,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Horário do resumo diário: $formattedTime",
                            style = MaterialTheme.typography.labelLarge,
                            color = GothamPearl,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val totalMins = (settings.dailySummaryHour * 60 + settings.dailySummaryMinute - 30 + 1440) % 1440
                                    onSelectDailyTime(totalMins / 60, totalMins % 60)
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, GothamHairline),
                            ) {
                                Text("-30m", color = GothamSilver, style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    val totalMins = (settings.dailySummaryHour * 60 + settings.dailySummaryMinute + 30) % 1440
                                    onSelectDailyTime(totalMins / 60, totalMins % 60)
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, GothamHairline),
                            ) {
                                Text("+30m", color = GothamSilver, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            7 to 30,
                            8 to 0,
                            9 to 0,
                            12 to 0,
                            20 to 0,
                        ).forEach { (h, m) ->
                            val selected = settings.dailySummaryHour == h && settings.dailySummaryMinute == m
                            val label = String.format(Locale.US, "%02d:%02d", h, m)
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectDailyTime(h, m) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GothamPureWhite,
                                    selectedLabelColor = GothamVoid,
                                    containerColor = GothamSurface,
                                    labelColor = GothamSilver,
                                ),
                            )
                        }
                    }
                }
            }

            NotificationToggleRow(
                title = "Resumo semanal (domingo)",
                subtitle = "Fechamento dos últimos 7 dias às 09:00",
                checked = settings.weeklySummaryEnabled,
                onCheckedChange = onToggleWeeklySummary,
                testTag = "toggle_weekly_summary",
            )
            NotificationToggleRow(
                title = "Marcos de metas (50%, 80%, 100%)",
                subtitle = "Comemora cada avanço importante nas suas metas",
                checked = settings.goalsEnabled,
                onCheckedChange = onToggleGoals,
                testTag = "toggle_goal_milestones",
            )

            HorizontalDivider(color = GothamHairline)

            // Editable nicknames
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Como o Alfred te chama",
                        style = MaterialTheme.typography.titleMedium,
                        color = GothamPureWhite,
                    )
                    Text(
                        text = "Toque para remover (mínimo 1 apelido)",
                        style = MaterialTheme.typography.bodySmall,
                        color = GothamSilver,
                    )
                }
                TextButton(onClick = onResetNicknames) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = GothamSilver,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Padrões", color = GothamSilver)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                settings.nicknames.forEach { nick ->
                    InputChip(
                        selected = false,
                        onClick = {
                            if (settings.nicknames.size > 1) {
                                onRemoveNickname(nick)
                            }
                        },
                        label = { Text(nick, color = GothamPureWhite) },
                        trailingIcon = if (settings.nicknames.size > 1) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remover apelido $nick",
                                    tint = GothamSilver,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = GothamSurface,
                            labelColor = GothamPureWhite,
                        ),
                        border = BorderStroke(1.dp, GothamHairline),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newNickname,
                    onValueChange = { newNickname = it },
                    label = { Text("Novo apelido (ex: Sr. Wayne)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GothamPureWhite,
                        unfocusedBorderColor = GothamHairline,
                        focusedTextColor = GothamPureWhite,
                        unfocusedTextColor = GothamPureWhite,
                    ),
                )
                OutlinedButton(
                    onClick = {
                        if (newNickname.isNotBlank()) {
                            onAddNickname(newNickname)
                            newNickname = ""
                        }
                    },
                    enabled = newNickname.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GothamHairlineStrong),
                    modifier = Modifier.height(52.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Adicionar apelido", tint = GothamPureWhite)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onSendTestNotification,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("send_test_notification_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GothamPureWhite,
                    contentColor = GothamVoid,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enviar notificação de teste agora", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GothamSurface,
        border = BorderStroke(1.dp, GothamHairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) GothamPureWhite else GothamSlate,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = GothamSilver,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.testTag(testTag),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = GothamVoid,
                    checkedTrackColor = GothamPureWhite,
                    uncheckedThumbColor = GothamSilver,
                    uncheckedTrackColor = GothamSurfaceElevated,
                    uncheckedBorderColor = GothamHairlineStrong,
                ),
            )
        }
    }
}
