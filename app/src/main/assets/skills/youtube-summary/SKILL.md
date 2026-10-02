---
name: youtube-summary
description: Summarise a YouTube video with youtube_summarize — key points with timestamps, a short or long version, long videos in parts, and an optional saved note. Use when the user shares a YouTube link or asks what a video says.
---

# YouTube summary

`youtube_summarize` sends the video to Gemini, which watches it. A 20-minute video costs about 100,000 input tokens, so watch only what the request needs.

## Steps

1. Get the link. If the user names a video without a link, find it with `web_search` restricted to youtube.com and confirm the title in one line before summarising.
2. Choose the scope:
   - The user asks about one part ("what does he say about prices") → pass that question as `prompt`.
   - The user gives a time range → pass `start` and `end`.
   - The video is longer than 30 minutes → set `low_resolution: true`.
   - The video is longer than 60 minutes → summarise it in parts of 30 minutes with `start` and `end`, then merge the parts yourself.
3. Ask for timestamps in the prompt, for example: "Summarise this video. Give the main points in order, each with its timestamp as mm:ss, then a two-sentence conclusion."
4. Answer in the chat in the user's language:
   - One line: title, channel and length when known.
   - 5 to 8 key points, each starting with its timestamp.
   - A two-sentence takeaway.
   - For a tutorial, the steps as a numbered list instead of key points.
5. If the user wants to keep it, or the summary is longer than about 400 words, also save it as `artifacts/<video-title>-summary.md` with the link at the top, and say so.

## Writing

- Each key point states what the video says as a checkable fact, with the number or example the speaker gives.
- No opening line for effect, no list of three built for rhythm, no praise of the video.

## Rules

- Report what the video says, and mark your own comments as such.
- If the tool fails (private video, age limit, live stream not yet ended), say what failed and offer a web search for an article about the same topic.
