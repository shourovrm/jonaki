package app.jonaki.tools.webfetch

import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup

internal data class ExtractedPage(val title: String, val text: String)

/**
 * Finds the main text of an HTML page with Readability4J (the Firefox reader
 * view algorithm), and falls back to the whole body when the page is not an
 * article, for example a list or a search page.
 */
internal object PageTextExtractor {
    /** Below this, Readability probably missed the content and the body is a better source. */
    private const val MINIMUM_ARTICLE_CHARACTERS = 300

    fun extract(url: String, html: String): ExtractedPage {
        val document = Jsoup.parse(html, url)
        val pageTitle = document.title()

        val article = runCatching { Readability4J(url, html).parse() }.getOrNull()
        val articleElement = article?.articleContent
        if (articleElement != null) {
            val articleText = HtmlToText.convert(articleElement)
            if (articleText.length >= MINIMUM_ARTICLE_CHARACTERS) {
                val title = article.title?.takeIf { it.isNotBlank() } ?: pageTitle
                return ExtractedPage(title, articleText)
            }
        }
        return ExtractedPage(pageTitle, HtmlToText.convert(document.body()))
    }
}
