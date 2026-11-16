package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AccountKind
import com.example.data.model.FinanceAccount
import com.example.data.model.FinanceReminder
import com.example.data.model.FinanceScope
import com.example.data.model.FinanceTransaction
import com.example.data.model.ReminderRecurrence
import com.example.data.model.ReminderScheduleHelper
import com.example.data.model.TransactionType
import com.example.ui.components.AppleSprings
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.GothamCard
import com.example.ui.components.GothamInteractiveCard
import com.example.ui.components.MonthHelper
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
import com.example.ui.viewmodel.InsightUiState
import com.example.ui.viewmodel.ScopeSummary
import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    scope: FinanceScope,
    userName: String,
    userPhotoUrl: String?,
    summary: ScopeSummary,
    selectedMonthKey: String,
    hideValues: Boolean,
    insightState: InsightUiState,
    onSelectMonth: (String) -> Unit,
    onToggleHideValues: () -> Unit,
    onRequestInsight: () -> Unit,
    onOpenAlfredChat: () -> Unit,
    onOpenQuickAdd: () -> Unit,
    onToggleTransactionPaid: (FinanceTransaction) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onAddAccount: (String, AccountKind, Double, Double) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onAddReminder: (String, Double, String, Timestamp?, ReminderRecurrence, String) -> Unit,
    onUpdateReminder: (String, String, Double, String, Timestamp?, ReminderRecurrence, String) -> Unit,
    onToggleReminder: (FinanceReminder) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit = {},
    onSignOut: () -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
) {
    var showAddAccountSheet by remember { mutableStateOf(false) }
    var showReminderEditorSheet by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<FinanceReminder?>(null) }
    var showProfileMenu by remember { mutableStateOf(false) }

    val isBusiness = scope == FinanceScope.BUSINESS

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = contentBottomPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(GothamSurfaceElevated)
                                    .border(1.dp, GothamHairlineStrong, CircleShape)
                                    .clickable { showProfileMenu = true }
                                    .testTag("profile_menu_button"),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isBusiness) {
                                    Icon(
                                        imageVector = Icons.Rounded.BusinessCenter,
                                        contentDescription = "Caixa da Empresa",
                                        tint = GothamPureWhite,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else if (!userPhotoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userPhotoUrl,
                                        contentDescription = "Foto do perfil",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_alfred_emblem_1794796240073),
                                        contentDescription = "Alfred",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showProfileMenu,
                                onDismissRequest = { showProfileMenu = false },
                                containerColor = GothamSurfaceElevated,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sair da conta Google", color = GothamPureWhite) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.ExitToApp,
                                            contentDescription = null,
                                            tint = GothamPureWhite,
                                        )
                                    },
                                    onClick = {
                                        showProfileMenu = false
                                        onSignOut()
                                    },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (isBusiness) "CAIXA EMPRESARIAL" else "FINANÇAS PESSOAIS",
                                style = MaterialTheme.typography.labelSmall,
                                color = GothamSilver,
                            )
                            Text(
                                text = if (isBusiness) "Minha Empresa" else "Olá, $userName",
                                style = MaterialTheme.typography.headlineMedium,
                                color = GothamPureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleHideValues,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("privacy_eye_button"),
                        ) {
                            Icon(
                                imageVector = if (hideValues) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (hideValues) "Mostrar valores" else "Ocultar valores",
                                tint = GothamPureWhite,
                            )
                        }
                        IconButton(
                            onClick = {
                                editingReminder = null
                                showReminderEditorSheet = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reminders_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsNone,
                                contentDescription = "Adicionar lembrete",
                                tint = GothamPureWhite,
                            )
                        }
                        IconButton(
                            onClick = onOpenNotificationSettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("notification_settings_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Configurar notificações",
                                tint = GothamPureWhite,
                            )
                        }
                    }
                }
            }

            // 2. Month Selector Strip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MonthHelper.availableMonths.forEach { month ->
                        val selected = month.key == selectedMonthKey
                        val bg by animateColorAsState(
                            targetValue = if (selected) GothamPureWhite else GothamSurface,
                            label = "month_bg",
                        )
                        val fg by animateColorAsState(
                            targetValue = if (selected) GothamVoid else GothamSilver,
                            label = "month_fg",
                        )
                        Surface(
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectMonth(month.key) }
                                .testTag("month_chip_${month.key}"),
                            shape = RoundedCornerShape(50),
                            color = bg,
                            border = BorderStroke(1.dp, if (selected) GothamPureWhite else GothamHairline),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = month.shortLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = fg,
                                )
                            }
                        }
                    }
                }
            }

            // 3. Hero Balance Card
            item {
                val totalInflows = summary.monthIncome
                val totalOutflows = summary.paidExpenses + summary.pendingExpenses
                val ratio = if (totalInflows > 0.0) {
                    (totalOutflows / totalInflows).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                val animatedRatio by animateFloatAsState(
                    targetValue = ratio,
                    animationSpec = AppleSprings.CriticallyDamped,
                    label = "cashflow_ratio",
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = GothamSurface,
                    border = BorderStroke(1.dp, GothamHairlineStrong),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(GothamSurfaceElevated, GothamSurface, GothamObsidian)
                                )
                            )
                            .padding(20.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isBusiness) "Caixa Total da Empresa" else "Saldo Total Pessoal",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = GothamSilver,
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.White.copy(alpha = 0.08f),
                                ) {
                                    Text(
                                        text = MonthHelper.labelFor(selectedMonthKey),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GothamPearl,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            AnimatedContent(
                                targetState = CurrencyFormatter.formatBrl(summary.totalBalance, hideValues),
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "hero_balance",
                            ) { formatted ->
                                Text(
                                    text = formatted,
                                    style = MaterialTheme.typography.displayLarge,
                                    color = GothamPureWhite,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isBusiness)
                                        "Resultado do mês: ${CurrencyFormatter.formatSignedBrl(summary.netMonthResult, summary.netMonthResult >= 0, hideValues)}"
                                    else
                                        "Balanço mensal: ${CurrencyFormatter.formatSignedBrl(summary.netMonthResult, summary.netMonthResult >= 0, hideValues)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GothamSilver,
                                )
                                Text(
                                    text = "${(animatedRatio * 100).toInt()}% usado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GothamSilver,
                                )
                            }

                            // Crisp monochrome progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.10f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedRatio)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(GothamPureWhite),
                                )
                            }
                        }
                    }
                }
            }

            // 4. 2x2 Summary Bento Grid (matching reference screen)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryMetricTile(
                            title = if (isBusiness) "Faturamento" else "Receitas",
                            amount = CurrencyFormatter.formatBrl(summary.monthIncome, hideValues),
                            icon = Icons.Rounded.ArrowUpward,
                            filledBadge = true,
                            onClick = onOpenQuickAdd,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryMetricTile(
                            title = if (isBusiness) "A Receber" else "A Pagar",
                            amount = CurrencyFormatter.formatBrl(
                                if (isBusiness) summary.pendingIncome else summary.pendingExpenses,
                                hideValues
                            ),
                            icon = Icons.Rounded.Schedule,
                            filledBadge = false,
                            onClick = onOpenQuickAdd,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryMetricTile(
                            title = if (isBusiness) "Custos Pagos" else "Pago",
                            amount = CurrencyFormatter.formatBrl(summary.paidExpenses, hideValues),
                            icon = Icons.Rounded.ArrowDownward,
                            filledBadge = false,
                            onClick = onOpenQuickAdd,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryMetricTile(
                            title = if (isBusiness) "A Pagar PJ" else "Cartão de Cr.",
                            amount = CurrencyFormatter.formatBrl(
                                if (isBusiness) summary.pendingExpenses else summary.creditCardTotal,
                                hideValues
                            ),
                            icon = if (isBusiness) Icons.AutoMirrored.Rounded.ReceiptLong else Icons.Rounded.CreditCard,
                            filledBadge = false,
                            onClick = onOpenQuickAdd,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 5. Insights do Alfred (IA) Card
            item {
                GothamInteractiveCard(
                    onClick = onRequestInsight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("request_insight_button"),
                    shape = RoundedCornerShape(22.dp),
                    containerColor = GothamObsidian,
                    borderColor = GothamHairlineStrong,
                    borderWidth = 1.2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Insights do Alfred",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GothamPureWhite,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GothamPureWhite,
                                ) {
                                    Text(
                                        text = "IA",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = GothamVoid,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (insightState is InsightUiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = GothamPureWhite,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Gerar análise IA",
                                    tint = GothamPureWhite,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        val insightCopy = when (insightState) {
                            is InsightUiState.Idle -> if (isBusiness) {
                                "\"Senhor, toque aqui para eu analisar a margem de lucro, receitas e custos da sua empresa neste mês.\""
                            } else {
                                "\"Senhor, toque aqui para eu revisar seus gastos pessoais, reservas e próximos vencimentos do mês.\""
                            }

                            is InsightUiState.Loading -> "Analisando seus registros em tempo real..."
                            is InsightUiState.Streaming -> insightState.text
                            is InsightUiState.Success -> "\"${insightState.text.trim()}\""
                            is InsightUiState.Error -> insightState.message
                        }

                        Text(
                            text = insightCopy,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (insightState is InsightUiState.Error) GothamPearl else GothamSilver,
                        )
                    }
                }
            }

            // 6. Próximos lançamentos & lembretes
            item {
                SectionHeaderRow(
                    title = if (isBusiness) "Agenda e Cobranças PJ" else "Próximos Lançamentos",
                    actionLabel = "+ Lembrete",
                    onAction = {
                        editingReminder = null
                        showReminderEditorSheet = true
                    },
                    actionTag = "add_reminder_button",
                )
            }

            item {
                if (summary.scopeReminders.isEmpty()) {
                    GothamInteractiveCard(
                        onClick = {
                            editingReminder = null
                            showReminderEditorSheet = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = GothamSilver,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Nenhum lembrete agendado. Peça ao Alfred ou toque para adicionar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GothamSilver,
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(summary.scopeReminders, key = { it.id }) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                hideValues = hideValues,
                                onToggle = { onToggleReminder(reminder) },
                                onEdit = {
                                    editingReminder = reminder
                                    showReminderEditorSheet = true
                                },
                                onDelete = { onDeleteReminder(reminder.id) },
                            )
                        }
                    }
                }
            }

            // 7. Meus Cartões & Contas
            item {
                SectionHeaderRow(
                    title = if (isBusiness) "Contas PJ & Caixa" else "Meus Cartões e Contas",
                    actionLabel = "+ Conta",
                    onAction = { showAddAccountSheet = true },
                    actionTag = "add_account_button",
                )
            }

            item {
                if (summary.scopeAccounts.isEmpty()) {
                    GothamInteractiveCard(
                        onClick = { showAddAccountSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalance,
                                contentDescription = null,
                                tint = GothamSilver,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isBusiness)
                                    "Adicione uma conta PJ ou caixa operacional."
                                else
                                    "Adicione seu banco principal ou cartão de crédito.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GothamSilver,
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        summary.scopeAccounts.forEach { account ->
                            AccountRowCard(
                                account = account,
                                hideValues = hideValues,
                                onDelete = { onDeleteAccount(account.id) },
                            )
                        }
                    }
                }
            }

            // 8. Últimas Transações
            item {
                SectionHeaderRow(
                    title = if (isBusiness) "Movimentações da Empresa" else "Últimas Transações",
                    actionLabel = "Falar c/ Alfred",
                    onAction = onOpenAlfredChat,
                    actionTag = "open_alfred_from_tx",
                )
            }

            if (summary.monthTransactions.isEmpty()) {
                item {
                    GothamCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = null,
                                tint = GothamSilver,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = "Sem movimentações em ${MonthHelper.labelFor(selectedMonthKey)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = GothamPureWhite,
                            )
                            Text(
                                text = "Use o botão + para abrir a calculadora ou diga ao Alfred o que deseja registrar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GothamSilver,
                            )
                        }
                    }
                }
            } else {
                items(summary.monthTransactions, key = { it.id }) { tx ->
                    TransactionRowCard(
                        transaction = tx,
                        hideValues = hideValues,
                        onTogglePaid = { onToggleTransactionPaid(tx) },
                        onDelete = { onDeleteTransaction(tx.id) },
                    )
                }
            }
        }
    }

    if (showAddAccountSheet) {
        AddAccountSheet(
            scope = scope,
            onDismiss = { showAddAccountSheet = false },
            onConfirm = { name, kind, balance, limit ->
                onAddAccount(name, kind, balance, limit)
                showAddAccountSheet = false
            },
        )
    }

    if (showReminderEditorSheet) {
        val currentEditing = editingReminder
        ReminderEditorSheet(
            existingReminder = currentEditing,
            onDismiss = {
                showReminderEditorSheet = false
                editingReminder = null
            },
            onConfirm = { title, amount, dueText, dueAt, recurrence, monthKey ->
                if (currentEditing == null) {
                    onAddReminder(title, amount, dueText, dueAt, recurrence, monthKey)
                } else {
                    onUpdateReminder(currentEditing.id, title, amount, dueText, dueAt, recurrence, monthKey)
                }
                showReminderEditorSheet = false
                editingReminder = null
            },
        )
    }
}

