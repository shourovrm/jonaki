package app.jonaki.tools.webfetch

import org.junit.Assert.assertTrue
import org.junit.Test

class PageTextExtractorTest {
    /**
     * A list page whose one long item looks like an article to Readability:
     * the phone check of hn.algolia.com (2026-10-03) got only a comment.
     */
    @Test
    fun aListPageKeepsItsListWhenReadabilityPicksOneLongItem() {
        val stories = (1..30).joinToString("") { number ->
            "<div class=\"story\"><a href=\"/s$number\">Story title number $number about something</a>" +
                "<span>$number points | someone | 2 years ago | 40 comments</span></div>"
        }
        val longComment = "<div class=\"comment\"><p>" + "Windows machines went into boot loops after the update. ".repeat(10) + "</p></div>"
        val html = "<html><head><title>All | Search</title></head><body><main>$stories$longComment</main></body></html>"

        val page = PageTextExtractor.extract("https://hn.algolia.com/", html)

        assertTrue(page.text.contains("Story title number 1 about something"))
        assertTrue(page.text.contains("Story title number 30 about something"))
    }

    @Test
    fun anArticleStillLosesItsMenus() {
        val menu = (1..8).joinToString("") { number -> "<li><a href=\"/m$number\">Menu $number</a></li>" }
        val paragraphs = (1..12).joinToString("") { number ->
            "<p>Paragraph $number of the article explains the measurement in plain words, with the numbers it found.</p>"
        }
        val html = "<html><head><title>Report</title></head><body><nav><ul>$menu</ul></nav>" +
            "<article><h1>Report</h1>$paragraphs</article></body></html>"

        val page = PageTextExtractor.extract("https://example.org/report", html)

        assertTrue(page.text.contains("Paragraph 12 of the article"))
        assertTrue(!page.text.contains("Menu 3"))
    }
}
