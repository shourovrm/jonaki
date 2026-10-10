package app.jonaki.feature.chat

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check: a 360 dp wide phone at font scale 1.3, a long prompt with line breaks,
// a 40-character reference file name and a long model id.

private val englishPrompt = StepPromptUi(
    prompt = "Portrait poster titled \"Morning stretches\", four numbered panels in a 2x2 grid.\n" +
        "Panel 1: \"Reach up\". Start figure standing, end figure with both arms overhead, labelled \"Start\" and \"End\", an arrow between them.\n" +
        "Footer: \"Hold each stretch for 20 seconds.\"",
    model = "openrouter:openai/gpt-image-2.5-sunburst",
    isHighQuality = true,
    aspectRatio = "2:3",
    referencePaths = listOf("images/morning-stretches-poster-draft-one.jpg", "inbox/photo.png"),
)

private val banglaPrompt = StepPromptUi(
    prompt = "\"সকালের ব্যায়াম\" শিরোনামের একটি পোস্টার, চারটি নম্বর দেওয়া ঘর।\nপ্রথম ঘর: \"হাত উপরে তুলুন\"। শুরুর ও শেষের ছবির মাঝে একটি তীর।",
    model = "openrouter:openai/gpt-image-2.5-sunburst",
    isHighQuality = true,
    aspectRatio = "2:3",
    referencePaths = listOf("images/morning-stretches-poster-draft-one.jpg"),
)

@Preview(name = "Prompt sheet, light", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun PromptSheetPreview() {
    JonakiTheme(ThemeMode.LIGHT) {
        Surface { StepPromptContent(englishPrompt) }
    }
}

@Preview(name = "Prompt sheet, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun PromptSheetBanglaPreview() {
    JonakiTheme(ThemeMode.LIGHT) {
        Surface { StepPromptContent(banglaPrompt) }
    }
}

@Preview(name = "Prompt sheet, video without settings", widthDp = 360, heightDp = 360, fontScale = 1.3f)
@Composable
private fun PromptSheetBareVideoPreview() {
    JonakiTheme(ThemeMode.LIGHT) {
        Surface { StepPromptContent(StepPromptUi(prompt = "A boat at dawn", model = "openrouter:google/veo-3.1-lite")) }
    }
}
