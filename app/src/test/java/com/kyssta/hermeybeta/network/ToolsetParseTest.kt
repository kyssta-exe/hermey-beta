package com.kyssta.hermeybeta.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Toolset list parsing behind Settings → Tools (mirrors web_server.py shapes). */
class ToolsetParseTest {

    @Test
    fun parsesBareArray() {
        val rows = parseToolsets(
            """[{"name":"ddgs","label":"DuckDuckGo","description":"Web search","enabled":true,"configured":false}]""",
        )
        assertEquals(1, rows.size)
        assertEquals("ddgs", rows[0].name)
        assertEquals("DuckDuckGo", rows[0].label)
        assertTrue(rows[0].enabled)
        assertFalse(rows[0].configured)
    }

    @Test
    fun toleratesMissingFields() {
        val rows = parseToolsets("""[{"name":"x"}]""")
        assertEquals(1, rows.size)
        assertEquals(null, rows[0].label)
        assertFalse(rows[0].enabled)
        assertEquals(emptyList<ToolsetInfo>(), parseToolsets("""{"toolsets":[]}"""))
        assertEquals(emptyList<ToolsetInfo>(), parseToolsets("garbage"))
    }
}
