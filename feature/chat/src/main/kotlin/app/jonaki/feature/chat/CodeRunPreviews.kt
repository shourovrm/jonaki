package app.jonaki.feature.chat

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check: a 360 dp wide phone at font scale 1.3, with long code
// lines and a 40-character file name.

private const val LONG_FILE_NAME = "work/quarterly-sales-by-region-2026.csv"

private val previewRun = CodeRunUi(
    stepId = "call-1",
    syntax = CodeSyntax.PYTHON,
    languageName = "Python",
    code = """
        import pandas as pd  # numpy and pandas come with the data add-on
        sales = pd.read_csv("inbox/sales.csv")
        totals = sales.groupby("region")["amount"].sum().sort_values(ascending=False)
        print(f"{len(totals)} regions, top: {totals.index[0]} with {totals.iloc[0]:,.2f} taka in the quarter")
        doc = '''A note
        over two lines'''
        totals.to_csv("$LONG_FILE_NAME")
        print(1 / 0)
    """.trimIndent(),
    isRunning = false,
    printed = "4 regions, top: Dhaka with 1,204,331.50 taka in the quarter\nA long printed line that goes on and on to see how the output wraps on a narrow phone",
    printedToStderr = "FutureWarning: the default of observed=False is deprecated",
    result = null,
    error = "Traceback (most recent call last):\n  File \"main.py\", line 8, in <module>\nZeroDivisionError: division by zero",
    errorLine = 8,
    savedFiles = listOf(LONG_FILE_NAME, "artifacts/regions.html"),
    notSavedFiles = listOf(NotSavedFileUi("inbox/sales.csv", "only files under work/ and artifacts/ are saved")),
    outputIsCut = true,
)

@Preview(name = "Code, light", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun CodeTabPreview() {
    JonakiTheme(ThemeMode.LIGHT) {
        Surface { CodeRunContent(previewRun, onOpenFile = {}) }
    }
}

@Preview(
    name = "Code, dark",
    widthDp = 360,
    heightDp = 640,
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CodeTabDarkPreview() {
    JonakiTheme(ThemeMode.DARK) {
        Surface { CodeRunContent(previewRun, onOpenFile = {}) }
    }
}

@Preview(name = "Output", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun OutputTabPreview() {
    JonakiTheme(ThemeMode.LIGHT) {
        Surface { CodeRunContent(previewRun, onOpenFile = {}, initialTab = OUTPUT_TAB) }
    }
}

@Preview(name = "Output, still running", widthDp = 360, heightDp = 300, fontScale = 1.3f)
@Composable
private fun RunningPreview() {
    val running = previewRun.copy(
        isRunning = true,
        printed = "",
        printedToStderr = "",
        error = null,
        errorLine = null,
        savedFiles = emptyList(),
        notSavedFiles = emptyList(),
        outputIsCut = false,
    )
    JonakiTheme(ThemeMode.DARK) {
        Surface { CodeRunContent(running, onOpenFile = {}, initialTab = OUTPUT_TAB) }
    }
}
