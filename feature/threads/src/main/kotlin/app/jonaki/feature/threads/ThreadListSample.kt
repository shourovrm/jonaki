package app.jonaki.feature.threads

/** Sample data matching docs/mockups/index.html, for screenshots before real data exists. */
object ThreadListSample {
    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE

    fun state(nowMillis: Long): ThreadListUiState = ThreadListUiState(
        threads = listOf(
            ThreadRow("thesis", "Thesis — sample size", "Reading pubmed.ncbi.nlm.nih.gov", nowMillis - 10_000, ThreadRunState.Running(3), costUsd = 0.041),
            ThreadRow("laptop", "Laptop under ৳90,000", "Needs approval: save comparison.md", nowMillis - 35 * MINUTE, ThreadRunState.WaitingForApproval, costUsd = 0.0134),
            ThreadRow("sylhet", "সিলেট ভ্রমণ পরিকল্পনা", "ট্রেনের সময়সূচি আর হোটেলের তালিকা তৈরি।", nowMillis - 137 * MINUTE, ThreadRunState.Idle, costUsd = 0.0087),
            ThreadRow("fusion", "Kurzgesagt: fusion video", "Summary: 6 key points, 19 min video", nowMillis - 3 * DAY, ThreadRunState.Idle, costUsd = 0.0311),
            ThreadRow("data", "Mobile data plans", "GP vs Robi vs Banglalink, 30-day packs", nowMillis - 4 * DAY, ThreadRunState.Idle, costUsd = 0.0022),
            ThreadRow("kotlin", "Kotlin coroutines notes", "Edited work/flows.md, 2 changes", nowMillis - 9 * DAY, ThreadRunState.Idle),
        ),
        monthCostUsd = 0.21,
    )

    val empty: ThreadListUiState = ThreadListUiState(threads = emptyList())
}
