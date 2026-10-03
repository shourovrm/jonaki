package app.jonaki.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StepDetailTest {
    @Test
    fun webSearchShowsTheExactQueryAndSite() {
        val detail = StepDetail.of("web_search", """{"query":"sample size hospital survey","site":"youtube.com"}""", englishStepWords)
        assertEquals("sample size hospital survey", detail.query)
        assertEquals("youtube.com", detail.target)
    }

    @Test
    fun webFetchShowsTheHostAndPath() {
        val detail = StepDetail.of("web_fetch", """{"url":"https://pubmed.ncbi.nlm.nih.gov/38211/"}""", englishStepWords)
        assertEquals("pubmed.ncbi.nlm.nih.gov/38211/", detail.target)
        assertEquals(null, detail.query)
    }

    @Test
    fun fileToolsShowThePath() {
        assertEquals("work/notes.md", StepDetail.of("write_file", """{"path":"work/notes.md","content":"x"}""", englishStepWords).target)
        assertEquals("**/*.md", StepDetail.of("find_files", """{"pattern":"**/*.md"}""", englishStepWords).target)
    }

    @Test
    fun youTubeShowsTheLink() {
        assertEquals("youtu.be/abc", StepDetail.of("youtube_summarize", """{"url":"https://youtu.be/abc"}""", englishStepWords).target)
    }

    @Test
    fun brokenArgumentsGiveAnEmptyDetailInsteadOfCrashing() {
        val detail = StepDetail.of("web_search", "{not json", englishStepWords)
        assertEquals(null, detail.query)
        assertEquals(null, detail.target)
    }

    @Test
    fun shareFileSaysWhereTheFileGoes() {
        assertEquals("To Downloads: artifacts/report.pdf", StepDetail.of("share_file", """{"action":"downloads","path":"artifacts/report.pdf"}""", englishStepWords).target)
        assertEquals("Share: work/a.csv", StepDetail.of("share_file", """{"action":"share","path":"work/a.csv"}""", englishStepWords).target)
        assertEquals("From linked folder: Invoices/may.pdf", StepDetail.of("share_file", """{"action":"import_linked","path":"Invoices/may.pdf"}""", englishStepWords).target)
        assertEquals("List linked folder", StepDetail.of("share_file", """{"action":"list_linked"}""", englishStepWords).target)
    }

    @Test
    fun mcpNamesTheServerAndToolOrTheSearchWords() {
        assertEquals("deepwiki: ask_wiki_question", StepDetail.of("mcp", """{"action":"call","server":"deepwiki","tool":"ask_wiki_question"}""", englishStepWords).target)
        assertEquals("wiki pages", StepDetail.of("mcp", """{"action":"search","query":"wiki pages"}""", englishStepWords).query)
    }

    @Test
    fun runCodeShowsTheLanguageAndLineCount() {
        val python = StepDetail.of("run_code", """{"language":"python","code":"import math\nprint(math.pi)\n"}""", englishStepWords)
        val oneLine = StepDetail.of("run_code", """{"language":"js","code":"6 * 7"}""", englishStepWords)
        val unknownLanguage = StepDetail.of("run_code", """{"language":"ruby","code":"puts 1\nputs 2"}""", englishStepWords)

        assertEquals("Python · 2 lines", python.target)
        assertEquals("JavaScript · 1 line", oneLine.target)
        assertEquals("2 lines", unknownLanguage.target)
        assertEquals(null, StepDetail.of("run_code", """{"language":"python"}""", englishStepWords).target)
    }
}
