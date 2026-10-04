# Jonaki

An Android app that runs an AI agent in a chat, with your own API keys.

**Download:** [jonaki-v1.4.0.apk](https://github.com/shourovrm/jonaki/releases/download/v1.4.0/jonaki-v1.4.0.apk) · [all releases](https://github.com/shourovrm/jonaki/releases)

- Android 8.0 or later, 64-bit ARM
- Interface in English and Bangla
- Pre-release: expect rough edges
- Jonaki (জোনাকি) is Bangla for firefly

## Getting started

1. Install the APK. A newer version installs over an older one and keeps every thread.
2. Open Settings > Models and save a key for one service.
3. Start a thread and send a message.

## Features

The sections follow the order in which you meet each part: the chat, the model behind it, the tools the agent uses, what it produces, what it remembers, and what it may do without asking.

### Chat

- Each thread has its own messages, files, memory and model
- Answers stream in, and every tool step shows live with a Stop button
- A message sent during a run waits in a queue, where you can edit or cancel it
- Any message can be copied, and a prompt can be edited and sent again
- Nothing is lost if Android closes the app in the middle of an answer
- Long threads are summarised, and the original messages stay readable

### Models and cost

- Cloud services: OpenRouter, DeepSeek, Gemini, OpenAI, GLM (Z.ai), Xiaomi MiMo, MiniMax, Qwen and Ollama Cloud
- Local models: an Ollama server on your network, or GGUF files that run on the phone
- The model and its thinking level are chosen per thread
- Cost is shown per answer, per thread and per month
- A service's card shows its balance where the service reports one (OpenRouter, DeepSeek, MiniMax, Tavily)

### Projects, personas and instructions

- A project groups threads and gives them shared files, shared facts and one prompt
- A persona is a saved set of instructions that a thread can use
- Custom instructions apply to every thread, and a thread can add its own
- The answer style (concise, normal or detailed) is set for all threads or for one

### Tools

**Web**
- Search runs through Tavily, Ollama or Exa, tried in your order
- Pages are fetched and read, including pages that need JavaScript
- YouTube videos are summarised through Gemini, whole or for a time range
- Web access can be turned off per thread

**Files**
- Files come in by sharing from another app, by attaching, or from the camera
- The agent reads text, PDF, Word, Excel, PowerPoint and images
- Results go to Downloads, to another app, or to one folder you link

**Code**
- The agent runs JavaScript on the phone
- Python is an optional download and then runs offline

**Phone and schedule**
- Calendar events, reminders and notifications
- A reminder rings again until you tap Done
- A scheduled task runs in its thread at the time you set

**Subagents and MCP**
- The agent hands parts of a task to subagents, each with a budget of steps, cost and minutes
- You can add your own subagent types
- Remote MCP servers add their tools to the agent

### Reports, slides and Office files

- The agent writes HTML pages with charts, shown in an offline viewer
- Every version is kept
- A report or slide deck exports to PDF with selectable text
- The agent writes and changes Word, Excel and PowerPoint files, with native charts in Excel and PowerPoint
- Office files follow one of three themes or your own `.docx` or `.pptx` template
- An HTML report or deck converts to Word, PowerPoint or Excel
- Office files need Python and its documents add-on, a 4.5 MB download

### Memory

- Facts are kept for one thread, for one project, or for all threads
- The agent saves facts during a chat, and a background model saves what it missed
- Each fact carries its date, so the newer of two facts wins
- Recall finds facts by any word, in Bangla or English, best match first
- The agent can search earlier chats in other threads
- You can read, edit, pin and delete facts, and restore one that was replaced
- Facts for all threads, and facts saved after a thread read a web page, wait for your approval
- Memory exports as a Markdown file

### Skills

- A skill is a set of instructions for one kind of task
- Built in: report, slides, Office documents, YouTube summary, Reddit, formal letter in Bangla
- Skills import from a file, a link or a GitHub folder
- The agent can propose a skill after a hard task; it is added only when you approve it
- Skills switch on or off per thread

### Approvals and guardrails

- An action that changes a file outside the thread or leaves the app asks first
- The approval mode is Ask, Auto or Bypass, for all threads or for one
- A card offers "Allow once" and "Allow all in this thread"
- "Always allow" rules for one action are made in Settings
- Text from web pages, documents and other outside sources reaches the model marked as outside content
- After a thread has read outside content, sending data out always asks
- The optional Jev guard skips cards for clearly safe actions and warns about outside text that carries instructions

### Privacy

- API keys stay on the phone
- An incognito thread uses no memory and is deleted a day after its last message
- There is no account and no Jonaki server; requests go from the phone to the services you set up

## Building

```
git submodule update --init --depth 1
gradle testReleaseUnitTest assembleRelease
```

- Release builds only
- Android SDK platform 35, JDK 17 or later, Gradle 9.7
- Signed with `jonaki.keystore` in the repository root (not committed; see `AGENTS.md`)
