#!/usr/bin/env python3
"""
process_artist_inserts.py

Reads an SQL file with INSERT statements for an Artist table,
extracts the Wikipedia URL for each artist, fetches intro and matching
sections from Wikipedia, and adds a new JSON column 'wikipedia_data'
with the retrieved content.

Writes output incrementally for real-time progress.
"""

import argparse
import json
import re
import time
import urllib.parse
import threading
import html
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Dict, List, Optional, Tuple, Any

import requests

# ------------------------------------------------------------
#  Rate limiter
# ------------------------------------------------------------
class RateLimiter:
    def __init__(self, delay: float):
        self.delay = delay
        self.lock = threading.Lock()
        self.last_time = 0.0

    def wait(self):
        with self.lock:
            now = time.time()
            elapsed = now - self.last_time
            if elapsed < self.delay:
                time.sleep(self.delay - elapsed)
            self.last_time = time.time()

# ------------------------------------------------------------
#  Wikipedia API helpers
# ------------------------------------------------------------

HEADERS = {
    "User-Agent": "JazzLibraryApp/1.0 (https://example.com; contact@example.com) PythonRequests"
}
def clean_media_templates(text: str) -> str:
    """Remove Wikipedia media-related garbage (audio samples, external media links)."""
    # Remove common media notes
    text = re.sub(r'Problems playing this file\? See media help\.', '', text)
    # Remove lines starting with "Sample from " (audio sample captions)
    text = re.sub(r'(?m)^Sample from .*$', '', text)
    # Remove the whole "External media" block (often followed by a blank line)
    lines = text.splitlines()
    cleaned_lines = []
    skip = False
    for line in lines:
        if line.startswith('External media'):
            skip = True
            continue
        if skip and line.strip() == '':
            skip = False
            continue
        if not skip:
            cleaned_lines.append(line)
    text = '\n'.join(cleaned_lines)
    return text.strip()

def clean_wikipedia_text(raw_text: str, section_title: Optional[str] = None) -> Optional[str]:
    """Clean Wikipedia text: remove "edit" lines, citation markers, CSS, etc."""
    lines = raw_text.splitlines()

    # Remove everything up to and including the first "edit" line
    for i, line in enumerate(lines):
        if line.strip() == "edit":
            lines = lines[i + 1:]
            break

    # Remove any subsequent "edit" line and everything after it
    for i, line in enumerate(lines):
        if line.strip() == "edit":
            lines = lines[:i]
            break

    cleaned = []
    skip_css_block = False
    for line in lines:
        line = line.strip()
        if not line:
            continue

        # --- Skip CSS / MediaWiki markup ---
        # Lines starting with .mw- or @media indicate CSS/JS blocks
        if re.match(r'^\.mw-', line) or re.match(r'^@media', line):
            skip_css_block = True
            continue
        # If we are inside a CSS block, skip until we see a closing brace at line start
        if skip_css_block:
            if line.startswith('}'):
                skip_css_block = False
            continue

        # Remove page references like : 281 (thin space)
        # The thin space is Unicode \u200A
        line = re.sub(r':\u200A\d+(\u200A)?', '', line)

        # Remove citations like [1], [2]
        line = re.sub(r'\[[^\]]*\]', '', line)

        # Skip lines that start with "obj" (case‑insensitive)
        if line.lower().startswith("obj"):
            continue

        # Stop at footnote markers (lines starting with '^')
        if line.startswith('^'):
            break

        cleaned.append(line)

    # Remove the section title if it appears as the first line
    if section_title and cleaned:
        first_line = cleaned[0].strip()
        if first_line.lower() == section_title.lower():
            cleaned.pop(0)

    # Remove lines shorter than 40 characters
    cleaned = [line for line in cleaned if len(line) >= 40]

    result = "\n".join(cleaned).strip()
    return result if len(result) >= 40 else None


