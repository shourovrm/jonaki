# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack
Kotlin, Jetpack Compose, Material 3 components, coroutines, Room, OkHttp with
server-sent events (D-006, D-021). minSdk 26, arm64-v8a, release APK only.

## Users
The author first: a Bangladeshi developer who works in English and Bangla and
uses AI agents (pi, Claude Code) on a Linux PC. Jonaki brings that agent work
to the phone. Others may install it later from a GitHub release, so the app
must not depend on the author's machine, but version 1 design choices favour
the author's own use.

## Product Purpose
Jonaki (জোনাকি, "firefly") is an Android app that runs an AI agent in a chat
interface, with the user's own API keys. The agent searches the web,
summarises YouTube videos, writes HTML reports, remembers facts, uses skills,
runs small programs, uses phone features and delegates to subagents. Success
means the author reaches for Jonaki instead of Hermes over Telegram or a PC
session for everyday research and quick tasks.

## Positioning
Work is organised in threads; each thread owns its messages, memory and
folder, while global memory and skills are shared. A fact from a thesis
thread never reaches a travel thread. Every agent step is visible live with a
Stop button, and every step is saved, unlike Kai 9000, which hangs on search
and loses work.

## Operating Context
- Quick asks on the go, often one-handed.
- Long research sessions that run many tool steps and end in a report.
- Night and low-light use; dark theme matters.
- Background tasks: start a task, leave the app, return to the result
  (agent loop runs in a foreground service).
- Chat content is in English, Bangla, or a mix.

## Capabilities and Constraints
- Version 1 milestones: streaming chat with threads (M1), agent loop with
  file tools (M2), web search, web fetch and YouTube summaries (M3), then
  memory, skills, artifacts, subagents, runtimes, phone tools, MCP.
- Tool calls that change something need user approval (approval cards:
  allow once, allow for this thread, deny).
- Each web search query is shown in its step card; web search can be
  switched off per thread.
- Interface language: English only for now; Bangla interface not planned yet.
- Theme follows the system light/dark setting, with a manual toggle.

## Brand Commitments
- Name Jonaki (জোনাকি), meaning firefly (D-002).

## Evidence on Hand
No users, testimonials or benchmarks exist yet. Do not invent any.

## Product Principles
1. Show the work: every tool step, query and approval is visible.
2. Never lose work: streamed text and steps survive the app being killed.
3. Threads keep contexts apart.
4. Light on the phone: small APK, few permissions, optional downloads.
5. The user decides on anything that changes files, costs money or leaves the phone.

## Accessibility & Inclusion
Bangla script must render well at body sizes alongside Latin text. Follow
Android accessibility defaults: 48 dp touch targets, TalkBack labels, system
font scaling.
