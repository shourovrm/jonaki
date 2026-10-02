# Jonaki

An Android app that runs an AI agent in a chat, with your own API keys.

**Download:** [jonaki-v0.7.0.apk](https://github.com/shourovrm/jonaki/releases/download/v0.7.0/jonaki-v0.7.0.apk) · [all releases](https://github.com/shourovrm/jonaki/releases)

- Android 8.0 or later, 64-bit ARM
- Pre-release: expect rough edges
- Jonaki (জোনাকি) is Bangla for firefly

## Features

**Threads**
- Each thread has its own messages, memory, files and model
- Answers stream in; every tool step shows live with a Stop button
- Nothing is lost if Android closes the app mid-answer
- Copy any message; edit a prompt and send it again

**Models and cost**
- OpenRouter, DeepSeek and Gemini with your own keys
- Model and thinking level per thread
- Cost per answer, per thread and per month
- Account balances where the service reports them

**Web**
- Search through Tavily, Ollama or Exa, tried in your order
- Each query is shown in its step
- Web access can be turned off per thread
- YouTube summaries through Gemini, for a whole video or a time range

**Memory**
- Facts kept per thread or across all threads
- Read, edit, pin, delete, or review new facts before they are kept
- Long threads are summarised; the original messages stay

**Skills**
- Built in: report, slides, YouTube summary, formal letter in Bangla
- Import from a file, a link or a GitHub folder
- Switch skills on or off per thread

**Files**
- Share into a thread from any app, attach, or take a photo
- Reads text, PDF, Word, Excel, PowerPoint and images
- Saves to Downloads, shares, or writes to one folder you link

**Reports and slides**
- HTML pages with charts, shown in an offline viewer
- Every version kept; print to PDF

**Approvals**
- Anything that changes a file or leaves the app asks first
- Allow once, allow for the thread, or deny

## Building

```
gradle testReleaseUnitTest assembleRelease
```

- Release builds only
- Android SDK platform 35, JDK 17 or later, Gradle 9.7
- Signed with `jonaki.keystore` in the repository root (not committed; see `AGENTS.md`)