def fetch_wikipedia_intro(page_title: str, rate_limiter: RateLimiter) -> Optional[str]:
    rate_limiter.wait()
    url = "https://en.wikipedia.org/w/api.php"
    params = {
        "action": "query",
        "prop": "extracts",
        "exintro": True,
        "explaintext": True,
        "titles": page_title,
        "format": "json"
    }
    try:
        resp = requests.get(url, params=params, headers=HEADERS, timeout=10)
        resp.raise_for_status()
        data = resp.json()
        pages = data.get("query", {}).get("pages", {})
        for page_info in pages.values():
            extract = page_info.get("extract", "")
            if extract:
                cleaned = clean_wikipedia_text(extract)  # No section title for intro
                if cleaned:
                    return cleaned
        return None
    except Exception as e:
        print(f"Error fetching intro for {page_title}: {e}")
        return None


def fetch_sections(page_title: str, rate_limiter: RateLimiter) -> List[Tuple[str, str]]:
    rate_limiter.wait()
    url = "https://en.wikipedia.org/w/api.php"
    params = {
        "action": "parse",
        "page": page_title,
        "prop": "sections",
        "format": "json"
    }
    try:
        resp = requests.get(url, params=params, headers=HEADERS, timeout=10)
        resp.raise_for_status()
        data = resp.json()
        sections = data.get("parse", {}).get("sections", [])
        return [(s["index"], s["line"]) for s in sections]
    except Exception as e:
        print(f"Error fetching sections for {page_title}: {e}")
        return []


def fetch_section_content(page_title: str, section_index: str, section_title: str, rate_limiter: RateLimiter) -> Tuple[str, Optional[str]]:
    rate_limiter.wait()
    url = "https://en.wikipedia.org/w/api.php"
    params = {
        "action": "parse",
        "page": page_title,
        "prop": "text",
        "section": section_index,
        "format": "json"
    }
    try:
        resp = requests.get(url, params=params, headers=HEADERS, timeout=10)
        resp.raise_for_status()
        data = resp.json()
        html_text = data.get("parse", {}).get("text", {}).get("*", "")
        if not html_text:
            return section_title, None

        # Remove <style> and <script> tags and their content
        html_text = re.sub(r'<style[^>]*>.*?</style>', '', html_text, flags=re.DOTALL)
        html_text = re.sub(r'<script[^>]*>.*?</script>', '', html_text, flags=re.DOTALL)

        # Strip HTML tags
        text = re.sub(r'<[^>]+>', '', html_text)
        # Decode HTML entities
        text = html.unescape(text)

        # Remove media template garbage
        text = clean_media_templates(text)   # <-- ADD THIS LINE

        cleaned = clean_wikipedia_text(text, section_title)
        return section_title, cleaned
    except Exception as e:
        print(f"Error fetching section {section_index} for {page_title}: {e}")
        return section_title, None

