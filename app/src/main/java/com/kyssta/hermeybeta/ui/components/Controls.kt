package com.kyssta.hermeybeta.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout

/**
 * The single button primitive — mirrors desktop src/components/ui/button.tsx.
 * Pick a [HermesVariant]; call sites never restyle padding/size/chrome.
 */
enum class HermesVariant {
    Default, Destructive, Secondary, Outline, Ghost, Link, Text, TextStrong,
}

enum class HermesSize { Default, Xs, Sm, Lg, Inline, Micro, Icon, IconXs, IconSm, IconLg }

@Composable
fun HermesButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: HermesVariant = HermesVariant.Default,
    size: HermesSize = HermesSize.Default,
    enabled: Boolean = true,
) {
    val p = Hermes
    val textSize = when (size) {
        HermesSize.Xs, HermesSize.Micro -> 12.sp
        HermesSize.Sm -> 13.sp
        HermesSize.Lg -> 16.sp
        else -> 14.sp
    }
    when (variant) {
        HermesVariant.Default -> Button(
            onClick = onClick, modifier = modifier, enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
        ) { Text(text, fontSize = textSize) }
        HermesVariant.Destructive -> Button(
            onClick = onClick, modifier = modifier, enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = p.red, contentColor = p.onAccent),
        ) { Text(text, fontSize = textSize) }
        HermesVariant.Secondary -> Button(
            onClick = onClick, modifier = modifier, enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = p.elevated, contentColor = p.textPrimary),
        ) { Text(text, fontSize = textSize) }
        HermesVariant.Outline -> OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text, fontSize = textSize, color = p.textPrimary)
        }
        HermesVariant.Ghost -> TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text, fontSize = textSize, color = p.textSecondary)
        }
        HermesVariant.Link, HermesVariant.Text -> TextButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            Text(
                text, fontSize = textSize, color = p.textSecondary,
                fontWeight = if (variant == HermesVariant.TextStrong) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (variant == HermesVariant.TextStrong) TextDecoration.Underline else null,
            )
        }
        HermesVariant.TextStrong -> TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text, fontSize = textSize, color = p.textPrimary, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
        }
    }
}

/**
 * Drawer opener for every top bar — one hamburger, Hermes tint, no restyling per screen.
 */
@Composable
fun MenuNavButton(onMenu: () -> Unit) {
    IconButton(onClick = onMenu) {
        Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Hermes.textPrimary)
    }
}

/**
 * The only search input — mirrors desktop SearchField: borderless,
 * underline-on-focus. Empty lists hide their search field.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    singleLine: Boolean = true,
) {
    val p = Hermes
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        singleLine = singleLine,
        textStyle = TextStyle(color = p.textPrimary, fontSize = 15.sp),
        cursorBrush = SolidColor(p.accent),
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 8.dp)) {
                    if (value.isEmpty()) Text(placeholder, color = p.textTertiary, fontSize = 15.sp)
                    inner()
                }
                androidx.compose.foundation.layout.Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .border(0.5.dp, if (focused) p.accent else p.strokeTertiary),
                )
            }
        },
    )
}

/**
 * Choice control for small mutually-exclusive sets — mirrors desktop
 * SegmentedControl (color mode, connection mode, usage period).
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Hermes
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(HermesLayout.CONTROL_RADIUS.dp))
            .border(1.dp, p.strokeTertiary, RoundedCornerShape(HermesLayout.CONTROL_RADIUS.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Surface(
                color = if (isSel) p.accent else Color.Transparent,
                shape = RoundedCornerShape((HermesLayout.CONTROL_RADIUS - 2).dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape((HermesLayout.CONTROL_RADIUS - 2).dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(i) },
            ) {
                Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSel) p.onAccent else p.textSecondary,
                    )
                }
            }
        }
    }
}

/** Label/description/action row — mirrors desktop settings ListRow. Flat, flush-left. */
@Composable
fun ListRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = Hermes
    val rowMod = if (onClick != null) {
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onClick() }
    } else modifier
    Row(
        modifier = rowMod
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = p.textPrimary)
            if (description != null) {
                Text(description, fontSize = 13.sp, color = p.textSecondary)
            }
        }
        action?.invoke()
    }
}
