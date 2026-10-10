package app.jonaki.tools.generatevectorimage

/**
 * Makes CSS (a `<style>` element or a `style` attribute) and attribute values
 * unable to load anything from outside the document. Only a reference to
 * an element of the same document (`url(#gradient)`) and a `data:image/` URI
 * may stay in a `url(...)`.
 */
internal object SvgStyles {
    /** Functions through which CSS can load a file without writing `url(`. */
    private val loadingFunctions = Regex("(?<![A-Za-z0-9_])(image-set|cross-fade|element|image|paint|src|expression)\\(", RegexOption.IGNORE_CASE)
    private val importRule = Regex("@import[^;{}]*;?", RegexOption.IGNORE_CASE)

    /**
     * Style text without `@import`, comments, outside `url(...)` and
     * calls of file-loading functions. Text with a backslash comes back empty: a CSS
     * escape can spell `url` or `@import` in ways this check does not
     * follow, so such text is not trusted.
     */
    fun cleanCss(css: String): String {
        if (css.contains('\\')) {
            return ""
        }
        val withoutComments = removeComments(css)
        val withoutImports = importRule.replace(withoutComments, "")
        val withoutOutsideUrls = neutraliseUrls(withoutImports)
        return removeLoadingFunctions(withoutOutsideUrls)
    }

    /** Replaces each call of a file-loading function, up to its closing bracket, with `none`; an unterminated call cuts the text. */
    private fun removeLoadingFunctions(css: String): String {
        val result = StringBuilder()
        var index = 0
        while (true) {
            val match = loadingFunctions.find(css, index)
            if (match == null) {
                result.append(css, index, css.length)
                break
            }
            result.append(css, index, match.range.first)
            val end = css.indexOf(')', match.range.last)
            if (end < 0) {
                break
            }
            result.append("none")
            index = end + 1
        }
        return result.toString()
    }

    /**
     * Replaces every `url(...)` that does not point inside the document with
     * `none`, which every property that takes a url also takes or ignores.
     * An unterminated `url(` cuts the text there.
     */
    fun neutraliseUrls(value: String): String {
        val result = StringBuilder()
        var index = 0
        while (true) {
            val start = value.indexOf("url(", index, ignoreCase = true)
            if (start < 0) {
                result.append(value, index, value.length)
                break
            }
            result.append(value, index, start)
            val end = value.indexOf(')', start)
            if (end < 0) {
                break
            }
            val argument = value.substring(start + "url(".length, end).trim().trim('\'', '"').trim()
            if (isInsideDocument(argument)) {
                result.append(value, start, end + 1)
            } else {
                result.append("none")
            }
            index = end + 1
        }
        return result.toString()
    }

    /** `#id` (same document) or a `data:image/` URI, which carries its picture in itself. */
    fun isInsideDocument(reference: String): Boolean {
        val trimmed = reference.trim()
        return trimmed.startsWith("#") || trimmed.startsWith("data:image/", ignoreCase = true)
    }

    private fun removeComments(css: String): String {
        val result = StringBuilder()
        var index = 0
        while (true) {
            val start = css.indexOf("/*", index)
            if (start < 0) {
                result.append(css, index, css.length)
                break
            }
            result.append(css, index, start)
            val end = css.indexOf("*/", start + 2)
            if (end < 0) {
                break
            }
            index = end + 2
        }
        return result.toString()
    }
}
