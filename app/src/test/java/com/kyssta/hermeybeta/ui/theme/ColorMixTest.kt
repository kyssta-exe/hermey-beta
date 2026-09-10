package com.kyssta.hermeybeta.ui.theme

import com.kyssta.hermeybeta.ui.screens.formatUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port invariants for the desktop color.ts math. */
class ColorMixTest {

    @Test
    fun hexRoundTrip() {
        assertEquals(Triple(0, 83, 253), hexToRgb("#0053fd"))
        assertEquals(Triple(255, 255, 255), hexToRgb("#ffffff"))
        assertNull(hexToRgb("#fff"))
        assertNull(hexToRgb("not-a-color"))
        assertEquals("#0053fd", rgbToHex(0, 83, 253))
    }

    @Test
    fun mixIdenticalIsIdentity() {
        assertEquals("#0053fd", mix("#0053fd", "#0053fd", 0.37))
    }

    @Test
    fun mixBlackWhiteMidpoint() {
        // Math.round half-up, same as the TS original.
        assertEquals("#808080", mix("#000000", "#ffffff", 0.5))
    }

    @Test
    fun mixEndpoints() {
        assertEquals("#000000", mix("#000000", "#ffffff", 0.0))
        assertEquals("#ffffff", mix("#000000", "#ffffff", 1.0))
    }

    @Test
    fun readability() {
        assertEquals("#161616", readableOn("#ffffff"))
        assertEquals("#ffffff", readableOn("#000000"))
        assertEquals("#ffffff", readableOn("#0053fd"))
    }

    @Test
    fun contrastBlackWhiteIs21() {
        assertTrue(contrastRatio("#000000", "#ffffff") > 20.9)
    }

    @Test
    fun ensureContrastClears() {
        val fixed = ensureContrast("#777777", "#ffffff", 4.5)
        assertTrue(contrastRatio(fixed, "#ffffff") >= 4.5)
    }

    @Test
    fun usageLabelFormats() {
        assertEquals("0 calls · 0 tokens", formatUsage(0, 0))
        assertEquals("12 calls · 45.2k tokens", formatUsage(12, 45213))
        assertEquals("3 calls · 2.1m tokens", formatUsage(3, 2_100_000))
    }

    @Test
    fun paletteSharesFixedSurfaces() {
        // Cinematic mobile scheme is dark-first and fixed; only the flag differs.
        val light = hermesPalette(false)
        val dark = hermesPalette(true)
        assertEquals(light.background, dark.background)
        assertEquals(light.card, dark.card)
        assertEquals(false, light.dark)
        assertEquals(true, dark.dark)
    }
}
