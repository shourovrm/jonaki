package app.jonaki.feature.chat

import java.util.Base64

/**
 * The small HTML page that shows one SVG in a WebView. The SVG is not placed
 * in the page as markup but as an `<img>` with a `data:` address: a browser
 * draws an SVG loaded that way in a restricted mode, with no script, no
 * outside request and no link that works, even if the file still held
 * something the sanitiser missed. The Content-Security-Policy below allows
 * nothing but that data image, so it is a second wall.
 */
internal object SvgPage {
    const val CONTENT_SECURITY_POLICY = "default-src 'none'; img-src data:; style-src 'unsafe-inline'"

    /**
     * [allowsZoom] lets the viewport scale, so that the WebView's own pinch
     * zoom works in the full-size view; the preview stays fixed.
     */
    fun html(svgText: String, allowsZoom: Boolean): String {
        val encoded = Base64.getEncoder().encodeToString(svgText.toByteArray(Charsets.UTF_8))
        val viewport = if (allowsZoom) {
            "width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=10, user-scalable=yes"
        } else {
            "width=device-width, initial-scale=1, user-scalable=no"
        }
        return """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="$CONTENT_SECURITY_POLICY">
<meta name="viewport" content="$viewport">
<style>
html,body{margin:0;width:100%;height:100%;background:transparent}
img{display:block;width:100%;height:100%;object-fit:contain}
</style></head>
<body><img alt="" src="data:image/svg+xml;base64,$encoded"></body></html>"""
    }
}
