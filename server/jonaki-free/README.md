# Jonaki Free

A Cloudflare Worker that answers OpenAI-style chat requests for the app's
"Jonaki Free" service. Each phone sends a random id in the header
`X-Jonaki-Device`; the Worker allows `DAILY_CALL_LIMIT` calls per id per UTC
day (60, the same number as `FreeTierUsage.DAILY_CALL_LIMIT` in the app) and
forwards the rest to Workers AI with the model set by `MODEL`.

Behaviour:

- `POST /v1/chat/completions` needs the device header (else 400), refuses a
  body over 200 KB (413), answers 429 past the daily limit, replaces the model
  with `MODEL`, caps `max_tokens` at 2048 and streams the upstream answer back.
  A non-2xx upstream answer becomes `{"error":{"message","type":"upstream_error"}}`
  with the same status; 401 and 403 say the server token is missing.
- `GET /v1/limit` needs the device header and returns `{"limit", "used",
  "resets_at"}` (the `DAILY_CALL_LIMIT` variable, today's count for the phone,
  the next UTC midnight) without counting a call. Change the limit in
  `wrangler.toml` and redeploy; the app reads it once a day.
- `GET /v1/models` lists one model, `free`.
- Anything else is 404.

## Deploy

1. `npm install`
2. `npx wrangler login`
3. `npx wrangler kv namespace create LIMITS`, then put the printed id in
   `wrangler.toml` in place of `REPLACE_WITH_KV_NAMESPACE_ID`.
4. Set the two secrets. They are never written to a file:
   - `npx wrangler secret put ACCOUNT_ID` (your Cloudflare account id)
   - `npx wrangler secret put CF_AI_TOKEN` (an API token with the Workers AI permission)
5. `npx wrangler deploy`

The app expects the address `https://jonaki-free.shourovrm.workers.dev/v1`.

## Check

```
curl -sS https://jonaki-free.shourovrm.workers.dev/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'X-Jonaki-Device: curl-check' \
  -d '{"model":"free","messages":[{"role":"user","content":"Say hi."}]}'
```

Without the `X-Jonaki-Device` header the answer is HTTP 400.
