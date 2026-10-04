"""Spike for fact keywords: does one cheap model follow the extraction prompt's keyword rule?

Sends 20 short facts (10 Bangla, 5 English, 5 mixed) through the memory extraction prompt of the
app (read from MemoryExtraction.kt, so the spike tests the real wording) and records the keywords
the model gives, whether a Bangla fact got a usable English keyword and an English fact a usable
Bangla one, and the cost. The key is read from secrets.properties in the repository root and is
never printed. The run stops when the cost passes COST_CAP_USD.
"""

import json
import re
import time
import urllib.error
import urllib.request
from pathlib import Path

ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
MODEL = "google/gemini-2.5-flash-lite"
COST_CAP_USD = 0.05
MAX_ATTEMPTS = 3  # one call and at most two retries
HERE = Path(__file__).parent
SECRETS = HERE.parent.parent / "secrets.properties"
EXTRACTION_SOURCE = HERE.parent.parent / "app/src/main/java/app/jonaki/memory/MemoryExtraction.kt"

# Each item: (kind, what the user wrote). The user writes the fact as a plain statement.
FACTS = [
    ("bangla", "আমার থিসিস জমা দেওয়ার শেষ তারিখ ১২ ডিসেম্বর।"),
    ("bangla", "আমি সিলেটে থাকি।"),
    ("bangla", "আমার সুপারভাইজার ড. রহমান, তিনি এপিএ স্টাইল পছন্দ করেন।"),
    ("bangla", "আমি দুধ ছাড়া লাল চা খাই।"),
    ("bangla", "আমার ল্যাপটপে আর্চ লিনাক্স চলে।"),
    ("bangla", "প্রতি শুক্রবার সকালে আমি বাজারে যাই।"),
    ("bangla", "আমার মেয়ের জন্মদিন ৫ মার্চ।"),
    ("bangla", "আমি ঢাকা বিশ্ববিদ্যালয়ে পদার্থবিজ্ঞান পড়েছি।"),
    ("bangla", "আমার বাজেট মাসে বিশ হাজার টাকা।"),
    ("bangla", "আমি উত্তর দিতে বাংলা ভাষা পছন্দ করি।"),
    ("english", "My thesis is due on 12 December."),
    ("english", "I live in Sylhet and work as a civil engineer."),
    ("english", "I prefer answers in short bullet points."),
    ("english", "My daughter's birthday is on 5 March."),
    ("english", "I use Kotlin and Jetpack Compose for my Android apps."),
    ("mixed", "আমার thesis supervisor হলেন Dr. Rahman।"),
    ("mixed", "আমি প্রতিদিন সকালে gym এ যাই।"),
    ("mixed", "Amar exam 3 January theke shuru."),
    ("mixed", "আমার laptop এর battery দুই ঘণ্টা চলে।"),
    ("mixed", "My বন্ধু Karim থাকে Chattogram এ।"),
]

BANGLA_LETTER = re.compile(r"[ঀ-৿]")
LATIN_WORD = re.compile(r"[A-Za-z]{3,}")


def read_key():
    for line in SECRETS.read_text().splitlines():
        if line.startswith("OPENROUTER_API_KEY="):
            return line.split("=", 1)[1].strip()
    return None


def read_system_prompt():
    """The extraction prompt as the app builds it with global facts allowed and no project."""
    source = EXTRACTION_SOURCE.read_text()
    base = re.search(r'const val SYSTEM_PROMPT = """(.*?)"""', source, re.S).group(1)
    global_rule = re.search(r'private const val GLOBAL_RULE = """(.*?)"""', source, re.S).group(1)
    return base + "\n" + global_rule


def user_prompt(statement):
    return (
        "Global facts (read only):\n(none)\n\n"
        "Thread facts:\n(none)\n\n"
        "New messages:\n[m1] User: [Monday 5 October 2026, 10:00 Asia/Dhaka]\n" + statement
    )


def ask(key, system_prompt, statement):
    body = json.dumps(
        {
            "model": MODEL,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt(statement)},
            ],
            "max_tokens": 600,
            "temperature": 0,
            "usage": {"include": True},
        }
    ).encode()
    last_error = None
    for _attempt in range(MAX_ATTEMPTS):
        request = urllib.request.Request(
            ENDPOINT,
            data=body,
            headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read()), None
        except urllib.error.HTTPError as error:
            last_error = f"HTTP {error.code}: {error.read().decode(errors='replace')[:300]}"
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = f"{type(error).__name__}: {error}"
        time.sleep(2)
    return None, last_error


def parse_operations(answer_text):
    start = answer_text.find("{")
    end = answer_text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        return json.loads(answer_text[start : end + 1]).get("operations", [])
    except json.JSONDecodeError:
        return None


