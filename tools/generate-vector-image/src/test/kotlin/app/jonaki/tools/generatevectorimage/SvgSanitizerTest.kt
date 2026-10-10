package app.jonaki.tools.generatevectorimage

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

class SvgSanitizerTest {
    private val svgOpen = """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 100 100" width="100" height="100">"""

    private fun clean(body: String, open: String = svgOpen): SvgSanitizing.Clean {
        val result = SvgSanitizer.sanitize("$open$body</svg>")
        assertTrue("expected a clean result, got $result", result is SvgSanitizing.Clean)
        return result as SvgSanitizing.Clean
    }

    private fun rejected(text: String): String {
        val result = SvgSanitizer.sanitize(text)
        assertTrue("expected a refusal, got $result", result is SvgSanitizing.Rejected)
        return (result as SvgSanitizing.Rejected).reason
    }

    private fun parse(svg: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(InputSource(StringReader(svg)))
    }

    private fun elements(svg: String, name: String) = parse(svg).getElementsByTagNameNS("*", name)

    @Test
    fun aCleanRecraftLikeSvgKeepsEveryShapeGradientAndStyle() {
        val original = """<?xml version="1.0" encoding="UTF-8"?>
            <!-- made by a model -->
            $svgOpen
              <defs>
                <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8ecae6"/><stop offset="1" stop-color="#ffffff"/></linearGradient>
                <clipPath id="round"><circle cx="50" cy="50" r="48"/></clipPath>
                <style>.leaf{fill:url(#sky);stroke:#222}</style>
              </defs>
              <g clip-path="url(#round)" transform="translate(1 2)">
                <rect width="100" height="100" fill="url(#sky)"/>
                <path class="leaf" d="M10 10 L90 10 L50 90 Z" style="stroke-width:2;fill-opacity:0.5"/>
                <text x="10" y="95" font-size="8">Jonaki &amp; friends</text>
                <use xlink:href="#round"/>
              </g>
            </svg>"""

        val result = SvgSanitizer.sanitize(original) as SvgSanitizing.Clean

        assertTrue(result.removed.isEmpty())
        val output = parse(result.svg)
        assertEquals("0 0 100 100", output.documentElement.getAttribute("viewBox"))
        assertEquals(1, output.getElementsByTagNameNS("*", "linearGradient").length)
        assertEquals(2, output.getElementsByTagNameNS("*", "stop").length)
        assertEquals("M10 10 L90 10 L50 90 Z", (output.getElementsByTagNameNS("*", "path").item(0) as Element).getAttribute("d"))
        assertEquals("stroke-width:2;fill-opacity:0.5", (output.getElementsByTagNameNS("*", "path").item(0) as Element).getAttribute("style"))
        assertEquals("translate(1 2)", (output.getElementsByTagNameNS("*", "g").item(0) as Element).getAttribute("transform"))
        assertEquals("url(#round)", (output.getElementsByTagNameNS("*", "g").item(0) as Element).getAttribute("clip-path"))
        assertEquals("Jonaki & friends", output.getElementsByTagNameNS("*", "text").item(0).textContent)
        assertEquals(".leaf{fill:url(#sky);stroke:#222}", output.getElementsByTagNameNS("*", "style").item(0).textContent)
        val use = output.getElementsByTagNameNS("*", "use").item(0) as Element
        assertEquals("#round", use.getAttributeNS("http://www.w3.org/1999/xlink", "href"))
    }

    @Test
    fun theSizeComesFromWidthAndHeightElseFromTheViewBox() {
        val sized = clean("")
        assertEquals(100.0, sized.width!!, 0.0)
        assertEquals(100.0, sized.height!!, 0.0)

        val fromViewBox = clean("", """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 256">""")
        assertEquals(512.0, fromViewBox.width!!, 0.0)
        assertEquals(256.0, fromViewBox.height!!, 0.0)

        val withUnits = clean("", """<svg xmlns="http://www.w3.org/2000/svg" width="24px" height="12.5">""")
        assertEquals(24.0, withUnits.width!!, 0.0)
        assertEquals(12.5, withUnits.height!!, 0.0)

        val percentOnly = clean("", """<svg xmlns="http://www.w3.org/2000/svg" width="100%" height="100%">""")
        assertNull(percentOnly.width)
        assertNull(percentOnly.height)
    }

