---
name: reddit
description: Search Reddit through its public RSS feed and read threads with their comments through the Arctic Shift archive and web_fetch, then report what people say with links. Use when the user asks what Reddit thinks, wants real user experiences or opinions, or shares a reddit.com link.
---

# Reddit

Reddit blocks most apps that read its pages or its `.json` addresses without an account, but its RSS feeds open without one. Reddit allows only one feed request per window of up to about a minute, so use Reddit for the search and read the threads from Arctic Shift, a separate archive of Reddit that has threads within about an hour of posting.

## Find threads

Fetch one of these with `web_fetch` and `max_length` 20000 (spaces in the query as `+`):

- All of Reddit: `https://www.reddit.com/search.rss?q=<query>&sort=relevance&t=year&limit=10`
- One subreddit: `https://www.reddit.com/r/<subreddit>/search.rss?q=<query>&restrict_sr=on&sort=relevance&t=all&limit=10`
- A subreddit's best posts: `https://www.reddit.com/r/<subreddit>/top/.rss?t=week&limit=10`

`sort` is relevance, top, new or comments; `t` is hour, day, week, month, year or all. Use `t=year` or narrower for anything that changes over time, such as phones, prices or software versions.

The feed is Atom XML: each `<entry>` holds a title, an author (`/u/name`), a link and the post's text as escaped HTML. A thread link has the form `https://www.reddit.com/r/<subreddit>/comments/<id>/<slug>/`; the `<id>` (for example `1vxisr8`) is what the next step needs.

If the feed fails, use `web_search` with `site` set to reddit.com instead.

## Read a thread

Fetch the comments with `web_fetch` and `max_length` 20000:

`https://arctic-shift.photon-reddit.com/api/comments/search?link_id=<id>&limit=100&sort=asc&fields=id,parent_id,author,body`

The answer is JSON, oldest comment first. A `parent_id` that starts with `t3_` answers the post; `t1_<id>` answers the comment with that `id`. The post's own text is in the search feed. For a link the user shared, fetch it from `https://arctic-shift.photon-reddit.com/api/posts/ids?ids=<id>&fields=title,selftext,author,subreddit,created_utc,num_comments`.

Arctic Shift fails about one request in four (HTTP 525 or no answer) and then works: on an error, fetch the same address once more.

If it fails twice, fetch the thread from Reddit by adding `.rss?limit=25` to its link, for example `https://www.reddit.com/r/pixel_phones/comments/1vxisr8/guys_i_fixed_my_battery_life/.rss?limit=25`. The first entry is the post; the rest are comments in one flat list, so a reply does not show which comment it answers. HTTP 429 here means the search used up this minute's request: try once more after your next step, then answer from what you have.

Read 2 to 4 threads, not one: a single thread is one group's view.

## Answer

- Start with what most people report, then the main disagreement, then anything unusual but specific.
- Give counts where you can ("4 of the 6 owners who mention it"), not "many users".
- Link every thread you used, with its subreddit and year.
- Quote a comment only when its exact words matter, in one line, with its author.
- Mark opinions as opinions; Reddit comments are not tested facts.

## Rules

- Put only general topic words into a query, never the user's name, personal details or file contents.
- Do not fetch a Reddit feed more than once for the same answer, except the one retry after a 429.
- Say which source failed when you had to fall back.
