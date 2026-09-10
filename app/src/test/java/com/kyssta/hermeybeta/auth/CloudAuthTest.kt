package com.kyssta.hermeybeta.auth

import com.kyssta.hermeybeta.network.GatewayCookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAuthTest {

    @Test
    fun privyDetection() {
        assertTrue(hasPrivySession("privy-token=jwt; Path=/"))
        assertTrue(hasPrivySession("a=b; __Secure-privy-token=x"))
        assertTrue(hasPrivySession("privy-session=old"))
        assertFalse(hasPrivySession("privy-token=; Path=/"))
        assertFalse(hasPrivySession(null))
        assertFalse(hasPrivySession("hermes_session_at=abc"))
    }

    @Test
    fun gatewayDetection() {
        assertTrue(hasGatewaySession("hermes_session_at=abc"))
        assertTrue(hasGatewaySession("__Host-hermes_session_rt=xyz"))
        assertFalse(hasGatewaySession("hermes_session_at="))
        assertFalse(hasGatewaySession("privy-token=jwt"))
    }

    @Test
    fun agentsAndOrgs() {
        val agents = parseCloudAgents(
            JSONObject("""{"agents":[{"id":"a1","name":"Home","status":"online","dashboardUrl":"https://gw.example.com"}],"org":{"id":"o1"}}"""),
        )
        assertEquals(1, agents.size)
        assertEquals("https://gw.example.com", agents[0].dashboardUrl)
        val alias = parseCloudAgents(
            JSONObject("""{"agents":[{"id":"a2","dashboard_url":"https://g2.example.com"}]}"""),
        )
        assertEquals("https://g2.example.com", alias[0].dashboardUrl)
        assertEquals("a2", alias[0].name)
        val orgs = parseCloudOrgs(
            JSONObject("""{"orgs":[{"id":"o1","slug":"personal","name":"Personal"}]}"""),
        )
        assertEquals("personal", orgs[0].slug)
        assertTrue(parseCloudAgents(JSONObject("{}")).isEmpty())
    }

    @Test
    fun injectRoundTrip() {
        injectCookies("https://gw.example.com", "hermes_session_at=abc; other=1")
        val loaded = GatewayCookieJar.loadForRequest("https://gw.example.com/".toHttpUrl())
        assertTrue(loaded.any { it.name == "hermes_session_at" && it.value == "abc" })
        GatewayCookieJar.clear()
    }
}
