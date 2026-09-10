package com.kyssta.hermeybeta.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesDisplay
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import com.kyssta.hermeybeta.ui.theme.HermesSans

/**
 * Shared cinematic surfaces (DESIGN v1.0 §04). One card, one top bar, one
 * avatar — every screen composes these so the scheme stays uniform.
 */

/** Card container: #121826 surface, 12dp radius, #1F2A3A hairline. */
@Composable
fun CineCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = Hermes
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(HermesLayout.CARD_RADIUS.dp),
        color = p.card,
        border = BorderStroke(1.dp, p.strokePrimary),
        content = { Column(Modifier.padding(14.dp), content = content) },
    )
}

/** Top bar: hamburger left, Inter semibold title, optional search/add right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CineTopBar(
    title: String,
    onMenu: () -> Unit,
    onSearch: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    avatar: String? = null,
) {
    val p = Hermes
    TopAppBar(
        title = {
            Text(title, color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        },
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = p.textPrimary)
            }
        },
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = p.textPrimary)
                }
            }
            if (onAdd != null) {
                Surface(
                    modifier = Modifier.padding(end = 12.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onAdd),
                    color = p.accent,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.padding(8.dp))
                }
            } else if (avatar != null) {
                Surface(modifier = Modifier.padding(end = 12.dp).size(32.dp), shape = CircleShape, color = p.elevated) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(avatar, color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = p.background),
    )
}

/** Hero wordmark: Playfair Black, always two lines, centered. */
@Composable
fun CineHero(text: String = "HERMES\nAGENT", modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = Hermes.textPrimary,
        fontFamily = HermesDisplay,
        fontWeight = FontWeight.Black,
        fontSize = 52.sp,
        lineHeight = 54.sp,
        letterSpacing = 1.sp,
    )
}

/** Lane header: colored dot + NAME + count, like the kanban columns. */
@Composable
fun LaneHeader(dot: Color, name: String, count: Int, modifier: Modifier = Modifier) {
    val p = Hermes
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = dot) {}
        Text(
            text = "$name  $count",
            color = p.textSecondary,
            fontFamily = HermesSans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Single-line muted description text used under headings. */
@Composable
fun CineSub(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Hermes.textSecondary, fontFamily = HermesSans, fontSize = 13.sp, lineHeight = 19.sp)
}