    @Test
    fun scriptElementsAreRemovedWithTheirContent() {
        val result = clean("""<script>alert(1)</script><svg:script xmlns:svg="http://www.w3.org/2000/svg">alert(2)</svg:script><circle r="5"/>""")

        assertFalse(result.svg.contains("alert"))
        assertFalse(result.svg.contains("script"))
        assertEquals(1, elements(result.svg, "circle").length)
        assertTrue(result.removed.isNotEmpty())
    }

    @Test
    fun aScriptInTheHtmlNamespaceIsRemovedToo() {
        val result = clean("""<script xmlns="http://www.w3.org/1999/xhtml">alert(1)</script><circle r="5"/>""")

        assertFalse(result.svg.contains("alert"))
        assertEquals(1, elements(result.svg, "circle").length)
    }

    @Test
    fun foreignObjectIsRemovedWithItsHtml() {
        val result = clean("""<foreignObject width="10" height="10"><div xmlns="http://www.w3.org/1999/xhtml"><iframe src="https://evil.example"/></div></foreignObject><rect width="1" height="1"/>""")

        assertFalse(result.svg.contains("foreignObject"))
        assertFalse(result.svg.contains("iframe"))
        assertFalse(result.svg.contains("evil.example"))
        assertEquals(1, elements(result.svg, "rect").length)
    }

    @Test
    fun htmlElementsWithoutANamespaceAreRemoved() {
        val result = clean("""<iframe src="https://evil.example"/><embed src="x"/><object data="y"/><link href="https://evil.example/a.css"/><circle r="1"/>""")

        for (name in listOf("iframe", "embed", "object", "<link", "evil.example")) {
            assertFalse(name, result.svg.contains(name))
        }
        assertEquals(1, elements(result.svg, "circle").length)
    }

    @Test
    fun everyAttributeThatStartsWithOnIsRemoved() {
        val result = clean("""<rect width="1" height="1" onload="alert(1)" onclick="alert(2)" ONmouseover="alert(3)" fill="red"/>""", """<svg xmlns="http://www.w3.org/2000/svg" onload="alert(0)">""")

        assertFalse(result.svg.contains("alert"))
        assertFalse(result.svg.contains("onload"))
        val rect = elements(result.svg, "rect").item(0) as Element
        assertEquals("red", rect.getAttribute("fill"))
        assertEquals("1", rect.getAttribute("width"))
    }

    @Test
    fun addressesThatLeaveTheDocumentAreRemovedWhateverTheScheme() {
        val hostile = listOf(
            "http://evil.example/a.svg",
            "https://evil.example/a.png",
            "file:///data/data/app.jonaki/databases/jonaki.db",
            "javascript:alert(1)",
            "  JavaScript:alert(1)",
            "java\nscript:alert(1)",
            "vbscript:x",
            "data:text/html;base64,PHNjcmlwdD4=",
            "//evil.example/a.png",
            "a.png",
            "content://app.jonaki/x",
        )
        for (address in hostile) {
            val escaped = address.replace("\n", "&#10;")
            val result = clean("""<a xlink:href="$escaped"><image href="$escaped" width="5" height="5"/><rect width="1" height="1" src="$escaped"/></a>""")
            assertFalse(address, result.svg.contains("evil.example"))
            assertFalse(address, result.svg.contains("javascript"))
            assertFalse(address, result.svg.contains("file:"))
            assertFalse(address, result.svg.contains("a.png"))
            assertFalse(address, result.svg.contains("content:"))
            assertFalse(address, result.svg.contains("text/html"))
            assertEquals(address, 0, elements(result.svg, "image").length)
            assertEquals(address, 1, elements(result.svg, "rect").length)
            assertEquals(address, "", (elements(result.svg, "rect").item(0) as Element).getAttribute("src"))
        }
    }

