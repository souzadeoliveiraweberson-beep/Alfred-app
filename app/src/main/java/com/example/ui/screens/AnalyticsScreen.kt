package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceScope
import com.example.ui.components.AppleSprings
import com.example.ui.components.CurrencyFormatter
import com.example.ui.components.GothamCard
import com.example.ui.components.GothamInteractiveCard
import com.example.ui.components.MonthHelper
import com.example.ui.components.SegmentedTogglePill
import com.example.ui.theme.GothamChartShades
import com.example.ui.theme.GothamHairline
import com.example.ui.theme.GothamHairlineStrong
import com.example.ui.theme.GothamObsidian
import com.example.ui.theme.GothamPureWhite
import com.example.ui.theme.GothamSilver
import com.example.ui.theme.GothamSlate
import com.example.ui.theme.GothamSurface
import com.example.ui.theme.GothamSurfaceElevated
import com.example.ui.theme.GothamVoid
import com.example.ui.viewmodel.CategorySlice
import com.example.ui.viewmodel.ScopeSummary

@Composable
fun AnalyticsScreen(
    personalSummary: ScopeSummary,
    businessSummary: ScopeSummary,
    selectedMonthKey: String,
    hideValues: Boolean,
    onAddGoal: (FinanceScope, String, Double, Double, String) -> Unit,
    onContributeGoal: (FinanceGoal, Double) -> Unit,
    onDeleteGoal: (String) -> Unit,
    contentBottomPadding: Dp,
) {
    var selectedScope by remember { mutableStateOf(FinanceScope.PERSONAL) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Gastos, 1 = Receitas, 2 = Reservas
    var showAddGoalSheet by remember { mutableStateOf(false) }

    val summary = if (selectedScope == FinanceScope.BUSINESS) businessSummary else personalSummary
    val slices = when (selectedTab) {
        1 -> summary.incomeSlices
        else -> summary.expenseSlices
    }
    val totalValue = slices.sumOf { it.amount }

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
                top = 16.dp,
                bottom = contentBottomPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "DETALHAMENTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = GothamSilver,
                    )
                    Text(
                        text = "Balanço de ${MonthHelper.labelFor(selectedMonthKey)}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = GothamPureWhite,
                    )
                }
            }

            // Scope Switcher: Finanças vs Empresa
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SegmentedTogglePill(
                        label = "Finanças (Pessoal)",
                        selected = selectedScope == FinanceScope.PERSONAL,
                        onClick = { selectedScope = FinanceScope.PERSONAL },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("analytics_scope_personal"),
                    )
                    SegmentedTogglePill(
                        label = "Empresa (Caixa)",
                        selected = selectedScope == FinanceScope.BUSINESS,
                        onClick = { selectedScope = FinanceScope.BUSINESS },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("analytics_scope_business"),
                    )
                }
            }

            // Sub-tabs: Gastos / Receitas / Reservas
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Gastos", "Receitas", "Reservas").forEachIndexed { index, title ->
                        val selected = selectedTab == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTab = index }
                                .testTag("analytics_tab_$index"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) GothamSurfaceElevated else GothamSurface,
                            border = BorderStroke(1.dp, if (selected) GothamPureWhite else GothamHairline),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) GothamPureWhite else GothamSilver,
                                )
                            }
                        }
                    }
                }
            }

            if (selectedTab != 2) {
                // Monochrome Donut Chart Card
                item {
                    GothamCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        borderColor = GothamHairlineStrong,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(196.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                MonochromeDonutChart(
                                    slices = slices,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (selectedTab == 1) "TOTAL ENTRADAS" else "TOTAL SAÍDAS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GothamSilver,
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatBrl(totalValue, hideValues),
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = GothamPureWhite,
                                    )
                                }
                            }

                            if (slices.isEmpty()) {
                                Text(
                                    text = "Sem lançamentos nesta visão para ${MonthHelper.labelFor(selectedMonthKey)}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GothamSilver,
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    slices.forEachIndexed { idx, slice ->
                                        val shade = GothamChartShades[idx % GothamChartShades.size]
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(shade),
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = slice.category,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = GothamPureWhite,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = "${(slice.percentage * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GothamSilver,
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = CurrencyFormatter.formatBrl(slice.amount, hideValues),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = GothamPureWhite,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Poupança e Metas / Reservas
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedScope == FinanceScope.BUSINESS)
                            "Reservas da Empresa"
                        else
                            "Poupança e Metas",
                        style = MaterialTheme.typography.headlineMedium,
                        color = GothamPureWhite,
                    )
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showAddGoalSheet = true }
                            .testTag("add_goal_button"),
                        shape = RoundedCornerShape(50),
                        color = GothamSurfaceElevated,
                        border = BorderStroke(1.dp, GothamHairline),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = GothamPureWhite,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Nova Meta",
                                style = MaterialTheme.typography.labelMedium,
                                color = GothamPureWhite,
                            )
                        }
                    }
                }
            }

            if (summary.scopeGoals.isEmpty()) {
                item {
                    GothamInteractiveCard(
                        onClick = { showAddGoalSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Savings,
                                contentDescription = null,
                                tint = GothamPureWhite,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (selectedScope == FinanceScope.BUSINESS)
                                    "Crie uma reserva de emergência ou meta de expansão para a empresa."
                                else
                                    "Defina uma meta pessoal (viagem, reserva, investimento).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GothamSilver,
                            )
                        }
                    }
                }
            } else {
                items(summary.scopeGoals.size, key = { summary.scopeGoals[it].id }) { index ->
                    val goal = summary.scopeGoals[index]
                    GoalProgressCard(
                        goal = goal,
                        hideValues = hideValues,
                        onQuickContribute = { onContributeGoal(goal, 100.0) },
                        onDelete = { onDeleteGoal(goal.id) },
                    )
                }
            }
        }
    }

    if (showAddGoalSheet) {
        AddGoalSheet(
            scope = selectedScope,
            onDismiss = { showAddGoalSheet = false },
            onConfirm = { title, target, current, deadline ->
                onAddGoal(selectedScope, title, target, current, deadline)
                showAddGoalSheet = false
            },
        )
    }
}

