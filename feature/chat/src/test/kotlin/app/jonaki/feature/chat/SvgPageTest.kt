package app.jonaki.feature.chat

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgPageTest {
    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><script>alert(1)</script><path d="M0 0h10v10z"/></svg>"""

    @Test
    fun theSvgIsOnlyEverADataImageNeverMarkupOfThePage() {
        val page = SvgPage.html(svg, allowsZoom = false)

        assertFalse(page.contains("<svg"))
        assertFalse(page.contains("alert"))
        val encoded = page.substringAfter("src=\"data:image/svg+xml;base64,").substringBefore('"')
        assertEquals(svg, String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
    }

    @Test
    fun theStrictPolicyIsInThePage() {
        val page = SvgPage.html(svg, allowsZoom = true)

        assertTrue(page.contains("""<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'">"""))
        assertFalse(page.contains("<script"))
    }

    @Test
    fun zoomIsAllowedOnlyInTheFullSizeView() {
        assertTrue(SvgPage.html(svg, allowsZoom = true).contains("user-scalable=yes"))
        assertTrue(SvgPage.html(svg, allowsZoom = false).contains("user-scalable=no"))
    }

    @Test
    fun aBanglaTextInTheSvgSurvivesTheEncoding() {
        val bangla = """<svg xmlns="http://www.w3.org/2000/svg"><text>জোনাকি</text></svg>"""
        val encoded = SvgPage.html(bangla, allowsZoom = false).substringAfter("base64,").substringBefore('"')
        assertEquals(bangla, String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
    }

    @Test
    fun theWebViewMayLoadOnlyDataAndBlankAddresses() {
        assertTrue(SvgRequestPolicy.allows("data:image/svg+xml;base64,AAAA"))
        assertTrue(SvgRequestPolicy.allows("about:blank"))
        assertTrue(SvgRequestPolicy.allows("DATA:text/html,x"))
        for (address in listOf("https://evil.example/a.png", "http://x/", "file:///data/data/app.jonaki/x", "content://app.jonaki/x", "javascript:alert(1)", "ftp://x", "", "evil.example")) {
            assertFalse(address, SvgRequestPolicy.allows(address))
        }
    }
}
