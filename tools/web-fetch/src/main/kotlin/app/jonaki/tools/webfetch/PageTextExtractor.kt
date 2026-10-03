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

    /**
     * On a list page Readability takes the longest item for the article. An
     * "article" this short that holds less than [LIST_PAGE_SHARE] of the
     * body's text is taken for such an item, and the body is used instead.
     */
    private const val SHORT_ARTICLE_CHARACTERS = 1_500
    private const val LIST_PAGE_SHARE = 0.25

    fun extract(url: String, html: String): ExtractedPage {
        val document = Jsoup.parse(html, url)
        val pageTitle = document.title()

        // Readability changes the document it reads, so the body text is taken first.
        val bodyText = HtmlToText.convert(document.body())
        val article = runCatching { Readability4J(url, html).parse() }.getOrNull()
        val articleElement = article?.articleContent
        if (articleElement != null) {
            val articleText = HtmlToText.convert(articleElement)
            val isLongEnough = articleText.length >= MINIMUM_ARTICLE_CHARACTERS
            val looksLikeOneListItem = articleText.length < SHORT_ARTICLE_CHARACTERS &&
                articleText.length < bodyText.length * LIST_PAGE_SHARE
            if (isLongEnough && !looksLikeOneListItem) {
                val title = article.title?.takeIf { it.isNotBlank() } ?: pageTitle
                return ExtractedPage(title, articleText)
            }
        }
        return ExtractedPage(pageTitle, bodyText)
    }
}
