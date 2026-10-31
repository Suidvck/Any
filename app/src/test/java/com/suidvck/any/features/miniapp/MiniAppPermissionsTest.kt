package com.suidvck.any.features.miniapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniAppPermissionsTest {

    @Test
    fun defaultsKeepBasicAppsWorkingAndAskBeforePrivateAccess() {
        val state = MiniAppPermissions.State()
        assertTrue(state.javaScript)
        assertTrue(state.storage)
        assertTrue(state.network)
        assertTrue(state.downloads)
        assertFalse(state.media)
        assertFalse(state.location)
        assertEquals(4, state.enabledCount)
    }

    @Test
    fun enabledCountFollowsEveryToggle() {
        val state = MiniAppPermissions.State(
            javaScript = false,
            storage = false,
            network = false,
            downloads = false,
            media = true,
            location = true
        )
        assertEquals(2, state.enabledCount)
        assertEquals(
            MiniAppPermissions.TOGGLE_COUNT,
            MiniAppPermissions.State().enabledCount + 2
        )
    }

    @Test
    fun onlyTheSyntheticMiniAppOriginIsTrusted() {
        assertTrue(MiniAppPermissions.isMiniAppHost("app-123.anyapp.local"))
        assertTrue(MiniAppPermissions.isMiniAppHost("APP-123.ANYAPP.LOCAL"))
        assertTrue(MiniAppPermissions.isMiniAppHost("anyapp.local"))
        assertFalse(MiniAppPermissions.isMiniAppHost("anyapp.local.example.com"))
        assertFalse(MiniAppPermissions.isMiniAppHost("evilanyapp.local"))
        assertFalse(MiniAppPermissions.isMiniAppHost(null))
        assertFalse(MiniAppPermissions.isMiniAppHost(""))
    }
}
