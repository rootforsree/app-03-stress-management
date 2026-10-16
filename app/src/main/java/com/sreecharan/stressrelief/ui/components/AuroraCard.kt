package com.sreecharan.stressrelief.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sreecharan.stressrelief.ui.theme.AuroraShapeTokens

/**
 * The one card every screen uses: 22dp expressive radius on
 * surfaceContainerHigh. Pass [onClick] for tappable cards.
 */
@Composable
fun AuroraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = modifier
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = AuroraShapeTokens.card,
            colors = colors,
        ) {
            Column(Modifier.padding(20.dp), content = content)
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = AuroraShapeTokens.card,
            colors = colors,
        ) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}
