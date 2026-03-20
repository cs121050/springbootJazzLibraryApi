#!/usr/bin/env python3
"""
Generate INSERT statements for the Artist table including thumbnail data.
Reads original INSERT lines, fetches image metadata, and outputs new INSERTs
with all original columns + thumbnail_url, image_author, image_license, image_source_url.

Integer columns (artist_id, discogs_id, instrument_id) are output without quotes,
exactly as in the original input. NULL values are output as NULL.
"""

import re
import requests
import time
import sys
import os
from urllib.parse import unquote
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

# ----------------------------------------------------------------------
# Configuration – UPDATE THESE WITH YOUR INFO
# ----------------------------------------------------------------------
API_URL = "https://en.wikipedia.org/w/api.php"
REQUEST_DELAY = 0.5          # seconds between requests
THUMBNAIL_WIDTH = 500
APP_USER_AGENT = 'JazzLibrary/1.0 (https://yourapp.com; your-email@example.com) Python-requests'
DEBUG = True

def log(msg):
    if DEBUG:
        print(f"[DEBUG] {msg}")

# ----------------------------------------------------------------------
# Helper functions
# ----------------------------------------------------------------------
def clean_text(text):
    """Remove newlines, carriage returns, and collapse multiple spaces."""
    if text is None:
        return None
    # Replace \r and \n with a space
    text = re.sub(r'[\r\n]+', ' ', text)
    # Collapse multiple spaces into one
    text = re.sub(r' +', ' ', text)
    # Strip leading/trailing spaces
    return text.strip()

def extract_page_title(wikipedia_url):
    url = wikipedia_url.rstrip('/')
    if '/wiki/' not in url:
        raise ValueError(f"Invalid Wikipedia URL: {wikipedia_url}")
    title = url.split('/wiki/')[-1]
    return unquote(title)

def fetch_wikipedia_image_data(page_title):
    headers = {'User-Agent': APP_USER_AGENT}
    params = {
        "action": "query",
        "prop": "pageimages|imageinfo",
        "titles": page_title,
        "pithumbsize": THUMBNAIL_WIDTH,
        "pilimit": 1,
        "iiprop": "url|user|extmetadata",
        "format": "json",
        "formatversion": 2
    }

    session = requests.Session()
    retries = Retry(total=3, backoff_factor=1, status_forcelist=[429, 500, 502, 503, 504])
    session.mount('https://', HTTPAdapter(max_retries=retries))

    try:
        log(f"  Fetching page data for {page_title}")
        resp = session.get(API_URL, params=params, headers=headers, timeout=15)
        resp.raise_for_status()
        data = resp.json()

        pages = data.get("query", {}).get("pages", [])
        if not pages:
            return None

        page = pages[0]
        thumbnail = page.get("thumbnail")
        if not thumbnail:
            return None

        thumbnail_url = thumbnail.get("source")
        page_image = page.get("pageimage")
        if not page_image:
            return {
                "thumbnail_url": thumbnail_url,
                "author": None,
                "license": None,
                "source_url": None
            }

        log(f"  Fetching image metadata for File:{page_image}")
        file_params = {
            "action": "query",
            "titles": f"File:{page_image}",
            "prop": "imageinfo",
            "iiprop": "url|user|extmetadata",
            "format": "json",
            "formatversion": 2
        }
        file_resp = session.get(API_URL, params=file_params, headers=headers, timeout=15)
        file_resp.raise_for_status()
        file_data = file_resp.json()

        file_pages = file_data.get("query", {}).get("pages", [])
        if not file_pages:
            return {
                "thumbnail_url": thumbnail_url,
                "author": None,
                "license": None,
                "source_url": None
            }

        file_page = file_pages[0]
        imageinfo = file_page.get("imageinfo", [])
        if not imageinfo:
            return {
                "thumbnail_url": thumbnail_url,
                "author": None,
                "license": None,
                "source_url": None
            }

        info = imageinfo[0]
        extmetadata = info.get("extmetadata", {})
        author = extmetadata.get("Artist", {}).get("value")
        license_short = extmetadata.get("LicenseShortName", {}).get("value")
        source_url = info.get("descriptionurl")

        if author:
            # Remove HTML tags
            author = re.sub(r"<[^>]+>", "", author).strip()

        # Clean the text fields to remove newlines etc.
        author = clean_text(author)
        license_short = clean_text(license_short)
        source_url = clean_text(source_url)

        return {
            "thumbnail_url": thumbnail_url,
            "author": author,
            "license": license_short,
            "source_url": source_url
        }

    except Exception as e:
        print(f"  Error for {page_title}: {e}", file=sys.stderr)
        return None