def fetch_artist_wikipedia_data(wikipedia_url: str, rate_limiter: RateLimiter, max_workers: int = 3) -> Optional[Dict[str, str]]:
    """
    Fetch intro and relevant sections from Wikipedia, then reorder them:
    - Intro first
    - Then all other sections that are NOT about death, legacy, or artistry
    - Then sections about death, legacy, artistry (in original order)
    """
    if not wikipedia_url or wikipedia_url == "NULL":
        return None

    match = re.search(r'/wiki/([^?#]+)', wikipedia_url)
    if not match:
        print(f"Could not extract title from URL: {wikipedia_url}")
        return None
    title_with_underscores = match.group(1)
    page_title = urllib.parse.unquote(title_with_underscores).replace('_', ' ')

    print(f"Processing: {page_title}")

    # Fetch section list
    sections = fetch_sections(page_title, rate_limiter)
    if not sections:
        print(f"  No sections found for {page_title}")
        intro = fetch_wikipedia_intro(page_title, rate_limiter)
        if intro:
            return {"Intro": intro}
        return None

    # Keywords to match – now includes "years" and also death/legacy/artistry
    general_keywords = {"life", "career", "biography", "music", "years"}
    special_keywords = {"death", "legacy", "artistry"}

    matching_sections = []
    for idx, title in sections:
        title_lower = title.lower()
        # Keep sections that match general keywords OR special keywords
        if any(kw in title_lower for kw in general_keywords) or any(kw in title_lower for kw in special_keywords):
            matching_sections.append((idx, title))

    # Helper to clean section titles
    def clean_title(title: str) -> str:
        # Remove HTML tags like <i>, <b>, etc.
        title = re.sub(r'<[^>]+>', '', title)
        # Decode HTML entities like &amp; → &
        title = html.unescape(title)
        return title.strip()

    # Temporary storage for all fetched content
    temp_data = {}

    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = []
        # Submit intro fetch
        futures.append(executor.submit(fetch_wikipedia_intro, page_title, rate_limiter))
        # Submit section fetches
        for idx, title in matching_sections:
            futures.append(executor.submit(fetch_section_content, page_title, idx, title, rate_limiter))

        for future in as_completed(futures):
            try:
                res = future.result()
                if isinstance(res, str):  # intro result
                    if res:
                        temp_data["Intro"] = res
                else:  # section result
                    title, content = res
                    if content:
                        cleaned_title = clean_title(title)
                        temp_data[cleaned_title] = content
            except Exception as e:
                print(f"  Error in concurrent fetch: {e}")

    if not temp_data:
        return None

    # Categorize sections based on titles (using the original order from matching_sections)
    regular_sections = []
    special_sections = []  # death, legacy, artistry

    for idx, raw_title in matching_sections:
        cleaned = clean_title(raw_title)
        if cleaned not in temp_data:
            continue  # content not fetched, skip
        title_lower = cleaned.lower()
        # Check if it's a special section
        if any(kw in title_lower for kw in special_keywords):
            special_sections.append(cleaned)
        else:
            regular_sections.append(cleaned)

    # Build final dict in desired order
    result = {}
    # Intro first (if present)
    if "Intro" in temp_data:
        result["Intro"] = temp_data["Intro"]
    # Then regular sections
    for title in regular_sections:
        result[title] = temp_data[title]
    # Then death, legacy, artistry sections (in the order they appeared in the original list)
    for title in special_sections:
        result[title] = temp_data[title]

    return result

# ------------------------------------------------------------
#  SQL parsing / rewriting helpers (unchanged)
# ------------------------------------------------------------

def parse_sql_values(values_str: str, debug: bool = False) -> List[Any]:
    if debug:
        print(f"[DEBUG] Parsing values: {repr(values_str)}")

    values_str = values_str.strip()
    tokens = []
    current = []
    in_quote = False
    i = 0
    n = len(values_str)

    while i < n:
        ch = values_str[i]

        if in_quote:
            if ch == "'":
                if i + 1 < n and values_str[i+1] == "'":
                    current.append("'")
                    i += 1
                else:
                    in_quote = False
                current.append(ch)
            else:
                current.append(ch)
        else:
            if ch == "'":
                in_quote = True
                current.append(ch)
            elif ch == ',':
                token = ''.join(current).strip()
                if token:
                    tokens.append(token)
                current = []
            elif ch == '(':
                pass
            elif ch == ')':
                if current:
                    token = ''.join(current).strip()
                    if token:
                        tokens.append(token)
                break
            else:
                current.append(ch)
        i += 1

    if current:
        token = ''.join(current).strip()
        if token:
            tokens.append(token)

    if debug:
        print(f"[DEBUG] Extracted tokens: {tokens}")

    values = []
    for token in tokens:
        values.append(parse_sql_value_token(token))

    if debug:
        print(f"[DEBUG] Parsed values: {values}")

    return values


def parse_sql_value_token(token: str) -> Any:
    token = token.strip()
    if token.upper() == 'NULL':
        return None
    if token.startswith("'") and token.endswith("'"):
        return token[1:-1].replace("''", "'")
    try:
        return int(token)
    except ValueError:
        return token


