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
Outcome (2026-10-03, spike S-2 on the A059, WebView 153): passed. Pyodide
314.0.7 installed over Wi-Fi in 8.4 s (core, 13,532,188 bytes) plus 10.4 s
(numpy, pandas, python-dateutil, pytz, six: 7,889,748 bytes); 21.4 MB on
disk. With airplane mode on, run_code read inbox/sales.csv with pandas and
saved work/summary.csv: 8.7 s from a cold app process, 3.2 to 3.7 s for a
program without packages. The WebView renderer held 261 MB PSS while
pandas worked on a 200,000-row frame; the app process 116 to 129 MB.
Checking the core hashes before a run takes 13 ms. Moved to the
background by Home, the same run took 36.3 s (see D-070).

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
tests Bangla. Outcome (2026-10-03): built on branch
worktree-agent-a3724d9efa58b5358; the M10 check saved 4.1 % of prompt
tokens, so it is left out of the app for now (D-129).

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
Amended by D-080: read-only calls of one turn now run side by side.

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

## D-031 · 2026-10-02 · Account balances on service cards — accepted
Settings shows each service's remaining balance where the service offers an
API, refreshed when Settings opens: OpenRouter /api/v1/credits (credits minus
usage; works with a normal key, checked 2026-10-02), fallback /api/v1/key
limit_remaining; DeepSeek /user/balance; Tavily /usage (plan credits used of
the limit this cycle). Gemini and Ollama have no balance API and show
nothing. Why: user request.

Outcome of D-027, D-028, D-030, D-031 (device test, 2026-10-02, v0.2.0):
upgrade from 0.1.0 kept all threads and settings; strip showed 1M context,
2% used and $0.0014 after one message; switching to DeepSeek V4.1 Flash
applied to the next turn (usage sheet: 5 turns GLM, 1 turn DeepSeek);
OpenRouter balance "$12.87 left" and Tavily "3 / 1,000 credits"; model
search listed 464 OpenRouter models. Routing fallback is covered by JVM
tests with a recorded 404, not yet seen on the phone.

## D-032 · 2026-10-02 · Balance and month spend on the closed service card — accepted
A closed chat service card in Settings shows one line, all in USD: "$12.87
left · $0.08 this month". The month comes from the service when it reports
one (OpenRouter GET /key usage_monthly, for this key), else from Jonaki's
own call records; the open card then says "Spend counted by Jonaki only".
DeepSeek's USD entry is used when present; other currencies convert with
the daily rates of ExchangeRate-API's free open endpoint (open.er-api.com,
no key, fetched once per app run); without a rate the balance is dropped
rather than shown in the wrong currency. From $100 the balance drops the
cents; spend under one cent shows "<$0.01"; under $1 left turns red and is
read out as "Low balance". A failed refresh keeps the last good value.
Mockup: docs/mockups/service-card-balance.html. Copy agreed with an Opus
reviewer. Why: user request. Limit: deleting a thread deletes its usage
rows, so Jonaki's own count drops with it.

Amendment (user, 2026-10-03): a card shows only what the service itself
reports. Jonaki's own count of the month is dropped, so DeepSeek shows
"$x left" only, and Gemini, Ollama, GLM and MiMo show no line.

## D-033 · 2026-10-02 · Compaction of long threads — accepted
When a run ends and its last request filled 70 % of the model's context
window (128k tokens assumed when the catalog does not know it), the
background model (D-036) writes a summary with the sections Goal,
Constraints and Preferences, Progress, Key Decisions, Next Steps and
Critical Context, merging any earlier summary. The last two user turns stay
word for word; the cut falls just before a user message, so a tool call
never loses its result. The summary is stored in `compactions`; later
requests send it at the start of the first kept message, so its bytes stay
the same until the next compaction (D-005). The chat still shows every
original message. A failed or cut-off summary (4,000-token limit) leaves
the thread whole until the next run. Why: plan M4 step 6. Limit: the chat
does not yet mark where the summary begins. Outcome: pending.

## D-034 · 2026-10-02 · Memory tool runs without approval — proposed
The memory tool (remember, forget, recall) declares a new cost,
`SideEffect.CHANGES_APP_DATA`: it changes only Jonaki's own records, which
the user sees as a step and can edit or undo on the memory screen, so the
permission broker runs it without an approval card. Guards instead of a
card: a fact is at most 500 characters; forget refuses a pinned fact; a
store serves one thread and cannot touch another thread's facts; the
guideline forbids keys and passwords. Why: an approval card on every
"remember" would interrupt most answers for facts the user just stated.
Rejected: `CHANGES` with approval (nags), `READ_ONLY` (false declaration),
approval only for forget (one tool has one cost; a forgotten fact shows its
text in the step, so it can be added back). Outcome: pending.

## D-035 · 2026-10-02 · Memory in the system prompt, cache-stable — proposed
The system prompt ends with a Memory section: global facts, then the
thread's facts, each within 6,000 characters (about 1,500 tokens at 4
characters per token), one line "- [id] text" per fact. Facts waiting for
review are left out. Which facts get in: pinned first, then latest
lastUsedAt, then highest id. The lines are then written in id order, never
in use order. The runner builds the section once per run and then sets
lastUsedAt of the included facts to now; they were already the most
recent, so the next run picks the same set and writes the same bytes, and
the prompt cache (D-005) breaks only when a fact is added, edited, removed,
pinned, or a recall pulls an older fact in. Facts the memory tool saves
during a run reach the next run. The section goes last so the base text
and tool list stay a cached prefix. Rejected: sorting lines by last use
(new bytes on every run). Outcome: pending.

## D-036 · 2026-10-02 · Background model and memory extraction — proposed
Background calls (memory extraction, compaction) use `BackgroundModel`: the
scoped model with a saved key and the lowest input plus output price per
million tokens in the model catalog, first in scoped order on a tie, else
the thread's own model. A model with no known price is never picked, so a
free OpenRouter model can win. Extraction runs after a run once 20 user or
assistant messages are unread, and when the user leaves a thread with at
least 2 unread (after the run, if one is going). It sends up to 40 new
messages (user text cut at 3,000 characters, answers at 1,200) with all
thread and global facts, and applies add, update and delete to thread
facts only; pinned and user-written facts, unknown ids and duplicates are
skipped. Review mode makes added facts wait; updates and deletes of
extracted facts still apply. A failed call leaves the messages unread; an
unreadable answer marks them read. Each call's usage is a hidden message
row (role BACKGROUND) so thread, month and usage-sheet totals include it.
Rejected: a separate usage table (all cost queries would need a union).
Outcome: pending.

## D-037 · 2026-10-02 · Skill library on disk, read-only for the model — proposed
Skills live in `files/skills/<name>/` with a SKILL.md whose YAML front
matter has `name` and `description` (Anthropic's format) and any files it
names. The name is also the folder name: lowercase letters, digits and
single hyphens, at most 64 characters; the description at most 1,024
characters. A SKILL.md that breaks these rules is refused on import and on
save, with the reason. The front matter is read by a small reader in
`core/skills` (plain, quoted and block values; other keys ignored), not a
YAML library. The model reads skill files with read_file under the virtual
path `/skills/<name>/...`; write_file and edit_file resolve only inside the
thread folder, so they cannot change a skill. Why: plan M5 step 1, D-014
(skills loaded via read_file). Rejected: a YAML library (new dependency for
two fields); real absolute paths in the prompt (longer, tied to the install).
Outcome: pending.

## D-038 · 2026-10-02 · Built-in skills update only while unedited — proposed
On each app start every folder under `assets/skills/` is compared with the
library. `files/skills-builtin.json` records the SHA-256 of the files the
app installed for each built-in skill and whether the user deleted it. A
missing skill is installed; a skill whose files still match the recorded
hash is replaced by a newer shipped version; a skill the user edited, or
replaced by an import of the same name, is kept and marked "Edited", and
the user can choose "Reset to built-in" in its editor; a deleted built-in
stays deleted until "Restore built-in skills". Why: an update must not
overwrite the user's edits silently. Rejected: always overwrite (loses
edits); never update (fixes to shipped skills never arrive).
Outcome: pending.

## D-039 · 2026-10-02 · Skills listed in the system prompt — proposed
The system prompt has a Skills section between the tool list and the
Memory section: a header that tells the model to read a skill's SKILL.md
with read_file before a matching task and follow it, then one line per
enabled skill, "- name: description (/skills/name/SKILL.md)", sorted by
name. The runner reads the library once per run, so every request of a
run sends the same bytes (D-005); an edit or import reaches the next run.
A skill whose SKILL.md is broken is left out of the prompt and shown with
its problem on the skills screen. read_file's prompt line names /skills/.
Why: plan M5 step 3, D-014. Rejected: the full SKILL.md text in the prompt
(every skill would cost its whole text on every request). Outcome: pending.

## D-040 · 2026-10-02 · Skills are on in every thread unless switched off — proposed
Each thread stores the skills the user switched off, as comma-separated
names in a new `threads.disabledSkills` column (Room version 4, an
AutoMigration with default ''). Every other usable skill in the library is
listed in the thread's prompt, so a new or imported skill is on everywhere
at once. Switches are in the chat's ⋮ menu under Skills. Why: a skill costs
one prompt line until used, and per-thread opt-in would make every new
skill a chore. Rejected: an enabled list per thread (new skills would be
off everywhere); a global on/off per skill in Settings (the thread switch
covers it). Outcome: pending.

## D-041 · 2026-10-02 · Skill import from a file, a link or a GitHub folder — proposed
Import takes a file from Android's document picker (no new permission): a
zip keeps the folder that holds the shallowest SKILL.md (macOS metadata
dropped), any other file is the SKILL.md. A link to github.com
(`/tree/<ref>/<path>`, a repository root, or a `/blob/` or raw link to a
SKILL.md) imports the whole folder through the GitHub contents API without
a key (60 listings an hour per address) and the files' raw links, with
OkHttp; any other link is fetched as a single SKILL.md. Limits: 100 files,
2 MB in all, 5 folder levels; the folder is listed before any file is
fetched. A name that exists asks "Replace?". Why: plan M5 step 2.
Limits: a branch name with "/" in a tree link is read as its first part
and fails with 404; private repositories are not reachable. Outcome: pending.

## D-042 · 2026-10-02 · Shared and attached files wait as chips, then go to inbox/ — proposed
The share sheet (SEND and SEND_MULTIPLE, any type) and the composer's
paper clip (system picker, several files) copy each file into the cache at
once, because the right to read it can end with the sending activity. A
share first asks "Add to thread": New thread or an existing one, newest
first. The files then show as chips above the chosen chat's field (tap to
remove) and shared text goes into the field. On Send the files move into
`inbox/` and the message ends with "Attached: inbox/a.csv, inbox/b.pdf",
so the model sees the paths. Why: one path for both ways in; a new thread
gets no folder until its first message. Rejected: copying into inbox/ when
the thread is picked (a file the user removes would stay, and New thread
would make an empty thread). Limit: chips do not survive the process; the
cache copies are deleted on the next start. Outcome: pending.

