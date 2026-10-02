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

## D-032 · 2026-10-02 · Balance and month spend on the closed service card — proposed
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

## D-048 · 2026-10-02 · Chart.js bundled for artifacts — proposed
Chart.js 4.5.1 (MIT, chart.umd.min.js, 208 KB, about 71 KB compressed in
the APK) ships in feature/artifact's assets; the file matches the npm
package (sha512 integrity checked 2026-10-02). Pages load it as
lib/chart.js. Why: plan M6 step 4; the user chose Chart.js over uPlot and
over inline SVG only (2026-10-02), because models write working Chart.js
code more often. Outcome: pending.
