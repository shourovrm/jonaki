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

## D-M9-1 · 2026-10-03 · Cost per call for tools with mixed actions — proposed
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

## D-M9-2 · 2026-10-03 · Reminders: an alarm plus a file — proposed
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

## D-M9-3 · 2026-10-03 · Scheduled tasks in a file, runs on WorkManager — proposed
files/scheduled-tasks.json holds each task's id (8 hex characters),
thread, title, prompt, repeat (none, daily or weekly), anchor (local
date and time) and next run. Each run is one WorkManager
OneTimeWorkRequest tagged with the id, delayed to the planned time, and
waiting for a network. At most 20 tasks. Cancel removes the entry and the
tagged work. A task whose thread was deleted is removed at its next run.
Settings > Scheduled lists reminders and tasks, soonest first, each with
Cancel. WorkManager 2.10.5 adds WAKE_LOCK and ACCESS_NETWORK_STATE to the
manifest. Why: plan M9 step 2 and 3. Rejected: a Room table (as in
D-M9-2); PeriodicWorkRequest (it drifts and cannot keep a clock time).
Outcome: pending.

## D-M9-4 · 2026-10-03 · A task keeps its clock time; each run plans the next — proposed
A daily task at 08:00 stays at 08:00 local time across daylight-saving
changes and time zones; weekly keeps the anchor's weekday. Each worker
first plans the next run, counted from the later of its planned time and
now, then runs the prompt. So a worker that starts a second early does not
run twice, runs missed while the phone was off do not catch up, and a
worker that WorkManager starts again after the process died finds a later
planned time and does nothing. Why: WorkManager timing is inexact; a
chain of delays would drift. Tested in NextRunTest and ScheduleBookTest.
Outcome: pending.

## D-M9-5 · 2026-10-03 · A scheduled run is a message in its thread — proposed
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

## D-MCP-1 · 2026-10-03 · MCP client on OkHttp, one session per tool run — proposed
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

## D-MCP-2 · 2026-10-03 · MCP tool lists cached in files for a day — proposed
Each server's tools/list answer is saved as `files/mcp-tools/<server id>.json`
with its address and fetch time. search and describe read it while it is
under 24 hours old and from the same address; otherwise they fetch it again.
describe of a tool missing from a cached list fetches once more. A call the
server refuses with a JSON-RPC error (unknown tool, bad arguments) fetches
the list on the same session, saves it, and tells the model whether the tool
still exists. Saving or removing a server in Settings deletes its file. Why:
search should not cost a connection per server on every use. Rejected: a
Room table (schema change for data that is only a cache). Outcome: pending.

## D-MCP-3 · 2026-10-03 · mcp tool: read-only search and describe, approved call — proposed
One tool `mcp` with action search (keywords over every server's tools; a word
in the name counts 3, in the description 1; blank lists all; default 10
results, at most 50), describe (full description and input schema) and call
(arguments as a JSON object, or JSON text). Results over 30,000 characters
go through OutputLimiter; images and audio become "[image/png image, not
shown]". The tool declares `SideEffect.CHANGES` and overrides
`Tool.sideEffectOf(arguments)` (D-M9-1) to return READ_ONLY for search and
describe, so the permission broker runs them without a card, while call
shows the existing card with "server: tool". "Allow in thread" then covers every
mcp call in that thread. For the coming approval modes, mcp counts as
leaving the app: Auto asks, only Bypass skips the card. The tool is offered
only when at least one server exists; its prompt line names the servers.
Changes D-034's "one tool has one cost" for this tool. Outcome: pending.

## D-MCP-4 · 2026-10-03 · MCP servers in Settings — proposed
Settings has an "MCP servers" group between Skills and Files: one row per
server (name on one line, URL up to two lines), and "Add server". A dialog
takes Name, URL, Header (optional) and Header value; it checks for a name,
a name not used by another server (case ignored, since the model names
servers) and an http or https URL; edit offers "Remove server". The list is
JSON in app preferences; header values are encrypted with the API keys'
Keystore key (`SecretStore` run-time secrets) and never shown again; the
value field then says "Saved" and empty keeps it. A value with no header
name is sent as Authorization. Outcome: pending.

## D-MCP-5 · 2026-10-03 · Model lists from each service's GET /models — proposed
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

## D-MCP-6 · 2026-10-03 · Presets and prices for GLM, MiMo, OpenAI, MiniMax and Qwen — proposed
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

## D-STY-1 · 2026-10-03 · Custom instructions, global and per thread — proposed
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

## D-STY-2 · 2026-10-03 · Answer style in the system prompt — proposed
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

## D-STY-3 · 2026-10-03 · Personas as saved, named instructions — proposed
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