## D-043 · 2026-10-02 · One linked folder, read and written through share_file — proposed
Settings > Files > "Link a folder" opens the system folder picker
(OPEN_DOCUMENT_TREE); Jonaki keeps a persisted read and write grant and the
folder's name; "Unlink" releases it; linking another replaces it. The model
reaches it only through share_file: list_linked (one folder level, folders
first, cut at 8,000 characters with the rest saved to a file),
import_linked (a file into inbox/) and linked_folder (a thread file into
the folder's top). Built on DocumentsContract, no DocumentFile library. A
grant taken back in system settings answers "link the folder again". Why:
plan M6 step 1c, D-017. Rejected: a separate linked-folder tool (a 17th
tool against D-014's count). Outcome: pending.

## D-044 · 2026-10-02 · share_file: six actions, one approval cost — proposed
Actions downloads, save_as, share, linked_folder, list_linked,
import_linked; files to send must resolve inside the thread folder
(ThreadPaths). The tool declares `SideEffect.CHANGES`, so every call shows
an approval card that names where the file goes ("To Downloads:
artifacts/report.pdf"); "Allow in thread" covers all six actions, reading
the linked folder included. Downloads uses MediaStore into
Downloads/Jonaki/ on Android 10 and later and the "Save as" picker on
Android 8 and 9 (no storage permission). share hands a FileProvider link
(authority `<package>.files`, threads/ only) to the share sheet and
reports only that the sheet opened. A closed picker is an error, so the
model does not claim a save. Time limit 5 minutes for the picker. Why:
plan M6 step 2, user rulings of 2026-10-02 (no new permission, no new
library). Rejected: READ_ONLY for the list actions (one tool has one cost,
D-034); WRITE_EXTERNAL_STORAGE on Android 8 and 9. Outcome: pending.

## D-045 · 2026-10-02 · Pickers and the share sheet through VisibleActivity — proposed
The tool module defines `FileDestinations` (as tools/memory defines
MemoryStore); the app implements it. Pickers and the share sheet need an
activity on screen, so the app's `VisibleActivity` keeps the live
MainActivity instances (attached in onCreate, detached in onDestroy),
launches on the newest one that is started, and suspends until the result
through the activity's result registry. After a rotation it registers the
same key on the new activity, which hands over the parked result. When no
Jonaki window is on screen the call fails at once with "Ask the user to
open Jonaki, then call share_file again, or use action downloads";
Downloads and the linked folder work in the background. Why: the tool runs
in the agent service, which may not start activities. Rejected: an
interface in core/tool-api (only one tool needs it); a notification that
opens the picker (more moving parts for a rare case). Outcome: pending.

## D-046 · 2026-10-02 · Rules for files coming in — proposed
`IncomingFiles` in core/tool-api serves the app and share_file: at most
25 MB per file, checked while copying since providers often report no
size, with "sales.zip is over 25 MB"; a name keeps one file name ("/", "\"
and control characters become "_", empty or dot names become "file", at
most 120 characters with the extension kept); a taken name gets " (2)",
" (3)" before the extension and nothing is replaced. Shared file://
links are refused as unreadable, because they could name Jonaki's own
private files. Why: plan M6 step 1c. Rejected: a larger cap (a phone and
the model gain little from bigger files). Outcome: pending.

## D-047 · 2026-10-02 · Artifact tool and offline viewer — proposed
The model writes an HTML file in artifacts/ with write_file or edit_file,
then calls `artifact` with its path. The tool keeps a version when the file
changed (artifacts/.versions/<name>/vN.html), warns the model about
scripts, styles or images it would load from the web, and runs without an
approval card (SideEffect.CHANGES_APP_DATA, as D-034), because it only copies
inside the thread's own folder. The chat shows one "Open" card per shown
file. The viewer is a WebView with JavaScript on (decks and charts need
it), file and content access off and no JavaScript interface. Pages load
from https://artifact.jonaki/; every request is answered by the app: files
inside artifacts/ and the bundled Chart.js, everything else gets an empty
403. Documents carry a Content-Security-Policy with connect-src 'none'.
Tapped web links open in the browser. A version picker shows older copies;
the print button opens Android's print dialog, which offers "Save as PDF".
A page that leaves the app through share_file (Downloads, save as, share,
linked folder) gets a copy with Chart.js inside, so its charts work in any
browser. Why: plan M6 step 3, D-018. Outcome: pending.

## D-048 · 2026-10-02 · Chart.js bundled for artifacts — accepted
Chart.js 4.5.1 (MIT, chart.umd.min.js, 208 KB, about 71 KB compressed in
the APK) ships in feature/artifact's assets; the file matches the npm
package (sha512 integrity checked 2026-10-02). Pages load it as
lib/chart.js. Why: plan M6 step 4; the user chose Chart.js over uPlot and
over inline SVG only (2026-10-02), because models write working Chart.js
code more often. Outcome: pending.

## D-049 · 2026-10-03 · Images reach the model as content parts — proposed
A user message whose "Attached:" line names a .jpg, .jpeg, .png, .webp,
.gif, .heic or .heif file carries that image when the thread's model takes
images: OpenAI-compatible `image_url` data URLs, Gemini `inlineData`. The
model catalog decides: OpenRouter's `architecture.input_modalities`, and a
flag on built-in rows copied from OpenRouter's list of 2026-10-03 (Gemini,
OpenAI, GLM 5.3 Flash, MiMo yes; DeepSeek no); unknown counts as no. The
database keeps text only (no schema change): before each request
`ImageMessages` finds the images again from the text. The app shrinks each
image to at most 1,568 px on the long side, turns it upright from EXIF,
puts transparency on white and saves JPEG quality 85 in
`files/image-cache/<thread>/<sha-256 of the file>.jpg`, so the same file
gives the same bytes on every request and the prompt cache holds. A model
without images gets "[inbox/photo.jpg is an image; this model cannot see
images.]"; an image that does not decode gets "[… could not be opened as an
image.]". Why: user report of 2026-10-03. Rejected: encoding again on every
request (a system update could change the bytes); images in the database
(schema change, version 5 is the lead's). Outcome: pending.

## D-050 · 2026-10-03 · view_image tool — proposed
`view_image path=… [page=N]` lets the model look at an image or a PDF page
in the thread folder. It answers "Image shown: work/chart.png" and, before
the next request, `ImageMessages` adds a user message "[view_image:
work/chart.png]" with the image right after that turn's tool results.
Offered only to models that take images. PDF pages render through
Android's PdfRenderer at 1,568 px. Why: tool results are text in OpenAI's
chat format and Gemini's function responses take images only on some
models, while a user message with images works in both; tested with
scripted payloads in `ImageRequestsTest`. Raises D-014's tool count from 16
to 18 with read_document. Rejected: images inside tool results (not
portable); an action of read_document (that tool reads, this one shows).
Outcome: pending.

## D-051 · 2026-10-03 · read_document with PdfBox-Android 2.0.27.0 — proposed
New tool module `tools/read-document`: PDF text page by page ("--- Page 3
---"), offset and limit in pages, slides or sheets (default 20, at most
100), output over 30,000 characters saved through OutputLimiter. A page
with no text says it may be a scan and names the view_image call. A
password-locked file is refused by name. PdfBox-Android 2.0.27.0 (Apache
License 2.0, checked in its POM; the newest release, January 2023) is an
AAR, so this one tool module is an Android library; it still depends only
on core/tool-api. The app calls `PDFBoxResourceLoader.init` at start.
Release APK 4,006,757 → 5,913,535 bytes (+1.9 MB, measured 2026-10-03):
PdfBox's font and CMap tables are 1.6 MB of it. Bouncy Castle comes along
for certificate-locked PDFs; its post-quantum tables (4.1 MB compressed)
are excluded from the APK, which would otherwise be 10.1 MB. Why: user
ruling of 2026-10-03. Outcome: pending.

## D-052 · 2026-10-03 · Word, Excel and PowerPoint through zip and XML — proposed
.docx: body paragraphs, "#" for headings, "- " for list items, table rows
as tab-separated cells (headers, footers, footnotes and comments left
out). .xlsx: each sheet as tab-separated rows, shared strings resolved,
formulas as their saved value, dates as Excel day numbers. .pptx: slides
in the presentation's order, then "Notes:". Read with java.util.zip and
the platform SAX parser, each part capped at 64 MB unpacked. .doc, .xls
and .ppt are refused with "save it as .docx (or .xlsx, .pptx) or PDF"; a
locked Office file is recognised by its OLE container. Why: user ruling
of 2026-10-03 (no extra library). Rejected: Apache POI (about 10 MB).
Outcome: pending.

## D-053 · 2026-10-03 · Camera through the system camera app — proposed
A camera button next to the paper clip opens the system camera with
ACTION_IMAGE_CAPTURE (`TakePicture`) and a FileProvider link to
`cache/camera/photo-YYYYMMDD-HHMMSS.jpg`; no CAMERA permission is
declared (checked with aapt2 on the release APK). The photo becomes a chip
like a picked file and moves to inbox/ on Send. The pending path is kept
across a process restart; camera files are not cleaned at start for that
reason. Why: user ruling of 2026-10-03. Outcome: pending.

## D-054 · 2026-10-03 · The model's reasoning is saved and shown — proposed
Reasoning that a model streams (OpenRouter `reasoning`, DeepSeek
`reasoning_content`, Gemini thought parts) is saved in a new nullable
column messages.reasoningText (Room version 5, AutoMigration 4 to 5) and
never sent back to the model. The chat shows it in a "Thinking" block above
the answer: open with the newest six lines while it streams, folded to one
line once the answer starts, opened on tap. Why: user request (2026-10-03);
the user agreed to saving after hearing the cost: about 1 to 5 KB per
answer, about 5 MB per 1,000 answers. Outcome: pending.

## D-055 · 2026-10-03 · Working line while the agent runs — proposed
While a run is going and no approval card waits, the chat ends with a
glowing firefly dot, what the agent is doing ("Thinking…", "Writing…",
"Searching the web…", "Reading files…" and so on, named from the running
tool) and, after 3 seconds, the seconds since the prompt was sent. It
replaces the bare caret. Why: user request (2026-10-03), modelled on Claude
Code's status line. Outcome: pending.

## D-056 · 2026-10-03 · Copy, and edit and resend — proposed
Every prompt and every finished answer has a Copy button (the raw text,
Markdown for answers). A prompt also has Edit while no run is going: its
text goes into the field under an "Editing message" banner; sending deletes
that prompt and everything after it (messages, their steps, summaries that
covered them, and the extraction mark), then runs the new prompt. Facts
already extracted from deleted messages stay in memory. Why: user request
(2026-10-03), the user chose "edit and resend" over copying into the
field. Outcome: pending.

## D-057 · 2026-10-03 · Thinking level per model and per thread — proposed
A model that takes a thinking level offers Default, Off, Low, Medium and
High: in Settings, in the model's ⋮ menu (shown under its prices when not
Default), and for one thread in the chat's model sheet, below the selected
model. The thread's choice wins over the model's; Default leaves the
model's own default. Support: OpenRouter models whose supported_parameters
list "reasoning" (333 of 465 on 2026-10-03), Gemini 2.5 and 3, OpenAI GPT-5,
GPT-6, o3 and o4; DeepSeek, GLM, MiMo and Ollama offer none. Wire format:
OpenRouter `reasoning: {effort}` or `{enabled: false}`; OpenAI
`reasoning_effort` ("none" for Off); Gemini 3 `thinkingLevel` (Off is
"minimal"); Gemini 2.5 `thinkingBudget` 0, 1,024, 8,192, 24,576 (2.5 Pro
128 for Off). Gemini requests now always ask for thought summaries
(includeThoughts) so reasoning shows (D-054). Stored as
settings.thinking_levels and threads.thinkingLevel (Room version 6). Why:
user request (2026-10-03), "Settings and chat". Outcome: pending.

## D-058 · 2026-10-03 · Approval modes Ask, Auto and Bypass — proposed
Three modes decide which tool calls show an approval card. Ask: every
`CHANGES` and `CHANGES_THREAD_FOLDER` call (the behaviour before). Auto:
`CHANGES_THREAD_FOLDER` (write_file, edit_file) runs, `CHANGES` (share_file,
later phone, schedule, MCP) still asks. Bypass: nothing asks, also not a
subagent's request. `READ_ONLY` and `CHANGES_APP_DATA` never ask, and
"Allow in thread" allowances still apply. Settings > Approvals holds the
default (Ask for new installs); the chat's ⋮ > Approvals sets one thread's
own mode (Default, Ask, Auto, Bypass), stored as the nullable
`threads.approvalMode` (null follows Settings; Room version 7). The broker
reads the mode before every call, so a change during a run applies from the
next call. The status strip shows a red "Bypass" pill while a thread's mode
is Bypass. Why: user ruling (2026-10-03). Outcome: pending.

## D-059 · 2026-10-03 · Four built-in subagent types — proposed
Version 1 has four types and no custom ones. researcher: web_search,
web_fetch, youtube_summarize, read_file, find_files, search_files,
read_document, view_image; answers with Summary, numbered Findings with
source links, Gaps. scout: find_files, search_files, read_file,
read_document, web_search; answers compressed (Found, Where with exact
paths or links, Start here). writer: the read tools plus write_file,
edit_file, artifact. worker: every thread tool. Writer and worker end with
"Files written or changed" and "Blockers", and only they see the skill list.
Each gets only the tools the thread has; no subagent gets delegate (no
nesting) or memory (it reads the memory section, and reports new facts in
its answer). view_image follows the subagent's own model, so a text-only
thread can hand an image to a vision model. Every subagent also has
request_tool and ask_parent, and notes when one call starts several. Its
system prompt is built once from its starting tools (D-005); a tool granted
later joins only the request's tool list. Why: user rulings (2026-10-03),
adapted from the user's pi agents. Outcome: pending.

## D-060 · 2026-10-03 · delegate tool — proposed
`tools/delegate` depends only on `core/tool-api`, whose `SubagentLauncher`
interface core/agent's `SubagentRunner` implements. Arguments: agent, task,
extra_tools, model, or tasks[] (at most 3, set by the user on
2026-10-03, run in parallel; nesting stays forbidden; each
subagent's calls run one after another). No parent conversation is passed.
model names one of the user's scoped models by key, id or name; an unknown
one returns an error that lists them. Each answer comes back capped at
16 KB (16,384 characters, user ruling 2026-10-03); a longer one is saved
whole to `work/delegations/<folder>/<agent>-<n>.md` and the result names
the read_file call that continues it; if saving fails, the answer is cut
at 16 KB with a note. The notes board of a call is
`work/delegations/<folder>/notes.md`. The folder is 12 hex characters of
the call id's SHA-256 (Gemini 3 call ids carry the thought signature and
exceed the 255-byte name limit; a blank id gets a fresh one). Subagents
run in a supervisor scope: one that crashes is recorded as Failed and the
others go on; its end is saved even during Stop. Time limit 11 minutes, one more than
a subagent's own, so a subagent at its limit still returns its work. Stop
cancels every subagent. Declared read-only: the subagents' own calls go
through the broker. Guidelines tell the model to delegate reasoning-heavy
work, not single searches, and that subagents have no context. The
working line says "Subagents working…". Outcome: pending.

## D-061 · 2026-10-03 · Subagent budgets — proposed
A subagent has 10 tool steps (every call counts, request_tool and notes
too), $0.10 of model cost (the calls' actual costs, reported by the
service or priced from the catalog, plus its ask_parent answers; a call
with no known cost adds nothing, so a model with neither has only the step
limit; the prompt names the cost limit when the catalog has a price or the
service is OpenRouter) and 10 minutes. A failed model call is retried once
per turn after 2 s. At the step limit it gets one last request without tools and
answers; calls beyond the limit in the same turn get a "Not run" result.
At the cost limit, the time limit, a failed model call (after one retry
after 2 s) or Stop, it returns its texts so far and its last three tool
results, cut to 3,000 characters each. The result names the limit that
stopped it. Why: brief from the lead (2026-10-03). Outcome: pending.

## D-062 · 2026-10-03 · request_tool and the 3-minute rule — proposed
request_tool(name, reason) and a subagent's own calls go through
`SubagentGate` with the thread's approval mode (D-058): read-only tools,
"Allow in thread" allowances and Bypass need no card. Otherwise a card
names the subagent, the tool and the reason, with Allow once, Allow for
this task, Deny. For a thread-folder tool (write_file, edit_file), Allow
once on a request covers its next call and Allow for this task all of
them. A tool that leaves the app (share_file) is only added: every call
still shows its own card with its arguments, as the mode asks, so a
subagent misled by a web page cannot send a file out under a grant given
for a stated reason. A card unanswered for 3 minutes is withdrawn and
counts as skipped; an answer that lands as the time runs out still counts: the
model is told to go on with the other parts or stop, and the result lists
every skipped part. The thread's own agent waits without limit. Tested
with a hand-made virtual clock (`WaitTimer`, first named `ApprovalTimer`), because
kotlinx-coroutines-test would be a new dependency. Outcome: pending.

