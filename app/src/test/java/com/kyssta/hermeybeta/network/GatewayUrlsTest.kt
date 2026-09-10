package com.kyssta.hermeybeta.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The exact strings the app dials — mirrors desktop connection-config tests. */
class GatewayUrlsTest {

    @Test
    fun normalizeStripsAndRequiresHttp() {
        assertEquals("https://h.example.com/prefix", normalizeRemoteBaseUrl("https://h.example.com/prefix/"))
        assertEquals("http://10.0.0.5:9119", normalizeRemoteBaseUrl("http://10.0.0.5:9119/?x=1#frag"))
        try {
            normalizeRemoteBaseUrl("ftp://h.example.com")
            fail("expected rejection")
        } catch (_: IllegalArgumentException) {
        }
        try {
            normalizeRemoteBaseUrl("   ")
            fail("expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun wsTicketUrl() {
        assertEquals(
            "wss://h.example.com/api/ws?ticket=abc",
            buildGatewayWsUrlWithTicket("https://h.example.com", "abc"),
        )
        // Path prefixes survive (gateway behind a sub-path).
        assertTrue(
            buildGatewayWsUrlWithTicket("http://h.example.com/hermes/", "a b")
                .startsWith("ws://h.example.com/hermes/api/ws?ticket="),
        )
    }

    @Test
    fun wsTokenUrl() {
        assertEquals(
            "wss://h.example.com/api/ws?token=t",
            buildGatewayWsUrl("https://h.example.com/", "t"),
        )
    }

    @Test
    fun buildUrlJoins() {
        assertEquals(
            "https://h.example.com/api/sessions",
            buildUrl("https://h.example.com/", "/api/sessions"),
        )
        assertEquals(
            "https://h.example.com/api/sessions/x/messages?limit=50",
            buildUrl("https://h.example.com", "api/sessions/x/messages", mapOf("limit" to "50", "offset" to null)),
        )
    }

    @Test
    fun tolerantParsersIgnoreUnknownShapes() {
        assertTrue(parseSessions("""{"sessions":[{"id":"a","new_field":{"x":1}}],"total":1}""").size == 1)
        assertTrue(parseSkills("""[{"name":"s"}]""").size == 1)
        assertTrue(parseCrons("""[]""").isEmpty())
        assertTrue(parseMessages("""{"messages":[]}""").isEmpty())
        assertEquals("manual", parseCrons("""[{"id":"1"}]""").first().schedule)
    }
}