def judge(kind, keywords):
    """Heuristic: a Bangla fact needs a Latin word of 3+ letters, an English fact a Bangla letter."""
    has_latin_word = bool(LATIN_WORD.search(keywords))
    has_bangla = bool(BANGLA_LETTER.search(keywords))
    if kind == "bangla":
        return {"usable_english_keyword": has_latin_word}
    if kind == "english":
        return {"usable_bangla_keyword": has_bangla}
    return {"usable_english_keyword": has_latin_word, "usable_bangla_keyword": has_bangla}


def main():
    key = read_key()
    if key is None:
        (HERE / "results.md").write_text(
            "# Memory keywords spike\n\nNot run: OPENROUTER_API_KEY is missing from secrets.properties.\n"
        )
        print("key missing, nothing run")
        return
    system_prompt = read_system_prompt()
    records = []
    total_cost = 0.0
    stopped_reason = None
    for index, (kind, statement) in enumerate(FACTS):
        if total_cost > COST_CAP_USD:
            stopped_reason = f"cost passed ${COST_CAP_USD} after {index} facts"
            break
        payload, error = ask(key, system_prompt, statement)
        if payload is None:
            records.append({"kind": kind, "fact": statement, "error": error})
            stopped_reason = f"call failed after {MAX_ATTEMPTS} attempts: {error}"
            break
        cost = payload.get("usage", {}).get("cost") or 0.0
        total_cost += cost
        answer = payload["choices"][0]["message"]["content"] or ""
        operations = parse_operations(answer)
        adds = [op for op in (operations or []) if op.get("op") == "add"]
        keywords = " ".join(op.get("keywords", "") for op in adds if isinstance(op.get("keywords", ""), str)).strip()
        records.append(
            {
                "kind": kind,
                "fact": statement,
                "answer": answer,
                "parsed": operations is not None,
                "adds": len(adds),
                "scopes": [op.get("scope") for op in adds],
                "keywords": keywords,
                "judgement": judge(kind, keywords),
                "cost_usd": cost,
            }
        )
    (HERE / "results.json").write_text(json.dumps(records, ensure_ascii=False, indent=1))
    write_report(records, total_cost, stopped_reason)
    print(f"{len(records)} facts recorded, cost ${total_cost:.6f}")


def write_report(records, total_cost, stopped_reason):
    answered = [record for record in records if "answer" in record]
    bangla = [r for r in answered if r["kind"] == "bangla"]
    english = [r for r in answered if r["kind"] == "english"]
    mixed = [r for r in answered if r["kind"] == "mixed"]
    bangla_ok = sum(r["judgement"]["usable_english_keyword"] for r in bangla)
    english_ok = sum(r["judgement"]["usable_bangla_keyword"] for r in english)
    mixed_english = sum(r["judgement"]["usable_english_keyword"] for r in mixed)
    mixed_bangla = sum(r["judgement"]["usable_bangla_keyword"] for r in mixed)
    unparsed = sum(1 for r in answered if not r["parsed"])
    no_add = sum(1 for r in answered if r["parsed"] and r["adds"] == 0)
    lines = [
        "# Memory keywords spike",
        "",
        f"Model: {MODEL} through OpenRouter, temperature 0. Prompt: the app's extraction prompt (global rule on), "
        "one fact per call, no earlier facts.",
        "",
        "## Numbers",
        "",
        f"- Facts sent: {len(FACTS)}; answered: {len(answered)}; answers that were not readable JSON: {unparsed}; "
        f"answers with no add operation: {no_add}.",
        f"- Bangla facts with a usable English keyword (a Latin word of 3+ letters): {bangla_ok} of {len(bangla)}.",
        f"- English facts with a usable Bangla keyword (a Bangla-script word): {english_ok} of {len(english)}.",
        f"- Mixed facts with an English keyword: {mixed_english} of {len(mixed)}; with a Bangla keyword: "
        f"{mixed_bangla} of {len(mixed)}.",
        f"- Total cost: ${total_cost:.6f} (cap ${COST_CAP_USD}).",
    ]
    if stopped_reason:
        lines.append(f"- Stopped early: {stopped_reason}.")
    lines += [
        "",
        "The check is a script heuristic (script of the keywords), not a judgement of meaning; read the table.",
        "",
        "## Keywords produced",
        "",
        "| Kind | Fact | Keywords | Scope |",
        "| --- | --- | --- | --- |",
    ]
    for record in records:
        if "answer" not in record:
            lines.append(f"| {record['kind']} | {record['fact']} | error: {record.get('error')} | |")
            continue
        scopes = ", ".join(str(scope) for scope in record["scopes"]) or "-"
        lines.append(f"| {record['kind']} | {record['fact']} | {record['keywords'] or '(none)'} | {scopes} |")
    (HERE / "results.md").write_text("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