## D-063 · 2026-10-03 · ask_parent — proposed
ask_parent(question), at most two per subagent, is answered by one call
on the thread's model with the thread's system prompt, tool list (sent
with tool_choice "none", Gemini mode NONE, new `ChatRequest.toolsCallable`),
thinking level and images, and its history up to, not including, the turn
that called delegate (that turn's calls have no results yet), then the
question as a user message; at most 1,000 output tokens, through
`BackgroundModel.completeOn`. So the request starts with the same bytes as
the one that produced the delegate call and the provider's prompt cache
can serve them. Its usage is a BACKGROUND row, so it counts to the thread;
its cost also counts against the asking subagent's $0.10 and shows on its
card. Outcome: pending.

## D-064 · 2026-10-03 · Subagents in the database — proposed
Room version 7 (one AutoMigration from 6, with D-058's column) adds the
`subagents` table (thread, delegate call id, order, type, task, model,
status, answer, latest text, cost, times; deleted with its thread) and the
nullable `steps.subagentId`. A subagent's steps are saved as they happen
under "<subagent id>/<call id>", so ids from two providers cannot clash;
its model calls are BACKGROUND rows with their cost, so thread, month and
usage-sheet totals include them, and the run's cost line adds them. Edit
and resend (D-056) deletes the subagents of removed delegate calls; a
killed app marks running subagents stopped. Outcome: pending.

## D-065 · 2026-10-03 · Model per subagent type — proposed
Settings > Subagent models picks a model per type from the scoped models.
Default: the thread's model; for the scout the background model (D-036),
which is the thread's model when no priced model has a key. A model named
in the delegate call wins; a setting whose model is no longer scoped is
ignored. A subagent uses its model's own thinking level, not the
thread's. Stored as settings.subagent_models. Outcome: pending.

## D-066 · 2026-10-03 · Subagent cards in the chat — proposed
Under the run that called delegate, one card per subagent: a dot, its
name ("Researcher 2"), status (Working, Done, Out of steps, Over budget,
Timed out, Failed, Stopped; a step whose card went unanswered shows "No
answer"), step count and cost, then its task and the first
line of the latest text it wrote, each on one line. Tapped, it shows the
whole task, its steps as a track and its answer. Its approval cards read
"Researcher 2 asks for share_file" with the reason, and Allow once, Allow
for task, Deny. Several cards can wait at once. Strings reviewed by an
Opus subagent (6 of 36 changed). Artifacts a subagent
shows get "Open" cards like the thread agent's. Outcome: pending; D-126
proposes rows in the run in place of the cards.

## D-067 · 2026-10-03 · run_code: files in by name, results out from work/ and artifacts/ — proposed
run_code takes language (javascript or python), code and files (thread
paths or folders; a list, one path, or a list written as text). The
program sees exactly those files at the same paths (inbox/sales.csv).
Afterwards only new or changed files under work/ and artifacts/ are saved;
anything else the program changed, inbox/ included, is dropped and named
as "Not saved". This is stricter than write_file, which may write anywhere
in the thread folder. Limits: 25 MB per file and 50 MB per run each way
(as D-046), 120 s for the program, 180 s for the tool including Python's
start, output cut to 20,000 characters through OutputLimiter.
requiredCapabilities stays empty because only some calls need Python; a
missing Python is reported per call. Engines come through the
constructor from core/runtime-api (`CodeRuntime`), one module per engine
under runtimes/. Why: plan M8 steps 1 and 2, user ruling of 2026-10-03.
Outcome: pending.

## D-068 · 2026-10-03 · JavaScript through androidx.javascriptengine 1.1.1 — proposed
runtimes/javascript runs programs in JavaScriptSandbox (V8 in Android
System WebView's isolated process: no network, no files, no Android API).
A fresh sandbox and isolate per run, one run at a time per app (the API
allows one connection), heap 256 MB where the WebView supports the limit.
The program gets console.log and a files object (read, write, list; text
only, so binary files are refused with a hint to use Python); the last
expression's value is the result, and top-level await works. A run past
the time limit is stopped by closing the isolate. Phones whose WebView
lacks the sandbox or promise results get "Android System WebView on this
phone is too old". Adds Guava (shrunk by R8): release APK 5,954,755 to
5,972,819 bytes. Why: user ruling of 2026-10-03 (new dependency approved).
Rejected: QuickJS (named in the plan; native code to ship). Outcome: on
the A059 (WebView 153) a CSV total ran in 155 ms end to end, await worked,
and an endless loop was stopped at 120.3 s.

## D-069 · 2026-10-03 · Pyodide 314.0.7 pinned, downloaded from jsDelivr — proposed
runtimes/pyodide downloads Pyodide 314.0.7 (Python 3.14.2; the latest
stable release on 2026-10-03, npm dist-tag latest) from
https://cdn.jsdelivr.net/pyodide/v314.0.7/full/ into files/pyodide/314.0.7/.
The five core files (pyodide.js, pyodide.asm.mjs, pyodide.asm.wasm,
python_stdlib.zip, pyodide-lock.json; 13,532,188 bytes) have their SHA-256
pinned in PyodideRelease.kt; each download is hashed while it streams and
kept only on a match, and the core is hashed again before every run.
Packages come from the same CDN, checked against the sha256 in the pinned
lock file, with their dependencies from it; Pyodide checks them again as
it loads. The data add-on is numpy and pandas (7,889,748 bytes with
python-dateutil, pytz and six). Only plain file names ending in .whl, .zip,
.tar, .tar.gz or .tgz are downloaded, so no .so or .dex file is ever
fetched; wheels hold WebAssembly modules that run only inside the WebView.
How the hashes were checked: the CDN files, the npm package
pyodide@314.0.7 (tarball sha512 equal to the registry's integrity
"sha512-0YvXxEhf…lD2R1A==") and the GitHub release archive
pyodide-core-314.0.7.tar.bz2 gave the same SHA-256 for all five files; the
six wheels matched the lock file. Installed state is read from the files,
no database row (no Room change). Packages outside the lock file (PyPI
through micropip) are not supported yet. Why: user ruling of 2026-10-03.
Outcome: see D-013.

## D-070 · 2026-10-03 · Python runs offline in a hidden WebView worker — proposed
Each Python run gets a new WebView, made on the main thread with the
application context and destroyed afterwards. Its page (harness.html) and
Python worker come from the module's resources on a made-up host,
https://python.jonaki/. Three walls keep programs offline: the request
filter answers only the harness, the installed Pyodide files and the run's
input files (403 for everything else); every response carries a
Content-Security-Policy with connect-src 'self'; and Python runs in a
dedicated worker, which has no DOM, frames, WebRTC or sendBeacon, nor the
page's JonakiBridge. Checked on the A059: fetch, XHR, pyfetch and a
WebSocket to the internet all failed. The program's folder is /thread
with inbox/, work/ and artifacts/; given files appear at their thread
paths; all new or changed files go back to the app, which applies D-067.
The 120 s limit starts when the program starts (Python's start and
package loading get 45 s of their own); past it the worker is terminated
and the WebView destroyed. A renderer crash is reported, not fatal.
Known gap: with the activity in the background the run was 4x slower
(36.3 s against 8.7 s); runs from the foreground service were not
measured. Why: user ruling of 2026-10-03. Outcome: pending.

## D-080 · 2026-10-03 · Parallel read-only tool calls — proposed
Amends D-026 (user approved 2026-10-03): of the tool calls in one model
turn, consecutive READ_ONLY calls run side by side, at most 4 at once. Any
other call (CHANGES, CHANGES_THREAD_FOLDER, CHANGES_APP_DATA, an unknown
tool, arguments that are not a JSON object, and delegate and request_tool,
which are declared read-only but can show cards) runs alone in its place:
earlier calls finish first, later ones wait, so approval cards still come
one at a time. Example: read, read, write, read, read runs as two reads
together, the write, then two reads together. web_search calls of one
group start 0.5 s apart (user's request, for rate limits), so three start
at 0, 0.5 and 1.0 s and overlap. Results go to the model, and their TOOL
rows get positions, in call order; a finished call's result is saved once
every earlier call has finished, so its step card can show Running a
little longer. The step budget still counts model turns. Stop cancels
every running call; started ones show Stopped. Subagents follow the same
rule with their per-call step count, fixed before the calls start (this
changes D-060's "each subagent's calls run one after another"). The run's
folded line counts overlapping steps' time once. Code:
`ToolCallScheduler` in core/agent; tests in `ParallelToolCallsTest`.
Outcome: pending (not checked on a device).

## D-081 · 2026-10-03 · Context sheet on the ring pill — proposed
Tapping the context ring in the status strip opens a sheet like Claude
Code's /context (user request, 2026-10-03): percent used, "N of M tokens",
a bar, then one row per part with tokens and percent of the window: System
prompt, Tool definitions (prompt lines, guidelines and schemas, with the
tool count), Skills (count), Memory (fact count), Summary of older
messages (with the messages it covers), Messages, Tool results, Images,
Free; then "Older messages are summarised at 70 % (N tokens)", from
`CompactionPlan.thresholdTokens`, the compactor's own check. The total is
the input count the service reported for the latest request, which the
ring already shows; the split is an estimate: each part is measured in
characters (4 per token; 1,500 tokens per image, as images are shrunk to
1,568 pixels) from the pieces the next request would send (the runner's
tools, sections and history after compaction, without marking memory as
used), then scaled to that total. Before the first request everything is
estimated and the sheet says so. No token counter exists in the
repository, so none was reused. Code: `ContextBreakdown` (core/agent, JVM
tests), `AgentRunner.contextBreakdown`, `ContextSheet`. Limit: the parts
describe the next request, the total the last one, so they differ by the
last answer and any compaction since. Outcome: pending (not checked on a
device).

## D-085 · 2026-10-03 · One + button and an in-app photo gallery — proposed
The composer's paper clip and camera button (D-042, D-053) become one "+"
at the bottom left, as in ChatGPT. It opens a sheet with three tiles:
Camera (as D-053), Photos, Files (the system document picker, as D-042).
Photos opens Jonaki's gallery sheet in the new module `feature/gallery`:
Recent, a grid of the newest images (MediaStore, newest added first, pages
of 120 loaded as the grid scrolls), and Collections, the MediaStore
buckets with their newest image as cover and a count on a line of its own;
an album opens in the same grid with a back arrow. Taps pick several
images across both tabs with numbered marks; "Add N" copies them through
IncomingShares into chips, so the 25 MB cap and naming of D-046 and the
send path of D-049 are unchanged. Thumbnails: ContentResolver.loadThumbnail
on Android 10 and later, a power-of-two decode (ImageScale.sampleSize)
turned by MediaStore's orientation on 8 and 9, four loads at a time on the
IO dispatcher, an LruCache of at most 32 MB. The MediaStore queries sit
behind the `PhotoLibrary` interface (`MediaStorePhotoLibrary` in the app).
No image library. Release APK 6,018,647 → 6,056,863 bytes (measured
2026-10-03). Why: user request of 2026-10-03. Rejected: Coil (a new
dependency); a separate Photos button (the composer had two buttons
already). Limits: the selection is lost on rotation; previews at 360 dp
and font scale 1.3 exist in GalleryPreviews.kt but were not rendered (no
emulator). Outcome: pending.

## D-086 · 2026-10-03 · Photo permission, asked once, with the system picker as fallback — proposed
New permissions, approved by the user on 2026-10-03: READ_MEDIA_IMAGES and
READ_MEDIA_VISUAL_USER_SELECTED (Android 13 and later), READ_EXTERNAL_STORAGE
with maxSdkVersion 32 (checked with aapt2 on the release APK). Jonaki asks
the first time Photos is tapped, never at start. Android 14 "Select
photos" opens the gallery with only those photos and a note "Only photos
you allowed" with Select more (asks again, so Android offers its picker)
and Settings (Jonaki's page in system settings). Any
answer that gives no access, a dismissed dialog included, counts as a
refusal: Jonaki stores it (preferences `photo_access`), never asks again,
and every Photos tap opens the system photo picker
(PickMultipleVisualMedia, images only), which needs no permission, until
access is granted in system settings; the grant is read again on every
tap and on return to the app. Why: user request of 2026-10-03; Android's
guidance for partial access. Rejected: asking again after a refusal
(Android blocks the dialog after two refusals anyway). Outcome: pending.

## D-090 · 2026-10-03 · Code sheet for run_code steps — proposed
Tapping a run_code step (in the run track or a subagent card) opens a
sheet with "Python · 12 lines", a copy button and two tabs. Code: line
numbers, colours, no wrapping (long lines scroll sideways), and the line
an error points at marked red. Output: printed text, stderr in amber, the
result, the error, saved files as links (a page in artifacts/ opens in
the artifact viewer, anything else in another app through the FileProvider
of D-044, ACTION_VIEW) and "Not saved" files in grey. The folded step
reads "Python · 12 lines". Data, without a Room change: the code from the
step's arguments; the output from the TOOL row of that call (the step
keeps 2,000 characters), or from OutputLimiter's file when the row was cut
at 20,000; a subagent's step has no TOOL row, so its 2,000-character
preview is shown with "Output cut". Printed text over 50,000 characters is
cut for the screen. `RunCodeReport` (tools/run-code) reads the tool's own
text back, with the labels shared with RunCodeTool; `ProgramErrorLine`
reads the line from Pyodide's `File "main.py", line N` and from V8's
`eval at …, <anonymous>:N:C` frames; the JavaScript runner now keeps the
program on line 1 when it wraps top-level await. Colours: `CodeColors` in
core/ui, one set per theme, off the firefly yellow-green (D-024); computed
contrast at least 5.1:1 on surfaceContainer. The tokenizer is our own
(`CodeTokenizer`, about 200 lines, JVM tests), no new dependency. Why: user
request of 2026-10-03. Limits: printed text that itself contains a label
line ("Result: …" with no result of its own) is read wrongly; JavaScript
syntax errors name no line; previews at 360 dp and font scale 1.3 exist
in CodeRunPreviews.kt but were not rendered (no emulator). Outcome:
pending.

## D-091 · 2026-10-03 · Tool groups and the first-run tool picker — proposed
Ten rows the user switches (plan M8 step 3, user ruling 2026-10-03): Files
(read_file, write_file, edit_file, find_files, search_files, read_document,
view_image; always on, as every other tool works on thread files), Web
(web_search, web_fetch), YouTube, Memory (the memory tool only; the memory
section and background extraction stay), Subagents (delegate), Reports
(artifact), Share (share_file), JavaScript and Python (languages of the one
run_code tool, whose language enum, prompt line and guidelines name only
the languages that are on; off for both leaves run_code out), and the data
add-on (D-092). A switched-off group's tools never reach ToolRegistry's
list, so they cost no prompt tokens; subagents see the same list. Every
group starts on; Settings stores the switched-off ones
(`disabled_tool_groups`), so a group added later starts on too. The
picker is the new module feature/onboarding; it shows while
`tool_picker_seen_version` (AppSettings, no Room change) is below
ToolPicker.CURRENT = 1, so on a new install and once on installs from
before it, and Continue sets it. The same rows (core/ui ToolGroupList) are
Settings > Tools. Code: `ToolGroup`, `ToolGroups`, `ToolPicker`
(app/settings), `ToolGroupRows`; tests ToolGroupsTest, ToolRegistryTest,
ToolGroupRowsTest. Rejected: a per-thread switch (the Web switch in the
chat stays and both must be on). Outcome: pending.

## D-092 · 2026-10-03 · Python's two switches — proposed
Python's switch says whether the model is offered Python; it starts on but
downloads nothing, and its row shows "13.5 MB download" with a Download
button. Switching it on from off starts the download at once with
progress and Cancel, since the size stands beside the switch; switching
it off keeps the files (Settings > Python removes them). The data
add-on's switch is its installed state: on downloads numpy and pandas
(7.9 MB, with Python first when missing), off removes them; it waits for
Python's switch. Jonaki cannot tell mobile data from Wi-Fi without the
ACCESS_NETWORK_STATE permission, which needs approval, so nothing
downloads without the size on screen. Why: user ruling 2026-10-03 (start
the download with progress, or offer it). Outcome: pending.

## D-093 · 2026-10-03 · Settings > Python — proposed
Status (Not installed, Installed, Damaged with the file names), "Pyodide
314.0.7", storage used, Install (13.5 MB) with progress and Cancel,
Repair (fetches again only files with the wrong SHA-256), Remove with a
confirmation, the data add-on with Install or Remove, the installed
packages, and a field to install one more package by name from the lock
file; another name fails with the installer's "requests is not available
for Pyodide 314.0.7". `PythonSetup` (app/run) holds the state, runs one
download at a time in the application scope (leaving the screen does not
stop it) and reads the state back from the files after every change.
Removing packages also removes the installed packages that need them,
and their dependencies no other installed package needs
(`PyodideFolder.removePackages`). Code tests: PythonSetupTest (15),
PyodideInstallerTest (3 new). Outcome: pending.

## D-094 · 2026-10-03 · Just-in-time Python install card from the saved step — proposed
When the last turn's run_code step failed with run_code's NotInstalled or
MissingPackages text, the chat shows a card after the turn: "Needs Python
(13.5 MB). Install?" or "Needs pandas (7.9 MB). Install?", with Install
and Not now; Install shows progress and Cancel, then "Python installed"
with Try again, which sends "Installed. Try again." (the user can also
just ask again). The card is read from the step's saved result through
`InstallNeeds.of` in tools/run-code, the same object that writes the
error's first sentence, so the model still gets plain text, the result
row is the only record (no Room change, no runner state) and the card
survives a restart. Packages within the data add-on install the whole
add-on with its measured size; others install by name with no size
shown. Only the last turn gets a card, so it goes once the user writes
again; Not now hides it until the chat is opened again. The tool's text
now names "Settings > Python" and the chat's Install button. Code:
`InstallNeeds`, `PythonCards`, `ChatItems.pythonCardFor`,
`PythonInstallCard`; tests RunCodeToolTest, PythonCardsTest,
ChatItemsTest. Rejected: a structured field on ToolOutput (changes
core/tool-api for one tool); a runner StateFlow (lost on restart).
Outcome: pending.

## D-095 · 2026-10-03 · Download sizes in decimal megabytes — proposed
Python's sizes show as decimal megabytes with one decimal (13,532,188
bytes is "13.5 MB", the add-on "7.9 MB"), as the user wrote them and as
app stores show downloads; run_code's error text uses the same form.
IncomingFiles.describeSize keeps binary megabytes for the 25 MB file cap.
Code: UsageFormat.byteSize. Outcome: pending.

## D-096 · 2026-10-03 · Cost per call for tools with mixed actions — proposed
`Tool.sideEffectOf(arguments)` returns the cost of one call and defaults to
`sideEffect`; the permission broker asks only when that cost is CHANGES.
phone: calendar_list and clipboard_read run at once; calendar_add,
reminder, notify, clipboard_write and open_app ask. schedule: list runs at
once; create and cancel ask. "Allow in thread" still covers the whole
tool. The approval card names the action ("Add to calendar: Dentist",
"Open app: Maps", "Schedule: News"). For M8's approval modes: phone and
schedule ask even in Auto. Why: plan M9 wants read-only actions without
approval, and D-014 keeps one tool per feature. share_file keeps its one
cost (D-044). Rejected: separate read tools (more tools, against D-014).
Outcome: pending.

## D-097 · 2026-10-03 · Reminders: an alarm plus a file — proposed
phone reminder saves id, text and time in files/reminders.json and sets
an AlarmManager `setExactAndAllowWhileIdle` alarm. Without "Alarms &
reminders" (SCHEDULE_EXACT_ALARM, off by default for new installs on
Android 14) it uses `setAndAllowWhileIdle`, and the tool result says the
reminder may come some minutes late. The alarm posts a "Reminder"
notification on the Reminders channel and removes the entry. A receiver
sets every alarm again on BOOT_COMPLETED, MY_PACKAGE_REPLACED and the
exact-alarm permission change; a reminder missed while the phone was off
fires at once. Permissions are asked on first use: notifications on the
first reminder, notify or schedule create; calendar on the first calendar
action. A refusal is an error that names Android settings. Reading the
clipboard, opening an app and every permission dialog need Jonaki on
screen; otherwise the error says to open Jonaki. open_app sees only
launcher apps through a manifest `<queries>` entry. Why: plan M9 step 1.
Rejected: a Room table (a schema version that would collide with the
M7 and M8 branches, for a few rows); USE_EXACT_ALARM (not approved, meant
for alarm-clock apps). Outcome: pending.

## D-098 · 2026-10-03 · Scheduled tasks in a file, runs on WorkManager — proposed
files/scheduled-tasks.json holds each task's id (8 hex characters),
thread, title, prompt, repeat (none, daily or weekly), anchor (local
date and time) and next run. Each run is one WorkManager
OneTimeWorkRequest tagged with the id, delayed to the planned time, and
waiting for a network. At most 20 tasks. Cancel removes the entry and the
tagged work. A task whose thread was deleted is removed at its next run.
Settings > Scheduled lists reminders and tasks, soonest first, each with
Cancel. WorkManager 2.10.5 adds WAKE_LOCK and ACCESS_NETWORK_STATE to the
manifest. Why: plan M9 step 2 and 3. Rejected: a Room table (as in
D-097); PeriodicWorkRequest (it drifts and cannot keep a clock time).
Outcome: pending.

## D-099 · 2026-10-03 · A task keeps its clock time; each run plans the next — proposed
A daily task at 08:00 stays at 08:00 local time across daylight-saving
changes and time zones; weekly keeps the anchor's weekday. Each worker
first plans the next run, counted from the later of its planned time and
now, then runs the prompt. So a worker that starts a second early does not
run twice, runs missed while the phone was off do not catch up, and a
worker that WorkManager starts again after the process died finds a later
planned time and does nothing. Why: WorkManager timing is inexact; a
chain of delays would drift. Tested in NextRunTest and ScheduleBookTest.
Outcome: pending.

## D-100 · 2026-10-03 · A scheduled run is a message in its thread — proposed
The worker waits while the thread is busy, then sends `Scheduled task
"<title>": <prompt>` through AgentRunner, so the run uses the thread's
model, tools, approvals and history like a typed message. When the run
ends it posts the last answer or error (at most 2,000 characters) as a
notification titled with the task's title; an approval request first
posts "Waiting for your approval". After 9 minutes (WorkManager stops a
worker at 10) the run stops and the notification says "Stopped after 9
minutes". Android 12 and later refuse to start the agent's foreground
service from a worker; AgentService.start now ignores that refusal and
the running worker keeps the process alive. Why: plan M9 step 2, one code
path for runs. Rejected: a separate background loop (a second code path);
setForeground on the worker (more manifest work for the same limit).
Outcome: pending.

## D-101 · 2026-10-03 · MCP client on OkHttp, one session per tool run — proposed
`tools/mcp` speaks MCP Streamable HTTP itself on OkHttp and
kotlinx.serialization; the official Kotlin SDK would force a Kotlin upgrade.
Each POST carries one JSON-RPC 2.0 message with `Accept: application/json,
text/event-stream`; an answer may be one JSON object or an event stream, in
which the client skips the server's own notifications until the answer with
its id. Order: initialize (asks for 2025-06-18), notifications/initialized,
then tools/list (follows nextCursor, at most 20 pages) or tools/call. The
`Mcp-Session-Id` from initialize goes on every later request, with
`MCP-Protocol-Version` set to the version the server answered. HTTP 404 on a
session opens a new one once; the session ends with DELETE (405 ignored). An
optional header per server (name and value) goes on every request. Each run
of the tool opens and closes its own session, so nothing outlives a call
(AGENTS.md: no hidden state); the cost is two extra round trips per call.
Live check (2026-10-03, `DeepWikiLiveTest`, no key, no cost): against
https://mcp.deepwiki.com/mcp search found read_wiki_structure, describe showed
repoName, and the call returned square/okhttp's page list in 5.7 s; DeepWiki
answers as an event stream and gives no session id. Outcome: pending.

## D-102 · 2026-10-03 · MCP tool lists cached in files for a day — proposed
Each server's tools/list answer is saved as `files/mcp-tools/<server id>.json`
with its address and fetch time. search and describe read it while it is
under 24 hours old and from the same address; otherwise they fetch it again.
describe of a tool missing from a cached list fetches once more. A call the
server refuses with a JSON-RPC error (unknown tool, bad arguments) fetches
the list on the same session, saves it, and tells the model whether the tool
still exists. Saving or removing a server in Settings deletes its file. Why:
search should not cost a connection per server on every use. Rejected: a
Room table (schema change for data that is only a cache). Outcome: pending.

## D-103 · 2026-10-03 · mcp tool: read-only search and describe, approved call — proposed
One tool `mcp` with action search (keywords over every server's tools; a word
in the name counts 3, in the description 1; blank lists all; default 10
results, at most 50), describe (full description and input schema) and call
(arguments as a JSON object, or JSON text). Results over 30,000 characters
go through OutputLimiter; images and audio become "[image/png image, not
shown]". The tool declares `SideEffect.CHANGES` and overrides
`Tool.sideEffectOf(arguments)` (D-096) to return READ_ONLY for search and
describe, so the permission broker runs them without a card, while call
shows the existing card with "server: tool". "Allow in thread" then covers every
mcp call in that thread. For the coming approval modes, mcp counts as
leaving the app: Auto asks, only Bypass skips the card. The tool is offered
only when at least one server exists; its prompt line names the servers.
Changes D-034's "one tool has one cost" for this tool. Outcome: pending.

## D-104 · 2026-10-03 · MCP servers in Settings — proposed
Settings has an "MCP servers" group between Skills and Files: one row per
server (name on one line, URL up to two lines), and "Add server". A dialog
takes Name, URL, Header (optional) and Header value; it checks for a name,
a name not used by another server (case ignored, since the model names
servers) and an http or https URL; edit offers "Remove server". The list is
JSON in app preferences; header values are encrypted with the API keys'
Keystore key (`SecretStore` run-time secrets) and never shown again; the
value field then says "Saved" and empty keeps it. A value with no header
name is sent as Authorization. Outcome: pending.

## D-105 · 2026-10-03 · Model lists from each service's GET /models — proposed
A preset with `listsModels` (OpenAI, MiniMax, Qwen, Xiaomi MiMo, Ollama Cloud,
Ollama on the network) is asked for `<base URL>/models` with the saved key
each time its Add models screen opens; listing is free. The OpenAI-format
ids join the built-in rows, which keep their prices; an id without a row is
offered without a price, like a typed id. Ids with embed, tts, whisper,
transcribe, dall-e, image, moderation, realtime, audio, search, davinci or
babbage are left out. The answer is cached as `cache/models-<service>.json`;
a failure keeps the last list. Ollama Cloud lists without a key. Z.ai
documents no model list, so GLM keeps the built-in rows. MiMo's list is
undocumented: GET /v1/models answers 401 without a key where an unknown path
answers 404 (curl, 2026-10-03); the same check holds for Qwen. Tested with
MockWebServer only (no keys for these services). Outcome: pending.

## D-106 · 2026-10-03 · Presets and prices for GLM, MiMo, OpenAI, MiniMax and Qwen — proposed
New services MiniMax (`https://api.minimax.io/v1`, default MiniMax-M3) and
Qwen (Alibaba Model Studio, Singapore, OpenAI-compatible mode,
`https://dashscope-intl.aliyuncs.com/compatible-mode/v1`, default
qwen3.8-flash) are cards with their own keys. Built-in rows now carry each
service's own pay-as-you-go price per million tokens (input, output,
cache read), read on 2026-10-03:
GLM 5.3 Flash 0.15/0.50/0.03, 5.3 FlashX 0.37/1.25/0.075, 5.3 and 5.2
1.40/4.40/0.26, 4.7 Flash free (docs.z.ai/guides/overview/pricing; context
from docs.z.ai/guides/llm/glm-5.3 and guides/vlm/glm-5.3-flash). MiMo V2.6
Flash 0.14/0.28/0.0028, Pro 0.435/0.87/0.0036, Pro UltraSpeed 4.35/8.70/0.036
(mimo.mi.com/docs/en-US/price/pay-as-you-go). OpenAI GPT-6 Luna
0.10/0.50/0.01, GPT-6 Sol 2/10/0.20, GPT-6.1 Sol 2/10/0.10, GPT-6 Astra
10/50/1, GPT-5.6 Luna 0.20/1.20/0.02, GPT-5 Mini, GPT-5.4 Nano and GPT-5.5
as before (developers.openai.com/api/docs/pricing, Standard tier). MiniMax
M3 0.30/1.20/0.06 up to 512K input tokens, M2.7 0.30/1.20/0.06, M2.7
Highspeed 0.60/2.40/0.06 (platform.minimax.io/docs/guides/pricing-paygo;
base URL from platform.minimax.io/docs/api-reference/text-openai-api, list
from .../models/openai/list-models). Qwen3.8 Max 2/6, Qwen3.7 Plus 0.40/1.60,
Qwen3.8 Flash 0.15/0.47, Qwen3.7 Flash 0.03/0.13 for requests up to 32K
tokens, cache reads at 20 % of input
(alibabacloud.com/help/en/model-studio/model-pricing and .../context-cache).
Ollama: docs.ollama.com/api/openai-compatibility; Ollama Cloud is a
subscription without token prices. Context windows and image input not on
the services' pages come from OpenRouter's list of 2026-10-03. Balances: none
of these services offers a balance a normal API key can read (Z.ai and
MiniMax document none; MiMo's console balance needs a login session; OpenAI
and Alibaba need admin or AccessKey credentials), so their cards show no
balance line, as D-032 already says for GLM and MiMo. Limits: one price per
model, so MiniMax M3 above 512K and Qwen3.7 Flash above 32K tokens are
priced too low (Qwen3.7 Flash costs 0.10/0.40 from 32K to 256K); Qwen
serves the Singapore region only. Thinking levels stay off for MiniMax and
Qwen. Outcome: pending.

## D-107 · 2026-10-03 · Custom instructions, global and per thread — proposed
The user writes general instructions in Settings (Answers, "Custom
instructions") and a thread's own instructions in the chat's ⋮ menu ("Style
and persona"). Both go into one "User instructions" section of the system
prompt, after the tool list and before Skills and Memory, in the order
answer style, general, persona, this thread; the header tells the model
that the later part wins. So the thread text extends the general text and
overrides it where they disagree. Each text takes at most 8,000 characters
(about 2,000 tokens). The runner reads them once per run, so the prompt
changes only when the user edits them: the next request misses the prompt
cache once (D-005). With nothing set the section is empty and the prompt is
byte-identical to 0.7.0. Stored as settings.custom_instructions and
threads.instructions (Room version 7). Rejected: thread text replacing the
general text (the user would copy shared rules into every thread).
Mockups: docs/mockups/style-and-personas.html. Outcome: pending.

## D-108 · 2026-10-03 · Answer style in the system prompt — proposed
Concise, Normal and Detailed. Settings holds the default (Normal); the
chat's "Style and persona" sheet adds Default, which follows Settings, as
the thinking level does (D-057). Normal adds no line; Concise and Detailed
add one line at the start of the User instructions section. The style is in
the system prompt, not in the latest message: it changes only when the user
changes it, so it costs one cache miss per change, while a line in every
message would be stored in the history for good, add tokens to every turn
and leave stale styles in older messages. Stored as settings.answer_style
and threads.answerStyle (null follows Settings). Rejected: more presets
(no clear use yet; the instructions cover the rest). Outcome: pending.

## D-109 · 2026-10-03 · Personas as saved, named instructions — proposed
A persona is a name (at most 60 characters) and instructions; its voice is
part of the instructions. Settings lists personas under Personas, with
"Add persona"; each opens an editor with Save and Delete (asks first). A
thread uses at most one persona, picked in the "Style and persona" sheet,
also before the first message. The persona's part comes after the general
instructions and before the thread's, so it adds to the user's general
rules and the thread text can still adjust it; the base prompt, tools and
rules stay. Deleting a persona sets the threads that used it to none.
Stored in a new personas table and threads.personaId (Room version 7, one
AutoMigration 6 to 7). Rejected: a persona replacing the general
instructions (language and unit rules would be lost); a default persona
for new threads (not asked for). Outcome: pending.

## D-110 · 2026-10-03 · Projects group threads — proposed
A project has a name, optional instructions and an optional model for new
threads; nothing else is shared. Table `projects` and nullable
`threads.projectId` (Room version 7, one AutoMigration with D-111), with
no foreign key: deleting a project first clears `projectId` on its threads,
so threads are never deleted with it. The thread list shows chips under the
search field (All, each project by name, "+ Project"); a selected project
shows a header with its name, model and ⋮ (Edit project, Delete project).
In All, a thread's last line starts with its project name. A thread joins a
project through its long-press menu (Move to project) or by starting it
while the project is selected; it then starts with the project's model and
can switch later. Instructions go into the system prompt after the tool
list and before skills and memory, read once per run, so the prompt cache
breaks only when they are edited or the thread moves (D-005). Why: user
request (2026-10-02); smallest design that organises the list. Rejected
for now: shared project memory and shared files (both need new scopes in
the memory tool and the file tools). Mockups:
docs/mockups/projects-and-incognito.html. Outcome: pending.

## D-111 · 2026-10-03 · Incognito chat — proposed
The lock in the thread list's bar starts an incognito chat
(`threads.incognito`, default 0, Room version 7). It neither reads nor
writes memory: no memory tool, no Memory section in its prompt (so no
global facts either), no background extraction, and no Memory item in ⋮.
It never joins a project when started. The list marks it with a lock in
place of the dot; the chat shows a banner "Incognito: no memory. Deleted a
day after the last message." with Keep. It is deleted with its folder 24
hours after its last non-background message (or its creation), checked at
app start and whenever the thread list shows, never while a run is going;
no WorkManager. Keep makes it a regular thread and marks its messages so
far as read by extraction, so only later messages reach memory. Why: user
request (2026-10-02); the user wrote those messages expecting privacy.
Rejected: offering past messages to extraction on Keep (breaks that
expectation); a periodic job (a check at start and list view is enough).
Limit: a chat left open on screen past the day is deleted at the next list
view or app start. Outcome: pending.
## D-112 · 2026-10-03 · Attachment chips survive a process restart — proposed
The staged copies move from `cache/incoming/` to `files/waiting-attachments/`
(Android may clear the cache), and the chips waiting per thread key are
saved after every change in `waiting-attachments/waiting.json` (thread key
to a list of id and name; written through a temporary file). At start the
app reads it before anything is staged, drops chips whose copy is gone and
deletes staged folders no chip names (a share whose thread was never
picked). Deleting a thread deletes its chips. This lifts D-042's limit.
Why: STATUS known gap. Rejected: a Room table (a migration for a few
lines of state); a file in the thread folder (a new thread has none until
its first message). Outcome: pending.

## D-113 · 2026-10-03 · Back from the skill editor asks before losing edits — proposed
When the editor's text differs from the saved SKILL.md, Back (the arrow or
the system gesture) asks "Discard changes?" with Discard and Keep editing;
tapping outside keeps editing. Without changes Back leaves at once. A failed
save counts as unsaved. feature/skills now uses activity-compose (already
in the app) for BackHandler. Why: STATUS known gap; the smallest fix.
Rejected: keeping a draft per skill (a stored second copy that can drift
from the file). Not shared: the memory and rename dialogs hold a line or
two and close only on Cancel or a tap outside. Outcome: pending.

## D-114 · 2026-10-03 · Chat divider where the summary ends — proposed
After compaction the chat shows a thin line, "Earlier messages summarised",
before the first turn the newest summary does not cover; a tap opens the
stored summary as Markdown under the line, a second tap folds it. Every
original message stays visible above it (D-033). No divider when the
summary covers no shown turn or all of them. The chat reads the summary
through a new Flow query on `compactions` (no schema change). Why: lifts
D-033's limit. Rejected: hiding the summarised messages (D-005 keeps them
in view). Outcome: pending.

## D-115 · 2026-10-03 · Only the newest user turns carry their images — proposed
Before each request ImageMessages counts the user turns (the step-budget
notice not counted) and sends images, attached or from view_image, only
for turns from `firstTurnWithImages` on: 0 up to five turns, then 3 at six
turns, 6 at nine and so on, so the newest 3 to 5 turns keep their images.
An older image becomes a note in its place: "[inbox/photo0.jpg is not
repeated; call view_image with path=inbox/photo0.jpg to see it again.]"
(with page=N for a PDF page); it is never loaded. The cut moves only every
third turn and only forward, and a note's words depend only on the image's
path, so between two moves every earlier message keeps its bytes and the
prompt cache holds (D-005, D-049). Tested in ImageMessagesTest and in both
wire formats in ImageRequestsTest. Why: STATUS known gap; each image costs
about 1,000 to 1,600 tokens on every request. Rejected: a cut that moves
every turn (breaks the cache at the cut on every new message); keeping
only the current run's images (the model loses a photo the user is still
asking about). Outcome: pending.
## D-116 · 2026-10-03 · WorkManager's own permissions — proposed
WorkManager 2.10.5 (approved for M9) merges WAKE_LOCK and
ACCESS_NETWORK_STATE into the manifest. Both are install-time permissions:
Android grants them without a dialog. They come with the approved
dependency, so no separate approval was asked. Outcome: pending.

