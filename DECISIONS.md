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

## D-033 · 2026-10-02 · Compaction of long threads — proposed
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
shows get "Open" cards like the thread agent's. Outcome: pending.

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

## D-200 · 2026-10-03 · run_code reads the JavaScript reply's text fields as text — proposed
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
