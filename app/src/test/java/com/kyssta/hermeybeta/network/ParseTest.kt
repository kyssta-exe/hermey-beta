package com.kyssta.hermeybeta.network

import com.kyssta.hermeybeta.ui.screens.formatSize
import com.kyssta.hermeybeta.ui.screens.formatUptime
import com.kyssta.hermeybeta.ui.screens.parseMcpCatalog
import com.kyssta.hermeybeta.ui.screens.parseToolEntries
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tolerant-parser invariants for the slice-2 surfaces. */
class ParseTest {

    @Test
    fun files() {
        val l = parseFileListing(
            JSONObject("""{"path":"/","parent":null,"entries":[{"name":"a","path":"/a","is_directory":true,"mtime":1},{"name":"f.txt","path":"/f.txt","is_directory":false,"size":12,"mime_type":"text/plain"}]}"""),
        )
        assertEquals(2, l.entries.size)
        assertTrue(l.entries[0].isDirectory)
        assertEquals("text/plain", l.entries[1].mime)
        assertEquals("12B", formatSize(12))
        assertEquals("2k", formatSize(2048))
    }

    @Test
    fun messaging() {
        val ps = parseMsgPlatforms(
            JSONObject("""{"platforms":[{"id":"telegram","name":"Telegram","enabled":true,"configured":true,"state":"connected","unknown_future":"x"}]}"""),
        )
        assertEquals(1, ps.size)
        assertEquals("connected", ps[0].state)
    }

    @Test
    fun pairing() {
        val (pending, approved) = parsePairing(
            JSONObject("""{"pending":[{"platform":"t","user_id":"1","code":"abc"}],"approved":[{"platform":"t","user_id":"2"}]}"""),
        )
        assertEquals(1, pending.size)
        assertEquals("abc", pending[0].code)
        assertEquals(1, approved.size)
    }

    @Test
    fun mcp() {
        val ss = parseMcpServers(
            JSONObject("""{"servers":[{"name":"s","transport":"http","enabled":true,"tools":["a","b"]}]}"""),
        )
        assertEquals(listOf("a", "b"), ss[0].tools)
    }

    @Test
    fun analytics() {
        val (models, totals) = parseModelsAnalytics(
            JSONObject("""{"models":[{"model":"m","sessions":2,"calls":5}],"totals":{"total_api_calls":5}}"""),
        )
        assertEquals(1, models.size)
        assertEquals(5, models[0].calls)
        assertEquals(5, totals?.optInt("total_api_calls"))
    }

    @Test
    fun profilesAndSpawn() {
        val ps = parseProfiles(
            JSONObject("""{"profiles":[{"name":"default","is_default":true,"skill_count":3}]}"""),
        )
        assertTrue(ps[0].isDefault)
        val snaps = parseSpawnEntries(
            JSONObject("""{"entries":[{"session_id":"s","subagents":[{},{}]}]}"""),
        )
        assertEquals(2, snaps[0].subagentCount)
        assertEquals("1h 1m", formatUptime(3700))
        assertEquals("45s", formatUptime(45))
    }

    @Test
    fun mcpCatalogAndTools() {
        val cat = parseMcpCatalog(
            JSONObject("""{"entries":[{"name":"gh","description":"GitHub","transport":"http","required_env":[{"name":"GH_TOKEN","prompt":"GitHub token"}]}]}"""),
        )
        assertEquals(1, cat.size)
        assertEquals("GH_TOKEN", cat[0].requiredEnv[0].first)
        val tools = parseToolEntries(
            JSONObject("""{"tools":[{"name":"read","description":"Read a file"},"plain"]}"""),
            "tools",
        )
        assertEquals(2, tools.size)
        assertEquals("", tools[1].second)
    }
}