## D-117 · 2026-10-03 · Per-call cost runs through the approval modes — proposed
M9 (D-096) and MCP each added a per-call cost; only `Tool.sideEffectOf
(arguments)` stays. `PermissionBroker.runsWithoutAsking(tool, call)` reads
that call's cost and then applies the thread's mode (D-058), so phone
calendar_list and mcp search run at once, while calendar_add, schedule
create and mcp call ask in Ask and Auto and run in Bypass. Subagents'
request checks go through the same function. Outcome: pending.

## D-118 · 2026-10-03 · Order of the user's instructions in the prompt — proposed
After the tools, one "User instructions" section joins, in this order:
answer style, general instructions, project instructions, persona, this
thread's instructions; the section says the later part wins, so the most
specific text comes last. Skills and memory follow (incognito threads have
no memory section). With nothing set the prompt is byte-identical to 0.7.0
(PromptBuilderTest). The context sheet counts this section with the system
prompt. Outcome: pending.

## D-119 · 2026-10-03 · One migration from 7 to 8 for personas and projects — proposed
Version 7 is v0.8.0's (subagents, approval mode). Personas (D-109) and
projects with incognito (D-110, D-111) were built as separate versions on
branches; they now share AutoMigration(7, 8): the personas and projects
tables, and on threads answerStyle, personaId, projectId (nullable),
instructions ('' by default) and incognito (0 by default). Tested by
PersonasMigrationTest and ProjectsAndIncognitoMigrationTest. Outcome:
pending.

