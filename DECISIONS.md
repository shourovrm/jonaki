# Decisions

Numbered log; newest at the bottom; it grows as needed. Keep each entry to a
few short lines. Only the user changes a status. The full reasoning for
D-001 to D-020 is in `docs/planning-chat-2026-10-02.md`.

Format:
```
## D-NNN · YYYY-MM-DD · Title — status
Decision. Why: reason. Rejected: alternative (reason). Outcome: pending.
```

## D-001 · 2026-10-02 · Governance — accepted
AGENTS.md (rules), CLAUDE.md (pointer), STATUS.md (≤20 lines), this log,
plans in docs/plans/ with a "Resume from" line. Agent proposes, user rules.
Agent commits after each incremental change (≤80-character subject, AI
trailer allowed). Rejected: OPFlow's journal, ledgers, script maps (research
records an app does not need).

## D-002 · 2026-10-02 · Name Jonaki (জোনাকি, firefly) — accepted
Rejected: Tuntuni, Wren, Setu, Kheya.

## D-003 · 2026-10-02 · General phone agent first, light coding — accepted
Why: user will not build apps on the phone. Rejected: coding agent first.

## D-004 · 2026-10-02 · No Linux sandbox — accepted
Built-in tools in app folders; SSH to PC or VPS later. Rejected: proot
(150 MB, hangs until reboot in Kai 9000 #359), Termux (second app).

## D-005 · 2026-10-02 · Runtime rules from Kai 9000 — accepted
API search with fallbacks; capped tool output; heavy research in subagents;
always stream, live steps, Stop button; tool time limits, step budgets;
loop in a foreground service, every step saved; stable system prompt;
summaries only between messages, originals kept.

## D-006 · 2026-10-02 · Stack — accepted
Kotlin, Compose, coroutines, Room, OkHttp with SSE, kotlinx.serialization,
Keystore for keys, WorkManager. Android only, minSdk 26. Rejected: React
Native, Flutter (heavier); Kotlin Multiplatform (unneeded platforms).

## D-007 · 2026-10-02 · One module per tool (Unix rule) — accepted
Each tool, provider, search backend, runtime, decider and screen is a Gradle
module depending only on core/. Text in, text out; spill large output to a
file. Rejected: one tools module with packages (not cleanly removable).

## D-008 · 2026-10-02 · Threads with own memory; global memory, skills — accepted
Thread owns messages, memory, folder (inbox/, work/, artifacts/), overrides.
User can add, edit, delete, pin, promote memories.

## D-009 · 2026-10-02 · Memory storage — accepted
SQLite facts table with full-text index (FTS5 trigram if spike S-1 passes,
else FTS4). Explicit memory tool plus background extraction (add, update,
delete) on a cheap model. Inject global and thread memory up to ~1,500
tokens each; rest via recall. Rejected: embeddings in v1; JSON in settings. Outcome (S-1, 2026-10-02): passed.
androidx.sqlite:sqlite-bundled 2.5.2 ships SQLite 3.46.0 with FTS5 trigram;
6 JVM tests in spikes/fts5 find Bangla, English and mixed facts by a
three-code-point fragment ("থিস", "dee", "ট্রে"). Limits: MATCH needs 3+
characters (LIKE scans below that); "thesis" does not find "থিসিস". Needs
Room 2.7 (D-023); arm64 native library adds 0.8 MB compressed.

## D-010 · 2026-10-02 · Providers — accepted
One OpenAI-compatible module with presets, one native Gemini module.
Rejected: a module per service (same wire format).

## D-011 · 2026-10-02 · Web search — accepted
Tavily default, then Ollama, then Exa on quota errors; web_fetch with jsoup
and Readability4J, capped. YouTube search = web_search on youtube.com.
Rejected: DuckDuckGo scraping (Kai 9000 hangs), Brave (card required).
Amendment (user, 2026-10-02): queries hold only general topic words, never
names, personal details or file contents (tool guideline); every query is
shown in its step card; web search can be switched off per thread. Why:
Tavily stores queries with no fixed deletion period and may use them to
improve its service (privacy policy of 24 November 2025). Outcome (S-4,
2026-10-02): passed. Tavily answers in 2.2 s with short snippets and finds
YouTube videos with include_domains; Ollama web search works on an account
without credit, returns 3,500 to 8,800 characters per result (cap needed),
and its web_fetch endpoint returns 404. Quota codes from Tavily's docs: 429,
432, 433. Recordings in testdata/search/.

## D-012 · 2026-10-02 · YouTube summaries via Gemini only — accepted
Rejected: NewPipeExtractor (scraping, breaks), yt-dlp and ffmpeg (no Android). Outcome (S-3, 2026-10-02):
passed on gemini-3.8-flash; gemini-2.5-flash is closed to new users (404).
A 19-minute video costs 105,878 input tokens and takes 14 to 21 s; a Bangla
prompt gets a good Bangla summary; MEDIA_RESOLUTION_LOW did not lower the
count; a repeat call reused 102,276 tokens from Gemini's automatic cache.
Errors seen: 503 overload (retry worked), 403 for an unavailable video.
Whether the key is on the free tier is not visible in the response.

## D-013 · 2026-10-02 · Python as on-demand Pyodide — accepted
Downloaded with checksum check; micropip packages; first-run tool picker,
Settings install and remove, just-in-time install card. Rejected: Chaquopy
bundled (+25–40 MB, fixed packages, reaches app secrets).

## D-014 · 2026-10-02 · 16 tools, pi token patterns — accepted
read_file, write_file, edit_file, find_files, search_files, share_file,
web_search, web_fetch, youtube_summarize, run_code, artifact, memory,
delegate, phone, schedule, mcp. One prompt line per tool; schemas only when
active; truncation notices name the next call; skills loaded via read_file;
one mcp proxy tool. Rejected: ~30 single-purpose tools (2x tokens).

## D-015 · 2026-10-02 · Subagents — accepted with amendment
delegate (single or parallel), minimal default tools plus extra_tools, no
parent context, capped answer, no nesting, budgets. request_tool goes to the
permission broker; approval unanswered for 3 minutes: skip that part and
continue, or stop and return results with the skipped parts listed.
ask_parent at most twice; shared notes board. Rejected: tool requests via
the main model (costly), direct agent chat (loops, deadlocks).

## D-016 · 2026-10-02 · Jev routing — superseded by D-022

## D-017 · 2026-10-02 · Files: copy in, work, export — accepted
Share sheet, attach picker, one linked folder → inbox/; export to
Downloads/Jonaki, save as, share, linked folder, with approval.
Rejected: all-files access.

## D-018 · 2026-10-02 · HTML artifacts — accepted
Self-contained HTML in artifacts/, WebView with network and file access off,
versions, PDF via print. Offline report and slide skills with a bundled
chart library. Rejected: generated native UI (Kai 9000; no export).

## D-019 · 2026-10-02 · Distribution: GitHub release APK only — accepted
Release APK only, never debug. Google Play requirements deferred.

## D-020 · 2026-10-02 · Phone tool scope — accepted
v1: calendar, reminder, notify, clipboard, open app. Later: notification
reading. Never: SMS, call log, AccessibilityService control.

## D-021 · 2026-10-02 · Build setup reused from the user's apps — accepted
System Gradle 9.7.1, AGP 8.7.3, Kotlin 2.1.0, KSP 2.1.0-1.0.29, Compose BOM
2024.11.00, Room 2.6.1, Java 17, arm64-v8a, release signing as in
BD-calendar. Why: all already in ~/.gradle (865 MB), no new toolchain
download. Rejected: latest versions (new downloads, untested with Gradle 9.7).

## D-022 · 2026-10-02 · Jev as an optional helper — accepted
deciders/jev behind a Decider interface, one call per user message with
parallel questions: which skill (if any); which memories among full-text
candidates are relevant; does the message hold a durable fact (gates memory
extraction); which web_search results to keep. Settings: Jev on/off plus a
switch each for skills, memory and search filtering; endpoint OpenRouter or
TypeSafe direct. Off or no key: list all skills, inject top memories by pin
and recency, extract on a timer, keep all results. Hints go in the latest
message, never the system prompt. Answers under 0.6 confidence or slower
than 1.5 s are ignored. Why: $0.042 per million input tokens; TypeSafe
reports 2.3x fewer wrong skill loads. Rejected: Jev for model routing and
jev-router as main model (user ruling). Risk: English-first; spike S-5
tests Bangla.

## D-023 · 2026-10-02 · Room 2.7.2 with the bundled SQLite driver — accepted
Use Room 2.7.2 with androidx.sqlite:sqlite-bundled 2.5.2 instead of Room
2.6.1 (changes a D-021 version). Why: S-1 shows the bundled SQLite 3.46.0
has FTS5 trigram for Bangla; Room 2.6.1 can only use Android's own SQLite,
which is 3.18 on Android 8 (trigram needs 3.34) and has no FTS5. Cost: new
downloads, about 1.9 MB of native code in the APK (0.8 MB compressed).
Room 2.7.2 with Kotlin 2.1.0 and KSP 2.1.0-1.0.29 is checked when M1 builds
core/storage. Rejected: Room 2.6.1 with FTS4 (no trigram; Bangla matching
depends on word splitting). Outcome (2026-10-02): Room 2.7.2 builds with Kotlin
2.1.0 and KSP 2.1.0-1.0.29; the app runs on the test phone with the bundled
driver. DAO tests need a device (the bundled driver has no host build).

## D-024 · 2026-10-02 · Visual design: Firefly look with Rail line steps — accepted
Material 3 structure. Firefly colours and shapes: leaf-dark night theme,
pale paddy-green day theme, one firefly yellow-green for live work only,
rounded shapes, a glowing dot on working threads. Inside a run, Rail line's
step track: one station per tool step (done, running, waiting), with
per-step times. Theme follows the system, with a manual toggle. English UI
only. Mockups: docs/mockups/index.html. Rejected: pure Rail line (too
formal for quick asks), pure Firefly (step list lacks detail).

## D-025 · 2026-10-02 · Logo: the lit j — accepted
A lowercase j whose dot is the firefly, lit with a soft halo, on the
leaf-dark ground; one-colour version for themed icons and notifications.
Options: docs/mockups/logo.html. Rejected: literal firefly (wings vanish at
48 px), step track (not tied to the name), light trail (faint when small).

## D-026 · 2026-10-02 · Agent loop behaviour, first version — accepted
Step budget 15 model turns that call tools; when used up, one last request
without tools. Tool calls of one turn run one after another. A stream that
ends without a finish counts as a retryable failure; the app retries a
retryable failure once after 2 s. Web search moves to the next backend on
any failure, not only quota errors (Tavily 429/432/433, Ollama and Exa
402/429 are quota). The per-thread web switch removes web_search and
web_fetch. Why: found while building M2 and M3 (workers A and B).
Outcome (device test, 2026-10-02): search, fetch, approval, Stop, killed app
and YouTube (Gemini 503 then retry) all behaved as described.

## D-027 · 2026-10-02 · Cost display and scoped models — accepted
A status strip above the message field: model (tap to switch among scoped
models), context window size, percent of context used, cost of this thread
in USD. Settings has a Status icons help page and a Models page (scoped
models in order; the first is the default for new threads). Extras: cost on
each finished run's line, thread totals and the month's total in the thread
list, a usage sheet (tokens in, cached, out; a line per model). Cost comes
from OpenRouter's usage report, else from token counts times a price table.
Mockups: docs/mockups/cost-and-models.html (option C, bottom). Rejected:
cost in the subtitle with a model chip in the composer (A), cost chip with
a menu from the subtitle (B), taka display and monthly limit (not now).

## D-028 · 2026-10-02 · Chat model settings as service cards — accepted
Settings lists each added chat service as a card that opens to show its key
and models. "Add service" is a drop-down of services not yet added. A saved
key shows its first 3 characters and dots, never more. Models are added by
searching the service's model list (OpenRouter's catalog; a built-in list
plus free text for others), several per service. One model across all
services is starred as the default for new threads; the status strip's
model menu (D-027) lists all added models. Mockups:
docs/mockups/settings-models.html (option A). Rejected: a page per service
(B; one more tap), first-and-last-3 masking.

## D-029 · 2026-10-02 · Long text never runs off the screen — accepted
In lists a name keeps one line and ends in "…"; ids, context and prices get
lines of their own; prices show in, out and cache per million tokens (a dash
when unknown). Opened views (a model's sheet) wrap the full name. Thread
names wrap in full in the thread list. Links and paths in steps and approval
cards wrap to two lines, then "…". The status strip shrinks the model name
first. Threads can be renamed (long-press in the list, ⋮ in a thread).
Mockups: docs/mockups/text-overflow.html, routing.html (Threads).

## D-030 · 2026-10-02 · OpenRouter provider routing — accepted
Three choices: Private, then cheapest (provider.data_collection "deny",
sort "price"); Cheapest (sort "price"); Automatic (no provider block). The
OpenRouter card sets the choice for all its models; a model's ⋮ menu
overrides it, and a model that differs shows a short label in the list.
Default: Private, then cheapest; when no private provider serves the model,
the request is retried once with Cheapest and the step says so. Source:
openrouter.ai/docs/features/provider-routing. Mockups:
docs/mockups/routing.html (option A). Rejected: a chip per model (B), a
model sheet (C).
