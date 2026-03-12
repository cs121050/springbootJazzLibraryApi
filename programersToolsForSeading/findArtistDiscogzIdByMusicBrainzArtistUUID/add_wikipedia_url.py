#!/usr/bin/env python3
"""
Reads a SQL file with INSERT statements for an Artist table,
adds a 'wikipedia_url' column by looking up each artist's MusicBrainz ID.
"""

import re
import time
import json
import requests
from collections import OrderedDict
from urllib.parse import quote

# ----------------------------------------------------------------------
# Configuration
# ----------------------------------------------------------------------
USER_AGENT = "WikipediaArtistEnricher/1.0 (https://github.com/yourusername; for research purposes)"
MB_API = "https://musicbrainz.org/ws/2"
WD_ENTITY_API = "https://www.wikidata.org/wiki/Special:EntityData"
RATE_LIMIT_MB = 1.0      # seconds between MusicBrainz requests
RATE_LIMIT_WD = 2.0      # seconds between Wikidata requests

# Global timestamps for rate limiting
last_mb_request = 0.0
last_wd_request = 0.0

# ----------------------------------------------------------------------
# Rate‑limited API callers
# ----------------------------------------------------------------------
def mb_request(url):
    """Make a rate‑limited request to MusicBrainz."""
    global last_mb_request
    now = time.time()
    elapsed = now - last_mb_request
    if elapsed < RATE_LIMIT_MB:
        time.sleep(RATE_LIMIT_MB - elapsed)
    headers = {'User-Agent': USER_AGENT, 'Accept': 'application/json'}
    resp = requests.get(url, headers=headers)
    last_mb_request = time.time()
    resp.raise_for_status()
    return resp.json()

def wd_request(url):
    """Make a rate‑limited request to Wikidata."""
    global last_wd_request
    now = time.time()
    elapsed = now - last_wd_request
    if elapsed < RATE_LIMIT_WD:
        time.sleep(RATE_LIMIT_WD - elapsed)
    headers = {'User-Agent': USER_AGENT, 'Accept': 'application/json'}
    resp = requests.get(url, headers=headers)
    last_wd_request = time.time()
    if resp.status_code == 404:
        return None   # entity not found
    resp.raise_for_status()
    return resp.json()

# ----------------------------------------------------------------------
# Core lookup functions
# ----------------------------------------------------------------------
def get_wikidata_id_from_mbid(mbid):
    """
    Fetch artist from MusicBrainz with relations, extract Wikidata ID.
    Returns Wikidata ID (e.g., 'Q12345') or None.
    """
    url = f"{MB_API}/artist/{mbid}?fmt=json&inc=url-rels"
    data = mb_request(url)
    if 'relations' not in data:
        return None
    for rel in data['relations']:
        if rel.get('type') == 'wikidata':
            resource = rel.get('url', {}).get('resource')
            if resource:
                match = re.search(r'/(Q\d+)$', resource)
                if match:
                    return match.group(1)
    return None

def get_wikipedia_title_from_wikidata(wd_id):
    """
    Fetch Wikidata entity, return English Wikipedia page title (if any).
    """
    url = f"{WD_ENTITY_API}/{wd_id}.json"
    data = wd_request(url)
    if not data:
        return None
    entities = data.get('entities', {})
    entity = entities.get(wd_id)
    if not entity:
        return None
    sitelinks = entity.get('sitelinks', {})
    enwiki = sitelinks.get('enwiki')
    if enwiki:
        return enwiki.get('title')
    return None

def get_wikipedia_url_from_mbid(mbid):
    """
    High‑level function: given an MBID, return the Wikipedia URL or None.
    """
    wd_id = get_wikidata_id_from_mbid(mbid)
    if not wd_id:
        return None
    title = get_wikipedia_title_from_wikidata(wd_id)
    if not title:
        return None
    # Convert spaces to underscores for the URL
    safe_title = title.replace(' ', '_')
    return f"https://en.wikipedia.org/wiki/{safe_title}"

