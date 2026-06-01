package com.example.ui.security

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Motion
import com.example.ui.theme.rememberReducedMotion

/**
 * Teclado numérico reutilizable para introducir el PIN. No contiene lógica de negocio:
 * solo emite eventos de pulsación de dígito, borrado y confirmación.
 */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { digit ->
                    KeyButton(
                        modifier = Modifier.weight(1f),
                        label = digit,
                        onClick = { onDigit(digit[0]) }
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconKeyButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Backspace,
                description = "Borrar",
                onClick = onBackspace
            )
            KeyButton(
                modifier = Modifier.weight(1f),
                label = "0",
                onClick = { onDigit('0') }
            )
            IconKeyButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Check,
                description = "Confirmar PIN",
                onClick = onConfirm,
                enabled = confirmEnabled,
                highlight = true
            )
        }
    }
}

@Composable
private fun KeyButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit
) {
    val reduced = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.94f else 1f,
        animationSpec = Motion.fast(reduced),
        label = "keyScale",
    )
    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick,
        shape = CircleShape,
        interactionSource = interaction
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun IconKeyButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlight: Boolean = false
) {
    val container = if (highlight) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val tint = if (highlight) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val reduced = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) 0.94f else 1f,
        animationSpec = Motion.fast(reduced),
        label = "iconKeyScale",
    )
    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape),
        color = if (enabled) container else container.copy(alpha = 0.3f),
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        interactionSource = interaction
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(26.dp),
                tint = if (enabled) tint else tint.copy(alpha = 0.4f)
            )
        }
    }
}
