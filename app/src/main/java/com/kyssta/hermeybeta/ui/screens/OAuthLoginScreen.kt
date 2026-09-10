package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import android.webkit.CookieManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.auth.ConnectionStore
import com.kyssta.hermeybeta.auth.GatewayMode
import com.kyssta.hermeybeta.auth.ServerConnection
import com.kyssta.hermeybeta.auth.hasGatewaySession
import com.kyssta.hermeybeta.auth.injectCookies
import com.kyssta.hermeybeta.network.GatewayApi
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.normalizeRemoteBaseUrl
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OAuthLoginViewModel(app: Application) : AndroidViewModel(app) {
    val store = ConnectionStore(app)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun agentCookies(url: String): String = try {
        CookieManager.getInstance().getCookie(url) ?: ""
    } catch (_: Exception) {
        ""
    }

    /** Gateway cookies landed in the WebView jar — verify, save, finish. */
    fun finish(baseUrl: String, onDone: (ServerConnection) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val norm = normalizeRemoteBaseUrl(baseUrl)
                injectCookies(norm, agentCookies(norm))
                GatewayApi(norm, {}).authMe()
                val conn = store.save(
                    ServerConnection(mode = GatewayMode.REMOTE, baseUrl = norm),
                )
                store.setActive(conn.id)
                SessionRepository.activate(conn)
                onDone(conn)
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            } finally {
                busy = false
            }
        }
    }
}

/**
 * Generic gateway OAuth login — the desktop interactive login window ported
 * to a WebView: loads the protected root so the gate auto-SSOs (or shows its
 * chooser), finishes the instant the gateway session cookies land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuthLoginScreen(baseUrl: String, onDone: () -> Unit, onCancel: () -> Unit) {
    val vm: OAuthLoginViewModel = viewModel()
    val p = Hermes

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gateway sign-in", color = p.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
                navigationIcon = {
                    HermesButton("Back", onClick = onCancel, variant = HermesVariant.Text, size = HermesSize.Sm)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HermesLayout.PAGE_INSET_X.dp),
        ) {
            Text(
                "Complete sign-in below. This closes automatically when done.",
                fontSize = 13.sp,
                color = p.textSecondary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            vm.error?.let {
                Text(it, fontSize = 13.sp, color = p.red, modifier = Modifier.padding(vertical = 4.dp))
            }
            GatewayWebLogin(
                baseUrl = baseUrl,
                modifier = Modifier.weight(1f),
                cookiesFor = { vm.agentCookies(baseUrl) },
                onSessionCookies = { vm.finish(baseUrl, onDone = { onDone() }) },
            )
        }
    }
}
