package app.jonaki.feature.artifact

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandaloneHtmlTest {
    private val library = "window.Chart = function () {};"

    @Test
    fun theChartScriptIsPutInsideThePage() {
        val html = """<head><script src="lib/chart.js"></script></head><body><script>new Chart()</script></body>"""

        assertEquals(
            """<head><script>window.Chart = function () {};</script></head><body><script>new Chart()</script></body>""",
            StandaloneHtml.withLibraries(html) { library },
        )
    }

    @Test
    fun quotesSpacesAndADotSlashAreAccepted() {
        val html = """<script  src='./lib/chart.js' ></script >"""

        assertEquals("<script>$library</script>", StandaloneHtml.withLibraries(html) { library })
    }

    @Test
    fun aPageWithoutTheLibraryIsLeftAlone() {
        assertNull(StandaloneHtml.withLibraries("<p>no charts</p>") { library })
    }
}
