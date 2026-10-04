"""Fetches a public page and extracts its readable text, for the second Jev guard run."""

import sys

import requests
from lxml import html

HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0",
    "Accept-Language": "en,bn;q=0.8",
}

REMOVED_TAGS = "//script|//style|//noscript|//nav|//header|//footer|//aside|//form|//svg|//iframe"
TEXT_TAGS = ("p", "h1", "h2", "h3", "h4", "li", "pre", "td", "blockquote", "dd", "dt")


def extract_text(raw_html: bytes) -> str:
    tree = html.fromstring(raw_html)
    for element in tree.xpath(REMOVED_TAGS):
        element.getparent().remove(element)
    seen = set()
    lines = []
    for element in tree.iter(*TEXT_TAGS):
        line = " ".join(element.text_content().split())
        if line and line not in seen:
            seen.add(line)
            lines.append(line)
    return "\n".join(lines)


def fetch_text(url: str) -> tuple[int, str]:
    response = requests.get(url, headers=HEADERS, timeout=20)
    if response.status_code != 200:
        return response.status_code, ""
    return 200, extract_text(response.content)


if __name__ == "__main__":
    for url in sys.argv[1:]:
        try:
            status, text = fetch_text(url)
            print(status, len(text), url)
        except Exception as error:
            print("ERR", type(error).__name__, url)