def parse_insert_statements(sql_content):
    """
    Parse each INSERT statement and extract all column values.
    Returns a list of dictionaries, each with keys: artist_id, spotify_playlist_id,
    artist_name, artist_surname, musicbrainz_uuid, discogs_id, instrument_id, wikipedia_url.
    """
    # Non‑greedy match: stop at the first ');' after VALUES
    pattern = re.compile(
        r"INSERT\s+INTO\s+Artist\s*\([^)]+\)\s*VALUES\s*\((.*?)\);",
        re.IGNORECASE | re.DOTALL
    )
    artists = []
    for match in pattern.finditer(sql_content):
        values_str = match.group(1).strip()
        # Split by comma – works because there are no commas inside quoted strings in your data.
        raw_values = [v.strip() for v in values_str.split(',')]
        if len(raw_values) < 8:
            log(f"  Skipping malformed line: {values_str[:100]}...")
            continue

        # Clean each value: remove surrounding quotes and handle NULL
        def clean(val):
            val = val.strip()
            if val.startswith("'") and val.endswith("'"):
                val = val[1:-1]
            elif val.startswith('"') and val.endswith('"'):
                val = val[1:-1]
            if val.upper() == 'NULL':
                return None
            return val

        artist = {
            "artist_id": clean(raw_values[0]),
            "spotify_playlist_id": clean(raw_values[1]),
            "artist_name": clean(raw_values[2]),
            "artist_surname": clean(raw_values[3]),
            "musicbrainz_uuid": clean(raw_values[4]),
            "discogs_id": clean(raw_values[5]),
            "instrument_id": clean(raw_values[6]),
            "wikipedia_url": clean(raw_values[7]),
        }
        # Only include if wikipedia_url is not None and not empty
        if artist["wikipedia_url"]:
            artists.append(artist)
    return artists

def format_value(val, is_numeric=False):
    """
    Return a string suitable for inclusion in an SQL INSERT statement.
    - If val is None, returns 'NULL'.
    - If is_numeric is True, returns the value as a string (no quotes).
    - Otherwise, returns the value quoted and with single quotes escaped.
    """
    if val is None:
        return 'NULL'
    if is_numeric:
        return str(val)
    escaped = str(val).replace("'", "''")
    return f"'{escaped}'"

# ----------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------
def main():
    if len(sys.argv) < 2:
        print("Usage: python enrich_inserts.py <input.sql> [output.sql]")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else "artists_with_thumbs.sql"

    if not os.path.isfile(input_file):
        print(f"Error: File '{input_file}' not found.")
        sys.exit(1)

    with open(input_file, 'r', encoding='utf-8') as f:
        sql_content = f.read()

    artists = parse_insert_statements(sql_content)
    print(f"Found {len(artists)} artists with Wikipedia URLs.")

    column_list = (
        "artist_id, spotify_playlist_id, artist_name, artist_surname, "
        "musicbrainz_uuid, discogs_id, instrument_id, wikipedia_url, "
        "thumbnail_url, image_author, image_license, image_source_url"
    )

    insert_statements = []
    for i, artist in enumerate(artists, 1):
        print(f"\nProcessing {i}/{len(artists)}: artist_id={artist['artist_id']} - {artist['wikipedia_url']}")

        # Fetch image data
        try:
            page_title = extract_page_title(artist['wikipedia_url'])
        except ValueError as e:
            print(f"  Skipping: {e}")
            image_data = None
        else:
            image_data = fetch_wikipedia_image_data(page_title)

        if image_data and image_data.get("thumbnail_url"):
            thumb = image_data['thumbnail_url']
            author = image_data.get('author')
            license_txt = image_data.get('license')
            source = image_data.get('source_url')
        else:
            thumb = None
            author = None
            license_txt = None
            source = None

        # Build list of (value, is_numeric) for each column in order
        value_tuples = [
            (artist['artist_id'], True),                # artist_id
            (artist['spotify_playlist_id'], False),     # spotify_playlist_id
            (artist['artist_name'], False),             # artist_name
            (artist['artist_surname'], False),          # artist_surname
            (artist['musicbrainz_uuid'], False),        # musicbrainz_uuid
            (artist['discogs_id'], True),               # discogs_id
            (artist['instrument_id'], True),            # instrument_id
            (artist['wikipedia_url'], False),           # wikipedia_url
            (thumb, False),                              # thumbnail_url
            (author, False),                             # image_author
            (license_txt, False),                        # image_license
            (source, False),                              # image_source_url
        ]

        formatted_values = [format_value(val, numeric) for val, numeric in value_tuples]

        insert_sql = f"INSERT INTO Artist ({column_list}) VALUES ({', '.join(formatted_values)});"
        insert_statements.append(insert_sql)

        time.sleep(REQUEST_DELAY)

    # Write output file
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("-- INSERT statements for Artist table with thumbnail data\n")
        f.write("-- Generated by enrich_inserts.py\n\n")
        for stmt in insert_statements:
            f.write(stmt + "\n")

    print(f"\nDone. {len(insert_statements)} INSERT statements written to {output_file}")

if __name__ == "__main__":
    main()