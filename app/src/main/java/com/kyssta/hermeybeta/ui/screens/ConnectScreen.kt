package com.kyssta.hermeybeta.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.auth.ConnectionStore
import com.kyssta.hermeybeta.auth.GatewayMode
import com.kyssta.hermeybeta.auth.ServerConnection
import com.kyssta.hermeybeta.network.DEFAULT_CLOUD_BASE_URL
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.normalizeRemoteBaseUrl
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.components.SegmentedControl
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.launch

class ConnectViewModel(app: Application) : AndroidViewModel(app) {
    val store = ConnectionStore(app)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    /** Remote or cloud login. Returns the activated connection, or null. */
    fun connect(
        mode: GatewayMode,
        baseUrl: String,
        username: String,
        password: String,
        onDone: (ServerConnection) -> Unit,
    ) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val norm = normalizeRemoteBaseUrl(baseUrl)
                // Reuse a saved entry for this URL when one exists.
                val conn = store.connections.value.find { it.baseUrl == norm && it.mode == mode }
                    ?: store.save(ServerConnection(mode = mode, baseUrl = norm, username = username.ifBlank { null }))
                val api = SessionRepository.apiFor(conn)
                if (username.isNotBlank() || password.isNotBlank()) {
                    val providers = api.authProviders()
                    val provider = providers.firstOrNull { it.supportsPassword == true }?.name
                        ?: providers.firstOrNull()?.name
                        ?: "password"
                    val ok = api.passwordLogin(provider, username, password)
                    if (!ok) throw IllegalArgumentException("Invalid username or password")
                    if (username.isNotBlank() && conn.username != username) {
                        store.save(conn.copy(username = username))
                    }
                } else {
                    // Token-less connect: verify the existing cookie session is live.
                    api.authMe()
                }
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
 * Gateway connect screen. Two modes only — remote (self-hosted) and cloud
 * (Nous-hosted) — mirroring the desktop connection switch minus local mode,
 * which cannot exist on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(onConnected: () -> Unit) {
    val vm: ConnectViewModel = viewModel()
    val p = Hermes
    var modeIdx by mutableIntStateOf(0)
    var serverUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    // Deep-link ticket capture (cloud OAuth return) is handled by pasting the
    // token as the password — the password-login provider accepts it.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect", color = p.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.sidebar),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HermesLayout.PAGE_INSET_X.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Hermey Beta",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = p.textPrimary,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                "Chat with your Hermes agent through a remote or cloud gateway. Nothing runs on this phone.",
                fontSize = 14.sp,
                color = p.textSecondary,
            )
            SegmentedControl(
                options = listOf("Remote Gateway", "Cloud Gateway"),
                selected = modeIdx,
                onSelect = {
                    modeIdx = it
                    if (it == 1 && serverUrl.isBlank()) serverUrl = DEFAULT_CLOUD_BASE_URL
                    if (it == 0 && serverUrl == DEFAULT_CLOUD_BASE_URL) serverUrl = ""
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text(if (modeIdx == 0) "Server URL" else "Cloud gateway URL") },
                placeholder = { Text(if (modeIdx == 0) "https://hermes.example.com" else DEFAULT_CLOUD_BASE_URL) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (or access token)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            if (vm.busy) {
                Loader()
            } else {
                HermesButton(
                    "Connect",
                    onClick = {
                        vm.connect(
                            if (modeIdx == 0) GatewayMode.REMOTE else GatewayMode.CLOUD,
                            serverUrl,
                            username,
                            password,
                            onDone = { onConnected() },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = serverUrl.isNotBlank(),
                )
            }
            vm.error?.let {
                ErrorState(title = "Connection failed", description = it)
            }
        }
    }
}
