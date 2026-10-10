package app.jonaki.tools.generatevectorimage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgCheckTest {
    @Test
    fun aPlainSvgIsAnSvg() {
        assertTrue(SvgCheck.looksLikeSvg("""<svg xmlns="http://www.w3.org/2000/svg"></svg>"""))
        assertTrue(SvgCheck.looksLikeSvg("<svg>"))
        assertTrue(SvgCheck.looksLikeSvg("<svg/>"))
        assertTrue(SvgCheck.looksLikeSvg("  \n<svg\nviewBox=\"0 0 1 1\">"))
    }

    @Test
    fun aByteOrderMarkAnXmlDeclarationAndCommentsMayComeFirst() {
        assertTrue(SvgCheck.looksLikeSvg("﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- one --><!-- two -->\n<svg>"))
    }

    @Test
    fun aDoctypeIsSkippedSoThatTheSanitiserGivesTheReason() {
        assertTrue(SvgCheck.looksLikeSvg("<!DOCTYPE svg PUBLIC \"a\" \"b\"><svg>"))
        assertTrue(SvgCheck.looksLikeSvg("<!DOCTYPE svg [<!ENTITY a \"b\">]><svg>"))
    }

    @Test
    fun otherDocumentsAreNotSvg() {
        assertFalse(SvgCheck.looksLikeSvg("<html><svg></svg></html>"))
        assertFalse(SvgCheck.looksLikeSvg("<svgfoo>"))
        assertFalse(SvgCheck.looksLikeSvg("<svg"))
        assertFalse(SvgCheck.looksLikeSvg("{\"error\":\"nope\"}"))
        assertFalse(SvgCheck.looksLikeSvg("<?xml version=\"1.0\"?>"))
        assertFalse(SvgCheck.looksLikeSvg("<!-- never closed <svg>"))
        assertFalse(SvgCheck.looksLikeSvg(""))
        assertFalse(SvgCheck.looksLikeSvg("   "))
    }

    @Test
    fun aPictureIsNotValidUtf8Text() {
        val pngStart = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A, 0xFF.toByte())
        assertNull(SvgCheck.decode(pngStart))
        assertEquals("<svg>", SvgCheck.decode("<svg>".toByteArray()))
        assertEquals("<svg>জোনাকি", SvgCheck.decode("<svg>জোনাকি".toByteArray()))
    }
}