## D-120 · 2026-10-03 · Phone, Schedule and MCP tool groups — proposed
Three groups join the tool picker and Settings > Tools: Phone (phone),
Schedule (schedule) and MCP (mcp), each on by default and switchable.
ToolPicker.CURRENT goes from 1 to 2, so the picker shows once more to
installs that saw the first version. Outcome: pending.

## D-121 · 2026-10-03 · run_code reads the JavaScript reply's text fields as text — proposed
On the phone, `console.log(fib.join(', '))` printed "0" instead of
"0, 1, 1, 2, 3, 5, 8, 13, 21, 34". The runner (run-program.js) printed the
whole line; the cut happened in `JavaScriptProgram.outcomeFrom`, which read
every reply field with kotlinx.serialization's `longOrNull` first.
`longOrNull` parses a leading number and stops at the first separator, so
any printed text, result or error that began with a number followed by a
comma or a space became that number. Every field is now read with
`contentOrNull` (a JSON number's content is its digits, so
droppedCharacters still parses). Python reads its fields with
`contentOrNull` and gets stdout from the bridge as plain text, so it never
had this bug. Test: JavaScriptProgramTest.printedTextThatStartsWithANumberIsKeptWhole.
Outcome: pending.

## D-122 · 2026-10-03 · Markdown tables in chat answers — proposed
The phone check of 0.8.0 showed tables as raw pipes. The chat parser now
reads GitHub-flavoured tables: a header line, then a separator such as
`|---|:---:|--:|` with as many cells as the header (colons set start,
centre or end alignment), then rows until a blank line or a line without a
pipe. Outer pipes are optional; `\|` is a pipe inside a cell, also in code;
short rows get empty cells and extra cells are dropped, as on GitHub. A
table may follow a paragraph line directly. A line with a pipe and no
separator under it stays text, so "a | b" in a sentence and a header that
is still streaming are not tables. Deviation from GitHub: a pipe-less line
ends the table instead of becoming a one-cell row, because models often
put their next sentence right under a table. Cells hold bold, italics,
code and links. Rendering: bodyMedium text, semibold header row, a 1 dp
outlineVariant line under each row except the last, no vertical lines,
cells padded 10 × 8 dp and top-aligned; each column is as wide as its
widest cell on one line, within 56 to 240 dp, longer text wraps, and a
table wider than the message scrolls sideways. A small copy button under
the table copies its Markdown source ("Copy table"). Selection works as
for the rest of the answer. Code: MarkdownTables, MarkdownText.TableView;
test MarkdownTableParserTest (12). Outcome: pending.

## D-123 · 2026-10-03 · UI revamp "Lantern" with the Leaf colours — proposed
Amends D-024's visual details; its structure (Material 3, firefly colour
only for live work, rail step track, theme follows the system with a
toggle) stays. Source: docs/mockups/revamp-taste.html (direction A) and
revamp-a-themes.html (palette 1, Leaf). Colours (night / day): background
#10130E / #F3F6EF, field #1A1E16 / #E7ECE1, ink #E6E9E0 / #171B14, ink-2
#C3C9B9 / #3A4233, muted #8B927F / #5F6857, line #262B21 / #DDE3D5, accent
#C9E86B on #1F2A00 / #3F5600 on #F3F6EF, user bubble #20261A / #E2E9D6, run
panel none / #FFFFFF, rail #33392B / #D3DBC8. WCAG contrast, night: muted
5.81 on background, 5.25 on field, 4.81 on the user bubble; accent 13.59 on
background; on-accent 10.97 on accent; live dot 14.69. Day: muted 5.34,
4.85, 4.68 and 5.83 on the run panel; accent 7.57; on-accent 7.57. The
day glow #9CC520 is 1.85 on the background, below 3:1 for a status mark, so
the day dot is #729600 (3.17 on background, 3.46 on white) and #9CC520
stays as its halo; text never uses the live colour. Type: Geist, numbers in
Geist Mono (SIL OFL 1.1, variable fonts in core/ui res/font, licence in
core/ui/OFL-Geist.txt); Bangla falls back to the system font; the APK grows
by about 190 KB. Thread list: rows without cards or tint, the glow dot only
on working threads, day groups (Today, Yesterday, Last 7 days, Older), an
Incognito chip beside the project chips while incognito threads exist (New
thread then starts an incognito chat, as under a project, D-110), and an
extended New thread button. Chat: title-only top bar; a globe pill in the
status strip switches web search ("Web search on" / "Web search off";
crossed out and muted when off), also before a new thread's first message;
the ⋮ switch stays. Answers have no bubble, user messages a tinted bubble
right-aligned at most 86 % wide. Run block and subagent cards: thin bordered
panels; finished stations are checks on the rail colour, only the running
station is lit, step names read as words ("Web search"). Settings, Tools,
Skills and Memory lists lose their card fills where spacing separates rows.
Tests: WebSearchPillStateTest, ThreadGroupsTest, StepDurationTest. Not
checked: a device or emulator view of the new screens (previews at 360 dp
and font scale 1.3 exist but were not rendered). Outcome: pending.

## D-124 · 2026-10-03 · Settings > Permissions and About — proposed
Two sections end Settings. Permissions has four live rows, each with its
purpose and status: Notifications (POST_NOTIFICATIONS from Android 13,
the app switch before), Calendar (read and write together), Photos (as
D-086: READ_MEDIA_IMAGES, "Selected photos" for Android 14's partial
grant, READ_EXTERNAL_STORAGE up to 12) and Alarms & reminders
(canScheduleExactAlarms, "Off" when false, always allowed below 12).
Android cannot tell "Not asked" from "Denied", so RuntimePermissions and
the composer's photo request record each permission whose dialog was
shown in AppSettings (`requested_permissions` in the "settings"
preferences); recorded and not granted is "Denied". Photos also count
D-086's `photo_access` refusal. "Not asked" rows ask through
RuntimePermissions ("Allow"); "Denied" and "Off" open Jonaki's page in
system settings, or the exact-alarm page ("Open settings"); allowed rows
open the same page on tap, with no button. Statuses are read again on
every resume. "Always on" lists the install-time permissions (Internet,
network state, foreground service, boot, wake lock) without status.
ManifestPermissionsTest fails when a manifest permission, or WorkManager's
WAKE_LOCK and ACCESS_NETWORK_STATE, has no row or two. About shows
"Version x" from PackageManager (no BuildConfig), the developer, and a
GitHub row with the Invertocat mark (Simple Icons path, CC0, tinted by the
theme) that opens github.com/shourovrm/jonaki. The strings were agreed in
an adversarial Sonnet review that cut an intro line, made "Always on" a
subheading, chose "Allow" over "Ask", dropped a camera row (Jonaki holds
no camera permission) and dropped a description line in About. Limit: a
permission refused on a build before this record shows "Not asked" until
Jonaki asks again. Outcome: pending.

## D-125 · 2026-10-03 · Bangla and English interface — proposed
Jonaki shows Bangla when the phone's language is Bangla and English
otherwise; Android 13 and later also offer the choice per app (Settings >
Apps > Jonaki > Language) through `android:localeConfig` and
res/xml/locales_config.xml (en, bn). No AppCompat, no in-app picker, no new
dependency or permission. Every values/strings*.xml has a values-bn twin
with the same keys (515 strings and plurals), written in everyday
Bangladeshi Bangla: "সেটিংস", "থ্রেড", "মেমরি", no "অনুগ্রহ করে". Kept in
English letters: model and service names, API key, MCP, JavaScript, Python,
GitHub, OpenRouter, YouTube, Pyodide, URL, HTML, numpy and pandas, and the
developer's name. Digits stay ASCII; the thread list's weekday and month
follow the locale ("মঙ্গল", "24 সেপ"). Stays English on purpose: everything
the model reads (system prompt, tool results, notes to the model, the
scheduled-task message), technical failure reasons passed through from
skill import and Python setup, and the "s" of seconds. The launcher label
becomes "জোনাকি" in Bangla. Moved out of code: approval and step lines
(StepDetail.Words), the no-model and no-key errors, "Ollama on this
network". BanglaStringsTest fails when a key, a placeholder or a plural's
"other" is missing in values-bn. Limit: the Bangla was not reviewed by a
second agent (the worker could not spawn one) and the bn previews were
compiled, not rendered; the user reviews both. Outcome: pending.
## D-126 · 2026-10-03 · Subagent rows, page and work sheet — proposed
Option A of docs/mockups/subagents.html; replaces D-066's stacked cards.
In the run block a delegate step becomes its group: "3 subagents", "1 of 3
done" (every subagent that has ended counts), and one row per subagent:
the task on one line ending in "…", the name and what it does now
("Web fetch ryans.com/…", "Waiting for you", "Asking the main agent", the
first line it wrote, "Thinking"; ended: "Done in 41 s", "Done, 1 skipped",
"Done, 2 files", "Over budget" and the other D-066 words), and steps of 10
and the cost on the right. Only a running row is lit; waiting shows a hand,
asking a speech bubble, done a check, every early stop a warning sign, all
in ink; the second line turns full ink when the row needs a look. A
subagent's approval card shows the 3 minutes counting down (step start +
D-062's limit). A tap opens a full-screen page over the chat (the chat stays
composed, so Back returns to the same place; the app keeps the open page and
the list position while a file viewer opened from it is in front): whole
task, steps and cost meters in ink against SubagentLimits, the step track,
and once ended the folded steps, skipped parts, files written (write_file,
edit_file and artifact calls that succeeded; Open uses the existing viewer)
and the Markdown answer; arrows step through the same call's subagents.
While it runs a bar holds "Stop Researcher 1": SubagentRunner now runs each
loop as its own job, so stop(id) ends that one with what it has and the
others go on (SubagentRunnerTest). After the run, "Work from N subagents"
under the answer names early stops, skipped parts and notes ("All done, 4
notes") and opens a sheet with the same rows, the notes board
(SubagentRunner.notesBoardPath, shown when notes were posted) and the
files. No Room change: everything comes from SubagentEntity and the steps.
Strings in feature/chat strings_subagents.xml with Bangla in values-bn; the
adversarial string review was not run (this worker may not spawn
subagents). Tests: SubagentRowsTest, ChatItemsTest, SubagentRunnerTest.
Not checked: a device view; previews at 360 dp and font scale 1.3 exist but
were not rendered. Outcome: pending.

## D-127 · 2026-10-03 · Fixes from the first phone check of the revamp — proposed
Thread list titles are NOT changed: D-029 (accepted) says a thread name
wraps in full in the list. A one-line title was built and then held back,
because changing an accepted decision needs the user's approval. The
user chose to keep wrapping (2026-10-03).
Permissions (amends D-124): a dialog closed with Back showed "Denied" with
"Open settings", because every shown dialog was recorded as a request.
Now only a refusal is recorded (`refused_permissions`, a new key, so the
old record with its Back presses is dropped). A closed dialog is a refusal
when it granted nothing and Android's rationale flag
(shouldShowRequestPermissionRationale) was true before or is true after
it; Back leaves a false flag false. Status: granted is "Allowed"; flag true
is "Denied" with "Allow" (Android shows the dialog again); recorded and
flag false is "Denied" with "Open settings"; otherwise "Not asked" with
"Allow". The photo row no longer reads D-086's refusal flag, since the
composer sets it after Back too. The composer's photo request records the
same way, and Settings sets D-086's photo refusal only on a recorded
refusal. Test:
PermissionStatusesTest. Limit: a permission refused for good on an older
build and never asked since shows "Not asked", and "Allow" then shows no
dialog.
Markdown: a line of three or more '-', '*' or '_' (spaces between allowed,
as "- - -") is a rule, drawn as a thin divider in the outline colour.
Dashes directly under a text line are a rule as well, not CommonMark's
setext heading, so a model's "Summary\n---" shows the text and a line.
A table's separator row is read as a table first. Test:
MarkdownParserTest.
Step names: stepLabel turns a tool name into words by its first letter,
so "mcp" read "Mcp"; two names now have fixed labels, "MCP" and "YouTube
summary" (from youtube_summarize). Both stay in English letters in Bangla
(D-125), so no string resource is added. Test: StepDurationTest.
Time line: the thread list showed "[Saturday 3 October 2026, 10:43
Asia/Dhaka]" (D-005's line for the model) in a running thread's preview.
The summary query took the text from the newest row with text but the role
from the newest row, which during a run is the assistant's tool call with
no text, so the user's message was read as an answer and kept its line.
Both now skip rows without text (ThreadListQueries.SUMMARIES). The three
copies of the strip pattern (chat, list preview, thread name) became one,
PromptBuilder.userTextOf, beside the code that writes the line. Tests:
ThreadListQueriesTest, PromptBuilderTest. Outcome: pending.