# ----------------------------------------------------------------------
# SQL line parsing and generation
# ----------------------------------------------------------------------
def parse_insert_line(line):
    """
    Extract values from a line like:
    INSERT [dbo].[Artist] (...) VALUES ('val1','val2',...,valN)
    Returns a tuple of values as strings (with quotes stripped).
    """
    # Match the VALUES part: VALUES ( ... )
    pattern = r"VALUES\s*\((.*)\)"
    match = re.search(pattern, line, re.IGNORECASE)
    if not match:
        return None
    values_str = match.group(1).strip()
    # Split by commas that are not inside quotes
    # This is a simple approach: we'll use a regex that respects quotes
    # A more robust way: use the `csv` module, but here we'll do a simple split.
    parts = []
    current = []
    in_quotes = False
    for ch in values_str:
        if ch == "'" and not in_quotes:
            in_quotes = True
            current.append(ch)
        elif ch == "'" and in_quotes:
            in_quotes = False
            current.append(ch)
        elif ch == ',' and not in_quotes:
            parts.append(''.join(current).strip())
            current = []
        else:
            current.append(ch)
    if current:
        parts.append(''.join(current).strip())
    # Clean each part: strip surrounding quotes if present
    cleaned = []
    for p in parts:
        p = p.strip()
        if p.startswith("'") and p.endswith("'"):
            p = p[1:-1]   # remove outer quotes
        cleaned.append(p)
    return tuple(cleaned)

def build_insert_line(values, with_url=True):
    """
    Build an INSERT line with the given values.
    values should be a tuple of 6 or 7 elements.
    If with_url is True, assume 7 elements (including wikipedia_url).
    """
    # Quote string values, leave numbers as is
    quoted = []
    for v in values:
        if isinstance(v, str):
            # Escape single quotes inside the string (replace ' with '')
            v = v.replace("'", "''")
            quoted.append(f"'{v}'")
        else:
            quoted.append(str(v))
    cols = ['[spotify_playlist_id]', '[artist_name]', '[artist_surname]',
            '[musicbrainz_uuid]', '[discogs_id]', '[instrument_id]']
    if with_url:
        cols.append('[wikipedia_url]')
    cols_str = ', '.join(cols)
    values_str = ', '.join(quoted)
    return f"INSERT [dbo].[Artist] ({cols_str}) VALUES ({values_str})"

# ----------------------------------------------------------------------
# Main processing
# ----------------------------------------------------------------------
def main(input_file, output_file):
    # Read all lines from input file
    with open(input_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    # First pass: collect unique MBIDs to avoid duplicate API calls
    unique_mbids = OrderedDict()  # preserve order? not required but nice
    for line in lines:
        line = line.strip()
        if not line or not line.upper().startswith('INSERT'):
            continue
        values = parse_insert_line(line)
        if values and len(values) >= 6:
            mbid = values[3]   # musicbrainz_uuid is the 4th field
            if mbid and mbid != 'NULL':
                unique_mbids[mbid] = None   # placeholder

    print(f"Found {len(unique_mbids)} unique MBIDs. Fetching Wikipedia URLs...")

    # Build cache: MBID -> Wikipedia URL (or None)
    cache = {}
    for i, mbid in enumerate(unique_mbids.keys(), 1):
        print(f"[{i}/{len(unique_mbids)}] Processing MBID: {mbid}")
        try:
            url = get_wikipedia_url_from_mbid(mbid)
            cache[mbid] = url
            if url:
                print(f"  -> {url}")
            else:
                print("  -> No Wikipedia page found.")
        except Exception as e:
            print(f"  -> Error: {e}")
            cache[mbid] = None

    # Second pass: generate output lines
    with open(output_file, 'w', encoding='utf-8') as out_f:
        for line in lines:
            line = line.strip()
            if not line or not line.upper().startswith('INSERT'):
                # Copy non‑INSERT lines as is (e.g., comments, blank lines)
                out_f.write(line + '\n')
                continue
            values = parse_insert_line(line)
            if values and len(values) >= 6:
                mbid = values[3]
                wiki_url = cache.get(mbid)
                # New values: original 6 + wiki_url (which may be None -> NULL in SQL)
                new_values = list(values[:6]) + [wiki_url]
                # Convert None to SQL NULL
                new_values = [v if v is not None else 'NULL' for v in new_values]
                new_line = build_insert_line(new_values, with_url=True)
                out_f.write(new_line + '\n')
            else:
                # If parsing failed, write the original line (should not happen)
                out_f.write(line + '\n')

    print(f"\nDone! Output written to: {output_file}")

if __name__ == '__main__':
    import sys
    if len(sys.argv) != 3:
        print("Usage: python add_wikipedia_url.py <input_sql_file> <output_sql_file>")
        sys.exit(1)
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    main(input_file, output_file)