@Composable
private fun SummaryMetricTile(
    title: String,
    amount: String,
    icon: ImageVector,
    filledBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GothamInteractiveCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = GothamSurface,
        borderColor = GothamHairline,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (filledBadge) GothamPureWhite else Color.White.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (filledBadge) GothamVoid else GothamPureWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = GothamSilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = amount,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = GothamPureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = GothamPureWhite,
        )
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onAction)
                .testTag(actionTag),
            shape = RoundedCornerShape(50),
            color = GothamSurfaceElevated,
            border = BorderStroke(1.dp, GothamHairline),
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = GothamPureWhite,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: FinanceReminder,
    hideValues: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val recurrence = ReminderRecurrence.fromWire(reminder.recurrence)
    GothamInteractiveCard(
        onClick = onToggle,
        modifier = Modifier.width(224.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = if (reminder.isCompleted) GothamObsidian else GothamSurface,
        borderColor = if (reminder.isCompleted) GothamHairline else GothamHairlineStrong,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (reminder.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (reminder.isCompleted) "Concluído" else "Pendente",
                        tint = if (reminder.isCompleted) GothamSilver else GothamPureWhite,
                        modifier = Modifier.size(18.dp),
                    )
                    if (recurrence != ReminderRecurrence.NONE) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.08f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Repeat,
                                    contentDescription = null,
                                    tint = GothamPearl,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = recurrence.labelPt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GothamPearl,
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("edit_reminder_${reminder.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Editar lembrete",
                            tint = GothamSilver,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Remover lembrete",
                            tint = GothamSlate,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (reminder.isCompleted) GothamSilver else GothamPureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reminder.dueDateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = GothamSilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (reminder.amount > 0.0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = CurrencyFormatter.formatBrl(reminder.amount, hideValues),
                        style = MaterialTheme.typography.labelLarge,
                        color = GothamPureWhite,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRowCard(
    account: FinanceAccount,
    hideValues: Boolean,
    onDelete: () -> Unit,
) {
    val isCard = account.kind == AccountKind.CREDIT_CARD.wireValue
    GothamCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isCard) Icons.Rounded.CreditCard else Icons.Rounded.AccountBalance,
                    contentDescription = account.name,
                    tint = GothamPureWhite,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = GothamPureWhite,
                )
                Text(
                    text = AccountKind.fromWire(account.kind).labelPt,
                    style = MaterialTheme.typography.labelMedium,
                    color = GothamSilver,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatBrl(
                        if (isCard && account.creditLimit > 0.0) account.creditLimit else account.balance,
                        hideValues
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = GothamPureWhite,
                )
                if (isCard) {
                    Text(
                        text = "Limite • Fecha dia ${account.closingDay}",
                        style = MaterialTheme.typography.labelSmall,
                        color = GothamSlate,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remover conta",
                    tint = GothamSlate,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TransactionRowCard(
    transaction: FinanceTransaction,
    hideValues: Boolean,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit,
) {
    val isIncome = transaction.type == TransactionType.INCOME.wireValue
    GothamInteractiveCard(
        onClick = onTogglePaid,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isIncome) GothamPureWhite else Color.White.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isIncome) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = if (isIncome) "Entrada" else "Saída",
                    tint = if (isIncome) GothamVoid else GothamPureWhite,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GothamPureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${transaction.category} • ${transaction.accountName} • ${if (transaction.isPaid) "Pago" else "Pendente"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GothamSilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = CurrencyFormatter.formatSignedBrl(transaction.amount, isIncome, hideValues),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = GothamPureWhite,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Excluir transação",
                    tint = GothamSlate,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(
    scope: FinanceScope,
    onDismiss: () -> Unit,
    onConfirm: (String, AccountKind, Double, Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember {
        mutableStateOf(
            if (scope == FinanceScope.BUSINESS) AccountKind.BUSINESS_CASH else AccountKind.CHECKING
        )
    }
    var amountText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GothamObsidian,
        contentColor = GothamPureWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (scope == FinanceScope.BUSINESS) "Nova Conta Empresarial" else "Nova Conta ou Cartão",
                style = MaterialTheme.typography.headlineMedium,
                color = GothamPureWhite,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome (ex: Nubank, Itaú PJ)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GothamPureWhite,
                    unfocusedBorderColor = GothamHairline,
                    focusedTextColor = GothamPureWhite,
                    unfocusedTextColor = GothamPureWhite,
                ),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val options = if (scope == FinanceScope.BUSINESS) {
                    listOf(AccountKind.BUSINESS_CASH, AccountKind.CHECKING, AccountKind.CREDIT_CARD)
                } else {
                    listOf(AccountKind.CHECKING, AccountKind.CREDIT_CARD, AccountKind.SAVINGS)
                }
                options.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = { Text(k.labelPt) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GothamPureWhite,
                            selectedLabelColor = GothamVoid,
                            containerColor = GothamSurface,
                            labelColor = GothamSilver,
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = {
                    Text(if (kind == AccountKind.CREDIT_CARD) "Limite do cartão (R$)" else "Saldo atual (R$)")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GothamPureWhite,
                    unfocusedBorderColor = GothamHairline,
                    focusedTextColor = GothamPureWhite,
                    unfocusedTextColor = GothamPureWhite,
                ),
            )
            Button(
                onClick = {
                    val value = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (kind == AccountKind.CREDIT_CARD) {
                        onConfirm(name, kind, 0.0, value.coerceAtLeast(0.0))
                    } else {
                        onConfirm(name, kind, value, 0.0)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GothamPureWhite,
                    contentColor = GothamVoid,
                ),
            ) {
                Text("Salvar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorSheet(
    existingReminder: FinanceReminder?,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, Timestamp?, ReminderRecurrence, String) -> Unit,
) {
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val initialDate = remember(existingReminder) {
        if (existingReminder != null) {
            ReminderScheduleHelper.extractDateInput(existingReminder)
        } else {
            LocalDate.now().plusDays(1)
        }
    }
    val initialTime = remember(existingReminder) {
        if (existingReminder != null) {
            ReminderScheduleHelper.extractTimeInput(existingReminder)
        } else {
            LocalTime.of(9, 0)
        }
    }

    var title by remember(existingReminder) { mutableStateOf(existingReminder?.title.orEmpty()) }
    var amountText by remember(existingReminder) {
        mutableStateOf(
            if (existingReminder != null && existingReminder.amount > 0.0) {
                String.format(java.util.Locale.US, "%.2f", existingReminder.amount).replace('.', ',')
            } else ""
        )
    }
    var dateText by remember(existingReminder) { mutableStateOf(initialDate.format(dateFmt)) }
    var timeText by remember(existingReminder) { mutableStateOf(initialTime.format(timeFmt)) }
    var recurrence by remember(existingReminder) {
        mutableStateOf(ReminderRecurrence.fromWire(existingReminder?.recurrence))
    }

    val parsedDate = runCatching { LocalDate.parse(dateText.trim(), dateFmt) }.getOrNull()
    val parsedTime = runCatching { LocalTime.parse(timeText.trim(), timeFmt) }.getOrNull()
    val isValidSchedule = parsedDate != null && parsedTime != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GothamObsidian,
        contentColor = GothamPureWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (existingReminder == null) "Novo Lembrete Financeiro" else "Editar Lembrete",
                style = MaterialTheme.typography.headlineMedium,
                color = GothamPureWhite,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título (ex: Conta de luz, DAS MEI)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GothamPureWhite,
                    unfocusedBorderColor = GothamHairline,
                    focusedTextColor = GothamPureWhite,
                    unfocusedTextColor = GothamPureWhite,
                ),
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Valor estimado (opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GothamPureWhite,
                    unfocusedBorderColor = GothamHairline,
                    focusedTextColor = GothamPureWhite,
                    unfocusedTextColor = GothamPureWhite,
                ),
            )

            // Quick date presets
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "Hoje" to LocalDate.now(),
                    "Amanhã" to LocalDate.now().plusDays(1),
                    "+7 dias" to LocalDate.now().plusDays(7),
                ).forEach { (label, presetDate) ->
                    val active = parsedDate == presetDate
                    FilterChip(
                        selected = active,
                        onClick = { dateText = presetDate.format(dateFmt) },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Data (dd/MM/aaaa)") },
                    singleLine = true,
                    isError = parsedDate == null,
                    modifier = Modifier.weight(1.3f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GothamPureWhite,
                        unfocusedBorderColor = GothamHairline,
                        focusedTextColor = GothamPureWhite,
                        unfocusedTextColor = GothamPureWhite,
                    ),
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("Hora (HH:mm)") },
                    singleLine = true,
                    isError = parsedTime == null,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GothamPureWhite,
                        unfocusedBorderColor = GothamHairline,
                        focusedTextColor = GothamPureWhite,
                        unfocusedTextColor = GothamPureWhite,
                    ),
                )
            }

            // Quick time presets
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("09:00", "12:00", "14:00", "18:00").forEach { presetTime ->
                    FilterChip(
                        selected = timeText.trim() == presetTime,
                        onClick = { timeText = presetTime },
                        label = { Text(presetTime) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GothamPureWhite,
                            selectedLabelColor = GothamVoid,
                            containerColor = GothamSurface,
                            labelColor = GothamSilver,
                        ),
                    )
                }
            }

            Text(
                text = "Recorrência",
                style = MaterialTheme.typography.labelMedium,
                color = GothamSilver,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReminderRecurrence.entries.forEach { option ->
                    FilterChip(
                        selected = recurrence == option,
                        onClick = { recurrence = option },
                        label = { Text(option.labelPt) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GothamPureWhite,
                            selectedLabelColor = GothamVoid,
                            containerColor = GothamSurface,
                            labelColor = GothamSilver,
                        ),
                    )
                }
            }

            Button(
                onClick = {
                    val value = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val schedule = ReminderScheduleHelper.fromDateAndTime(
                        date = parsedDate ?: LocalDate.now().plusDays(1),
                        time = parsedTime ?: LocalTime.of(9, 0),
                    )
                    onConfirm(
                        title,
                        value,
                        schedule.displayText,
                        schedule.timestamp,
                        recurrence,
                        schedule.monthKey,
                    )
                },
                enabled = title.isNotBlank() && isValidSchedule,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GothamPureWhite,
                    contentColor = GothamVoid,
                ),
            ) {
                Text(
                    text = if (existingReminder == null) "Agendar Lembrete" else "Salvar Alterações",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