@Composable
private fun MonochromeDonutChart(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 28.dp.toPx()
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        if (slices.isEmpty()) {
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )
            return@Canvas
        }

        var startAngle = -90f
        val gapDegrees = if (slices.size > 1) 4f else 0f
        slices.forEachIndexed { idx, slice ->
            val rawSweep = slice.percentage * 360f
            val sweep = (rawSweep - gapDegrees).coerceAtLeast(2f)
            val color = GothamChartShades[idx % GothamChartShades.size]
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            startAngle += rawSweep
        }
    }
}

@Composable
private fun GoalProgressCard(
    goal: FinanceGoal,
    hideValues: Boolean,
    onQuickContribute: () -> Unit,
    onDelete: () -> Unit,
) {
    val ratio = if (goal.targetAmount > 0.0) {
        (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = ratio,
        animationSpec = AppleSprings.CriticallyDamped,
        label = "goal_progress",
    )

    GothamCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = GothamPureWhite,
                    )
                    if (goal.deadlineText.isNotBlank()) {
                        Text(
                            text = "Prazo: ${goal.deadlineText}",
                            style = MaterialTheme.typography.labelMedium,
                            color = GothamSilver,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onQuickContribute),
                        shape = RoundedCornerShape(50),
                        color = GothamPureWhite,
                    ) {
                        Text(
                            text = "+ R$ 100",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = GothamVoid,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Remover meta",
                            tint = GothamSlate,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.1f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(GothamPureWhite),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${CurrencyFormatter.formatBrl(goal.currentAmount, hideValues)} de ${CurrencyFormatter.formatBrl(goal.targetAmount, hideValues)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GothamSilver,
                )
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = GothamPureWhite,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalSheet(
    scope: FinanceScope,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var currentText by remember { mutableStateOf("0") }
    var deadlineText by remember { mutableStateOf("Dez 2026") }

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
                text = if (scope == FinanceScope.BUSINESS) "Nova Reserva Empresarial" else "Nova Meta Pessoal",
                style = MaterialTheme.typography.headlineMedium,
                color = GothamPureWhite,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nome da meta") },
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
                value = targetText,
                onValueChange = { targetText = it },
                label = { Text("Valor alvo (R$)") },
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
            OutlinedTextField(
                value = currentText,
                onValueChange = { currentText = it },
                label = { Text("Valor já guardado (R$)") },
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
            OutlinedTextField(
                value = deadlineText,
                onValueChange = { deadlineText = it },
                label = { Text("Prazo estimado") },
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
                    val target = targetText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val current = currentText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && target > 0.0) {
                        onConfirm(title, target, current, deadlineText)
                    }
                },
                enabled = title.isNotBlank() && (targetText.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GothamPureWhite,
                    contentColor = GothamVoid,
                ),
            ) {
                Text("Criar Meta", fontWeight = FontWeight.Bold)
            }
        }
    }
}
