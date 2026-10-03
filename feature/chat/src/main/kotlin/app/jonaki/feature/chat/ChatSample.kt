package app.jonaki.feature.chat

/** Sample data matching docs/mockups/index.html, for screenshots before real data exists. */
object ChatSample {
    val running: ChatUiState = ChatUiState(
        title = "Thesis — sample size",
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

    private val scopedModels = listOf(
        ModelChoiceUi("openrouter:z-ai/glm-5.3-flash", "GLM 5.3 Flash", "OpenRouter", 0.10, 0.40, 0.025),
        // A 40-character id: checks that long names ellipsize instead of running off the screen.
        ModelChoiceUi("deepseek:deepseek/deepseek-v4-flash", "deepseek/deepseek-v4-flash-0925-preview-x", "DeepSeek", 0.27, 1.10),
        ModelChoiceUi("openrouter:anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5", "OpenRouter", 3.00, 15.00, 0.30),
        ModelChoiceUi("gemini:gemini-3.8-flash", "Gemini 3.8 Flash", "Gemini"),
    )

    /** The running sample with the status strip, model choices and usage filled in (D-027 mockup). */
    val withUsage: ChatUiState = running.copy(
        status = ChatStatusUi(modelName = "GLM 5.3 Flash", contextWindowTokens = 200_000, contextUsedTokens = 48_210, costUsd = 0.0134),
        modelChoices = scopedModels,
        selectedModelKey = scopedModels.first().key,
        usage = UsageUi(
            totalCostUsd = 0.0134,
            inputTokens = 48_210,
            cachedTokens = 31_900,
            outputTokens = 3_480,
            perModel = listOf(
                ModelUsageUi("GLM 5.3 Flash", turns = 6, costUsd = 0.0062),
                ModelUsageUi("Claude Sonnet 5.5", turns = 1, costUsd = 0.0072),
            ),
            requests = listOf(
                RequestTimeUi("21:47:12", providerWaitMillis = 812, shownAfterMillis = 23),
                RequestTimeUi("21:47:05", providerWaitMillis = null, shownAfterMillis = null),
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
                costUsd = 0.0041,
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
        webSearchEnabled = true,
        isRunning = false,
        draft = "",
        items = emptyList(),
    )
}