    @Test
    fun sameDocumentAddressesAndDataImagesStay() {
        val result = clean("""<defs><linearGradient id="g"/></defs><a xlink:href="#g"><image href="data:image/png;base64,iVBORw0KGgo=" width="5" height="5"/></a>""")

        assertEquals(1, elements(result.svg, "image").length)
        assertTrue(result.svg.contains("data:image/png;base64,iVBORw0KGgo="))
        assertTrue(result.svg.contains("#g"))
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun aUseElementMayPointOnlyInsideTheDocument() {
        val outside = clean("""<use href="https://evil.example/a.svg#x"/><use xlink:href="data:image/svg+xml;base64,PHN2Zz48L3N2Zz4="/><use href="#x"/>""")

        assertEquals(1, elements(outside.svg, "use").length)
        assertFalse(outside.svg.contains("evil.example"))
        assertFalse(outside.svg.contains("data:"))
    }

    @Test
    fun animationThatSetsAnAddressOrAHandlerIsRemoved() {
        val result = clean(
            """<a><set attributeName="href" to="javascript:alert(1)"/><animate attributeName="xlink:href" values="javascript:alert(2)"/>""" +
                """<set attributeName="onclick" to="alert(3)"/><animate attributeName="opacity" from="0" to="1" dur="1s"/><rect width="1" height="1"/></a>""",
        )

        assertFalse(result.svg.contains("javascript"))
        assertFalse(result.svg.contains("alert"))
        assertEquals(1, elements(result.svg, "animate").length)
    }

    @Test
    fun aDoctypeIsRefusedBeforeAnyParsing() {
        val reason = rejected("""<?xml version="1.0"?><!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd"><svg xmlns="http://www.w3.org/2000/svg"/>""")
        assertTrue(reason, reason.contains("DOCTYPE"))
    }

    @Test
    fun anEntityDeclarationIsRefused() {
        val billionLaughs = """<?xml version="1.0"?>
            <!DOCTYPE svg [
              <!ENTITY a "aaaaaaaaaa">
              <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">
              <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">
            ]>
            <svg xmlns="http://www.w3.org/2000/svg"><text>&c;</text></svg>"""
        rejected(billionLaughs)
    }

    @Test
    fun anExternalEntityThatReadsAFileIsRefused() {
        rejected("""<!DOCTYPE svg [<!ENTITY secret SYSTEM "file:///etc/passwd">]><svg xmlns="http://www.w3.org/2000/svg"><text>&secret;</text></svg>""")
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><!ENTITY x SYSTEM "file:///etc/passwd"></svg>""")
    }

    @Test
    fun anUndeclaredEntityReferenceFailsClosed() {
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><text>&secret;</text></svg>""")
    }

