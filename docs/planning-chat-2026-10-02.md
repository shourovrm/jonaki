# Jonaki planning chat (2026-10-02)

Transcript of the first planning conversation for Jonaki, an Android coding agent with a mobile chat interface. The user's messages are quoted verbatim; the assistant's replies are reproduced in full.

---

## User

> I'd like to develop an android app that'll be like a coding agent like pi or similar, but for a mobile chat interface, which can connect to different api keys like openai, openrouter, deepseek, mimo, glm, ollama-cloud, ollama-local and also have the features like skill adding, memory, mcp connection - like a coding agent like hermes or similar, but in a miniscule scale for android device. It may have a personal sandbox for coding or may connect to the apps own space if possible. THe app interface will be lightweight and fast.
>
> NOw, suggest me if this is possible.
>
> Is there any similar project available?
>
> Also suggest some names for such app - considering english and maybe some bangla names too

## Assistant

### Short answer: yes, it can be built

The model does not run on the phone. The app only has to do three things in a loop: send the conversation to a model's API, receive the model's "tool calls" (for example "read file X" or "run command Y"), execute those tools on the phone, and send the results back. That loop is called an **agent harness**, and it is what Pi and Hermes are. A harness is mostly HTTP, JSON and file handling, which a phone does easily.

Worked example: you type "add a dark mode toggle to my notes app". The app sends that to DeepSeek. DeepSeek replies with `read_file("MainActivity.kt")`. The app reads the file from its workspace and sends the content back. DeepSeek replies with `edit_file(...)`, the app applies it, and the loop continues until the model answers in plain text.

### Feasibility by feature

