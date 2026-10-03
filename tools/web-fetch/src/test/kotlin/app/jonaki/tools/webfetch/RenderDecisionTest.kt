package app.jonaki.tools.webfetch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderDecisionTest {
    private val script = "<script src=/app.js></script>"

    private fun page(text: String) = ExtractedPage(title = "Title", text = text)

    @Test
    fun emptyPageWithScriptsIsRendered() {
        assertTrue(RenderDecision.needsRendering(page(""), "<div id=root></div>$script"))
    }

    @Test
    fun shortPageWithScriptsIsRendered() {
        assertTrue(RenderDecision.needsRendering(page("Loading…"), "<p>Loading…</p>$script"))
    }

    @Test
    fun shortPageWithoutScriptsIsNotRendered() {
        val exampleDotCom = "This domain is for use in illustrative examples in documents. More information..."

        assertFalse(RenderDecision.needsRendering(page(exampleDotCom), "<p>$exampleDotCom</p>"))
    }

    @Test
    fun scriptTagIsFoundInAnyLetterCase() {
        assertTrue(RenderDecision.needsRendering(page(""), "<SCRIPT>start()</SCRIPT>"))
    }

    @Test
    fun javaScriptWarningIsRenderedEvenWithoutScriptTags() {
        val warning = "We're sorry but this site doesn't work properly without JavaScript. Please enable JavaScript to continue. ".repeat(3)

        assertTrue(RenderDecision.needsRendering(page(warning), "<p>$warning</p>"))
    }

    @Test
    fun javaScriptRequiredWordingIsRecognised() {
        val warning = "JavaScript is required to view this page. ".repeat(6)

        assertTrue(RenderDecision.needsRendering(page(warning), "<p>$warning</p>"))
    }

    @Test
    fun longPageThatMentionsJavaScriptIsNotRendered() {
        val article = "Browsers let users enable JavaScript per site. ".repeat(40)

        assertFalse(RenderDecision.needsRendering(page(article), "<article>$article</article>$script"))
    }

    @Test
    fun mediumPageWithScriptsAndNoWarningIsNotRendered() {
        val text = "Dhaka metro timetable: trains every eight minutes. ".repeat(6)

        assertFalse(RenderDecision.needsRendering(page(text), "<p>$text</p>$script"))
    }
}
