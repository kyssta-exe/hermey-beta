package com.kyssta.hermeybeta.ui.screens

import android.annotation.SuppressLint
import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyssta.hermeybeta.auth.CloudAgent
import com.kyssta.hermeybeta.auth.CloudOrg
import com.kyssta.hermeybeta.auth.ConnectionStore
import com.kyssta.hermeybeta.auth.GatewayMode
import com.kyssta.hermeybeta.auth.NOUS_PORTAL_BASE_URL
import com.kyssta.hermeybeta.auth.ServerConnection
import com.kyssta.hermeybeta.auth.hasGatewaySession
import com.kyssta.hermeybeta.auth.hasPrivySession
import com.kyssta.hermeybeta.auth.injectCookies
import com.kyssta.hermeybeta.auth.parseCloudAgents
import com.kyssta.hermeybeta.auth.parseCloudOrgs
import com.kyssta.hermeybeta.network.GatewayApi
import com.kyssta.hermeybeta.network.GatewayCookieJar
import com.kyssta.hermeybeta.network.gatewayErrorMessage
import com.kyssta.hermeybeta.network.normalizeRemoteBaseUrl
import com.kyssta.hermeybeta.session.SessionRepository
import com.kyssta.hermeybeta.ui.components.EmptyState
import com.kyssta.hermeybeta.ui.components.ErrorState
import com.kyssta.hermeybeta.ui.components.HermesButton
import com.kyssta.hermeybeta.ui.components.HermesSize
import com.kyssta.hermeybeta.ui.components.HermesVariant
import com.kyssta.hermeybeta.ui.components.Loader
import com.kyssta.hermeybeta.ui.theme.Hermes
import com.kyssta.hermeybeta.ui.theme.HermesLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private enum class CloudStage { PORTAL, AGENTS, CASCADE }

class CloudSignInViewModel(app: Application) : AndroidViewModel(app) {
    val store = ConnectionStore(app)
    var stage by mutableStateOf(CloudStage.PORTAL)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var agents by mutableStateOf<List<CloudAgent>>(emptyList())
    var orgs by mutableStateOf<List<CloudOrg>>(emptyList())
    var orgSlug by mutableStateOf<String?>(null)
    var cascadeAgent by mutableStateOf<CloudAgent?>(null)

    private val portalHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cookieJar(GatewayCookieJar)
        .build()

    fun portalCookies(): String =
        CookieManager.getInstance().getCookie(NOUS_PORTAL_BASE_URL) ?: ""

    fun agentCookies(url: String): String =
        CookieManager.getInstance().getCookie(url) ?: ""

    /** Portal session found in the WebView jar — harvest + discover agents. */
    fun onPortalSession() {
        if (busy || stage != CloudStage.PORTAL) return
        busy = true
        error = null
        injectCookies(NOUS_PORTAL_BASE_URL, portalCookies())
        viewModelScope.launch {
            discover()
            busy = false
        }
    }

