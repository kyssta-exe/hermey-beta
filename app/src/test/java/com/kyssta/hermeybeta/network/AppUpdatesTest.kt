package com.kyssta.hermeybeta.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tag-vs-build comparison behind the Settings → About updater. */
class AppUpdatesTest {

    @Test
    fun newerTagDetected() {
        assertTrue(AppUpdates.isNewer("v0.5.0", "0.4.0"))
        assertTrue(AppUpdates.isNewer("v0.4.1", "0.4.0"))
        assertTrue(AppUpdates.isNewer("v0.10.0", "0.9.9"))
    }

    @Test
    fun currentOrOlderTagIgnored() {
        assertFalse(AppUpdates.isNewer("v0.4.0", "0.4.0"))
        assertFalse(AppUpdates.isNewer("0.4.0", "0.4.0"))
        assertFalse(AppUpdates.isNewer("v0.3.0", "0.4.0"))
        assertFalse(AppUpdates.isNewer("", "0.4.0"))
    }
}
