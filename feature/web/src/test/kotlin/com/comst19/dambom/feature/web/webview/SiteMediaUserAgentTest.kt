package com.comst19.dambom.feature.web.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMediaUserAgentTest {
    @Test
    fun `desktop sites retain browser version while removing Android WebView and Mobile tokens`() {
        listOf("m.vk.com", "www.viki.com", "fullhdfilmizlesene.net", "m.facebook.com", "zee5.onelink.me", "tver.jp")
            .forEach { host ->
                val agent = siteMediaUserAgent("https://$host/watch", MOBILE_AGENT)
                assertFalse(agent.contains("Android"))
                assertFalse(agent.contains("Mobile"))
                assertFalse(agent.contains("; wv"))
                assertFalse(agent.contains("Version/4.0"))
                assertTrue(agent.contains("Chrome/150.0.0.0"))
                assertTrue(agent.contains("X11; Linux x86_64"))
            }
    }

    @Test
    fun `normal sites lookalike domains and invalid URLs keep the original user agent`() {
        listOf("https://example.org/", "https://viki.com.attacker.org/", "https://notviki.com/", "not a URL")
            .forEach { url -> assertEquals(MOBILE_AGENT, siteMediaUserAgent(url, MOBILE_AGENT)) }
    }
}

private const val MOBILE_AGENT =
    "Mozilla/5.0 (Linux; Android 15; Pixel; wv) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Version/4.0 Chrome/150.0.0.0 Mobile Safari/537.36"
