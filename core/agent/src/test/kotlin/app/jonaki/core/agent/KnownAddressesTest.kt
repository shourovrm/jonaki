package app.jonaki.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownAddressesTest {
    @Test
    fun anAddressMatchesItselfInRunningText() {
        val text = "Look at https://example.com/page?id=7 for details."
        assertTrue(WebAddresses.appearsIn("https://example.com/page?id=7", text))
    }

    @Test
    fun schemeAndHostIgnoreCaseButPathAndQueryDoNot() {
        val text = "see https://Example.COM/Path?Q=1"
        assertTrue(WebAddresses.appearsIn("HTTPS://example.com/Path?Q=1", text))
        assertFalse(WebAddresses.appearsIn("https://example.com/path?Q=1", text))
        assertFalse(WebAddresses.appearsIn("https://example.com/Path?q=1", text))
    }

    @Test
    fun aTrailingSlashAndAFragmentDoNotMatter() {
        assertTrue(WebAddresses.appearsIn("https://example.com/a/", "https://example.com/a#top"))
        assertTrue(WebAddresses.appearsIn("https://example.com/a#section", "https://example.com/a/"))
    }

    @Test
    fun anAddressThatDiffersInTheQueryIsNotKnown() {
        assertFalse(WebAddresses.appearsIn("https://example.com/a?q=1&x=secret", "https://example.com/a?q=1"))
        assertFalse(WebAddresses.appearsIn("https://example.com/a?q=1", "https://example.com/a?q=1&x=2"))
    }

    @Test
    fun theSameHostIsNotEnough() {
        assertFalse(WebAddresses.appearsIn("https://example.com/other", "https://example.com/a"))
    }

    @Test
    fun anAddressThatIsOnlyTheStartOfALongerOneIsNotKnown() {
        assertFalse(WebAddresses.appearsIn("https://example.com/a", "https://example.com/abc"))
        assertFalse(WebAddresses.appearsIn("https://example.com", "https://example.com.evil.example/x"))
    }

    @Test
    fun httpAndHttpsAreDifferentAddresses() {
        assertFalse(WebAddresses.appearsIn("http://example.com/a", "https://example.com/a"))
    }

    @Test
    fun punctuationAndMarkdownAroundAnAddressAreNotPartOfIt() {
        assertEquals(
            listOf("https://a.example/x", "https://b.example/y", "https://c.example/z"),
            WebAddresses.addressesIn("Read https://a.example/x, then (https://b.example/y). [c](https://c.example/z)"),
        )
    }

    @Test
    fun aBracketInsideAnAddressStays() {
        assertTrue(WebAddresses.appearsIn("https://en.wikipedia.org/wiki/Foo_(bar)", "See https://en.wikipedia.org/wiki/Foo_(bar)."))
    }

    @Test
    fun textWithoutAddressesKnowsNone() {
        assertFalse(WebAddresses.appearsIn("https://example.com/a", "no address here"))
        assertFalse(WebAddresses.appearsIn("not an address", "not an address"))
    }
}
