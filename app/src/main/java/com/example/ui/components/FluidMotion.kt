package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GothamHairline
import com.example.ui.theme.GothamSurface
import java.text.NumberFormat
import java.util.Locale

object AppleSprings {
    // Critically damped default (damping 1.0, ~0.35s response, zero overshoot)
    val CriticallyDamped = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    // Gentle momentum spring (damping 0.8) for sheets and physical gestures
    val Momentum = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )

    // Instant pointer-down press response
    val PressFeedback = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )
}

@Composable
fun GothamInteractiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    containerColor: Color = GothamSurface,
    borderColor: Color = GothamHairline,
    borderWidth: Dp = 1.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = AppleSprings.PressFeedback,
        label = "apple_press_scale",
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = Color.White),
                enabled = enabled,
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Box(content = content)
    }
}

@Composable
fun GothamCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    containerColor: Color = GothamSurface,
    borderColor: Color = GothamHairline,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Box(content = content)
    }
}

object CurrencyFormatter {
    private val ptBrLocale = Locale.forLanguageTag("pt-BR")

    fun formatBrl(amount: Double, hideValues: Boolean = false): String {
        if (hideValues) return "R$ •••••"
        val formatter = NumberFormat.getCurrencyInstance(ptBrLocale)
        return formatter.format(amount)
    }

    fun formatSignedBrl(amount: Double, isIncome: Boolean, hideValues: Boolean = false): String {
        if (hideValues) return "R$ •••••"
        val formatted = formatBrl(kotlin.math.abs(amount), false)
        return if (isIncome) "+ $formatted" else "− $formatted"
    }
}

data class MonthOption(
    val key: String,
    val shortLabel: String,
    val fullLabel: String,
)

object MonthHelper {
    val availableMonths = listOf(
        MonthOption("2026-09", "Set", "Setembro 2026"),
        MonthOption("2026-10", "Out", "Outubro 2026"),
        MonthOption("2026-11", "Nov", "Novembro 2026"),
        MonthOption("2026-12", "Dez", "Dezembro 2026"),
        MonthOption("2027-01", "Jan", "Janeiro 2027"),
    )

    const val DEFAULT_MONTH_KEY = "2026-11"

    fun labelFor(monthKey: String): String =
        availableMonths.firstOrNull { it.key == monthKey }?.fullLabel ?: monthKey
}