    @Test
    fun malformedXmlIsRefused() {
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><g></svg>""")
        rejected("""<svg xmlns="http://www.w3.org/2000/svg"><rect width=1/></svg>""")
        rejected("not xml at all")
        rejected("")
    }

    @Test
    fun aRootThatIsNotSvgIsRefused() {
        rejected("""<html xmlns="http://www.w3.org/1999/xhtml"><script>alert(1)</script></html>""")
        rejected("""<svg xmlns="http://www.w3.org/1999/xhtml"/>""")
    }

    @Test
    fun veryDeepNestingIsRefusedWithoutACrash() {
        val depth = 5000
        val deep = svgOpen + "<g>".repeat(depth) + "</g>".repeat(depth) + "</svg>"
        rejected(deep)
    }

    @Test
    fun styleElementsLoseImportsAndOutsideUrlsButKeepLocalOnes() {
        val result = clean(
            """<style>@import url("https://evil.example/a.css"); @import 'https://evil.example/b.css';
               .a{fill:url(#g)} .b{background:url(https://evil.example/x.png)} .c{fill:URL( 'http://evil.example' )}
               .d{background:image-set("https://evil.example/y.png" 1x)} .e{background:url(data:image/png;base64,AAAA)}</style>""",
        )

        val style = elements(result.svg, "style").item(0).textContent
        assertFalse(style, style.contains("evil.example"))
        assertFalse(style, style.contains("@import"))
        assertTrue(style, style.contains(".a{fill:url(#g)}"))
        assertTrue(style, style.contains("url(data:image/png;base64,AAAA)"))
        assertFalse(style, style.contains("image-set"))
        assertTrue(result.removed.isEmpty() || result.removed.all { it.isNotBlank() })
    }

    @Test
    fun aCdataStyleIsCleanedToo() {
        val result = clean("""<style><![CDATA[ @import "https://evil.example/a.css"; .a > .b{fill:url(https://evil.example/x)} ]]></style>""")

        val style = elements(result.svg, "style").item(0).textContent
        assertFalse(style, style.contains("evil.example"))
        assertTrue(style, style.contains(".a > .b"))
    }

    @Test
    fun cssEscapesCannotHideAnUrl() {
        val result = clean("""<style>.a{background:\75rl(https://evil.example/x)} @\69mport "https://evil.example/c.css";</style><rect style="fill:\75rl(https://evil.example/y)" width="1" height="1"/>""")

        assertFalse(result.svg.contains("evil.example"))
        assertEquals("", (elements(result.svg, "rect").item(0) as Element).getAttribute("style"))
    }

    @Test
    fun aCommentInsideUrlCannotHideIt() {
        val result = clean("""<rect style="fill:u/**/rl(https://evil.example/x);stroke:#000" width="1" height="1"/>""")
        assertFalse(result.svg.contains("evil.example"))
    }

    @Test
    fun styleAttributesAndPresentationAttributesLoseOutsideUrls() {
        val result = clean(
            """<rect width="1" height="1" style="fill:url(https://evil.example/a#x);stroke:red" filter="url(http://evil.example/f.svg#f)" mask="url(#m)" cursor="url(file:///x), auto"/>""",
        )

        val rect = elements(result.svg, "rect").item(0) as Element
        assertFalse(result.svg.contains("evil.example"))
        assertFalse(result.svg.contains("file:"))
        assertTrue(rect.getAttribute("style").contains("stroke:red"))
        assertEquals("url(#m)", rect.getAttribute("mask"))
        assertEquals("none", rect.getAttribute("filter"))
    }

    @Test
    fun anUnterminatedUrlIsCut() {
        val result = clean("""<rect width="1" height="1" fill="url(https://evil.example"/>""")
        assertFalse(result.svg.contains("evil.example"))
    }

    @Test
    fun processingInstructionsAndCommentsAreDropped() {
        val result = SvgSanitizer.sanitize(
            """<?xml version="1.0"?><?xml-stylesheet href="https://evil.example/s.css" type="text/css"?><!-- c --><svg xmlns="http://www.w3.org/2000/svg"><!-- inner --><rect width="1" height="1"/></svg>""",
        ) as SvgSanitizing.Clean

        assertFalse(result.svg.contains("evil.example"))
        assertFalse(result.svg.contains("<!--"))
        assertFalse(result.svg.contains("<?"))
        assertEquals(1, elements(result.svg, "rect").length)
    }

    @Test
    fun elementsOfOtherNamespacesAreRemovedButTheirNamespaceDeclarationsDoNotBreakAnything() {
        val result = clean(
            """<metadata><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"/></metadata><sodipodi:namedview xmlns:sodipodi="http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd"/><rect width="1" height="1"/>""",
        )

        assertEquals(0, elements(result.svg, "RDF").length)
        assertEquals(0, elements(result.svg, "namedview").length)
        assertEquals(1, elements(result.svg, "rect").length)
    }

    @Test
    fun aRootWithoutXmlnsGetsOneSoThatABrowserShowsItAsAnImage() {
        val result = SvgSanitizer.sanitize("""<svg viewBox="0 0 10 10"><rect width="1" height="1"/></svg>""") as SvgSanitizing.Clean

        val root = parse(result.svg).documentElement
        assertEquals("http://www.w3.org/2000/svg", root.namespaceURI)
        assertEquals(1, elements(result.svg, "rect").length)
    }

    @Test
    fun aPrefixDeclaredOnlyBelowTheRootIsAlsoDeclaredOnTheRootAndTheOutputParses() {
        val result = SvgSanitizer.sanitize(
            """<svg xmlns="http://www.w3.org/2000/svg"><defs><g id="a" xmlns:xlink="http://www.w3.org/1999/xlink"/></defs><use xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="#a"/></svg>""",
        ) as SvgSanitizing.Clean

        assertTrue(result.svg, result.svg.startsWith("""<svg xmlns:xlink="http://www.w3.org/1999/xlink" xmlns="""))
        assertEquals("#a", (elements(result.svg, "use").item(0) as Element).getAttributeNS("http://www.w3.org/1999/xlink", "href"))
    }

    @Test
    fun theOutputIsWellFormedAndStable() {
        val first = SvgSanitizer.sanitize("""<svg xmlns="http://www.w3.org/2000/svg"><text x="1" y="2" data-note="a &lt; b &amp; &quot;c&quot;">1 &lt; 2 &amp; 3</text></svg>""") as SvgSanitizing.Clean
        val second = SvgSanitizer.sanitize(first.svg) as SvgSanitizing.Clean

        assertEquals(first.svg, second.svg)
        val text = parse(first.svg).getElementsByTagNameNS("*", "text").item(0) as Element
        assertEquals("1 < 2 & 3", text.textContent)
        assertEquals("a < b & \"c\"", text.getAttribute("data-note"))
        assertNotNull(parse(first.svg).documentElement)
    }
}
