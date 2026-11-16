package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.FinanceAccount
import com.example.data.model.FinanceScope
import com.example.data.model.TransactionType
import com.example.data.remote.ManualEntryDraft
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

private val PERSONAL_CATEGORIES = listOf(
    "Alimentação", "Moradia", "Transporte", "Saúde", "Assinaturas", "Lazer", "Salário", "Outros"
)

private val BUSINESS_CATEGORIES = listOf(
    "Vendas", "Serviços", "Fornecedores", "Impostos", "Software", "Marketing", "Pró-labore", "Outros"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorEntrySheet(
    initialScope: FinanceScope,
    personalAccounts: List<FinanceAccount>,
    businessAccounts: List<FinanceAccount>,
    initialDraft: ManualEntryDraft? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        scope: FinanceScope,
        type: TransactionType,
        title: String,
        category: String,
        amount: Double,
        account: FinanceAccount?,
        isPaid: Boolean,
        isRecurring: Boolean,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedScope by remember(initialDraft) { mutableStateOf(initialDraft?.scope ?: initialScope) }
    var selectedType by remember(initialDraft) { mutableStateOf(initialDraft?.type ?: TransactionType.EXPENSE) }
    var expression by remember(initialDraft) {
        mutableStateOf(
            if (initialDraft != null && initialDraft.amount > 0.0) {
                formatDraftAmountForExpression(initialDraft.amount)
            } else {
                "0"
            }
        )
    }
    var title by remember(initialDraft) { mutableStateOf(initialDraft?.title.orEmpty()) }
    var selectedCategory by remember(initialDraft) { mutableStateOf(initialDraft?.category ?: "Alimentação") }
    var selectedAccount by remember { mutableStateOf<FinanceAccount?>(null) }
    var isPaid by remember(initialDraft) { mutableStateOf(initialDraft?.isPaid ?: true) }
    var isRecurring by remember { mutableStateOf(false) }

    val categories = if (selectedScope == FinanceScope.BUSINESS) BUSINESS_CATEGORIES else PERSONAL_CATEGORIES
    if (selectedCategory !in categories) {
        selectedCategory = categories.first()
    }
    val availableAccounts = if (selectedScope == FinanceScope.BUSINESS) businessAccounts else personalAccounts
    if (selectedAccount != null && selectedAccount !in availableAccounts) {
        selectedAccount = null
    }

    val evaluatedAmount = remember(expression) { evaluateCalculatorExpression(expression) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GothamObsidian,
        contentColor = GothamPureWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(GothamHairlineStrong)
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header + Scope & Type pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Novo Lançamento",
                    style = MaterialTheme.typography.headlineMedium,
                    color = GothamPureWhite,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Fechar calculadora",
                        tint = GothamSilver,
                    )
                }
            }

            // Scope toggle: Finanças (Pessoal) vs Empresa
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
                        .testTag("calc_scope_personal"),
                )
                SegmentedTogglePill(
                    label = "Empresa (Caixa)",
                    selected = selectedScope == FinanceScope.BUSINESS,
                    onClick = { selectedScope = FinanceScope.BUSINESS },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_scope_business"),
                )
            }

            // Type toggle: Despesa vs Receita
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SegmentedTogglePill(
                    label = if (selectedScope == FinanceScope.BUSINESS) "Custo / Saída" else "Despesa",
                    selected = selectedType == TransactionType.EXPENSE,
                    onClick = { selectedType = TransactionType.EXPENSE },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_type_expense"),
                )
                SegmentedTogglePill(
                    label = if (selectedScope == FinanceScope.BUSINESS) "Faturamento" else "Receita",
                    selected = selectedType == TransactionType.INCOME,
                    onClick = { selectedType = TransactionType.INCOME },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc_type_income"),
                )
            }

            // Amount display card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = GothamSurface,
                border = BorderStroke(1.dp, GothamHairlineStrong),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = if (expression.any { it in listOf('+', '-', '×', '÷') }) expression else "VALOR DO LANÇAMENTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = GothamSilver,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyFormatter.formatBrl(evaluatedAmount),
                        style = MaterialTheme.typography.displayLarge,
                        color = GothamPureWhite,
                        maxLines = 1,
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        text = if (selectedScope == FinanceScope.BUSINESS)
                            "Descrição (ex: Venda cliente, Fornecedor...)"
                        else
                            "Descrição (ex: Almoço, Academia, Salário...)",
                        color = GothamSlate,
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calc_title_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GothamPureWhite,
                    unfocusedBorderColor = GothamHairline,
                    focusedTextColor = GothamPureWhite,
                    unfocusedTextColor = GothamPureWhite,
                    cursorColor = GothamPureWhite,
                    focusedContainerColor = GothamSurface,
                    unfocusedContainerColor = GothamSurface,
                ),
            )

            // Categories
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GothamPureWhite,
                            selectedLabelColor = GothamVoid,
                            containerColor = GothamSurface,
                            labelColor = GothamSilver,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategory == cat,
                            borderColor = GothamHairline,
                            selectedBorderColor = GothamPureWhite,
                        ),
                    )
                }
            }

            // Accounts & Status chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = isPaid,
                    onClick = { isPaid = !isPaid },
                    label = {
                        Text(
                            if (isPaid) {
                                if (selectedType == TransactionType.INCOME) "Recebido" else "Pago"
                            } else {
                                if (selectedType == TransactionType.INCOME) "A Receber" else "A Pagar"
                            }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GothamSurfaceElevated,
                        selectedLabelColor = GothamPureWhite,
                        containerColor = GothamSurface,
                        labelColor = GothamSilver,
                    ),
                )
                FilterChip(
                    selected = isRecurring,
                    onClick = { isRecurring = !isRecurring },
                    label = { Text("Recorrente") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GothamSurfaceElevated,
                        selectedLabelColor = GothamPureWhite,
                        containerColor = GothamSurface,
                        labelColor = GothamSilver,
                    ),
                )
                availableAccounts.forEach { acc ->
                    FilterChip(
                        selected = selectedAccount?.id == acc.id,
                        onClick = {
                            selectedAccount = if (selectedAccount?.id == acc.id) null else acc
                        },
                        label = { Text(acc.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GothamPureWhite,
                            selectedLabelColor = GothamVoid,
                            containerColor = GothamSurface,
                            labelColor = GothamSilver,
                        ),
                    )
                }
            }

            // Custom Calculator Keypad (inspired by right-side screen in reference)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val appendToken: (String) -> Unit = { token ->
                    expression = applyCalculatorToken(expression, token)
                }

                CalculatorRow(
                    keys = listOf("7", "8", "9", "÷"),
                    onKey = appendToken,
                )
                CalculatorRow(
                    keys = listOf("4", "5", "6", "×"),
                    onKey = appendToken,
                )
                CalculatorRow(
                    keys = listOf("1", "2", "3", "-"),
                    onKey = appendToken,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalcKeyButton(
                        label = "AC",
                        isOperator = true,
                        onClick = { expression = "0" },
                        modifier = Modifier.weight(1f),
                    )
                    CalcKeyButton(
                        label = "0",
                        isOperator = false,
                        onClick = { appendToken("0") },
                        modifier = Modifier.weight(1f),
                    )
                    CalcKeyButton(
                        label = ",",
                        isOperator = false,
                        onClick = { appendToken(",") },
                        modifier = Modifier.weight(1f),
                    )
                    CalcKeyButton(
                        label = "+",
                        isOperator = true,
                        onClick = { appendToken("+") },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Bottom action row: Backspace + Confirm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GothamInteractiveCard(
                        onClick = {
                            expression = if (expression.length <= 1) "0" else expression.dropLast(1)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = GothamSurfaceElevated,
                    ) {
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                contentDescription = "Apagar dígito",
                                tint = GothamPureWhite,
                            )
                        }
                    }

                    val canConfirm = evaluatedAmount > 0.0
                    GothamInteractiveCard(
                        onClick = {
                            if (canConfirm) {
                                val resolvedTitle = title.trim().ifBlank { selectedCategory }
                                onConfirm(
                                    selectedScope,
                                    selectedType,
                                    resolvedTitle,
                                    selectedCategory,
                                    evaluatedAmount,
                                    selectedAccount,
                                    isPaid,
                                    isRecurring,
                                )
                            }
                        },
                        enabled = canConfirm,
                        modifier = Modifier
                            .weight(2.2f)
                            .height(54.dp)
                            .testTag("calc_confirm_button"),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = if (canConfirm) GothamPureWhite else GothamSurface,
                        borderColor = if (canConfirm) GothamPureWhite else GothamHairline,
                    ) {
                        Row(
                            modifier = Modifier.matchParentSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Confirmar lançamento",
                                tint = if (canConfirm) GothamVoid else GothamSlate,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Registrar",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (canConfirm) GothamVoid else GothamSlate,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedTogglePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) GothamPureWhite else GothamSurface,
        label = "pill_bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) GothamVoid else GothamSilver,
        label = "pill_fg",
    )
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, if (selected) GothamPureWhite else GothamHairline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CalculatorRow(
    keys: List<String>,
    onKey: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keys.forEach { key ->
            val isOp = key in listOf("÷", "×", "-", "+")
            CalcKeyButton(
                label = key,
                isOperator = isOp,
                onClick = { onKey(key) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CalcKeyButton(
    label: String,
    isOperator: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GothamInteractiveCard(
        onClick = onClick,
        modifier = modifier
            .height(50.dp)
            .testTag("calc_key_$label"),
        shape = RoundedCornerShape(14.dp),
        containerColor = if (isOperator) GothamSurfaceElevated else GothamSurface,
        borderColor = GothamHairline,
    ) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                color = if (isOperator) GothamPearl else GothamPureWhite,
            )
        }
    }
}

private fun applyCalculatorToken(current: String, token: String): String {
    val operators = setOf('+', '-', '×', '÷')
    if (token.length == 1 && token[0] in operators) {
        if (current.isEmpty() || current == "0") return current
        return if (current.last() in operators) current.dropLast(1) + token else current + token
    }
    if (token == ",") {
        val lastNumber = current.split('+', '-', '×', '÷').lastOrNull().orEmpty()
        return if (lastNumber.contains(',')) current else "$current,"
    }
    return if (current == "0") token else current + token
}

private fun evaluateCalculatorExpression(expr: String): Double {
    val sanitized = expr.replace(',', '.').trimEnd('+', '-', '×', '÷', '.')
    if (sanitized.isBlank()) return 0.0
    val numbers = mutableListOf<Double>()
    val ops = mutableListOf<Char>()
    val currentNum = StringBuilder()

    for (ch in sanitized) {
        if (ch in listOf('+', '-', '×', '÷') && currentNum.isNotEmpty()) {
            numbers.add(currentNum.toString().toDoubleOrNull() ?: 0.0)
            ops.add(ch)
            currentNum.clear()
        } else {
            currentNum.append(ch)
        }
    }
    if (currentNum.isNotEmpty()) {
        numbers.add(currentNum.toString().toDoubleOrNull() ?: 0.0)
    }
    if (numbers.isEmpty()) return 0.0

    // Handle × and ÷ first
    val reducedNums = mutableListOf(numbers[0])
    val reducedOps = mutableListOf<Char>()
    for (i in ops.indices) {
        val op = ops[i]
        val next = numbers.getOrElse(i + 1) { 0.0 }
        when (op) {
            '×' -> reducedNums[reducedNums.lastIndex] = reducedNums.last() * next
            '÷' -> reducedNums[reducedNums.lastIndex] = if (next != 0.0) reducedNums.last() / next else 0.0
            else -> {
                reducedOps.add(op)
                reducedNums.add(next)
            }
        }
    }

    var result = reducedNums[0]
    for (i in reducedOps.indices) {
        val next = reducedNums.getOrElse(i + 1) { 0.0 }
        when (reducedOps[i]) {
            '+' -> result += next
            '-' -> result -= next
        }
    }
    return if (result.isFinite() && result > 0.0) result else 0.0
}

private fun formatDraftAmountForExpression(amount: Double): String {
    if (amount <= 0.0 || !amount.isFinite()) return "0"
    val roundedLong = amount.toLong()
    return if (amount == roundedLong.toDouble()) {
        roundedLong.toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", amount)
            .trimEnd('0')
            .trimEnd('.')
            .replace('.', ',')
    }
}