    fun discover(org: String? = orgSlug) {
        viewModelScope.launch {
            error = null
            try {
                val url = NOUS_PORTAL_BASE_URL + "/api/agents" +
                    (if (!org.isNullOrBlank()) "?org=${java.net.URLEncoder.encode(org, "UTF-8")}" else "")
                val req = Request.Builder().url(url).get().build()
                portalHttp.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    when (resp.code) {
                        200 -> {
                            val root = JSONObject(body)
                            agents = parseCloudAgents(root)
                            orgs = emptyList()
                            stage = CloudStage.AGENTS
                        }
                        401 -> {
                            error = "Portal session expired — sign in again."
                            stage = CloudStage.PORTAL
                        }
                        409 -> {
                            // Multi-org picker (mirrors desktop NAS 409 handling).
                            val found = parseCloudOrgs(JSONObject(body))
                            if (found.isNotEmpty()) {
                                orgs = found
                            } else {
                                error = "Choose an organisation to continue."
                            }
                        }
                        else -> error = "Discovery failed (HTTP ${resp.code})"
                    }
                }
            } catch (e: Exception) {
                error = gatewayErrorMessage(e)
            }
        }
    }

    fun pickOrg(org: CloudOrg) {
        orgSlug = org.slug ?: org.id
        orgs = emptyList()
        discover(orgSlug)
    }

    fun startCascade(agent: CloudAgent) {
        cascadeAgent = agent
        error = null
        stage = CloudStage.CASCADE
    }

    /** Agent gateway cookies landed — verify, save, and finish. */
    fun onAgentSession(url: String, onDone: (ServerConnection) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val norm = normalizeRemoteBaseUrl(url)
                injectCookies(norm, agentCookies(norm))
                // No reauth hook: a 401 here means the cascade didn't finish.
                GatewayApi(norm, {}).authMe()
                val conn = store.save(
                    ServerConnection(mode = GatewayMode.CLOUD, baseUrl = norm),
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
 * Hermes Cloud sign-in with the Nous portal — mirrors the desktop cloud flow:
 * portal sign-in once, agent discovery, silent per-agent cascade. The WebView
 * is the isolated cookie surface; cookies are harvested into the app jar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSignInScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val vm: CloudSignInViewModel = viewModel()
    val p = Hermes

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (vm.stage) {
                            CloudStage.PORTAL -> "Sign in with Nous"
                            CloudStage.AGENTS -> "Your agents"
                            CloudStage.CASCADE -> vm.cascadeAgent?.name ?: "Connecting…"
                        },
                        color = p.textPrimary,
                    )
                },
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
            vm.error?.let {
                Text(it, fontSize = 13.sp, color = p.red, modifier = Modifier.padding(vertical = 4.dp))
            }
            when (vm.stage) {
                CloudStage.PORTAL -> {
                    Text(
                        "Sign in on the Nous portal, then return here — discovery starts automatically.",
                        fontSize = 13.sp,
                        color = p.textSecondary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    AuthWebView(
                        url = NOUS_PORTAL_BASE_URL,
                        modifier = Modifier.weight(1f),
                        onPageDone = {
                            if (hasPrivySession(vm.portalCookies())) vm.onPortalSession()
                        },
                    )
                    // Belt-and-braces poll for IDPs that finish via in-page JS.
                    LaunchedEffect(Unit) {
                        while (vm.stage == CloudStage.PORTAL) {
                            delay(750)
                            if (hasPrivySession(vm.portalCookies())) vm.onPortalSession()
                        }
                    }
                    if (vm.busy) Loader(modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp))
                }
                CloudStage.AGENTS -> {
                    if (vm.orgs.isNotEmpty()) {
                        Text("Choose an organisation", fontSize = 13.sp, color = p.textSecondary)
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(vm.orgs, key = { it.id }) { org ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(org.name, fontSize = 15.sp, color = p.textPrimary, modifier = Modifier.weight(1f))
                                    HermesButton("Use", onClick = { vm.pickOrg(org) }, variant = HermesVariant.Secondary, size = HermesSize.Sm)
                                }
                                HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                            }
                        }
                    } else if (vm.agents.isEmpty()) {
                        EmptyState(
                            title = "No cloud agents",
                            description = "Create one at portal.nousresearch.com/agents, then refresh.",
                            actionLabel = "Refresh",
                            onAction = { vm.discover() },
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(vm.agents, key = { it.id }) { agent ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(agent.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.textPrimary)
                                        Text(agent.status, fontSize = 12.sp, color = p.textTertiary)
                                    }
                                    HermesButton(
                                        "Connect",
                                        onClick = { vm.startCascade(agent) },
                                        variant = HermesVariant.Secondary,
                                        size = HermesSize.Sm,
                                        enabled = agent.dashboardUrl != null,
                                    )
                                }
                                HorizontalDivider(color = p.strokeTertiary, thickness = 0.5.dp)
                            }
                        }
                    }
                }
                CloudStage.CASCADE -> {
                    val agent = vm.cascadeAgent
                    if (agent?.dashboardUrl == null) {
                        ErrorState(title = "Agent has no gateway URL", retryLabel = "Back", onRetry = { vm.stage = CloudStage.AGENTS })
                    } else {
                        Text(
                            "Connecting to ${agent.name}…",
                            fontSize = 13.sp,
                            color = p.textSecondary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        AuthWebView(
                            url = agent.dashboardUrl,
                            modifier = Modifier.weight(1f),
                            onPageDone = {
                                if (hasGatewaySession(vm.agentCookies(agent.dashboardUrl))) {
                                    vm.onAgentSession(agent.dashboardUrl, onDone = { onDone() })
                                }
                            },
                        )
                        LaunchedEffect(agent.id) {
                            while (vm.stage == CloudStage.CASCADE) {
                                delay(750)
                                if (hasGatewaySession(vm.agentCookies(agent.dashboardUrl))) {
                                    vm.onAgentSession(agent.dashboardUrl, onDone = { onDone() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Cookie-isolated browser surface (the desktop OAuth partition equivalent). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AuthWebView(url: String, modifier: Modifier = Modifier, onPageDone: () -> Unit) {
    var loaded by remember(url) { mutableStateOf<String?>(null) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    acceptThirdPartyCookies(this@WebView, true)
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        onPageDone()
                    }
                }
            }
        },
        update = { wv ->
            if (loaded != url) {
                loaded = url
                wv.loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
        modifier = modifier,
    )
}
