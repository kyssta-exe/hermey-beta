package com.kyssta.hermeybeta.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout

/**
 * Overlay primitives — mirrors the desktop floating panels (shadow-nous +
 * stroke-nous hairline, no nested boxes). All dialogs and sheets go through
 * here; call sites never touch raw AlertDialog/ModalBottomSheet.
 */
@Composable
fun HermesDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    val p = Hermes
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        containerColor = p.elevated,
        titleContentColor = p.textPrimary,
        textContentColor = p.textSecondary,
        tonalElevation = HermesLayout.OVERLAY_ELEVATION.dp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesSheet(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val p = Hermes
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(),
        containerColor = p.elevated,
        contentColor = p.textPrimary,
        tonalElevation = HermesLayout.OVERLAY_ELEVATION.dp,
    ) {
        content()
    }
}