def build_insert_statement(table_name: str, columns: List[str], values: List[Any]) -> str:
    escaped_values = []
    for v in values:
        if v is None:
            escaped_values.append("NULL")
        elif isinstance(v, str):
            escaped = v.replace("'", "''")
            escaped_values.append(f"'{escaped}'")
        else:
            escaped_values.append(str(v))
    return f"INSERT INTO {table_name} ({', '.join(columns)}) VALUES ({', '.join(escaped_values)});"


def process_insert_line(line: str, new_column_name: str, new_column_value: Optional[Dict[str, str]], debug: bool = False) -> str:
    match = re.match(r"INSERT INTO (\w+)\s*\(([^)]+)\)\s*VALUES\s*\((.*)\);", line.strip())
    if not match:
        raise ValueError(f"Could not parse INSERT line: {line}")

    table_name = match.group(1)
    columns_str = match.group(2)
    values_str = match.group(3)

    columns = [col.strip() for col in columns_str.split(',')]
    values = parse_sql_values(values_str, debug)

    if debug:
        print(f"[DEBUG] Columns: {columns}")
        print(f"[DEBUG] Values count: {len(values)}")
        print(f"[DEBUG] Expected count: {len(columns)}")

    if len(columns) != len(values):
        raise ValueError(f"Column count mismatch: columns={len(columns)}, values={len(values)}\n"
                         f"Columns: {columns}\nValues: {values}")

    columns.append(new_column_name)
    if new_column_value is not None:
        json_str = json.dumps(new_column_value, ensure_ascii=False, separators=(',', ':'))
        values.append(json_str)
    else:
        values.append(None)

    return build_insert_statement(table_name, columns, values)


# ------------------------------------------------------------
#  Main script with incremental output
# ------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Add Wikipedia data column to Artist INSERT statements.")
    parser.add_argument("--input", required=True, help="Input SQL file with INSERT statements.")
    parser.add_argument("--output", required=True, help="Output SQL file with modified INSERTs.")
    parser.add_argument("--delay", type=float, default=0.2, help="Minimum seconds between Wikipedia API calls.")
    parser.add_argument("--workers", type=int, default=3, help="Number of concurrent threads for fetching content.")
    parser.add_argument("--debug", action="store_true", help="Enable debug output for SQL parsing.")
    args = parser.parse_args()

    rate_limiter = RateLimiter(args.delay)
    cache = {}

    with open(args.input, 'r', encoding='utf-8') as infile, \
         open(args.output, 'w', encoding='utf-8') as outfile:

        for line in infile:
            line = line.strip()
            if not line:
                outfile.write("\n")
                outfile.flush()
                continue

            if line.upper().startswith("INSERT INTO ARTIST"):
                try:
                    match = re.match(r"INSERT INTO (\w+)\s*\(([^)]+)\)\s*VALUES\s*\((.*)\);", line)
                    if not match:
                        outfile.write(line + "\n")
                        outfile.flush()
                        continue

                    columns = [col.strip() for col in match.group(2).split(',')]
                    values = parse_sql_values(match.group(3), args.debug)

                    try:
                        url_index = columns.index("wikipedia_url")
                    except ValueError:
                        outfile.write(line + "\n")
                        outfile.flush()
                        continue

                    wikipedia_url = values[url_index] if url_index < len(values) else None

                    if wikipedia_url:
                        if wikipedia_url in cache:
                            wiki_data = cache[wikipedia_url]
                        else:
                            wiki_data = fetch_artist_wikipedia_data(wikipedia_url, rate_limiter, args.workers)
                            cache[wikipedia_url] = wiki_data
                    else:
                        wiki_data = None

                    new_line = process_insert_line(line, "wikipedia_data", wiki_data, args.debug)
                    outfile.write(new_line + "\n")
                    outfile.flush()

                except Exception as e:
                    print(f"Error processing line: {line}\n{e}")
                    outfile.write(line + "\n")
                    outfile.flush()
            else:
                outfile.write(line + "\n")
                outfile.flush()

    print(f"Processed {len(cache)} unique artists. Output written to {args.output}")


if __name__ == "__main__":
    main()