## D-128 · 2026-10-03 · Settings sub-pages and one word per intent — accepted
Settings is a first page and nine sub-pages (mockup
docs/mockups/settings-pages.html). The first page has "Search settings"
and nine rows in three unlabelled groups: Models, Web and YouTube, Tools
and approvals; Answers, Memory and skills, Files and schedule; Theme,
Permissions, About. Each row has a Material or JonakiIcons icon (Folder,
DarkMode, Lightbulb added as paths, no dependency), its title and a live
one-line summary ending in "…": "OpenRouter, 3 models · $12.82 left" (the
first service with a key or needing none; "No chat service" when none is
added, "No key" when none has a key), "Tavily, then Ollama" or "Search
off", plus " · No Gemini key"; "12 of 12 tools on · Ask"; "Normal · no
personas"; "3 facts · 5 skills"; "Jonaki folder · 1 scheduled" or "No
folder · nothing scheduled"; "Follows the phone"; "Calendar blocked", "2
not allowed" or "All allowed"; "Version 1.0.0". Sub-pages hold today's
sections unchanged in behaviour: Models (chat services, Subagents), Web
(search order, off in new threads, Gemini key), Tools (approvals in new
threads, All tools and Python rows opening their screens, MCP servers
inline), Answers, Memory and skills (rows to both screens), Files and
schedule, Theme (with Status icons), Permissions, About. Routes are
"settings" and "settings:<page>"; the first page's scroll and search
survive a sub-page; screens opened from a sub-page return to it; the
chat's "Edit list" opens Models. The other links named in the task do not
exist: "Add one in Settings" errors and the tool picker's line are text,
and no permission prompt links to Jonaki's Settings. Search matches page
and setting titles from string resources plus service names. Wording
(adversarial Sonnet review): Delete for what the user made, Remove for
what the user added; thread, never chat; key statuses "Added" and "No
key"; "Needs approval", "No answer", "Damaged" everywhere; Bangla the same
(মুছুন / সরান, থ্রেড, অনুমতি লাগবে, উত্তর নেই, নষ্ট); the Permissions row is
"ফোনের অনুমতি" in Bangla so it differs from approvals (অনুমতি). Two
reviewed strings were not added: the segmented approvals label and its
help line, because the page keeps the radio options with their own help.
Permissions (user ruling, amends D-124 and D-127): three statuses.
"Allowed"; "Not allowed" with "Allow" when Android can still show its
dialog (never asked, closed with Back, refused once with the rationale
true); "Blocked" with "Open settings" for a recorded refusal with the
rationale false. Alarms off, and notifications off below Android 13,
show "Not allowed" with "Open settings". Bangla অনুমতি আছে / অনুমতি নেই /
আটকানো. Only Blocked is red, on the page and in the summary. Tests:
SettingsSummariesTest and SettingsSearchTest (read the English values
files), PermissionStatusesTest, BanglaStringsTest. Limit: previews at 360
dp and font scale 1.3 (dark, light, Bangla) were compiled, not rendered;
nothing was checked on a phone. Outcome: pending.

## D-129 · 2026-10-03 · Jev left out of the app for now — accepted
The Jev branch (worktree-agent-a3724d9efa58b5358) is not merged. On 20
labelled messages Jev saved 4.1 % of prompt tokens in all and the model
sometimes took more steps (spikes/m10-check/results.md on that branch), and
the provider cache already makes the repeated prompt cheap. The branch stays
for a later look. Why: user ruling, 2026-10-03. The prompt plus 18 tools
(4,875 tokens against the 3,500 target) stays as it is for now, also by user
ruling, because a request is still cheap with the cache.

## D-130 · 2026-10-03 · About as a short page — proposed
Settings > About is a short page of text, not rows: the lit j on its dark
launcher tile (56 dp) beside "Jonaki" and "Version 1.0.0" in Geist Mono;
one sentence on what the app does; "Made by Riad Mashrub Shourov"; and the
GitHub path as an underlined link with the GitHub icon. The logo is a copy
of the launcher foreground in the settings module, because feature modules
cannot read app resources. Why: user request ("not like rows"); nothing on
the page is a setting. Amends D-124's About rows. Outcome: pending a phone
check.

