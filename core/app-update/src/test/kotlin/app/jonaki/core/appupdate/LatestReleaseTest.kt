package app.jonaki.core.appupdate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatestReleaseTest {
    // The shape of GET /repos/shourovrm/jonaki/releases/latest, cut down to the fields we read.
    private val githubJson = """
        {"tag_name":"v1.4.4","name":"Jonaki 1.4.4","draft":false,
         "assets":[
           {"name":"notes.txt","browser_download_url":"https://github.com/x/notes.txt"},
           {"name":"jonaki-v1.4.4.apk","size":13585504,
            "browser_download_url":"https://github.com/shourovrm/jonaki/releases/download/v1.4.4/jonaki-v1.4.4.apk"}
         ]}
    """.trimIndent()

    @Test
    fun `reads the version and the apk asset`() {
        val release = parseLatestRelease(githubJson)

        assertEquals("1.4.4", release?.versionName)
        assertEquals("jonaki-v1.4.4.apk", release?.apkName)
        assertEquals(
            "https://github.com/shourovrm/jonaki/releases/download/v1.4.4/jonaki-v1.4.4.apk",
            release?.apkUrl,
        )
    }

    @Test
    fun `a release without an apk asset gives null`() {
        val json = """{"tag_name":"v1.5.0","assets":[{"name":"a.zip","browser_download_url":"https://h/a.zip"}]}"""
        assertNull(parseLatestRelease(json))
    }

    @Test
    fun `text that is not a release gives null`() {
        assertNull(parseLatestRelease("not json"))
        assertNull(parseLatestRelease("""{"message":"Not Found"}"""))
    }

    @Test
    fun `the apk extension is matched without regard to case`() {
        val json = """{"tag_name":"1.5.0","assets":[{"name":"Jonaki.APK","browser_download_url":"https://h/j"}]}"""
        assertEquals("1.5.0", parseLatestRelease(json)?.versionName)
    }
}
