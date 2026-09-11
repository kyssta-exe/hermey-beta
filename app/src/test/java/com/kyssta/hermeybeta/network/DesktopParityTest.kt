package com.kyssta.hermeybeta.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop-parity invariants for webhooks / artifacts / session-import. */
class DesktopParityTest {

    @Test
    fun webhooks() {
        val s = parseWebhooks(
            JSONObject("""{"enabled":true,"subscriptions":[{"name":"gh","description":"push","events":["push"],"deliver":"log","enabled":true,"url":"https://x/hook","unknown_future":1}]}"""),
        )
        assertTrue(s.enabled)
        assertEquals(1, s.subscriptions.size)
        assertEquals("gh", s.subscriptions[0].name)
        assertEquals("log", s.subscriptions[0].deliver)
        // tolerant alternate key
        val s2 = parseWebhooks(JSONObject("""{"webhooks":[{"name":"a"}]}"""))
        assertEquals(1, s2.subscriptions.size)
    }

    @Test
    fun artifacts() {
        assertEquals("image", artifactKindFor("artifact_image", "https://x/a.png"))
        assertEquals("link", artifactKindFor("url", "https://example.com/x"))
        assertEquals("file", artifactKindFor("saved_to", "/tmp/out.pdf"))
        assertNull(artifactKindFor("random_key", "hello world"))
        assertNull(artifactKindFor("saved_to", "x".repeat(3000)))
        assertEquals("out.pdf", artifactLabelFor("https://x/out.pdf?sig=1"))
    }

    @Test
    fun foreign() {
        val (list, host) = parseForeignSessions(
            JSONObject("""{"host":"mac","sessions":[{"id":"1","source":"claude","title":"T","cwd":"/a","excerpt":"e","turn_count":3}]}"""),
        )
        assertEquals(1, list.size)
        assertEquals("claude", list[0].source)
        assertEquals(3, list[0].turnCount)
        assertEquals("mac", host)
    }
}
