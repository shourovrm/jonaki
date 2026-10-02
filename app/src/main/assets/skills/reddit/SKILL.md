---
name: reddit
description: Search Reddit and read threads with their comments through Reddit's public RSS feeds and web_fetch, then report what people say with links. Use when the user asks what Reddit thinks, wants real user experiences or opinions, or shares a reddit.com link.
---

# Reddit

Reddit blocks most apps that read its pages or its `.json` addresses without an account, but its RSS feeds open without one. `web_fetch` returns a feed as Atom XML: each `<entry>` holds a title, an author (`/u/name`), a link and the post or comment text as escaped HTML.

## Find threads

Fetch one of these with `web_fetch` (spaces in the query as `+`):

- All of Reddit: `https://www.reddit.com/search.rss?q=<query>&sort=relevance&t=year`
- One subreddit: `https://www.reddit.com/r/<subreddit>/search.rss?q=<query>&restrict_sr=on&sort=relevance&t=all`
- A subreddit's best posts: `https://www.reddit.com/r/<subreddit>/top/.rss?t=week`

`sort` is relevance, top, new or comments; `t` is hour, day, week, month, year or all. Use `t=year` or narrower for anything that changes over time, such as phones, prices or software versions.

If the feed fails, use `web_search` with `site` set to reddit.com instead.

## Read a thread

Take the thread's link from the search feed, which has the form `https://www.reddit.com/r/<subreddit>/comments/<id>/<slug>/`, and fetch it with `.rss?limit=25` added, for example `https://www.reddit.com/r/pixel_phones/comments/1vxisr8/guys_i_fixed_my_battery_life/.rss?limit=25`. The first entry is the post; the rest are comments in one flat list, so a reply does not show which comment it answers.

Read 2 to 4 threads, not one: a single thread is one group's view.

## Answer

- Start with what most people report, then the main disagreement, then anything unusual but specific.
- Give counts where you can ("4 of the 6 owners who mention it"), not "many users".
- Link every thread you used, with its subreddit and year.
- Quote a comment only when its exact words matter, in one line, with its author.
- Mark opinions as opinions; Reddit comments are not tested facts.

## Rules

- Put only general topic words into a query, never the user's name, personal details or file contents.
- Reddit allows only a few feed requests a minute. On a 429 or a block page, wait for the next step or fall back to `web_search`, and say so.
- Do not fetch a feed more than once for the same answer.