| Feature | How on Android | Difficulty |
|---|---|---|
| Multiple providers | OpenRouter, DeepSeek, GLM (Zhipu), Xiaomi MiMo, Ollama Cloud and local Ollama all offer an **OpenAI-compatible API** (same request format as OpenAI). One client with a configurable base URL and key covers almost all of them. | Easy |
| Skills | Use the Agent Skills format: a folder with a `SKILL.md`. Load only each skill's name and one-line description into the system prompt; the model reads the full file when it needs it. This keeps prompts small. | Easy |
| Memory | Markdown files plus SQLite FTS5 (SQLite's built-in full-text search) for recall. Tools: `remember`, `recall`. | Easy |
| MCP | MCP (Model Context Protocol) is a standard way to plug external tool servers into an agent. The official Kotlin MCP SDK connects to **remote** servers over HTTP. **Local** stdio servers need a Linux runtime on the phone (see sandbox). | Medium |
| Code sandbox | The hard part. See below. | Hard |

### The sandbox problem

Android 10 and later forbid apps from executing files they downloaded into their own data folder (a rule called W^X: a file can be writable or executable, not both). So the app cannot simply download Python or Node and run it. There are four ways around this:

1. **App workspace with built-in tools only.** The agent reads, writes and greps files in the app's private folder, uses JGit (a Java Git library) for git, and QuickJS (a small JavaScript engine) for scripts. Works everywhere and is fully Play Store safe, but cannot run `npm` or `gcc`.
2. **Bundled proot Linux.** proot runs a full Alpine or Debian userland inside the app without root. It is shipped as a native library, because Android allows the native-library folder to be executable. This is what Operit and DSHBox do. Cost: 100 to 300 MB of installed size, and Google Play's policy on downloaded executable code is a risk (GitHub or F-Droid distribution avoids it).
3. **Hand off to Termux.** Send commands to the Termux app through its `RUN_COMMAND` intent. The APK stays tiny, but the user must install Termux.
4. **Remote sandbox.** Run commands over SSH on a VPS or a cloud sandbox. Most powerful, needs network.

Recommendation: ship option 1 by default and make options 2 and 3 opt-in. That keeps the app light and fast while still allowing real coding for power users.

Suggested stack: Kotlin with Jetpack Compose (no WebView or React Native, for speed); OkHttp or Ktor with SSE (server-sent events) streaming; Room/SQLite; Android Keystore for encrypting API keys; a foreground service so long agent runs survive the screen turning off.

### Similar projects

- **[Operit](https://github.com/antclt/Operit)**: the closest match. Open-source Android agent with a built-in Ubuntu 24 environment, MCP, Skills, a memory system, workspaces and local models.
- **[Mobile Agent](https://mobile-agent.vercel.app/)**: on-phone agent with MCP, skills, persistent memory, and a terminal through Termux.
- **Android-Agent (Pi agent for Android)**: runs the actual Pi coding agent in a single APK with an embedded Termux runtime, no root. Listed under [GitHub's android-agent topic](https://github.com/topics/android-agent).
- **DSHBox**: one APK bundling Debian, Node.js and the DeepSeek Harness. Same topic page.
- **Zafiro**: bring-your-own-key agent with shell, Python, Skills and MCP, supports OpenRouter and GLM.
- **RikkaHub, DeepCo**: multi-provider chat clients with MCP but no coding sandbox; good references for a fast chat UI.
- **[LMSA](https://lmsa.app/)**: lightweight client for Ollama, LM Studio and OpenRouter.

Operit already covers most of the feature list, so the app needs a clear difference. Two candidates: **tiny and fast** (small APK, instant start, sandbox only when needed) and **Bangla-first UI and docs**. No existing app was found that targets either.

### Name ideas

English: **Wren** (tiny bird, loud voice), **Pocketsmith** (a budgeting app already uses this name), **Tinker**, **Burrow**, **Mote**, **Chisel**.

Bangla: **Tuntuni (টুনটুনি)**, the clever tailorbird from Upendrakishore's *Tuntunir Boi*, small and "stitches" things together (top pick); **Jonaki (জোনাকি)**, firefly, a small light always with you; **Setu (সেতু)**, bridge; **Kheya (খেয়া)**, ferry; **Kaj (কাজ)**, work; **Buddhi (বুদ্ধি)**, wit; **Kolom (কলম)**, pen; **Shohokari (সহকারী)**, assistant.

Sources: [Operit](https://github.com/antclt/Operit), [Operit overview](https://www.sofarbot.com/opensource/OqFn91yOCLyZ), [Mobile Agent](https://mobile-agent.vercel.app/), [GitHub android-agent topic](https://github.com/topics/android-agent), [GitHub openrouter topic (Kotlin)](https://github.com/topics/openrouter?l=kotlin), [LMSA](https://lmsa.app/).

---

## User

> WOuld only selecting 1 would make the app limited? Or how much leverage the app can have with built-in tools?
>
> NOw, how would one connect to a vps? isn't it a separate costing?
>
> LEt's set the name Jonaki for the agent and create a folder in this ~/repos and you transfer this chat to this folder.

## Assistant

### What option 1 (built-in tools only) can and cannot do

Option 1 makes Jonaki a strong **code-editing** agent but a weak **code-running** agent. A coding agent's work splits into read, change, and verify. Built-in tools cover read and change fully; verify (running the code or its tests) is the part they mostly lose.

Worked example: you ask "fix the failing test in my React repo". With option 1, Jonaki clones the repo with JGit, finds the test, reads the code, edits the bug and commits. It cannot run `npm test` to confirm the fix, because QuickJS is a bare JavaScript engine without Node's APIs or npm. You confirm by pushing and letting GitHub Actions (CI) run the tests.

Built-in tools can provide:

- File tools: read, write, edit, search, list, diff, inside the app's workspace and in folders the user grants through Android's file picker (SAF, the Storage Access Framework).
- Git through JGit: clone, pull, commit, branch, diff, push to GitHub or GitLab.
- JavaScript execution through QuickJS for small scripts and calculations.
- Python through Chaquopy, a library that bundles a Python interpreter in the APK. It is allowed on the Play Store because the code ships inside the APK, but it adds roughly 20 to 40 MB, and pip packages must be chosen at build time.
- Web fetch and web search, so the agent can read documentation.
- Remote MCP servers, which can add capabilities including remote code runners.
- HTML and Markdown preview in a WebView, so the agent can build a small web page and show it.
- CI as a test runner: trigger a GitHub Actions workflow through the GitHub API and read the result back. This recovers a large part of "verify" for free.

Built-in tools cannot provide: `npm install`, compilers (`gcc`, `rustc`, the Android build), test suites that need Node or a full Python, databases, or any arbitrary binary.

Conclusion: option 1 is a good first release, because the agent loop, providers, skills, memory and MCP are the same work for every option. Design the `run_command` tool so its backend can be swapped (none, proot, Termux, SSH), and add the other backends later without touching the agent loop.

### Connecting to a VPS

A VPS (virtual private server) is a Linux machine you rent in a data centre. Jonaki connects to it over **SSH** (Secure Shell), the standard encrypted remote-login protocol:

1. Jonaki generates an SSH key pair on the phone and stores the private key in Android Keystore.
2. You add the public key to `~/.ssh/authorized_keys` on the server once.
3. Jonaki's `run_command` tool opens an SSH session with a Java SSH library (sshj or Apache MINA SSHD), runs the command, and streams the output back to the model.
4. File tools either work over SFTP (file transfer over SSH), or the project simply lives on the server and the agent edits it there.

Yes, a VPS is a separate cost, but there are free and cheap routes:

| Route | Cost | Notes |
|---|---|---|
| Your own PC at home | Free | Install Tailscale (free personal plan) on the PC and the phone; Jonaki then reaches the PC over SSH from anywhere. Works only while the PC is on. |
| Oracle Cloud Always Free | Free | ARM VM with up to 4 cores and 24 GB RAM; sign-up needs a card, and free capacity is sometimes unavailable in a region. |
| Google Cloud e2-micro free tier | Free | Very small (about 1 GB RAM), fine for scripts and light tests. |
| Hetzner, DigitalOcean, Vultr | About 4 to 6 USD per month | Reliable small VPS. |
| E2B, Daytona (cloud sandboxes) | Pay per use, usually with free starter credit | Disposable sandboxes created through an API, no server to manage. |

Prices and free tiers change often, so check each provider before choosing.

Safety point: an agent with SSH access can run any command as that user. Give it a dedicated user account or a container on the server, not your main account.

---

*Project folder created at `~/repos/jonaki` with this transcript at `docs/planning-chat-2026-10-02.md`.*

---

## User

> I would actually don't need the coding work as the main feature. After all I won't be making app on phone. BUt I'll do various agenting work there. ON the other hand, some coding capability is always good

## Assistant

### Decision: Jonaki is a general phone agent first, with light coding

The main feature is now general agent work, so the sandbox drops in priority and phone-native tools become the main feature. Coding stays as a secondary capability through Python and JavaScript, which also serve general tasks.

### Effect on the sandbox

Option 1 (built-in tools) plus Python through Chaquopy is enough. Python covers most "agent needs to compute something" cases: parsing a CSV of expenses, extracting tables from a PDF, converting units, drawing a chart. proot Linux moves off the roadmap. SSH to a PC or VPS stays as an optional extra for heavy jobs.

Worked example: "here is my bank statement PDF, total my food spending this month". The agent reads the PDF, writes a short Python script that filters and sums the rows, runs it with Chaquopy, and replies with the total. No Linux sandbox is involved.

### Phone-native tools (the main feature)

These are what a desktop agent cannot do, and they are Jonaki's advantage:

| Tool | Android mechanism | Example task |
|---|---|---|
| Share to Jonaki | Share-sheet intent receiver | Share a web page, PDF or photo from any app and ask about it |
| Web fetch and search | HTTP plus a search API | "Compare prices of these two phones" |
| Files and documents | SAF file picker, PDF text extraction | "Summarise this contract" |
| Images | Vision-capable models | "What does this error on my screen mean?" |
| Calendar, reminders, alarms | CalendarContract, AlarmClock intents | "Add my exam dates from this notice" |
| Contacts, clipboard | Contacts provider, ClipboardManager | "Copy the address from this message" |
| Notifications | Post notifications; reading other apps' notifications needs NotificationListenerService | "Notify me when the job finishes" |
| Scheduled tasks | WorkManager | "Every morning at 8, summarise the news on X" |
| Voice input | SpeechRecognizer | Talk instead of type |
| Hand-off to other apps | Intents (maps, email draft, dialer) | Agent drafts an email, you press send |

Left out of v1: phone control through an AccessibilityService (tapping and typing in other apps). It is powerful, but Google Play restricts it and it carries high risk.

### Safety model

Read-only tools run freely. Any tool with a side effect (send, delete, create event, run SSH command) asks for one-tap confirmation, with an option to allow it per tool. This matters more on a phone because the agent can reach personal data.

### Role of skills, memory and MCP

Skills become task recipes, for example "daily briefing", "expense tracker" or "Bangla to English formal letter". Memory holds personal context such as your name, city, preferred language and recurring tasks. Remote MCP servers connect Jonaki to services like GitHub, Notion or Google services.

### Revised v1 scope

1. Chat UI with streaming, multiple providers (OpenAI-compatible client).
2. Agent loop with tool confirmation.
3. Tools: web fetch and search, files and PDFs, images, Python (Chaquopy), JavaScript (QuickJS), calendar and reminders, notifications, share-sheet input.
4. Skills (SKILL.md folders) and memory (Markdown plus SQLite FTS5).
5. Remote MCP client.
6. Scheduled tasks through WorkManager.

Later: SSH backend, Git through JGit, NotificationListener, local models.

---

## User

> One think I'd like to add is artifact creation like html presentation or html report too; also to analyze youtube video (like this pi agent in this pc has a youtube video skill).
>
> Another thing I'd focus is on having a lightweight memory system; at the same time very effective. It 'll be efficient in collecting memory for global and local chat purposes. The user can also edit/ remove and add memory too.
>
> One problem with hermes agent through telegram or whatsapp is that they give one interface for all tasks. But here one should be able to create new thread and each trhead has their own memory, and there will be main global memory and skill too

## Assistant

### Threads

Each thread is its own workspace, unlike Hermes over Telegram where every task shares one chat. A thread owns:

- its messages, with older parts summarised (compaction) when the thread grows long;
- its **thread memory**;
- its folder for files and artifacts;
- optional overrides: model, system instructions, enabled skills, MCP servers.

Global memory and skills are shared by all threads.

Worked example: thread "Thesis" remembers "supervisor is Dr. X, deadline 15 December". Thread "Sylhet trip" remembers "budget 15,000 taka". Global memory holds "lives in Dhaka, casual replies in Bangla, reports in English". In the trip thread the thesis facts are never sent, which saves tokens and stops the model mixing topics.

Each request is assembled as: base prompt, then global memory, then the skill index (names and one-line descriptions), then thread memory and thread instructions, then the thread summary and recent messages.

### Memory design

Storage: one SQLite table. Each row is one short fact with: scope (global or thread id), text, source message, created, last used, pinned. An FTS5 full-text index sits on the text, using the trigram tokenizer so mixed Bangla and English text is searchable. No embeddings in v1: they need an extra API or an on-device model, and FTS5 costs nothing.

Collection, cheapest first:

1. **Explicit**: the model has `remember(text, scope)` and `forget(id)` tools; "remember that ..." works directly. No extra model call.
2. **Background extraction**: when the user leaves a thread, or every N messages, one call to a cheap model (for example DeepSeek Flash) reads the new messages plus the existing memories and returns operations: add, update or delete. Updating replaces the old fact instead of piling up near-duplicates.
3. Optional review mode: extracted memories wait in a "suggested" list until the user accepts them.

Recall:

- Global memory and thread memory are injected in full, with a size cap (for example about 1,500 tokens each). When a scope exceeds the cap, pinned and recently used facts win.
- Anything beyond the cap is reachable through a `recall(query)` tool backed by FTS5.

User control: a Memory screen per scope with add, edit, delete, pin, and "promote to global" for a thread fact that turns out to apply everywhere. Each memory links back to the message it came from.

### HTML artifacts

The agent writes a single self-contained HTML file into the thread's artifacts folder. Jonaki renders it in a WebView with file access and network turned off. Artifacts are versioned on each edit, can be shared as HTML, and can be exported to PDF through Android's built-in print system (`WebView.createPrintDocumentAdapter`). Built-in skills supply offline templates for reports and slide presentations, so no CDN is needed.

### YouTube analysis

The pi setup on this PC (`pi-config-rms/extensions/video-extract`) does two things: it sends the YouTube URL to the Gemini API for full analysis, and it uses yt-dlp plus ffmpeg to grab frames. Jonaki can do both without those desktop tools:

| Need | On Jonaki |
|---|---|
| Full analysis (speech plus visuals) | Same as pi: pass the YouTube URL to the Gemini API, which accepts public YouTube URLs directly. Plain HTTP; needs a Gemini key (free tier exists). |
| Transcript for any model | NewPipeExtractor, the Java library behind the NewPipe app, fetches captions (including auto-generated) and metadata. Then DeepSeek, GLM or any model summarises the transcript. |
| Frames at a timestamp | NewPipeExtractor gives the stream URL; Android's `MediaMetadataRetriever` grabs the frame; a vision model reads it. Replaces yt-dlp plus ffmpeg. |
| Search | NewPipeExtractor search, like pi's `youtube-search`. |

Caveats: NewPipeExtractor breaks when YouTube changes its site and needs regular updates, and apps that scrape YouTube are not accepted on Google Play (NewPipe itself ships on F-Droid). The Gemini path has neither problem.

---

## User

> I'd also like the power to call subagents; and a agent-router using jev model (from openrouter).
>
> For youtube, my main target is to summarize the video - may be it'll work with only the gemini too.
>
> Oh, and we'd need web search too; so suggest what web search (free tier) and how to use. Hopefully, youtube search will work this web search, right?

## Assistant

### Subagents

A subagent is a second agent loop that the main agent starts through a tool, `spawn_agent(task, agent_type)`. It runs with a fresh, empty context, does its job, and returns only its final answer. The main thread therefore stays small, because the subagent's searching and reading never enter it.

Worked example: "compare these three laptops and make a report". The main agent spawns three "researcher" subagents in parallel, one per laptop. Each searches and reads pages and returns a short summary. The main agent merges the three summaries into an HTML report artifact.

Design:

- Agent types are user-editable definitions: name, description, model, allowed tools, instructions. Built-ins: researcher, YouTube summariser, writer.
- Parallel runs use Kotlin coroutines. The work is network-bound, so three to five at once is fine on a phone.
- Limits: subagents cannot spawn subagents (depth 1); a cap on concurrent subagents; a step and cost budget per subagent; the same confirmation rules for tools with side effects.
- Memory: subagents read global and thread memory but do not write it; they report facts back and the main agent decides.
- UI: a collapsible card in the chat shows each subagent's progress.

### Agent router with Jev

Jev is TypeSafe's decision model on OpenRouter. It does not write text; it answers typed questions (pick one option, yes/no, or a score) with probabilities. It is used in two ways:

1. **`typesafe/jev-router` as a model.** Set the model to `typesafe/jev-router`. For each request Jev judges the task type and difficulty, and the router picks the cheapest model in TypeSafe's curated pool that meets the bar, plus a reasoning effort. It keeps the same model for a session to preserve the prompt cache, which fits Jonaki's threads. The response's `model` field names the model actually used, the pool can be narrowed with the `jev-router` plugin's include and exclude lists, and the header `X-OpenRouter-Metadata: enabled` returns the routing decision. Limit: it only routes to OpenRouter models, so direct DeepSeek, MiMo, GLM or Ollama keys are not used.
2. **Jev (`typesafe/jev-1.13`) as Jonaki's own router.** Ask Jev a "choice" question: given this request, which of the user's configured models fits (for example DeepSeek Flash, MiMo Pro, GLM, local Ollama)? Jev charges input tokens only; output is free. Jonaki uses the answer when its probability is above a threshold and otherwise falls back to the thread's default model. This routes across all of the user's keys and can also choose the model for each subagent.

Plan: v1 offers option 1 as a selectable model "Auto (Jev)", which is only a model id. Option 2 comes later as "Smart routing" across own keys. In both cases routing happens once per user message, not on every tool-call step, so the prompt cache is kept.

### YouTube summary with Gemini only

Gemini alone is enough for summaries. Gemini accepts a public YouTube URL directly as a `file_data` part, and watches the audio and the frames itself. No transcript library, no yt-dlp, no ffmpeg. Notes:

- Only public videos work; private videos do not.
- Long videos use many tokens. Requesting low media resolution cuts the cost a lot, and a start and end offset can limit analysis to part of a video.
- The free tier has a daily limit on hours of YouTube video; check Google's current Gemini limits.

So NewPipeExtractor moves to "maybe later", and the Play Store problem with it goes away.

### Web search

Free tiers (as of October 2026; these change often):

| Service | Free tier | Card needed | Notes |
|---|---|---|---|
| Tavily | 1,000 credits per month (basic search is 1 credit) | No | Built for agents; returns cleaned page text; has an extract endpoint |
| Exa | About 10 USD of credit per month | No | Meaning-based search; returns page text |
| Ollama web search | Free tier, limits not published | No | `POST https://ollama.com/api/web_search` with the Ollama API key the user already has for Ollama Cloud; also has web fetch |
| Brave Search API | 5 USD credit per month (about 1,000 searches) | Yes | Free plan replaced by metered credit in February 2026 |

Recommendation: Tavily as the default, Ollama and Exa as fallbacks. The `web_search` tool tries the next provider when one returns a quota error, so the free tiers add up to a few thousand searches a month.

Separate `web_fetch` tool: downloads a page and converts the HTML to readable text on the phone (jsoup plus Readability4J). Free, no API needed.

How a search call looks (Tavily):

```
POST https://api.tavily.com/search
Authorization: Bearer tvly-...
{"query": "Bangladesh budget 2026 analysis", "max_results": 5}
```

### YouTube search through web search

Yes. Restrict the search to YouTube, for example Tavily's `"include_domains": ["youtube.com"]`, or `site:youtube.com` in the query for providers without a domain filter. Results give title, URL and a snippet. Limits: very new videos may be missing, and there is no duration or view count. If those matter later, the YouTube Data API v3 is official and free up to 10,000 quota units a day; one search costs 100 units, so about 100 searches a day.

Full flow: "summarise the latest video on X": web search restricted to youtube.com finds the URL, then Gemini summarises it.

Sources: [Jev Router docs](https://openrouter.ai/docs/guides/routing/routers/jev-router), [Jev docs](https://openrouter.ai/docs/guides/community/jev), [GIGAZINE on Jev Router](https://gigazine.net/gsc_news/en/20260928-openrouter-jev-router/), [Firecrawl: free web search APIs 2026](https://www.firecrawl.dev/blog/best-free-web-search-apis), [Brave API credit change](https://www.implicator.ai/brave-drops-free-search-api-tier-puts-all-developers-on-metered-billing/), [Ollama web search](https://lqdev.me/responses/ollama-web-search-2025-09-25/).

---

## User

> Also, how would we save the files or collect files from the mobile's own storage (like copy it to the sandbox, then work then place in mobile)?

## Assistant

### Files: copy in, work in the sandbox, export out

The sandbox is the app's private folder (`filesDir/threads/<thread-id>/`). Jonaki reads and writes it freely with no permission. Each thread has `inbox/` for imported files, `work/` for the agent's files, and `artifacts/` for reports and presentations.

Files are copied in rather than edited in place because Android's file picker returns `content://` URIs, not file paths, while Python (Chaquopy), PDF libraries and most tools need a real path. Copy in, work, export out is therefore the reliable pattern.

Getting files in:

1. **Share sheet**: share from any app (Files, Gallery, WhatsApp, Chrome) to Jonaki; the file is copied into the thread's `inbox/`.
2. **Attach button**: opens the system file picker (`ACTION_OPEN_DOCUMENT`), or the Photo Picker for images. No storage permission needed.
3. **Linked folder**: the user grants one folder once (`ACTION_OPEN_DOCUMENT_TREE`, for example `Documents/Jonaki`). Jonaki keeps the permission across restarts and the agent can list, read and write inside that folder only.

Saving files out:

1. **Downloads/Jonaki** through MediaStore: Android 10 and later let an app create files in shared Downloads and Documents with no permission. Default for "save".
2. **Save as**: the system "create document" picker (`ACTION_CREATE_DOCUMENT`) lets the user choose the place and name.
3. **Share**: hand the file to another app (WhatsApp, Gmail, Drive) through a FileProvider.
4. **Write back** into the linked folder.

Agent tools: `list_files`, `read_file`, `write_file`, `edit_file` inside the thread folder run freely; `export_file(path, destination)` and writes to the linked folder ask for confirmation.

Not used: the "All files access" permission (`MANAGE_EXTERNAL_STORAGE`). Google Play allows it only for file managers, backup and similar apps, and the agent does not need the whole phone. It sees only what the user hands over.

Worked example: the user shares `sales.xlsx` from WhatsApp to the thread "Shop accounts". Jonaki copies it to `inbox/sales.xlsx`. The agent runs a Python script that totals sales by month and draws a chart, then writes `artifacts/sales-report.html`. The user taps "Save"; Jonaki writes `Downloads/Jonaki/sales-report.pdf` and shows a notification to open it.

Housekeeping: copying streams the file so large files do not fill memory, and the settings show storage used per thread with a button to clear a thread's files.

---

## User

> What parts of the above ideas are not google play compliant? tell in short.
>
> Also, in total list what tools we'll need now, and how slow will it become.
>
> Then, do a comparison of our design with kai-9000. ALso, what are the limitations of kai-9000 for which its not that usable now (one example is I have found that if we do a complex search, its hanged and cannot process, similar things happened in other agent apps from f-droid or playstore)
>
> (mid-turn) what web search does claude code use?

## Assistant

### Google Play compliance

Not compliant, already dropped from the design:

- YouTube scraping with NewPipeExtractor (breaks YouTube's terms).
- proot Linux downloaded after install (Play bans downloading executable code). Kai 9000 ships this on Play anyway, so enforcement is uneven, but it remains a removal risk.
- Phone control through AccessibilityService.
- "All files access" (`MANAGE_EXTERNAL_STORAGE`).
- SMS and call-log access.

Allowed with paperwork:

- Reading other apps' notifications: needs an in-app disclosure and a Play declaration.
- Foreground service for long agent runs: the service type must be declared and justified.
- AI-generated content policy: the app needs a way for users to report offensive AI output, and a privacy policy that says chats are sent to the chosen AI provider.
- Python (Chaquopy): it must ship inside the app or as a Play Feature Delivery module, not as a separate download.

### Full tool list (about 30 built-in)

| Group | Tools |
|---|---|
| Web | `web_search` (Tavily, then Ollama, then Exa), `web_fetch` |
| YouTube | `youtube_summarize` (Gemini); search uses `web_search` limited to youtube.com |
| Files | `list_files`, `read_file`, `write_file`, `edit_file`, `search_files`, `export_file` |
| Compute | `run_python`, `run_javascript` |
| Artifacts | `create_artifact`, `update_artifact`, `export_pdf` |
| Memory | `remember`, `forget`, `recall` |
| Skills | `load_skill` |
| Subagents | `spawn_agent` |
| Phone | `calendar_list`, `calendar_add`, `set_reminder`, `notify`, `clipboard_read`, `clipboard_write`, `open_app` (email draft, maps, dialer) |
| Schedule | `schedule_task`, `list_tasks`, `cancel_task` |
| MCP | whatever tools the connected servers provide |
| Later | `ssh_run`, Git tools |

### Speed

Idle tools cost nothing: each tool is a Kotlin function with no background process. The real costs:

1. **Tool descriptions sent with every request.** Each costs about 100 to 200 tokens, so 30 tools add roughly 3,000 to 6,000 tokens per request. Cloud providers process that in well under a second and prompt caching makes it cheap, but a local Ollama on a modest GPU may take several seconds. Fix: only send the tool groups a thread uses, and keep the system prompt identical between requests so the cache holds.
2. **Model round trips.** Each tool step is one model call of 1 to 10 seconds, so a six-step research task takes about 20 to 60 seconds. Subagents running in parallel cut the waiting time.
3. **App size.** About 15 MB without Python; Python adds 20 to 40 MB, so it should be an on-demand module.
4. **Python start.** About 1 to 2 seconds the first time it is used in a session; nothing if unused.

### Kai 9000 compared with Jonaki

Kai 9000 (`SimonSchubert/Kai`, about 1,270 GitHub stars, active) is open source, built with Kotlin and Compose Multiplatform, and on Google Play, F-Droid, iOS, desktop and web. Findings below come from its source, docs and issue tracker as of 2026-10-02.

| Area | Kai 9000 | Jonaki plan |
|---|---|---|
| Providers | About 24, with fallback chains | OpenAI-compatible providers, Gemini, plus Jev routing |
| Chats | Multiple conversations | Threads with their own memory, files, settings |
| Memory | One global list stored as JSON in app settings, injected into every prompt; hit counts; strong memories get promoted into the "soul" | Global plus per-thread memory in SQLite with full-text search; editable; extraction with update and delete |
| Web search | Scrapes DuckDuckGo Lite HTML | Search APIs with a fallback chain |
| Sandbox | proot with Debian (about 150 MB) or Alpine | No Linux; Python and JavaScript inside the app; SSH optional |
| Skills | SKILL.md, but stored inside the Linux sandbox, so they need the sandbox installed | SKILL.md in app storage, no sandbox needed |
| Subagents | None found | `spawn_agent` with parallel runs |
| Artifacts | Native interactive UI generated in chat (strong feature) | HTML artifacts with PDF export |
| Background | Heartbeat checks tasks and email; daemon service | Scheduled tasks; agent loop in a foreground service |
| Extras | Email, SMS, on-device models, Kai Build | YouTube summaries, Jev router |

### Why Kai 9000 hangs on complex searches

1. **Scraped search.** `WebSearchTool.kt` downloads `lite.duckduckgo.com` and parses the HTML with regular expressions. DuckDuckGo throttles or blocks rapid automated queries, and a complex search fires many queries in a row, so later searches return nothing or fail after the 15-second timeout. The model then retries.
2. **No streaming.** Issue #384 (open): the app waits for the whole response before showing anything, so a long answer looks frozen.
3. **Long turns with little feedback.** One message may run up to 15 tool rounds (`MAX_TOOL_ITERATIONS = 15`), each with a model call allowed 180 seconds and a tool allowed 30 seconds. A complex search can run for minutes.
4. **Context fills fast.** Each tool result may be up to 20,000 characters. A few fetched pages push the history past 70% of the context window, which triggers a summarising model call in the middle of the work. Messages were lost when that was interrupted by the app going to the background (#483, partly fixed).
5. **Prompt cache broken.** The system prompt includes the current time, so it changes on every message and the provider re-processes the whole conversation each time (#349, open). This is slow and costly, worst on local models.
6. **Sandbox degrades.** proot slows down over a session and then hangs; recovery needs a phone reboot (#359, open).
7. Other open issues: local models don't see tools (#488); "AI unresponsive" (#505); no folder access through the file picker (#514).

Other agent apps share most of these causes: scraped search, no streaming, the agent loop tied to the screen instead of a service, and tool results dumped into the context without limits.

### Jonaki design rules drawn from this

- Search through APIs with a fallback chain; return snippets first and fetch only chosen pages; clean fetched pages and cap them at about 3,000 to 5,000 tokens.
- Heavy research goes to subagents, so the main thread only receives summaries.
- Always stream; show each step live with a Stop button; time limit per step and a budget per message.
- Run the agent loop in a foreground service and save every step to the database as it happens, so nothing is lost if Android kills the app.
- Keep the system prompt stable; put the time and other changing details in the latest message.
- Summarise long threads between messages, not in the middle of a tool run, and keep the original messages in the database.
- No proot.

### Claude Code's web search

Claude Code's WebSearch tool uses Anthropic's server-side web search tool: Claude asks for a search and Anthropic's servers run it and return results with citations. Anthropic's documentation does not name the search engine; independent tests in 2025 found the results matched Brave Search. It costs 10 USD per 1,000 searches plus tokens and works only with Claude models through Anthropic's API, so Jonaki could offer it only to users with an Anthropic key.

Sources: [Kai 9000 site](https://kai9000.com/), [Kai repository](https://github.com/SimonSchubert/Kai), [Kai on F-Droid](https://f-droid.org/packages/com.inspiredandroid.kai/), Kai issues [#359](https://github.com/SimonSchubert/Kai/issues/359), [#384](https://github.com/SimonSchubert/Kai/issues/384), [#349](https://github.com/SimonSchubert/Kai/issues/349), [#483](https://github.com/SimonSchubert/Kai/issues/483), [#488](https://github.com/SimonSchubert/Kai/issues/488), [#505](https://github.com/SimonSchubert/Kai/issues/505), [#514](https://github.com/SimonSchubert/Kai/issues/514).

---

## User

> checkout various tools and skills design of pi agent which are pragmatic, but well covered, and takes less tokens for the above choosen tools.
> Would every tools work without python. If not then add the python in built. Or its better to at startup offer what tools to choose with a little description of each one to understand; if anything choosen that needs python, then download and install it. Also, provide python download and install option in the settings too. Tell me first if these are possible, and don't interfere with google policy.
>
> I think if most of the tools need python, then install it alongside. compare and suggest me the options.
>
> Your suggested rules are right.
>
> ONe think we can do as minimize the number of tools subagents can call so that to make them lightweight and fast. We should also have the options for the main agent to tell what other tools to add to the subagent. Most importantly, I think our subagent should be interactive among themselves and the main agent only when necessary. FOr example, to do its tasks if it needs an extra tool from the tool list, it asks the main agent, and the user approves.
> WHat doe you think?

## Assistant

Decision recorded: the design rules from the Kai 9000 comparison are accepted.

### Patterns taken from pi

Studied: pi core tools (`@earendil-works/pi-coding-agent/dist/core/tools`), the user's extensions (`pi-config-rms/extensions`), `pi-mcp-adapter`, `pi-intercom`, `pi-boomerang`.

1. **Two layers per tool.** The system prompt carries one line per tool (`- read: Read file contents`). The full schema is sent only for active tools, and usage guidelines are added only when their tool is active, so a disabled tool costs zero tokens.
2. **Truncation notices that name the next call.** Read caps at 2,000 lines or 50 KB and appends `[Showing lines X-Y of N. Use offset=N to continue.]`.
3. **Spill to file.** Oversized output is saved to a file and the model gets the first part plus the path, instead of the whole output.
4. **Multi-edit with exact-then-fuzzy matching.** `edit(path, edits: [{oldText, newText}])`; each `oldText` must be unique; if no exact match, retry after normalising whitespace, smart quotes and dashes.
5. **Skills need no tool.** The prompt lists name, description and file path per skill; the model loads a skill with the ordinary read tool.
6. **One proxy tool for all MCP servers** (`pi-mcp-adapter`): a single `mcp` tool (about 200 tokens) with search, describe and call modes replaces every MCP schema; servers connect on first use; tool lists are cached.
7. **Search that can include page text** (`web_search` with `includeContent`, each page cut to 5,000 characters) saves a separate fetch round trip. Pi's `web_fetch` has no output cap; Jonaki must cap it.
8. **Subagents get no conversation context**; the parent writes the whole task; only the final answer returns, capped at 50 KB.
9. **Structured compaction summary**: Goal, Constraints and Preferences, Progress, Key Decisions, Next Steps, Critical Context; each summary updates the previous one.

### Revised tool set (16 tools, about 2,500 tokens)

| Tool | Parameters (short) | About |
|---|---|---|
| `read_file` | path, offset, limit | 110 |
| `write_file` | path, content | 50 |
| `edit_file` | path, edits[{oldText, newText}] | 180 |
| `find_files` | pattern, path, limit | 80 |
| `search_files` | pattern, path, glob, limit | 130 |
| `share_file` | path, destination: downloads, save_as, share, linked_folder | 80 |
| `web_search` | query, site, freshness, count, include_content | 200 |
| `web_fetch` | url, max_length | 80 |
| `youtube_summarize` | url, prompt, start, end | 100 |
| `run_code` | language: javascript or python, code, files | 120 |
| `artifact` | action: show, export_pdf; path | 80 |
| `memory` | action: remember, forget, recall; text, scope, id | 150 |
| `delegate` | agent, task, extra_tools, or tasks[] | 200 |
| `phone` | action: calendar_list, calendar_add, reminder, notify, clipboard_read, clipboard_write, open_app | 250 |
| `schedule` | action: create, list, cancel | 120 |
| `mcp` | search, describe, call (proxy) | 200 |

Rarely used actions are grouped into one tool with an `action` parameter (`phone`, `schedule`, `memory`); frequently used file tools stay separate because small single-purpose schemas are easier for weaker models. `load_skill` is dropped (skills load through `read_file`). Tool groups can be switched off per thread.

### Python: which tools need it

None of the 16 tools needs Python. PDF text comes from PdfBox-Android, charts in reports come from a small JavaScript chart library bundled in the app, and calculations can run in JavaScript (QuickJS). Python matters for two things: `run_code` with `language: python` (data analysis with pandas) and skills that ship Python scripts (common in public skill collections).

### Python options

| Option | How it is delivered | Size | Play policy | Trade-offs |
|---|---|---|---|---|
| A. Chaquopy bundled | Inside the APK | +25 to 40 MB for every user | Compliant | Native speed; packages fixed at build time; Python runs inside the app process and can reach Android and app internals such as stored API keys |
| B. Chaquopy on demand | Play Feature Delivery module downloaded from Play | Same, only for users who enable it | Compliant (delivered by Play) | Same as A; Chaquopy's changelog says it supports dynamic feature modules, needs a test build; F-Droid and GitHub builds cannot use Play delivery and need a separate "with Python" APK |
| C. Pyodide on demand | Python compiled to WebAssembly, run in a hidden WebView, downloaded from our server | Core 6.4 MB; numpy plus pandas about 10.5 MB more | Fits Play's exception for code that runs in an interpreter or virtual machine with only indirect access to Android APIs, such as JavaScript in a WebView | Can install packages at runtime with micropip; cannot touch Android or app secrets; same build for Play and F-Droid; slower start (a few seconds) and slower heavy computation; no subprocesses; files must be copied into and out of its virtual file system |

Recommendation: **C, Pyodide on demand.** It is the only option that is sandboxed from the app's secrets, can install packages a skill asks for, and works the same on every store. Speed rarely matters for agent scripts.

Policy conditions for C: download over HTTPS from our own host, verify a checksum, and never download native `.so` or `.dex` files.

### First-run tool picker and Python in settings

Both are possible and policy-safe:

- **First run**: a screen of tool groups with one-line descriptions and toggles. Groups needing Python show the download size ("Python code runner, 6 MB", "Data analysis add-on, 10 MB").
- **Settings, Python**: install, remove, list installed packages, storage used.
- **Just in time**: if the model or a skill needs Python and it is missing, a card asks "This needs Python (6 MB). Install?".

### Subagents: assessment of the proposal

Agreed:

1. Minimal default tools per agent type (researcher: `web_search`, `web_fetch`, `read_file`).
2. The main agent adds tools at spawn time through `delegate(..., extra_tools: [...])`.

Changed:

3. **Tool requests go to the permission system, not the main agent's model.** Waking the main agent means a full-context model call, which is slow and costly, and the main agent may be busy. Flow: the subagent calls `request_tool(name, reason)`; a read-only tool already allowed in the thread is granted at once; a tool with side effects shows the user an approval card (which subagent, which tool, why) with allow once, allow for this task, deny; with no answer within a set time it counts as denied, and the subagent finishes with what it has and reports what it could not do. Nothing waits forever.
4. **The main agent is asked only about the task itself**: `ask_parent(question)`, at most two per subagent, for real ambiguity.
5. **Subagents share a notes board instead of chatting directly.** Direct agent-to-agent chat multiplies model calls (each message costs a call for sender and receiver), can loop, and agents waiting on each other can deadlock, which is the same class of hang seen in Kai 9000. With `notes(action: post or read)` per delegation group, a subagent posts a finding and the others read it when they choose; nobody waits.

Worked example: three laptop researchers. One finds a review site comparing all three and posts the URL; the other two read the board, use the same source and skip duplicate searches.

Limits stay: subagents cannot delegate further; results are capped; each subagent has a step and cost budget.

Sources: [Pyodide deployment docs](https://pyodide.org/en/stable/usage/downloading-and-deploying.html), [Microsoft: Pyodide feasibility](https://devblogs.microsoft.com/python/feasibility-use-cases-and-limitations-of-pyodide/), [Chaquopy changelog](https://chaquo.com/chaquopy/doc/current/changelog.html), [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery).

---

## User

> YOur suggestions are good.
>
> For premissions, when a subagents is waiting for more than 3 min, then it skips those part and tries to run other part; if not possible then stop there.
>
> Now, review our main CLAUDE md guidelines. Then, review the agent md, claude md from the from this @~/repos/OPFlow-BD-scratch/; remember that this is a research repo, so we may not need all the features from here; but decision log is a good idea.
>
> THen, create our own claude md or agent md in the jonaki repo. git init too.
>
> THen, create the plan.
>
> ONe thing to keep in mind is - we should follow Unix policy when coding. for example, one tool is one module; we can add or remove similar modules easily in our future versions.
>
> If you understood everything, then write the plan.

## Assistant

Created in the repository (git initialised, nothing committed):

- `AGENTS.md`: project rules for any coding agent, adapted from OPFlow-BD: session start and end routine, control rules, the one-job-per-module code rules, code style from the user's global guidelines, the D-005 runtime rules, Google Play rules, verification, writing and git rules.
- `CLAUDE.md`: points to AGENTS.md.
- `DECISIONS.md`: D-001 to D-020 from this conversation, in OPFlow's template; decisions the user stated or approved are marked accepted, the rest proposed. The 3-minute rule is recorded in D-015.
- `STATUS.md`: present state.
- `docs/plans/2026-10-02-v1-plan.md`: version 1 plan, milestones M0 to M11 with checks.
- `.gitignore`.

Taken from OPFlow-BD: AGENTS.md plus a pointer CLAUDE.md, STATUS.md, the decision log with status set only by the user, plans with a "Resume from" line, propose-before-implement, claim nothing not run, the user commits, no AI attribution. Left out: the daily journal, data-source and modifications ledgers, script and code maps, references (research needs; git history and the plan checklist cover an app).

---

## User

> Well, you can add AI attribution. And I also want the agent to run the commit automatically with each incremental changes. The commit msg should be limited to 80 character.
> Decisions md not just limited to D20; rather it'll grow if needed; but the msg should be concise so that reading it doesn't eat too much token.
>
> Let's skip the playstore requirements for now. We'd release it through github for now. So, you only build the release apk, no debug apk. You may check other repos in this app, where I've built other android apps; and you can reuse their gradle setup if possible, so that we don't need to install and take space again for this one.
>
> Checkout github.com/shourovrm/jev-browser, which I've built recently; you can clone it in ~/repos too. There, you may also find how I've set up jev-router for claude code.
>
> Jev is system one model that only provides classification results. So, tell me if there'll be really that much help using it; that means if it really helps to save tokens or not. One think it may work well is to use it for calling skills or memory; search and check it out too. We can set a api from typesafe or we can use openrouter api's jev too for this.

## Assistant

Changes: AGENTS.md now has automatic commits (subject at most 80 characters, AI trailer allowed), a release-only build section reusing the BD-calendar and hujur-tracker Gradle stack (D-021), and no Google Play section. DECISIONS.md was rewritten in a short format and grows as needed. The plan's M11 is now a GitHub release; spike S-5 tests Jev on Bangla and mixed messages; M10 builds Jev as an optional decider module (D-022, superseding D-016).

Jev assessment: Jev (TypeSafe's decision model, $0.042 per million input tokens, output free, 64k-token request limit, English-first) does not write text, so it cannot replace the main model. It saves tokens and money where it decides what the main model sees or whether a model call happens at all: which skill to load (TypeSafe's cookbook on 488 requests: wrong loads 16.8 to 7.3 percent, needless loads 9.8 to 4.0 percent), which memories among full-text candidates are relevant, whether a message holds a durable fact worth an extraction call, which web results to keep, and which model size a message needs. Its saving is small when the provider's prompt cache already makes repeated input cheap, and larger for uncached providers and local Ollama. Its hints must go into the latest message, not the system prompt, or they break the cache. The user's own jev-router (jev-browser/agent-setup/bin/jev-router) already asks size, follow-up and skill questions in one call with a 0.6 confidence threshold and a 1.5 s timeout; Jonaki's decider copies that shape. No router log exists on this machine, so there is no local usage data yet; the only measured call is the use-jev skill's example (3 questions, about 0.5 s, about 0.000024 USD).
