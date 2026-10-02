# Jonaki

An Android app that runs an AI agent in a chat, with your own API keys.

**Download:** [jonaki-v0.7.0.apk](https://github.com/shourovrm/jonaki/releases/download/v0.7.0/jonaki-v0.7.0.apk) · [all releases](https://github.com/shourovrm/jonaki/releases)

Android 8.0 or later, 64-bit ARM phones. Pre-release: expect rough edges.

Jonaki (জোনাকি) is Bangla for firefly.

## Features

**Chat in threads.** Each thread has its own messages, memory, files and model.
Answers stream in, every tool step shows live with a Stop button, and nothing
is lost if Android closes the app mid-answer.

**Your keys, your models.** OpenRouter, DeepSeek and Gemini. Pick a model per
thread, set a thinking level, and see the cost of every answer, the thread and
the month. Account balances show where the service reports them.

**Web search and pages.** Tavily, Ollama or Exa, tried in your order. Each
query is shown in its step, and web access can be turned off per thread.

**YouTube summaries** through Gemini, for a whole video or a time range.

**Memory.** The agent remembers facts per thread or across all threads. You can
read, edit, pin and delete them, or review new ones before they are kept. Long
threads are summarised; the original messages stay.

**Skills.** Instructions the agent follows for a kind of task. Built in:
report, slides, YouTube summary, formal letter in Bangla. Import more from a
file, a link or a GitHub folder.

**Files.** Share files into a thread from any app, attach them, or take a
photo. The agent reads text files, PDFs, Word, Excel and PowerPoint files and
images. It can save results to Downloads, share them, or write to one folder
you link.

**Reports and slides.** The agent writes HTML pages with charts that open in
an offline viewer, keep every version, and print to PDF.

**You stay in control.** Anything that changes a file or leaves the app asks
first: allow once, allow for the thread, or deny.

## Building

Release builds only:

```
gradle testReleaseUnitTest assembleRelease
```

Needs the Android SDK (platform 35), JDK 17 or later and Gradle 9.7. The APK
is signed with `jonaki.keystore` in the repository root, which is not
committed; see `AGENTS.md` for how to create it.
