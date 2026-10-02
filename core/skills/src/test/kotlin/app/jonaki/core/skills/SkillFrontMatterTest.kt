package app.jonaki.core.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillFrontMatterTest {
    private fun parsed(text: String): SkillFrontMatter {
        val result = SkillFrontMatter.parse(text)
        assertTrue("expected Parsed, got $result", result is FrontMatterResult.Parsed)
        return (result as FrontMatterResult.Parsed).frontMatter
    }

    private fun invalidReason(text: String): String {
        val result = SkillFrontMatter.parse(text)
        assertTrue("expected Invalid, got $result", result is FrontMatterResult.Invalid)
        return (result as FrontMatterResult.Invalid).reason
    }

    @Test
    fun readsNameAndDescription() {
        val frontMatter = parsed(
            """
            ---
            name: report
            description: Write a report as an HTML page.
            ---
            # Report
            Steps follow.
            """.trimIndent(),
        )

        assertEquals("report", frontMatter.name)
        assertEquals("Write a report as an HTML page.", frontMatter.description)
    }

    @Test
    fun ignoresOtherKeysNestedMapsCommentsAndWindowsLineEnds() {
        val text = "﻿---\r\n# a comment\r\nname: pdf\r\nlicense: Proprietary\r\nmetadata:\r\n  version: 2\r\n  " +
            "tags: [a, b]\r\ndescription: Fill PDF forms\r\nallowed-tools: Bash\r\n---\r\nBody\r\n"

        val frontMatter = parsed(text)

        assertEquals("pdf", frontMatter.name)
        assertEquals("Fill PDF forms", frontMatter.description)
    }

    @Test
    fun readsQuotedValuesWithColonsAndEscapes() {
        val frontMatter = parsed(
            """
            ---
            name: "slides"
            description: 'Make slides: one idea per slide, it''s "short".'
            ---
            """.trimIndent(),
        )

        assertEquals("slides", frontMatter.name)
        assertEquals("Make slides: one idea per slide, it's \"short\".", frontMatter.description)
    }

    @Test
    fun readsDoubleQuotedEscapes() {
        val frontMatter = parsed("---\nname: a\ndescription: \"Line one\\nLine \\\"two\\\"\"\n---\n")

        assertEquals("Line one\nLine \"two\"", frontMatter.description)
    }

    @Test
    fun foldsBlockScalarsAndPlainContinuationLines() {
        val folded = parsed(
            """
            ---
            name: letter
            description: >
              Write a formal letter
              in Bangla.
            ---
            """.trimIndent(),
        )
        val literal = parsed("---\nname: letter\ndescription: |-\n  First line\n  Second line\n---\n")
        val plain = parsed("---\nname: letter\ndescription: Write a formal\n  letter in Bangla.\n---\n")

        assertEquals("Write a formal letter in Bangla.", folded.description)
        assertEquals("First line\nSecond line", literal.description)
        assertEquals("Write a formal letter in Bangla.", plain.description)
    }

    @Test
    fun keepsBanglaText() {
        val frontMatter = parsed("---\nname: bangla-formal-letter\ndescription: আনুষ্ঠানিক চিঠি লেখা\n---\n")

        assertEquals("আনুষ্ঠানিক চিঠি লেখা", frontMatter.description)
    }

    @Test
    fun missingOpeningLineIsInvalid() {
        assertEquals("SKILL.md must start with a --- line", invalidReason("# Report\nname: report\n"))
    }

    @Test
    fun missingClosingLineIsInvalid() {
        assertEquals("the front matter has no closing --- line", invalidReason("---\nname: report\ndescription: x\n"))
    }

    @Test
    fun missingFieldsAreInvalid() {
        assertEquals("name is missing", invalidReason("---\ndescription: Write a report\n---\n"))
        assertEquals("description is missing", invalidReason("---\nname: report\n---\n"))
        assertEquals("description is missing", invalidReason("---\nname: report\ndescription: \"\"\n---\n"))
    }

    @Test
    fun badYamlIsInvalid() {
        assertEquals("line 3 is not \"key: value\"", invalidReason("---\nname: report\njust words\n---\n"))
        assertEquals("line 2 has an unclosed quote", invalidReason("---\nname: \"report\ndescription: x\n---\n"))
        assertEquals("name appears twice", invalidReason("---\nname: a\nname: b\ndescription: x\n---\n"))
    }

    @Test
    fun nameMustBeLowercaseWordsWithHyphens() {
        val expected = "name must be lowercase letters, digits and single hyphens, at most 64 characters"

        assertEquals(expected, invalidReason("---\nname: My Report\ndescription: x\n---\n"))
        assertEquals(expected, invalidReason("---\nname: -report\ndescription: x\n---\n"))
        assertEquals(expected, invalidReason("---\nname: ${"a".repeat(65)}\ndescription: x\n---\n"))
        assertEquals("a".repeat(64), parsed("---\nname: ${"a".repeat(64)}\ndescription: x\n---\n").name)
    }

    @Test
    fun veryLongDescriptionIsInvalid() {
        val longDescription = "word ".repeat(300).trim()

        assertEquals(
            "description has 1499 characters; the limit is 1024",
            invalidReason("---\nname: report\ndescription: $longDescription\n---\n"),
        )
        assertEquals(1024, parsed("---\nname: report\ndescription: ${"d".repeat(1024)}\n---\n").description.length)
    }
}
