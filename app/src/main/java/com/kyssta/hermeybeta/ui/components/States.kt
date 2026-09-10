package com.kyssta.hermeybeta.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import com.kyssta.hermeybeta.ui.theme.HermesMono

/**
 * Feedback primitives — mirrors desktop Loader / ErrorState / LogView /
 * EmptyState / PanelEmpty. One look everywhere; never hand-roll a third
 * centered empty.
 */

/** Animated progress for long ops. Never ships literal "Loading…". */
@Composable
fun Loader(modifier: Modifier = Modifier, label: String? = null) {
    val p = Hermes
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(color = p.accent)
        if (label != null) Text(label, fontSize = 13.sp, color = p.textTertiary)
    }
}

@Composable
fun ErrorState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val p = Hermes
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.red)
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
        if (description != null) Text(description, fontSize = 13.sp, color = p.textSecondary)
        if (retryLabel != null && onRetry != null) {
            HermesButton(retryLabel, onClick = onRetry, variant = HermesVariant.Secondary, size = HermesSize.Sm)
        }
    }
}

/** Plain page-body empty. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val p = Hermes
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
        if (description != null) {
            Text(description, fontSize = 13.sp, color = p.textSecondary)
        }
        if (actionLabel != null && onAction != null) {
            HermesButton(actionLabel, onClick = onAction, variant = HermesVariant.Secondary, size = HermesSize.Sm)
        }
    }
}

/** Overlay master/detail empty with an action — mirrors PanelEmpty. */
@Composable
fun PanelEmpty(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = EmptyState(title, modifier, description, actionLabel, onAction)

/** Raw-log surface: no bg, hairline border, tight padding, small mono. */
@Composable
fun LogView(log: String, modifier: Modifier = Modifier) {
    val p = Hermes
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, p.strokeTertiary, RoundedCornerShape(HermesLayout.CONTROL_RADIUS.dp))
            .padding(8.dp),
    ) {
        Text(
            log,
            fontSize = 12.sp,
            fontFamily = HermesMono,
            color = p.textSecondary,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}