## D-131 · 2026-10-03 · web_fetch renders pages that need JavaScript — proposed
web_fetch can load a page in Android's system WebView, run its scripts and
read the HTML they built, through the same Readability and HTML-to-text
steps as a plain download (user's option A). No new dependency and no new
permission: the WebView is part of Android and INTERNET is already
declared. The tool module sees only a `PageRenderer` interface, kept in
tools/web-fetch beside the tool as Phone and TaskScheduler are, because
web_fetch is its only caller; the app's `WebViewPageRenderer` implements
it and ToolServices passes it in. When: the model passes `render: true`
(the download is skipped), or the download has scripts and under 200
characters of text, or under 1,500 characters that say "enable
JavaScript" or similar (RenderDecision). A page without scripts is never
rendered. The WebView: fresh per page and destroyed after it, JavaScript
and DOM storage on, file and content access off, geolocation off,
third-party cookies off, no autofill, images not loaded, links to other
schemes (intent:, market:) blocked; afterwards cookies, storage and the
HTTP cache are deleted (no other WebView in the app uses them). One page
at a time. It waits for onPageFinished, then until the visible text
length is equal in two checks 0.5 s apart, within 25 s; a main-frame HTTP
error of 400 or more, a load error or a renderer crash fails the render.
The tool's time limit goes from 30 s to 60 s. If an automatic render
fails, a download with some text is returned with a notice, and an empty
one is an error that names the reason. Cost: the schema grows by 92
characters, about 23 tokens, sent only while web_fetch is active. Not
done: Cloudflare-style challenge pages (HTTP 403 or 503) are not
rendered; a browse tool that clicks or fills forms stays later. Tests:
RenderDecisionTest and WebFetchToolTest with a fake renderer; the
WebView part runs only on a phone and was not checked there. Outcome:
pending.
Phone check (A059, 2026-10-03, DeepSeek V4.1 Flash): rendering runs page
scripts; hn.algolia.com gave its search frame (114 to 2,165 characters
against 37 without rendering), but its story list, loaded after the page,
arrived in 1 of 4 runs, and the Pokédex list in none; desktop headless
Chromium at the same width and user agent got the lists every time. A WebView
never attached to a window drew about 35 frames in several seconds, the
likely cause; the fix to try is attaching it, invisible, to the app's
window while it renders. Changed after the check: the text must stay the
same for 1.5 s and at least 2.5 s must pass before capture, and a list page
whose Readability "article" is one item (under 1,500 characters and a
quarter of the body) keeps its whole text (PageTextExtractorTest).

## D-132 · 2026-10-03 · Request-log times for the first streamed token — proposed
The plan's budget "first streamed token shown: provider latency plus under
100 ms" named a request log that did not exist; assistant message rows
held token counts but no times. Each model call of the thread's agent now
saves four nullable columns on its messages row (Room 8 to 9,
AutoMigration): requestSentAtMillis (wall clock, for display),
requestSentElapsedMillis, firstTextElapsedMillis and
firstShownElapsedMillis (all SystemClock.elapsedRealtime). AgentLoop
records a new AgentEvent.RequestSent before each provider.stream call;
RequestTimer (core/agent) takes the sent time there and the first-text
time at the first text delta with a visible character, since the chat
draws nothing for blank text. RunSession writes both as soon as that
delta is saved. The chat marks an answer row that has a first-text time
and no shown time; a drawWithContent modifier on the answer calls back
after drawing it, and the app reads elapsedRealtime in that draw pass and
saves it once (UPDATE ... WHERE firstShownElapsedMillis IS NULL; the final
row save keeps the mark inside a transaction). The usage sheet gets a
"Requests" table of the latest 10 timed calls, newest first: Sent,
"Service wait" (first text minus sent) and "App delay" (shown minus
first text), "–" when a call only used tools. Bangla: রিকোয়েস্ট, পাঠানো,
সার্ভিসের দেরি, অ্যাপের দেরি. Error of "App delay": it ends when the
frame is recorded on the UI thread, before RenderThread and the display
show it, so it reads about one to two frames (8 to 33 ms at 120 to 60 Hz)
short of what the eye sees; it includes the database write, Room's
invalidation, the flow, ChatItems.build and the recomposition. It is too
long when the answer is off screen (scrolled up, app in the background)
and drawn later. "Service wait" starts when the flow is collected, so
it includes connection setup and any reasoning stream. Subagents and
background calls are not timed. Strings after an adversarial review:
"Service wait" and "App delay" replace "Provider wait" and "Shown after",
because the app says "service" and "after what?" was open. Limit: nothing
was checked on a phone. Tests: RequestTimerTest (through AgentLoop with a
scripted provider and a fake clock), RequestTimesMigrationTest,
RequestLogRowsTest, ChatItemsTest. Outcome: pending the phone
measurement.
Phone check (A059, 2026-10-03): the first build crashed after every turn,
because Room's withTransaction needs a SupportSQLiteOpenHelper and the app
uses the bundled driver (D-023); fixed with useWriterConnection and
immediateTransaction. After the fix, "App delay" read 15 to 53 ms on four
local-model turns (target under 100 ms: met).

## D-133 · 2026-10-03 · Local models with llama.cpp, downloaded on demand — proposed
Jonaki runs GGUF models on the phone with llama.cpp (pinned release, built
from source with NDK r28 and CMake 3.31, arm64 only) in a new module
`providers:local-llama` that implements ChatProvider in-process, with its
own JNI layer using llama.cpp's chat templates, lazy tool-call grammar and
streaming parser. No model ships in the APK. Settings gets a "Local models"
sub-page: a short recommended list (Qwen3.5 0.8B, 2B, 4B; Gemma 4 E2B)
and a search of Hugging Face (`/api/models?search=…&filter=gguf
&expand[]=gguf&expand[]=gated&expand[]=downloads`, sorted by downloads),
showing each repo's parameter count, architecture, licence and a fit label
(fits, tight, too big) from the rule (file + KV cache + compute buffer) ×
1.1 against min(available memory, 0.6 × RAM); opening a repo lists its
.gguf files from `/api/models/<repo>/tree/main` with sizes, defaulting to
Q4_K_M or Q4_0. Gated repos and architectures the bundled llama.cpp cannot
load are shown but cannot be downloaded. A foreground WorkManager download
resumes with Range and checks the SHA-256 from the tree's `lfs.oid`; files
live in app-specific storage, excluded from backup. Local models get a
smaller default tool set (web_search, web_fetch, phone, read_file,
read_document: about 1,450 tokens with the base prompt), changeable per
thread; context 8,192 tokens; 4 threads. Why: user ruling 2026-10-03
("go with llama.cpp", "don't put the models inside the app", "a way to
search"). Measured on the A059 with Qwen3.5-0.8B Q4_0: 175 to 197 prompt
tokens/s and 22 generated tokens/s on 4 threads (docs/research/
local-models-2026-10-03.md). Limit: the APK grows by about 6 MB of native
code. Provider built 2026-10-03, not yet run on a device: llama.cpp is a shallow git
submodule (`providers/local-llama/src/main/cpp/llama.cpp`, tag b11366,
commit 2923cf286), so Jonaki's history holds only the pin; a fresh clone
needs `git submodule update --init --depth 1` (without `--depth 1` it
fetched llama.cpp's whole history, 384 MB, in over 10 minutes). CMake builds llama, ggml's CPU backend
for one fixed level (armv8.2-a+dotprod+fp16+i8mm, the features of the
armv8.6 variant the A059 used; the app checks /proc/cpuinfo before loading),
no OpenMP, no KleidiAI, and llama.cpp's common library, statically into one
`libjonaki_llama.so`: 6,321,560 bytes stripped (2,560,824 gzipped), LOAD
segments aligned to 0x4000 (`llvm-readelf -l`), stored uncompressed and
16 KB-aligned in the APK (`zipalign -c -P 16`). Release APK 6,769,683 →
13,110,369 bytes. Prefix reuse: each turn keeps the longest common token
prefix with the previous one; Qwen3.5's recurrent layers cannot be rolled
back, so the JNI layer saves up to three state checkpoints (llama-server's
method) at the start of the generation prompt and 4 tokens before the end,
and a turn that diverges before every checkpoint processes the whole
prompt again. Local models appear in the model menu as "local:<file name>"
for each .gguf that LocalModelStore lists; Thinking Off sends
enable_thinking=false. A local thread's tools are those whose group is on
(Settings > Tools, and the thread's web switch) that are also in the saved
local tool list (AppSettings `local_model_tools`, default the five above);
memory, write_file, youtube_summarize, schedule, find_files, search_files,
share_file and edit_file can be added; delegate, mcp, run_code, view_image
and artifact never are. `LocalModelTools` gives the Local models page the
list, a switch per tool and a rough token cost per tool ((prompt line +
guidelines + schema characters) / 4); that page section is not built.
Finding and downloading, built separately: module
`core:local-models` (HuggingFaceClient, MemoryFit, GgufFiles,
SupportedArchitectures, RecommendedModels, ModelDownloader) and Settings >
Local models, a tenth row on the first page after Models ("2 downloaded ·
Qwen3.5-2B fits", or "None downloaded · No model fits"). Recommended:
unsloth Qwen3.5-0.8B, 2B, 4B and gemma-4-E2B-it, Q4_0, pinned to commits
6ab4614, f6d5376, e87f176, 0314792 with size and SHA-256 from the tree,
and exact KV cache and compute buffer from each config.json (Gemma 4 E2B:
63 MB cache, 540 MB buffer, 4.0 GB required). Search rows have no per-layer
data, so they estimate high: a 4-bit file of 5.7 bits per parameter, a KV
cache of 147,456 bytes per token (36 layers × 1,024 KV values × 2 × 2
bytes), times parameters/8 B above 8 B, times 1/4 for qwen35, qwen3next and
Gemma 3/3n/4 and 1/2 for Gemma 2 and LFM2, and a buffer for a 262,144-token
vocabulary; the four recommended models' exact figures are never above the
estimate (MemoryFitTest), so Qwen3.5-2B's search row reads Tight on the
A059 while its recommended row reads Fits. Default file: Q4_K_M, else
Q4_0, else the smallest Q4; never BF16, F16, F32; mmproj, imatrix, split
parts and mtp or eagle draft heads are not listed. Architectures: the 126
text generators of LLM_ARCH_NAMES in llama.cpp b11366; gated ("auto" or
"manual") and unknown architectures show the reason without Download.
Files: `noBackupFilesDir/models/<file>.gguf`, partials in
`models-downloading/<file>.part`; the worker is a dataSync foreground
CoroutineWorker (WorkManager's SystemForegroundService declared with
dataSync, no new permission) with 5 attempts, hashing the part file first
on resume; storage must hold the rest plus 1 GB (getAllocatableBytes).
Searched files download from `main`, not a pinned commit (the tree gives
none); a change mid-download fails the hash and deletes the file. Tests:
38 in core:local-models with recorded responses in testdata/huggingface/,
LocalModelsRowsTest, summary and search tests. Not checked: anything on a
phone; previews at 360 dp and 1.3 compiled, not rendered. Outcome: pending.
Phone check (A059, 2026-10-03, Qwen3.5-0.8B Q4_0, downloaded in the app at
about 2.5 MB/s, continuing in the background): the native library loads;
the first turn read 2,162 prompt tokens in 14.8 s and wrote 157 tokens in
11.1 s; a later turn restored a checkpoint and reused 2,229 of 2,285
tokens, reading the rest in 0.7 s. Asked to set a reminder, the 0.8B model
asked for the text instead of calling phone; tool use needs the 2B model or
larger, not yet tested.

## D-134 · 2026-10-03 · Reddit skill reads threads from Arctic Shift — proposed
The built-in reddit skill searches through Reddit's RSS feed and reads a
thread's comments from Arctic Shift, a third-party archive of Reddit
(`arctic-shift.photon-reddit.com/api/comments/search?link_id=<id>&limit=100
&sort=asc&fields=id,parent_id,author,body`), with Reddit's own thread feed
as the fallback after two failures. Why: Reddit's feeds answer one request
per window of up to about a minute (`x-ratelimit-remaining: 0` after each
request, HTTP 429 inside the window), so a search followed by 2 to 4 thread
feeds could not work. Measured from a PC with curl and the app's
User-Agent, not on the phone: search feed 59 KB without `limit`, 17 KB
with `limit=10`; Arctic Shift, 100 comments with the four fields, 14 KB,
each with the id of the comment it answers; a post made about an hour
earlier was already there; 3 of 11 Arctic Shift requests failed (HTTP 525
twice, one timeout) and worked on a retry. The skill passes `max_length`
20000 for these fetches. Rejected: Arctic Shift's `comments/tree` (95 KB
for 58 comments, no `fields` parameter); PullPush as a third source (it
ignores `fields`: 190 KB for 100 comments); comment scores (the search
endpoint's scores are from the time of archiving: at most 5 where the tree
shows 196). No code, dependency or permission changes: one asset file,
which reaches installed copies through D-038 unless the user edited the
skill. Limit: it depends on a volunteer-run archive. Outcome: pending.

## D-135 · 2026-10-03 · Project memory, project files and the project prompt — accepted
User ruling 2026-10-03 ("build project memory and the suggested design of
the project prompt; also add project specific files"). Facts have three
scopes: global, project (new nullable memories.projectId, Room version 10)
and thread. A thread sees global facts, its project's and its own
(MemorySearchIndex.VISIBLE_FROM_THREAD); a thread without a project sees no
project facts. The memory tool offers scope project only in a project
thread, with one guideline naming the project. Background extraction in a
project thread sees the project's facts and may add ("scope":"project"),
update and delete extracted project facts; global facts stay read-only to
it (D-036). The prompt's Memory section lists All threads, This project,
This thread, with budgets of 6,000, 2,400 and 6,000 characters for cloud
models and 1,200, 800 and 1,200 for local ones. Project files live in
filesDir/projects/<id>/, outside every thread folder; every file tool
reaches them as /project/... through ThreadPaths, run_code reads them as
project/... and never writes them, and share_file can share them
(FileProvider path added). The system prompt order is: base and tools;
user instructions (style, general, project instructions, persona, thread);
skills; the project part ("Project "X": this thread belongs to it…", then
up to 20 file paths, no sizes); memory. The project's ⋮ menu gets Memory
(project facts and global ones, add, move to all threads) and Files (add
from the phone, open, delete); a thread fact can move to its project.
Deleting a project deletes its files and, by a checked box, keeps its facts
as global ones or deletes them. Strings reviewed by the main session only
(user ruling: no subagents), not by a separate reviewer as AGENTS.md asks.
Tests: ProjectPathsTest, ProjectFilesSectionTest, MemorySectionTest,
ProjectMemoryExtractionTest, ProjectMemoryQueriesTest,
ProjectMemoryMigrationTest, FactMenuTest, MemoryToolTest. Outcome (A059,
2026-10-03, project "test", DeepSeek V4.1 Flash, $0.0004): the model saved
"Project "test" deadline is 15 December." with scope project and, after
Allow once, wrote /project/notes.md (14 bytes); the project's Memory screen
listed the fact under This project, its Files screen listed notes.md; a
second thread of the project, on another model and told to use no tool,
named the deadline and /project/notes.md from its prompt.


## D-136 · 2026-10-03 · Local tool list: four always, read_document with files — accepted
User ruling 2026-10-03. A local thread is offered web_search, web_fetch,
phone and read_file, plus read_document only when the thread folder or its
project folder holds a file (LocalModelToolList.ONLY_WITH_FILES), decided
once per run so the prompt stays the same within it. Settings > Local
models gets "Tools for local models": a switch per choosable tool with its
rough token cost. The page also gets a Downloading section above
Downloaded, shown only while a download runs, for downloads started from
any list, and the Remove dialog says "Clears 672 MB" with the file's size.
Outcome: pending a phone check.

## D-137 · 2026-10-03 · Two subagents per message without asking — accepted
User ruling 2026-10-03: no model starts more than 2 subagents (the default;
the user sets it in Settings > Subagents, D-138) per user
message on its own, each with the default limits of D-061; more needs the
user's approval, and above 5 (default) the app warns. delegate's cost is per call
(sideEffectOf): READ_ONLY while the run's started subagents plus the call's
tasks stay at the limit (default 2) or fewer, else the new SideEffect.NEEDS_USER, which asks in
every approval mode, Bypass included, and which "Allow in thread" never
covers (the card shows only Allow once and Deny). The count lives in the
run's SubagentRunner (startedThisRun); delegate calls never run side by side
(ToolCallScheduler.RUN_ALONE), so a second call in one turn sees the first's
subagents. The card says "3 subagents in this message" and, above 5
(default), "Over 5 subagents. Each can cost up to $0.10." with the user's
numbers. One guideline tells the model that more than the limit (default 2)
wait for approval. A delegate call still starts at most 3 (default) at
once (D-060). Tests: DelegateToolTest, PermissionBrokerTest. Outcome:
pending a phone check.

## D-138 · 2026-10-03 · Settings > Subagents: limits and custom subagents — proposed
User request 2026-10-03. A new first-page row "Subagents" (after Tools and
approvals, icon Material "people") opens its own page; the Models page loses
its Subagents section, which moves here unchanged. Summary: "2 without asking
· 1 custom" or "· no custom"; search finds the page's titles and the custom
names. Limits (stepper rows, −/+): Start without asking, per message (default
2, range 0 to 10, D-137); Start at once, in one call (3, 1 to 6, D-060); Warn
above, per message (5, from the automatic limit to 20); each subagent's Tool
steps (10, 1 to 30), Cost ($0.10, $0.05 to $1.00 in 5-cent steps) and Minutes
(10, 1 to 30, D-061). Raising the automatic limit above the warning raises
the warning. They live in core/tool-api `SubagentLimitSettings`
(withinBounds) and replace SubagentLauncher's constants; the run reads them
once from the settings snapshot into `SubagentRunner.limitSettings`, so
delegate's prompt line, guidelines, schema maxItems and time limit (minutes +
1) stay fixed for the run and the prompt cache holds. With 0 without asking,
the guideline says every call waits. The approval card's warning and the
subagent rows' step and cost meters use the current settings (rows of
earlier runs too: a subagent's row stores no limits). Custom subagents: a
name (lowercase letters, digits and single hyphens, a letter first, at most
30 characters, not a built-in or another custom name; typing turns capitals
and spaces into a valid name), a description of at most 200 characters on
one line (shown to the thread's model in delegate's type list), instructions
(the type's own prompt part), a model (the user's models, default "Same as
the thread"; a removed model falls back to the thread's) and tools picked
from every tool group's tools except delegate and memory (no nesting).
`AgentTypes.custom` builds the type; it sees the skill list when it has
write_file, edit_file or artifact. They join delegate's enum and type list
after the four built-in ones and run through the same SubagentRunner,
budgets, gate and approval rules; extra_tools and request_tool work as for
the built-in types; one named like a built-in type is ignored. The page
lists them (name, description, one line each) with "Add subagent"; a row
opens a full-screen editor (Save, and Delete with a confirm for an existing
one, D-128). Storage: app preferences, not Room — the limits as six ints and
the custom types as a JSON array (`custom_subagents`, like `mcp_servers`),
because they are settings read in the same snapshot as subagent_models and
nothing in the database refers to them; Room stays at version 10. Strings:
feature/settings strings_subagents.xml with Bangla; the chat warning now
takes the numbers. The strings had no separate adversarial review (the user
reviews them). Tests: SubagentLimitSettingsTest, DelegateToolTest,
SubagentRunnerTest, CustomSubagentsTest, CustomSubagentFormTest,
SubagentLimitRowsTest, ChatItemsTest, SettingsSummariesTest,
SettingsSearchTest, UsageFormatTest. Not checked: a device; the previews at
360 dp and font scale 1.3 (dark, light, Bangla; editor with a 30-character
name) were compiled, not rendered. Outcome: pending.
Strings reviewed by the main session (2026-10-03): kept as written, except
the card warning, which became a plural ("Over 1 subagent", "Over 5
subagents").
Phone check (A059, 2026-10-03): the Subagents row reads "2 without asking ·
no custom"; raising Start without asking to 6 lifted Warn above to 6, and
both went back to 2 and 5; "Price Checker" became price-checker and the
tool list had no delegate or memory; a DeepSeek thread delegated to
price-checker, whose row showed "Out of steps 10/10 $0.0037"; asking for
three scouts showed "3 subagents in this message" with only Allow once and
Deny (denied).

## D-139 · 2026-10-04 · Subagent budgets per type, cap of 5, free notes — proposed
User ruling after a research run where six subagents all ended at 10 of 10
steps with tasks too big for them. Each type has its own steps, cost cap and
minutes in Settings > Subagents: 10, $0.10 and 10 minutes, except the
researcher at 20, $0.20 and 15. The delegate tool tells the thread's agent
each type's steps, that one step is one tool call, to give one narrow
question and never to guess links. Calls of the notes board cost no step
(ceiling of 10 calls). A subagent at its limit reads "Step limit", shown as
finished. "Warn above" became "Most per message" (default 5): a call over it
always asks, and approving is the user's override. A queued message restarts
the per-message count. Old global budgets carry over to the other types.

## D-140 · 2026-10-04 · Messages queue during a run; Stop is an icon — proposed
User ruling. While a thread runs, Send queues the message; it reaches the
model after all tool results of the turn and before the next request, or
continues a run that would have ended, and starts a fresh step budget. A
queued row shows it with edit (back into the field) and cancel. After Stop
or a failure the queued text returns to the field and is not sent. The queue
lives in memory. Stop is a round icon beside Send. Phone check (A059): both
buttons, the row, and delivery after the tool steps.

## D-141 · 2026-10-04 · Reminders repeat until Done; battery row — proposed
User ruling. A reminder stays in the book until confirmed. Its notification
has Done, 10 min and Later… (1 hour, 1 day, pick date and time). Unanswered,
it rings again every 10 minutes up to 5 times (Settings > Files and
schedule), then waits silently. Swiping it away does not confirm. A restart
restores repeat alarms and shows a finished reminder's notification again.
Settings > Permissions has a Battery row that opens Android's battery
optimisation list; no new permission. Phone check: the three buttons, the
Later sheet and Done.

## D-142 · 2026-10-04 · Approvals: fewer cards, thread allowance, Settings rules — proposed
User ruling ("the app asks almost everywhere"). Changes D-015, D-058, D-062.
Subagents never see a card: reads and thread-folder writes run in every
mode, and actions that leave the app return an error telling the subagent
to list them under Blockers, so the thread's agent asks once; the 3-minute
wait is removed. New side effect CHANGES_REVERSIBLE (set a reminder, save to
Downloads) runs in Auto. The card offers "Allow once" and "Allow all in this
thread"; the allowance covers every tool except very risky calls (delete or
overwrite outside the thread, send to another app or server), send-outs
after outside content and the subagent cap. "Always allow" rules for all
threads name one tool and one action and are made only in Settings > Tools,
never from a card. A shield chip in the status strip shows the mode and
opens its sheet; the menu entry is gone and the two context items became
one. Room v11 stores the allowance and the outside-content flag per thread.

## D-143 · 2026-10-04 · Guardrails against prompt injection — proposed
User ruling. (1) Once a thread has read outside content (web, video,
document, inbox image, MCP result, subagent answer), a call that sends data
out, or creates a scheduled task, always asks: in Bypass, with the thread
allowance and against Settings rules. (2) Outside results reach the model
inside <outside-content source="...">, with any marker in the text escaped,
and one fixed prompt line says such text is material, never a task. The
chat shows results without the wrapper. This lowers the chance of an
injection working; rule 1 is the backstop.

## D-144 · 2026-10-04 · Jev guard, off by default — proposed
User ruling after spikes/jev-guard (40 labelled cases, all right, median
0.42 s, $0.000024 a call; cases written by the same hand, 7 with Bangla,
none over 300 characters). Settings > Tools has a "Jev guard" switch that
needs an OpenRouter key. With it on, a call that would show an ordinary
card runs when Jev says read only or reversible at confidence 0.9 or more
and "the user asked for this" at 0.65 or more; calls that always ask are
never put to it; it cannot deny. An outside result flagged at 0.65 or more
gains a warning line inside its wrapper. Any failure shows the card. Its
cost is not added to the usage figures yet. Not checked on a phone with the
switch on.

## D-145 · 2026-10-04 · export_pdf tool — proposed
User ruling. tools/export-pdf turns an HTML file of artifacts/ into a PDF
beside it with the WebView's print engine and no dialog (a helper in
package android.print, kept by an R8 rule); page a4, letter or slides. For
print it opens boxes that scroll sideways and wraps table cells. Phone
check: the CGRA report came out as 9 A4 pages with selectable text and
tables inside the page.

## D-146 · 2026-10-04 · Date line without the zone; no language line; thumbnails — proposed
User rulings. Each message's date line carries the local date and time
only; Settings > Answers can add the offset or the zone's name. Three tool
results say "(local time)" instead of the zone. The system prompt's line
about English and Bangla is removed, because some models answered in Bangla
because of it. Images the user attaches show as thumbnails in the message
and the composer, and a tap opens them full screen with zoom; the stored
text and what the model gets are unchanged. Thumbnails not checked on a
phone.

## D-147 · 2026-10-05 · Memory: dates, ranked recall, keywords, superseded facts — proposed
User ruling ("go ahead, implement all") after a research round (Hermes Agent
docs and r/hermesagent, other Reddit threads, LongMemEval, Letta, Mem0, the
SQLite FTS5 docs). Room v12 adds memories.keywords and
memories.supersededAtMillis, messages.promptFactIds and steps.guardNote, and
rebuilds the fact index over text and keywords (KeywordsMigrationTest).
(1) A fact's line in the prompt is "- [id] (2026-10-04) text", the day it was
saved or last changed, and the header says the newer of two facts holds; the
day changes only with the fact, so D-035's cache rule stands. (2) Recall
takes several words, matches any of them (FtsQuery) and orders by
bm25(text 1.0, keywords 0.5); amends D-009's phrase match. (3) Each fact
carries keywords in both scripts, written by the model that saves it and
never shown. Spike on 20 facts with gemini-2.5-flash-lite
(spikes/memory-keywords): asking for "the other script" gave an English
keyword for 3 of 10 Bangla facts, asking for both scripts every time gave 10
of 10 and 5 of 5; four runs cost $0.005. Closes D-009's limit ("thesis" did
not find "থিসিস") where the model writes the keyword. (4) Background
extraction no longer destroys: an update keeps the old text as a superseded
copy and a delete marks the fact superseded; the Memory screen lists them
under Replaced with Restore and Delete, and extraction removes those older
than 30 days. The memory tool's forget and the user's delete stay real
deletes. Amends D-036. (5) Extraction may mark a fact "scope":"global"; it
always waits for approval, and the rule names what qualifies (4 of 20 spike
facts marked, against 14 before it was narrowed). Rejected: embeddings, a
vector store, a knowledge graph (no evidence of gain at a few thousand
facts), a consolidation pass (wait until facts number in the hundreds).
Limits: the update's two writes are not one transaction; restoring a copy
can leave two similar facts. Outcome: not checked on a phone.

## D-148 · 2026-10-05 · search_chats tool and a message index outside Room — proposed
tools/search-chats finds complete user and assistant messages of regular
threads by words, across threads or in this one, best match first, each hit
with the message before and after it. The index is a contentless FTS5
trigram table plus an id map, made and rebuilt by a database callback, so it
needs no Room version. Measured on 20,000 mixed Bangla and English messages
(11.4 MB of text): 21.8 MB, 1.91 times the text
(spikes/message-index/results.md). Rejected: keying on messages' implicit
rowid (VACUUM may renumber it), detail=none or detail=column (FTS5 then
refuses trigram phrase queries), a view as content source (breaks Room's
table rebuild), an own copy of the text (2.99 times). Why: LongMemEval
reports that extracted facts alone lower accuracy and that raw turns with
facts as keys raise it; Hermes Agent searches sessions the same way. The
tool is in the Memory group and is not given to incognito threads, subagents
or local models. Its results are not wrapped as outside content, since they
are the user's own chats; an old answer that quoted a web page is the gap.
About 145 prompt tokens (estimate). Outcome: not checked on a phone; the
first open after the upgrade indexes old messages and was not timed.

## D-149 · 2026-10-05 · Facts held after outside content; the Jev guard screens them — proposed
Published attacks plant memories through web pages, so a fact saved in a
thread that has read outside content (D-143) waits for the user's approval
on the Memory screen, from the memory tool and from extraction alike. With
the Jev guard's screening on, each such fact is first put to Jev as a
"memory fact": only a planted-looking fact or a missing answer waits, a
clear one is saved (FactScreen, OneJobGuardAndFactScreenTest). A plain
thread is never asked, so it costs nothing. Settings > Guardrails >
"Hold new facts for review" switches the rule off. Limit: without the guard
every later fact of a thread that searched the web once waits. Outcome: not
checked on a phone.

## D-150 · 2026-10-05 · Settings > Guardrails; the Jev guard's options — proposed
User rulings. A new Settings page holds the Jev guard and the two rules that
follow outside content ("Ask before sending out", D-143 rule 1, and "Hold
new facts for review", D-149); both rules can be switched off. The guard's
single switch (D-144) became two, "Skip cards for safe actions" and "Screen
outside content", each defaulting to the old switch's value. A slider picks
Careful (0.95, 0.80, flag at 0.50), Balanced (0.90, 0.65, 0.65) or Relaxed
(0.80, 0.50, 0.80); only Balanced's action values were tested, while all
three flag values had no miss and no false alarm in the spikes. The page
shows the guard's cost this month. Amends D-144: every guard question saves
a one-line note on its step, shown under the step, and its cost as a hidden
BACKGROUND row, so thread and month totals include it (D-036); the shield
sheet says On, Off or On without a key. Second test on 21 real pages (3,935
to 19,993 characters) and 12 poisoned copies, 2 in Bangla: 0 false positives
(0.02 to 0.03), 0 misses (0.81 to 0.98), median 0.42 s, $0.006
(spikes/jev-guard/results_pages.md). Left out by user ruling: the TypeSafe
direct endpoint. Settings > Memory and skills gains switches for saving
facts from chats, reviewing new facts, suggesting facts for all threads and
suggesting skills. Outcome: not checked on a phone, with the guard on or off.

## D-151 · 2026-10-05 · The agent proposes a skill; the user adds it — proposed
tools/propose-skill saves a proposal (name, description, SKILL.md, optional
"replaces") outside the skill library, at most 5 waiting and 6,000
characters each. The Skills screen lists them under Proposed; Add writes the
skill through the library, Discard deletes the proposal. Nothing is listed
in the prompt or loadable until the user adds it. Guidelines: only after a
task with many steps, a solved dead end or a correction, and a change to an
existing skill before a new one. Not given to subagents, incognito threads
or local models. Why: Hermes Agent's users report about five near-duplicate
auto-created skills per workflow. Rejected: creating skills without the
user. About 305 prompt tokens (estimate). Outcome: not checked on a phone.

## D-152 · 2026-10-05 · Facts in the context sheet; Markdown export; MiniMax balance — proposed
(1) A run records the ids of its memory section on its user message, and the
context sheet's Memory row opens those facts. (2) Settings > Memory and
skills > "Export as Markdown" writes every fact in use to
Downloads/Jonaki/jonaki-memory-<date>.md, grouped by scope. Why: users of
file-based memory value a copy they can read and keep; the store stays
SQLite. (3) MiniMax's card shows a balance: a pay-as-you-go key (sk-api-)
reads /account/query_balance, any other key the 5-hour window of
/v1/token_plan/remains. The second endpoint is in MiniMax's FAQ; the field
names come from MiniMax's own command line tool, so any other answer shows
no line. MiMo documents only its console's balance page, and four likely
paths on its API host return 404, so its card stays without one. Amends the
balance note of 2026-10-03. Outcome: none of the three checked on a phone,
and the MiniMax answer was not seen with a real key.

## D-153 · 2026-10-05 · Word, Excel and PowerPoint files through a documents add-on — proposed
User ruling ("go with the python packages"). Jonaki could read .docx, .xlsx
and .pptx (D-052) but not write them. The documents add-on of the Python
runtime is lxml, pillow, typing-extensions, beautifulsoup4 and soupsieve
from the Pyodide lock file (3,373,005 bytes) plus five pure-Python wheels
pinned by URL, SHA-256 and size in PyodideRelease.kt: python-docx 1.2.0,
openpyxl 3.1.5, et-xmlfile 2.0.0, python-pptx 1.0.2 and XlsxWriter 3.2.9
(1,170,059 bytes; MIT and BSD). Total 4,543,064 bytes, downloaded on demand,
so the APK grows only by the helper below. The wheels come from
files.pythonhosted.org, a second download host the user approved; each is
hashed while it streams and again before every run, and Pyodide loads it
from the app's files without micropip. Amends D-069 ("packages outside the
lock file are not supported"). Programs stay offline (D-070): the request
filter also serves the bundled module by its exact name, nothing else.
Importing docx, openpyxl, pptx, xlsxwriter or jonaki_docs loads the whole
add-on; a missing one is reported like numpy, and D-094's card offers it.
Settings > Python has a row with Install, Repair and Remove.
jonaki_docs.py (bundled, 67 KB) gives three builders, WordDoc, Sheets and
Deck, that share the themes leaf, plain and formal, accept the user's own
.docx or .pptx as a template (its styles and layouts are used, its sample
content left out), and convert an HTML report or deck (html_to_docx,
html_to_pptx, html_tables_to_xlsx). CSS is not carried over, because Word
and PowerPoint have none. A Chart.js canvas keeps its data in script, so the
report and slides skills now add a data-chart attribute; it becomes a native
chart in PowerPoint and a picture in Word (python-docx has no charts; the
picture's labels are Latin only). New built-in skill office-documents.
Checked in Pyodide 314.0.7 under Node (spikes/office-docs): 31 assertions
on reopened files pass, and the files rendered with LibreOffice look as
intended in all three themes, with Bangla text. Rejected: Apache POI (about
10 MB of APK, D-052), an own zip-and-XML writer (new files only, no
editing), translating CSS. Also fixed: a download that failed at once could
finish before its job was recorded, so its problem never showed. Outcome:
not run on a phone or in the WebView worker; not opened in Microsoft Office.

## D-154 · 2026-10-05 · MIT licence — proposed
The user asked for a licence in the README without naming one. `LICENSE` is
the MIT text with "Copyright (c) 2026 Riad Mashrub Shourov". Why: the
repository is public and had no licence, so nobody could legally reuse the
code; MIT matches llama.cpp (the one submodule) and the documents add-on's
wheels (MIT and BSD), and it is the shortest to comply with. Rejected for
now: Apache-2.0 (adds a patent grant, longer), GPL-3.0 (forks must stay
open). Changing it before others contribute costs one commit. Outcome:
none yet.

## D-155 · 2026-10-05 · Bangla formal letter skill no longer shipped; About text — proposed
User ruling ("it's my personal skill, not necessary for the app"). The
folder assets/skills/bangla-formal-letter is removed, so five skills ship.
installBuiltIns now forgets the record of a skill the app no longer ships,
so a phone that already has it keeps it as the user's own skill, with no
"built-in" mark and no reset. Without this the skill would still show as
built-in and its reset would do nothing. The About description becomes
"Jonaki is Bangla for firefly. It is an AI chat app that uses your own API
keys. There is no Jonaki account or server." (the user found the old one
generic); agreed with an Opus reviewer, which also changed the Bangla
developer line. Outcome: see the commit's checks.

## D-156 · 2026-10-05 · PDF export breaks a word only in a table too wide for the paper — proposed
The export's fit-to-paper rule gave every table cell `overflow-wrap:
anywhere`. That value lets the browser size a column narrower than its
longest word, so a table that fitted the page printed "RAN K" and
"POPULATIO N" (the agent saw it on the test phone). Cells now get
`overflow-wrap: break-word`, which keeps a column as wide as its longest
word, and only a table whose right edge is still past the paper after that
gets `anywhere`. The rules left @media print so the table can be measured
before printing; the WebView is never shown. Checked in desktop Chromium at
the A4 width of 717 CSS pixels: the old rule broke "Rank" and "Population"
in a six-column table, the new one breaks neither, and a ten-column table
still fits the page. Released as 1.4.1. Outcome (2026-10-05, phone A059,
build 1.4.1): the same report, its HTML unchanged, exports to a four-page
PDF whose headers are whole ("RANK", "POPULATION"; pdftotext finds no
broken word), and the table sits on page 1 with no gap above it.

## D-157 · 2026-10-10 · Short generated thread names; one line in the list — proposed
Amends D-029 and reverses the choice recorded in D-127; the user approved
both in chat on 2026-10-10 ("like ChatGPT"). A new thread still gets the
first line of its first message at once. After the first answer finishes,
one call to the background model (the cheapest priced model with a saved
key, D-036) writes a name of 3 to 6 words in the language of the message,
cleaned by ThreadTitles.fromGenerated and cut at 60 characters. It replaces
the name only while the name is still the first-line one, so a rename by
the user wins; a failed call is logged and not retried. Incognito threads
are not named this way. The thread list shows the name on one line ending
in "…"; the rename dialog still shows it whole. The first-line name is
remembered in memory only, so a thread whose first answer never finished
before the app was closed keeps it. Also in this change: "Web search" left
the thread's ⋮ menu, because the status strip's globe pill does the same
(D-123). Tests: ThreadTitlesTest, ThreadNamerTest. Outcome
(2026-10-10, phone A059, build of 31d951d): the list shows each name on one
line with "…"; the ⋮ menu has Rename, Memory, Skills, Style and persona; a
new thread asked "Explain in one sentence why fireflies glow" was renamed
"Firefly Bioluminescence Explained".

## D-158 · 2026-10-10 · A fresh install has no service and no model — proposed
User request. With no saved settings the 0.1.0 migration ran and left an
OpenRouter card with z-ai/glm-5.3-flash starred. The migration now runs only
when a 0.1.0 preference exists (ChatModels.hasLegacySettings); otherwise the
app starts with no service. With no model the status strip stays, its model
pill reads "No model" and opens the picker, and a sent message gets the
existing "No model. Add one in Settings." Outcome: unit tests pass; not
seen on a phone.

## D-159 · 2026-10-10 · Chosen providers for an OpenRouter model — proposed
Amends D-030; the user approved it in chat on 2026-10-10. A model's ⋮ menu
in Settings has "Providers": a sheet lists who serves the model (GET
/api/v1/models/{id}/endpoints, no key, loaded when opened), cheapest first,
with input and output price and quantization. Ticked providers are sent as
provider.order with their tags (for example "deepinfra/fp4") and
allow_fallbacks from the sheet's switch (on by default); nothing else goes
in the provider block, because OpenRouter does not document how order
combines with sort or data_collection. A model with chosen providers ignores
its routing choice and gets no private-then-cheapest retry. With fallbacks
off, an HTTP 404 becomes "None of your chosen providers (…) can serve this
request". Stored as text under routing_pinned_providers; no Room change.
Removing a model clears its providers and its routing override. Outcome
(2026-10-10, phone A059): the sheet listed GLM 5.3 Flash's providers with
prices, DeepInfra was ticked, the model's row showed "deepinfra", and a
message on that model was answered ($0.0006). Not checked: which provider
served it, fallbacks off, and the 404 for "no chosen provider available",
which is assumed, not recorded. The test choice was removed afterwards.

## D-160 · 2026-10-10 · Image generation through OpenRouter — proposed
User request. New tool module tools/generate-image (generate_image: prompt,
aspect_ratio, file_name, model). It calls POST /api/v1/images through an
ImageGenerator interface in core/tool-api, implemented in
providers/openai-compatible, saves the picture in the thread's images/
folder and returns the path, size, model and cost, never base64. The file
type comes from the answer's media_type, then from the first bytes: a real
request (flux.2-klein-4b, $0.014, 6.4 s, recorded in testdata/openrouter)
returned a JPEG although no format was asked for. The tool changes
something (it spends money and writes a file), so every call asks for
approval, and subagents never get it. The cost is saved as a hidden row of
the thread like a background call. Settings > Models has "Image
generation": image models are added from /api/v1/images/models, one is
starred, and the tool is offered only with an OpenRouter key and at least
one image model. The chat shows a thumbnail with Save and Share under the
run. Rejected: picking an image model as a thread's chat model; a price in
the picker (one request per model). No new dependency, permission or Room
change. Outcome (2026-10-10, phone A059): the picker listed OpenRouter's
image models, flux.2-klein-4b was added and starred with "$0.014 per
megapixel"; asked for a picture, the agent called generate_image, the card
showed model and prompt, and after Allow once the thumbnail firefly-leaf.jpg
appeared with Save and Share in 7.8 s; the thread's cost rose to $0.015.
Not checked: Save, Share, the full-size view, a failed or blocked request. OpenRouter's Batch API (half price, answers within 24
hours) was looked at and left out by user ruling.

## D-161 · 2026-10-10 · A wake lock while a run is going — proposed
New permission WAKE_LOCK, approved by the user in chat on 2026-10-10; Android
grants it without a prompt. AgentService kept the process alive but not the
processor, so with the screen off the phone could sleep in the middle of a
run: coroutine timers stop and connections die. Seen on the phone: one
message's run took 22 min 38 s and its subagent failed, and another thread
shows "The connection to OpenRouter broke: timeout"; that these came from
sleep is inferred, not shown. The service now holds one partial wake lock
from its start to its end. Each new run renews it, and Android releases it
by itself after 30 minutes, so a run that never ends cannot keep the phone
awake for good. A scheduled task's run is covered by its worker (D-100).
Outcome: compiles; the locked-phone test is still to do.

## D-162 · 2026-10-10 · What a fresh start shows; saved drafts — proposed
User ruling: 30 minutes, no setting. A fresh start of the activity reopens
the thread the user left less than 30 minutes ago; otherwise it opens a new
empty thread; Back leads to the list. The start keeps its screen when a
share is arriving, the first-run tool picker is due, or no model is set up.
An incognito thread is never reopened. The left thread and time are in their
own preferences file (LeftThreadStore). Drafts: each thread's message box
text is saved to files/thread-drafts.json after 500 ms without typing and at
once on leaving; sending or deleting the thread clears it; the new-thread
box has its own draft; incognito drafts stay in memory; an Edit keeps the
draft aside and puts it back. The thread list shows "Draft: …" as the
preview line. No Room change. Outcome (2026-10-10, phone A059): a fresh
start with no record opened a new thread; after leaving a thread with typed
text, a fresh start reopened it with the text in the box, and the list
showed "Draft: a draft to keep". Not checked: the 30-minute boundary on a
device, a share or reminder start, Edit with a draft.

## D-163 · 2026-10-10 · Selecting several threads or facts to delete — proposed
User request. A long press starts selection in the thread list and in
Memory; a tap toggles; the top bar shows Close, the count, Delete and a ⋮
menu with Select all or Deselect all (and Rename and Move to project for
threads, which the removed long-press menu held). One dialog asks with the
count. Each item goes through the existing single delete. Selection and its
top bar are shared in core/ui (Selection, SelectionTopBar); core/ui now
declares activity-compose, which the app already ships. Outcome
(2026-10-10, phone A059): two threads selected, rows tinted with a check
mark, "2 selected" in the bar. Not checked: Delete, Select all, Memory.

## D-164 · 2026-10-10 · Copyable errors, a subagent's reason, :batch models — proposed
From a thread on the phone where a Researcher failed after 17 of 20 steps
and the sheet said only "Failed". An error row's text can be selected and
has the copy button. A subagent that did not finish shows a reason line
(the provider's text, or the time, step or cost limit) on its row and whole
on its page; the reason is read back from the ending already saved in
resultText, so old rows show it too and no column was added. OpenRouter ids
ending in ":batch" are left out of the catalog, the background model and
subagent models, because they answer HTTP 404 on the chat endpoint; an
already added one stays, and its 404 gains "Batch models do not work in
chat. Pick another model." Read from the code, not measured: a run can last
about 22 minutes by design when the network half-dies (120 s read timeout,
one retry per turn, a 15-minute researcher limit), so that run does not
prove the phone slept. Not built: ending a subagent after repeated network
failures of its tools. Outcome: unit tests pass; not seen on a phone.

## D-165 · 2026-10-10 · Answer language rule in the system prompt — proposed
A thread whose message was a link and an English sentence was answered in
Bangla; the model named a memory fact about the user's city. SystemPrompt
now says, in order: keep a language the user asked for earlier in the
thread (or in their instructions) whatever language later messages are in;
otherwise answer in the language the user writes in; never choose it from
memory, location, name or the language of a page, file or tool result. The
subagent prompt, the delegate guideline and the summary prompt carry the
same wish along. Outcome (2026-10-10, phone A059, Haiku 5.5): asked for
Bangla, then asked a follow-up in English, the second answer stayed Bangla.
Not checked: an English request with a link, which was the failing case.
