package app.jonaki.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jonaki.core.ui.CodeColors
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily

/** A run_code step opened from its card: the program, and what it printed, returned and saved (D-090). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CodeRunSheet(codeRun: CodeRunUi, onOpenFile: (path: String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        CodeRunContent(codeRun, onOpenFile, Modifier.navigationBarsPadding())
    }
}

@Composable
internal fun CodeRunContent(
    codeRun: CodeRunUi,
    onOpenFile: (path: String) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: Int = CODE_TAB,
) {
    var selectedTab by rememberSaveable(codeRun.stepId) { mutableIntStateOf(initialTab) }
    Column(modifier) {
        CodeRunHeader(codeRun)
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == CODE_TAB,
                onClick = { selectedTab = CODE_TAB },
                text = { Text(stringResource(R.string.chat_code_tab_code)) },
            )
            Tab(
                selected = selectedTab == OUTPUT_TAB,
                onClick = { selectedTab = OUTPUT_TAB },
                text = { Text(stringResource(R.string.chat_code_tab_output)) },
            )
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (selectedTab == CODE_TAB) {
                CodeListing(codeRun)
            } else {
                RunOutput(codeRun, onOpenFile)
            }
        }
    }
}

internal const val CODE_TAB = 0
internal const val OUTPUT_TAB = 1

/** "Python · 12 lines" and the copy button. */
@Composable
private fun CodeRunHeader(codeRun: CodeRunUi) {
    val clipboard = LocalClipboardManager.current
    val lineCount = codeRun.code.lines().size
    val lines = pluralStringResource(R.plurals.chat_code_lines, lineCount, lineCount)
    val title = if (codeRun.languageName.isEmpty()) lines else "${codeRun.languageName} · $lines"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { clipboard.setText(AnnotatedString(codeRun.code)) }) {
            Icon(
                JonakiIcons.ContentCopy,
                contentDescription = stringResource(R.string.chat_copy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Line numbers beside the coloured code. Lines never wrap, so the numbers
 * stay level with their lines; long lines scroll sideways instead.
 */
@Composable
private fun CodeListing(codeRun: CodeRunUi) {
    val codeColors = JonakiTheme.codeColors
    val errorColor = JonakiTheme.colors.deny
    val lineCount = codeRun.code.lines().size
    val errorLine = codeRun.errorLine?.takeIf { line -> line in 1..lineCount }
    val highlighted = remember(codeRun.code, codeRun.syntax, codeColors) {
        highlightedCode(codeRun.code, codeRun.syntax, codeColors)
    }
    val lineNumbers = remember(lineCount, errorLine, errorColor) { lineNumbers(lineCount, errorLine, errorColor) }
    var codeLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val codeStyle = codeTextStyle()
    val verticalPadding = 10.dp
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val layout = codeLayout
                    if (errorLine != null && layout != null && errorLine <= layout.lineCount) {
                        val top = verticalPadding.toPx() + layout.getLineTop(errorLine - 1)
                        val bottom = verticalPadding.toPx() + layout.getLineBottom(errorLine - 1)
                        drawRect(errorColor.copy(alpha = ERROR_LINE_ALPHA), Offset(0f, top), Size(size.width, bottom - top))
                    }
                }
                .padding(vertical = verticalPadding),
        ) {
            Text(
                lineNumbers,
                style = codeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp, end = 10.dp),
            )
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Text(
                    highlighted,
                    style = codeStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                    onTextLayout = { layout -> codeLayout = layout },
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
}

private const val ERROR_LINE_ALPHA = 0.22f

@Composable
private fun codeTextStyle(): TextStyle =
    MaterialTheme.typography.bodySmall.copy(fontFamily = MonospaceFamily, fontSize = 13.sp, lineHeight = 19.sp)

private fun highlightedCode(code: String, syntax: CodeSyntax?, colors: CodeColors): AnnotatedString {
    if (syntax == null) {
        return AnnotatedString(code)
    }
    return buildAnnotatedString {
        append(code)
        for (token in CodeTokenizer.tokenize(code, syntax)) {
            addStyle(styleOf(token.kind, colors), token.start, token.end)
        }
    }
}

private fun styleOf(kind: CodeTokenKind, colors: CodeColors): SpanStyle = when (kind) {
    CodeTokenKind.KEYWORD -> SpanStyle(color = colors.keyword, fontWeight = FontWeight.Medium)
    CodeTokenKind.STRING -> SpanStyle(color = colors.string)
    CodeTokenKind.NUMBER -> SpanStyle(color = colors.number)
    CodeTokenKind.COMMENT -> SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
    CodeTokenKind.FUNCTION -> SpanStyle(color = colors.function)
}

private fun lineNumbers(lineCount: Int, errorLine: Int?, errorColor: Color): AnnotatedString = buildAnnotatedString {
    for (number in 1..lineCount) {
        if (number > 1) {
            append('\n')
        }
        if (number == errorLine) {
            withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.Bold)) { append(number.toString()) }
        } else {
            append(number.toString())
        }
    }
}

/** Printed text, stderr in amber, the result, the error and the files, in the order they matter. */
@Composable
private fun RunOutput(codeRun: CodeRunUi, onOpenFile: (path: String) -> Unit) {
    val codeColors = JonakiTheme.codeColors
    val result = codeRun.result
    val error = codeRun.error
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!codeRun.hasOutput()) {
            val emptyText = if (codeRun.isRunning) R.string.chat_code_running else R.string.chat_code_no_output
            QuietLine(stringResource(emptyText))
        }
        if (codeRun.printed.isNotEmpty() || codeRun.printedToStderr.isNotEmpty()) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (codeRun.printed.isNotEmpty()) {
                        OutputText(codeRun.printed, MaterialTheme.colorScheme.onSurface)
                    }
                    if (codeRun.printedToStderr.isNotEmpty()) {
                        OutputText(codeRun.printedToStderr, codeColors.warning)
                    }
                }
            }
        }
        if (result != null) {
            OutputSection(stringResource(R.string.chat_code_result)) {
                SelectionContainer { OutputText(result, MaterialTheme.colorScheme.onSurface) }
            }
        }
        if (error != null) {
            OutputSection(stringResource(R.string.chat_code_error)) {
                SelectionContainer { OutputText(error, JonakiTheme.colors.deny) }
            }
        }
        if (codeRun.savedFiles.isNotEmpty()) {
            OutputSection(stringResource(R.string.chat_code_saved)) {
                for (path in codeRun.savedFiles) {
                    FileLink(path, onClick = { onOpenFile(path) })
                }
            }
        }
        if (codeRun.notSavedFiles.isNotEmpty()) {
            OutputSection(stringResource(R.string.chat_code_not_saved)) {
                for (file in codeRun.notSavedFiles) {
                    QuietLine("${file.path} · ${file.reason}")
                }
            }
        }
        if (codeRun.outputIsCut) {
            QuietLine(stringResource(R.string.chat_code_cut))
        }
    }
}

private fun CodeRunUi.hasOutput(): Boolean =
    printed.isNotEmpty() || printedToStderr.isNotEmpty() || result != null || error != null ||
        savedFiles.isNotEmpty() || notSavedFiles.isNotEmpty()

@Composable
private fun OutputSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun OutputText(text: String, color: Color) {
    Text(text, style = codeTextStyle(), color = color)
}

@Composable
private fun QuietLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A saved file; paths wrap to two lines, then "…" (D-029). */
@Composable
private fun FileLink(path: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Icon(
            JonakiIcons.Document,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            path,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFamily),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
