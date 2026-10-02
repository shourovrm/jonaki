package app.jonaki.feature.chat

/** Sample data matching docs/mockups/index.html, for screenshots before real data exists. */
object ChatSample {
    val running: ChatUiState = ChatUiState(
        title = "Thesis — sample size",
        modelLabel = "DeepSeek V3",
        webSearchEnabled = true,
        isRunning = true,
        draft = "",
        items = listOf(
            ChatItem.UserMessage("u1", "Find recent papers on sample size for hospital surveys, summarise the top three and save notes."),
            ChatItem.Run(
                id = "r1",
                isActive = true,
                steps = listOf(
                    StepUi("s1", "web_search", StepUiStatus.DONE, "Tavily · 5 results", query = "minimum sample size clinical survey hospital 2024", durationMillis = 2_100),
                    StepUi("s2", "web_fetch", StepUiStatus.DONE, "pmc.ncbi.nlm.nih.gov · 10,000 chars, rest saved", durationMillis = 2_700),
                    StepUi("s3", "web_fetch", StepUiStatus.RUNNING, "pubmed.ncbi.nlm.nih.gov/38211…", durationMillis = 6_000),
                    StepUi("s4", "write_file", StepUiStatus.WAITING_FOR_APPROVAL, "work/sample-size-notes.md"),
                ),
            ),
            ChatItem.Approval("s4", "write_file", "Create work/sample-size-notes.md (2.3 KB)"),
            ChatItem.AssistantMessage(
                "a1",
                "So far, the clearest guidance comes from a 2023 review in *BMC Medical Research Methodology*, which recommends at least **150 respondents** for a",
                isStreaming = true,
            ),
        ),
    )

    val finished: ChatUiState = running.copy(
        isRunning = false,
        items = listOf(
            ChatItem.UserMessage("u1", "Summarise the fusion video from Kurzgesagt."),
            ChatItem.Run(
                id = "r1",
                isActive = false,
                steps = listOf(
                    StepUi("s1", "web_search", StepUiStatus.DONE, "Tavily · 3 results", query = "Kurzgesagt fusion video", durationMillis = 2_200),
                    StepUi("s2", "youtube_summarize", StepUiStatus.DONE, "19 min video", durationMillis = 14_400),
                ),
            ),
            ChatItem.AssistantMessage(
                "a1",
                "## Fusion in six points\n\n1. Fusion joins light nuclei; the Sun does it with gravity.\n2. On Earth, plasma must reach about **150 million °C**.\n3. Magnetic bottles (`tokamaks`) hold the plasma.\n\nSource: [the video](https://www.youtube.com/watch?v=mZsaaturR6E).",
                isStreaming = false,
            ),
            ChatItem.Error("e1", "DeepSeek is busy (503).", canRetry = true),
        ),
    )

    val empty: ChatUiState = ChatUiState(
        title = "New thread",
        modelLabel = "DeepSeek V3",
        webSearchEnabled = true,
        isRunning = false,
        draft = "",
        items = emptyList(),
    )
}
