package com.kyssta.hermey.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyssta.hermey.ui.components.CineCard
import com.kyssta.hermey.ui.components.CineSub
import com.kyssta.hermey.ui.components.CineTopBar
import com.kyssta.hermey.ui.components.ListRow
import com.kyssta.hermey.ui.components.SegmentedControl
import com.kyssta.hermey.ui.theme.Hermes
import com.kyssta.hermey.ui.theme.HermesLayout
import com.kyssta.hermey.ui.theme.HermesSans
import com.kyssta.hermey.ui.theme.ThemeMode
import com.kyssta.hermey.ui.theme.ThemeModeStore
import com.kyssta.hermey.ui.theme.ThemeModeStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceTab() {
    val p = Hermes
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(ThemeModeStore.mode.value) }

    Scaffold(
        topBar = { CineTopBar(title = "Appearance", onMenu = {}) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HermesLayout.PAGE_INSET_X.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Theme", color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            CineSub("Choose between light, dark, and system-following modes.")
            Spacer(Modifier.height(8.dp))

            SegmentedControl(
                options = listOf("System", "Light", "Dark"),
                selected = when (mode) {
                    ThemeMode.SYSTEM -> 0
                    ThemeMode.LIGHT -> 1
                    ThemeMode.DARK -> 2
                },
                onSelect = { i ->
                    mode = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)[i]
                    ThemeModeStore.setMode(mode)
                    scope.launch { ThemeModeStorage.save(ctx, mode) }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            Spacer(Modifier.height(16.dp))
            Text("Preview", color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            CineCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sample heading", color = p.textPrimary, fontFamily = HermesSans, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Body text goes here.", color = p.textSecondary, fontFamily = HermesSans, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    ListRow(
                        label = "Accent color",
                        description = "Used for primary actions and highlights",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
