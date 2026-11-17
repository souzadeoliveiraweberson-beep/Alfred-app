package com.example.ui.navigation

import android.content.Intent
import android.net.Uri
import com.example.data.model.FinanceScope
import com.example.notifications.NotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlfredDeepLinkParserTest {

    @Test
    fun `parse alfred chat uri with voice query activates route and autoStartVoice`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("alfred://chat?voice=true&scope=business"))
        val action = AlfredDeepLinkParser.parse(intent)

        assertEquals(ROUTE_ALFRED, action?.route)
        assertEquals(FinanceScope.BUSINESS, action?.targetScope)
        assertTrue(action?.autoStartVoice == true)
        assertFalse(action?.openQuickAdd == true)
    }

    @Test
    fun `parse quick add and business uris resolve correctly`() {
        val quickAdd = AlfredDeepLinkParser.parse(Intent(Intent.ACTION_VIEW, Uri.parse("alfred://quick-add")))
        assertEquals(ROUTE_FINANCES, quickAdd?.route)
        assertTrue(quickAdd?.openQuickAdd == true)

        val business = AlfredDeepLinkParser.parse(Intent(Intent.ACTION_VIEW, Uri.parse("alfred://business")))
        assertEquals(ROUTE_BUSINESS, business?.route)
        assertEquals(FinanceScope.BUSINESS, business?.targetScope)
    }

    @Test
    fun `parse notification extra route works and unrelated intent returns null`() {
        val notifIntent = Intent().apply {
            putExtra(NotificationChannels.EXTRA_NAV_ROUTE, ROUTE_ANALYTICS)
        }
        val action = AlfredDeepLinkParser.parse(notifIntent)
        assertEquals(ROUTE_ANALYTICS, action?.route)
        assertFalse(action?.autoStartVoice == true)

        assertNull(AlfredDeepLinkParser.parse(Intent(Intent.ACTION_MAIN)))
    }
}
