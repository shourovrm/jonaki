package app.jonaki.tools.editfile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditsTest {
    private fun applied(result: EditResult): EditResult.Applied {
        assertTrue("expected success but got $result", result is EditResult.Applied)
        return result as EditResult.Applied
    }

    private fun failed(result: EditResult): EditResult.Failed {
        assertTrue("expected failure but got $result", result is EditResult.Failed)
        return result as EditResult.Failed
    }

    @Test
    fun exactMatchIsReplaced() {
        val result = applyEdits("one\ntwo\nthree\n", listOf(TextEdit("two", "TWO")))
        val success = applied(result)
        assertEquals("one\nTWO\nthree\n", success.newContent)
        assertEquals(MatchKind.EXACT, success.matches.single().kind)
        assertEquals(2, success.matches.single().firstLine)
    }

    @Test
    fun severalEditsApplyInOrder() {
        val result = applyEdits(
            "alpha\nbeta\ngamma\n",
            listOf(TextEdit("alpha", "ALPHA"), TextEdit("ALPHA\nbeta", "merged")),
        )
        assertEquals("merged\ngamma\n", applied(result).newContent)
    }

    @Test
    fun ambiguousMatchFailsAndSaysHowOften() {
        val failure = failed(applyEdits("x = 1\nx = 1\n", listOf(TextEdit("x = 1", "x = 2"))))
        assertEquals(1, failure.editNumber)
        assertTrue(failure.reason, failure.reason.contains("2 times"))
    }

    @Test
    fun missingTextFails() {
        val failure = failed(applyEdits("hello\n", listOf(TextEdit("goodbye", "x"))))
        assertTrue(failure.reason.contains("not found"))
    }

    @Test
    fun oneFailingEditMeansNoEditIsApplied() {
        val failure = failed(
            applyEdits("a\nb\n", listOf(TextEdit("a", "A"), TextEdit("zzz", "Z"))),
        )
        assertEquals(2, failure.editNumber)
    }

    @Test
    fun emptyOldTextIsRejected() {
        failed(applyEdits("a\n", listOf(TextEdit("", "x"))))
    }

    @Test
    fun fuzzyMatchIgnoresTrailingSpacesAndWindowsLineEnds() {
        val content = "first   \r\nsecond\t\r\nthird\r\n"
        val result = applyEdits(content, listOf(TextEdit("first\nsecond", "changed")))
        val success = applied(result)
        assertEquals(MatchKind.FUZZY, success.matches.single().kind)
        // Lines outside the edit keep their original bytes.
        assertEquals("changed\r\nthird\r\n", success.newContent)
    }

    @Test
    fun fuzzyMatchTreatsSmartQuotesAndDashesAsPlain() {
        val content = "He said “hello” – twice.\nnext\n"
        val result = applyEdits(content, listOf(TextEdit("He said \"hello\" - twice.", "Replaced.")))
        assertEquals("Replaced.\nnext\n", applied(result).newContent)
    }

    @Test
    fun fuzzyMatchMustAlsoBeUnique() {
        val content = "item  \nitem\n"
        val failure = failed(applyEdits(content, listOf(TextEdit("item \n", "x"))))
        assertTrue(failure.reason, failure.reason.contains("2 times"))
    }

    @Test
    fun banglaTextIsMatchedExactly() {
        val result = applyEdits("জোনাকি অ্যাপ\n", listOf(TextEdit("অ্যাপ", "app")))
        assertEquals("জোনাকি app\n", applied(result).newContent)
    }
}
