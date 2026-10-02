# Recorded API responses

Responses recorded from this PC on 2026-10-02 during spikes S-3 and S-4. M3
tests replay them instead of calling the services. No file contains a key.

## gemini/ (S-3, model gemini-3.8-flash, generateContent)

| File | Request | Result |
|---|---|---|
| `youtube-summary-200.json` | 19-minute TED talk (iG9CE55wbtY), English summary prompt | 105,878 video tokens, 383 output tokens, 21.0 s |
| `youtube-summary-bangla-200.json` | Same video, Bangla prompt, `mediaResolution: MEDIA_RESOLUTION_LOW` | Same 105,878 video tokens (low resolution had no effect); 102,276 served from Gemini's automatic cache; 14.1 s |
| `error-503-overloaded.json` | Same request as above, first attempt | `UNAVAILABLE`, "high demand"; retry succeeded |
| `error-403-video-unavailable.json` | Non-existent video ID | `PERMISSION_DENIED`, "The caller does not have permission" |
| `error-404-model-retired.json` | Model gemini-2.5-flash | `NOT_FOUND`, "no longer available to new users", names gemini-3.8-flash |

## search/ (S-4)

| File | Request | Result |
|---|---|---|
| `tavily-search-200.json` | `POST api.tavily.com/search`, 5 results | 2.2 s round trip; snippets are short (first one 84 characters) |
| `tavily-search-youtube-200.json` | `include_domains: ["youtube.com"]` | The right TED video in the top 3 |
| `tavily-error-401-bad-key.json` | Invalid key | 401 `{"detail":{"error":"Unauthorized: ..."}}` |
| `ollama-web-search.json` | `POST ollama.com/api/web_search`, 5 results, account without credit | 2.7 s; each result has 3,480 to 8,761 characters of content |
| `ollama-web-fetch-404.json` | `POST ollama.com/api/web_fetch` | 404 `{"error": "not found"}` |
| `ollama-error-401-bad-key.json` | Invalid key | 401 `{"error":"Unauthorized"}` |

Tavily quota errors could not be triggered on a fresh account. Its
documentation lists 429 (rate limit, with a `Retry-After` header), 432 (plan
limit exceeded) and 433 (pay-as-you-go limit exceeded), each with the body
shape `{"detail": {"error": "..."}}`.

## balance/ (D-031)

| File | Request | Result |
|---|---|---|
| `openrouter-credits.json` | `GET openrouter.ai/api/v1/credits`, 2026-10-02 | 24 credits, 11.126219445 used |
| `openrouter-key.json` | `GET openrouter.ai/api/v1/key`, trimmed to the limit fields (label and ids removed) | limit 20 monthly, 19.927821514 left |
| `tavily-usage.json` | `GET api.tavily.com/usage` | plan usage 3 of 1,000 credits |
| `deepseek-balance-documented.json` | Not recorded (no DeepSeek key); shape from DeepSeek's documentation of `GET /user/balance` | balance as text, per currency |

## openrouter/ (M3b, D-027)

| File | Request | Result |
|---|---|---|
| `models-trimmed.json` | `GET openrouter.ai/api/v1/models` (no key), 2026-10-02; 5 of 464 models kept | Prices are USD per token as text; `openrouter/auto-beta` has price `-1` (varies); `deepseek/deepseek-chat` has no cached-input price |
| `error-404-data-policy.json` | Chat request to `liquid/lfm-2.5-2.6b:free` with `provider: {data_collection: "deny", sort: "price"}` (D-030) | HTTP 404, "No endpoints found matching your data policy (Free model training)", `failed_routing_step: "Filter by Data Policy"` |
