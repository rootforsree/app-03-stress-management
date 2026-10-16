package com.sreecharan.stressrelief.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sreecharan.stressrelief.ui.theme.AuroraBrushes
import com.sreecharan.stressrelief.ui.theme.AuroraShapeTokens
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark

/**
 * Pill CTA. [gradient] paints the signature dawn/night-aurora gradient;
 * otherwise the button uses the M3 primary roles. Shape contrast rule: the
 * CTA is always a pill, never the card radius.
 */
@Composable
fun AuroraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val darkTheme = LocalAuroraDark.current
    val shape = AuroraShapeTokens.pill
    if (gradient) {
        // Deep ink on the dawn gradient (light) for contrast, white on night aurora.
        val contentColor = if (darkTheme) Color.White else Color(0xFF002021)
        Button(
            onClick = onClick,
            modifier = modifier.background(AuroraBrushes.hero(darkTheme), shape),
            enabled = enabled,
            shape = shape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = contentColor,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = contentColor.copy(alpha = 0.5f),
                ),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
