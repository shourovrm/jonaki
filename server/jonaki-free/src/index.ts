// Jonaki Free: a small OpenAI-compatible front for Workers AI, limited per phone per day.

interface Env {
  LIMITS: KVNamespace;
  DAILY_CALL_LIMIT: string;
  MODEL: string;
  ACCOUNT_ID: string;
  CF_AI_TOKEN: string;
}

const MAX_BODY_BYTES = 200 * 1024;
const MAX_TOKENS = 2048;
const COUNTER_TTL_SECONDS = 2 * 24 * 60 * 60;

function jsonError(status: number, message: string, type: string): Response {
  return Response.json({ error: { message, type } }, { status });
}

/** Adds one call to the phone's count for today (UTC) and returns the new count. */
async function countCall(env: Env, device: string): Promise<number> {
  const day = new Date().toISOString().slice(0, 10);
  const counterKey = `${device}:${day}`;
  const calls = Number((await env.LIMITS.get(counterKey)) ?? "0") + 1;
  await env.LIMITS.put(counterKey, String(calls), { expirationTtl: COUNTER_TTL_SECONDS });
  return calls;
}

async function chatCompletions(request: Request, env: Env): Promise<Response> {
  const device = request.headers.get("X-Jonaki-Device")?.trim();
  if (!device || device.length > 100) {
    return jsonError(400, "Missing header X-Jonaki-Device.", "invalid_request_error");
  }
  const bodyText = await request.text();
  if (new TextEncoder().encode(bodyText).length > MAX_BODY_BYTES) {
    return jsonError(413, "The request is larger than 200 KB.", "invalid_request_error");
  }
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(bodyText);
  } catch {
    return jsonError(400, "The body is not valid JSON.", "invalid_request_error");
  }

  const limit = Number(env.DAILY_CALL_LIMIT);
  if ((await countCall(env, device)) > limit) {
    return jsonError(
      429,
      `Daily limit reached for this phone (${limit} calls). It resets at midnight UTC. Add your own key in Settings for more.`,
      "rate_limit",
    );
  }

  body.model = env.MODEL;
  const requestedTokens = typeof body.max_tokens === "number" ? body.max_tokens : MAX_TOKENS;
  body.max_tokens = Math.min(requestedTokens, MAX_TOKENS);

  const upstream = await fetch(
    `https://api.cloudflare.com/client/v4/accounts/${env.ACCOUNT_ID}/ai/v1/chat/completions`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${env.CF_AI_TOKEN}`, "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  );
  // The body is passed on as a stream, so server-sent events reach the phone as they arrive.
  return new Response(upstream.body, {
    status: upstream.status,
    headers: { "Content-Type": upstream.headers.get("Content-Type") ?? "application/json" },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);
    if (request.method === "POST" && pathname === "/v1/chat/completions") {
      return chatCompletions(request, env);
    }
    if (request.method === "GET" && pathname === "/v1/models") {
      return Response.json({ object: "list", data: [{ id: "free", object: "model", owned_by: "jonaki" }] });
    }
    return jsonError(404, "Not found.", "invalid_request_error");
  },
};
