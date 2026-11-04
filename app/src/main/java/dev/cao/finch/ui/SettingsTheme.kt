package dev.cao.finch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.cao.finch.ui.design.InsetSurface
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.theme.FinchShapes
import dev.cao.finch.ui.theme.FinchTheme
import dev.cao.finch.ui.theme.finchBundle
import dev.cao.finch.ui.theme.finchScheme

@Composable
internal fun ThemeSwatch(
    theme: FinchTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bundle = finchBundle()
    val preview = finchScheme(theme, bundle.isDark)
    InsetSurface(
        modifier = modifier,
        shape = FinchShapes.tile,
        onClick = onClick,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = FinchSpace.md, horizontal = FinchSpace.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = FinchSpace.xs)
                    .clip(FinchShapes.thumb)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                preview.scheme.primaryContainer,
                                preview.scheme.tertiary.copy(alpha = 0.35f),
                            )
                        )
                    )
                    .border(
                        width = if (selected) 1.5.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = FinchShapes.thumb,
                    ),
            ) {
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = FinchSpace.xs),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf(
                        preview.scheme.primary,
                        preview.scheme.tertiary,
                        preview.ambient.glowA,
                    ).forEach { c ->
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                    }
                }
            }
            Spacer(Modifier.height(FinchSpace.xs))
            Text(
                theme.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
