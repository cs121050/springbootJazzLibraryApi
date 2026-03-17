#!/usr/bin/env python3
"""
Fetch Discogs releases for artists from a SQL file, enrich with MusicBrainz data,
and output JSON Lines. Preserves artist order from SQL file.
Handles both 7‑column (original) and 8‑column (with artist_id) INSERT formats.
Now also performs a Wikipedia search for each album and adds the URL as a top-level field,
while removing any existing wikipedia_url_search from the MusicBrainz data.
If the album Wikipedia URL matches the artist's Wikipedia URL, the field is set to null.
"""

import argparse
import json
import os
import re
import time
import copy
from urllib.parse import urlencode

import requests

# ----------------------------------------------------------------------
# Configuration
# ----------------------------------------------------------------------
BASE_API_URL = "https://api.discogs.com"
USER_AGENT = "DiscogsDataCollector/1.0 +https://example.com"
MIN_REQUEST_INTERVAL = 2.5          # seconds (25 requests/minute)
MAX_RETRIES = 3
RETRY_BACKOFF = 5                    # seconds (base for exponential backoff)

# Wikipedia API endpoint
WIKIPEDIA_API_URL = "https://en.wikipedia.org/w/api.php"


# ----------------------------------------------------------------------
# Wikipedia search function with proper User-Agent, rate limiting,
# query simplification, and retry logic.
# ----------------------------------------------------------------------
# ----------------------------------------------------------------------
# Wikipedia search function with improved query for better accuracy.
# Uses 'intitle:' and adds 'album' to target album pages.
# ----------------------------------------------------------------------
def search_wikipedia(artist_name, album_title):
    """
    Search Wikipedia for the album's page using a precise query:
    intitle:"album_title" "artist_name" album
    Returns the URL of the first result, or None if no result found.
    """
    # Clean artist and album names (remove punctuation, truncate very long titles)
    clean_artist = re.sub(r'[^\w\s-]', '', artist_name)
    clean_album = re.sub(r'[^\w\s-]', '', album_title)
    if len(clean_album) > 200:
        clean_album = clean_album[:200]

    # Only use the precise intitle query (no fallback)
    query = f'intitle:"{clean_album}" "{clean_artist}" album'
    print(f"      [Wikipedia] Searching for: {query}")

    params = {
        'action': 'query',
        'list': 'search',
        'srsearch': query,
        'format': 'json',
        'srlimit': 1
    }

    headers = {
        'User-Agent': 'JazzLibraryDiscogsEnricher/1.0 (your-email@example.com)'
    }

    url = WIKIPEDIA_API_URL + '?' + urlencode(params)
    print(f"      [Wikipedia] Request URL: {url}")

    time.sleep(0.5)  # polite delay

    try:
        response = requests.get(url, headers=headers, timeout=10)
        print(f"      [Wikipedia] Response status: {response.status_code}")

        if response.status_code != 200:
            print(f"      [Wikipedia] Error: HTTP {response.status_code}")
            return None

        data = response.json()
        print(f"      [Wikipedia] Response data (first 500 chars): {json.dumps(data)[:500]}...")

        search_results = data.get('query', {}).get('search', [])
        if not search_results:
            print("      [Wikipedia] No search results found.")
            return None

        title = search_results[0].get('title')
        if not title:
            print("      [Wikipedia] First result has no title.")
            return None

        wiki_url = f"https://en.wikipedia.org/wiki/{title.replace(' ', '_')}"
        print(f"      [Wikipedia] Found URL: {wiki_url}")
        return wiki_url

    except Exception as e:
        print(f"      [Wikipedia] Exception during search: {e}")
        return None

# ----------------------------------------------------------------------
# Step 1: Parse SQL file (preserve order) – robust column detection
# ----------------------------------------------------------------------
def parse_sql_file(sql_path):
    """
    Reads the SQL INSERT statements and extracts artist metadata.
    Works with both 7‑column and 8‑column INSERT formats.

    Returns:
        ordered_discogs_ids: list of unique Discogs artist IDs in order of first appearance
        artist_map:          dict discogs_id -> artist metadata (dict)
    """
    ordered_ids = []
    seen = set()
    artist_map = {}

    insert_pattern = re.compile(
        r"INSERT\s+\[dbo\]\.\[Artist\]\s*\(([^)]+)\)\s*VALUES\s*\((.+)\)\s*$",
        re.IGNORECASE
    )

    with open(sql_path, 'r', encoding='utf-8-sig') as f:
        for line_num, line in enumerate(f, 1):
            match = insert_pattern.search(line)
            if not match:
                continue

            columns_str = match.group(1).strip()
            values_str = match.group(2).strip()

            columns = [col.strip().strip('[]') for col in columns_str.split(',')]
            values = [v.strip().strip("'") for v in values_str.split(',')]

            if len(columns) != len(values):
                print(f"Warning: line {line_num} column/value count mismatch. Skipping.")
                continue

            row = dict(zip(columns, values))

            required_fields = ['spotify_playlist_id', 'artist_name', 'artist_surname',
                               'musicbrainz_uuid', 'discogs_id', 'instrument_id', 'wikipedia_url']
            if not all(field in row for field in required_fields):
                print(f"Warning: line {line_num} missing some required fields. Skipping.")
                continue

            discogs_id_raw = row['discogs_id']
            if discogs_id_raw.isdigit():
                discogs_id = int(discogs_id_raw)

                if discogs_id not in seen:
                    seen.add(discogs_id)
                    ordered_ids.append(discogs_id)

                artist_info = {
                    'discogs_id': discogs_id,
                    'musicbrainz_uuid': row['musicbrainz_uuid'],
                    'artist_name': row['artist_name'],
                    'artist_surname': row['artist_surname'],
                    'wikipedia_url': row['wikipedia_url'],
                    'full_name': f"{row['artist_name']} {row['artist_surname']}".strip()
                }
                if 'artist_id' in row:
                    artist_info['artist_id'] = row['artist_id']

                artist_map[discogs_id] = artist_info
            else:
                print(f"Warning: line {line_num} discogs_id not a number ({discogs_id_raw})")

    return ordered_ids, artist_map


# ----------------------------------------------------------------------
# Step 2: Load MusicBrainz JSONL and build lookup table (with encoding fallback)
# ----------------------------------------------------------------------
def load_musicbrainz_lookup(mb_path):
    """
    Reads a JSONL file where each line is a MusicBrainz album object.
    Returns a dict keyed by Discogs album ID (string) containing the whole object.
    """
    mb_lookup = {}
    if not mb_path or not os.path.exists(mb_path):
        print("MusicBrainz file not found. Proceeding without enrichment.")
        return mb_lookup

    encodings = ['cp1252', 'utf-8-sig', 'latin-1']
    lines = None
    for enc in encodings:
        try:
            with open(mb_path, 'r', encoding=enc) as f:
                lines = f.readlines()
            print(f"Successfully read MusicBrainz file with encoding: {enc}")
            break
        except UnicodeDecodeError:
            continue
    if lines is None:
        print("Could not read MusicBrainz file with any attempted encoding.")
        return mb_lookup

    for line_num, line in enumerate(lines, 1):
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
            discogs_album_id = None
            rg = obj.get('release_group')
            if rg:
                raw_id = rg.get('discogs_id')
                if raw_id and raw_id != 'null':
                    discogs_album_id = raw_id
                else:
                    raw_rel_id = rg.get('release_discogs_id')
                    if raw_rel_id and raw_rel_id != 'null':
                        discogs_album_id = raw_rel_id

            if discogs_album_id:
                mb_lookup[str(discogs_album_id)] = obj
                print(f"  Line {line_num}: extracted Discogs ID = {discogs_album_id}")
            else:
                props = list(obj.keys())
                print(f"Warning: line {line_num} no Discogs ID. Top-level props: {props}")
        except json.JSONDecodeError as e:
            print(f"Warning: line {line_num} JSON error: {e}")

    print(f"Loaded {len(mb_lookup)} unique Discogs album IDs from MusicBrainz data.")
    return mb_lookup


# ----------------------------------------------------------------------
# Helper: Discogs API call with rate limiting and retries
# ----------------------------------------------------------------------
def discogs_api_request(url, session, last_request_time, min_interval=MIN_REQUEST_INTERVAL,
                        retries=MAX_RETRIES):
    """
    Performs a GET request to the Discogs API, respecting rate limits.
    last_request_time is a mutable list holding the timestamp of the last call.
    """
    now = time.time()
    elapsed = now - last_request_time[0]
    if elapsed < min_interval:
        sleep_time = min_interval - elapsed
        print(f"Rate limiting: sleeping {sleep_time:.2f} s")
        time.sleep(sleep_time)

    headers = {'User-Agent': USER_AGENT}
    attempt = 0
    while attempt <= retries:
        try:
            print(f"Calling API: {url}")
            response = session.get(url, headers=headers, timeout=30)
            last_request_time[0] = time.time()

            if response.status_code == 200:
                return response.json()
            else:
                print(f"Request to {url} failed with HTTP {response.status_code}")
                if response.status_code == 429 or (500 <= response.status_code < 600):
                    if attempt < retries:
                        wait = RETRY_BACKOFF * (2 ** attempt)
                        print(f"Retry {attempt+1}/{retries} after {wait} seconds")
                        time.sleep(wait)
                        attempt += 1
                        continue
                response.raise_for_status()
        except requests.RequestException as e:
            print(f"Request exception: {e}")
            if attempt < retries:
                wait = RETRY_BACKOFF * (2 ** attempt)
                print(f"Retry {attempt+1}/{retries} after {wait} seconds")
                time.sleep(wait)
                attempt += 1
                continue
            else:
                raise
    raise Exception(f"Max retries exceeded for {url}")


# ----------------------------------------------------------------------
# Helper to extract Wikipedia page title from URL
# ----------------------------------------------------------------------
def extract_wiki_title(url):
    """
    Given a Wikipedia URL like https://en.wikipedia.org/wiki/Some_Title,
    returns the page title part ('Some_Title').
    Returns None if the URL doesn't contain '/wiki/'.
    """
    match = re.search(r'/wiki/(.*)', url)
    return match.group(1) if match else None


# ----------------------------------------------------------------------
# Step 3: Process a single artist
# ----------------------------------------------------------------------
def process_artist(artist_id, artist_info, mb_lookup, output_file, session, last_request_time):
    """
    Fetches all releases for the given artist, enriches with MusicBrainz,
    and appends JSON lines to output_file.
    Also performs a Wikipedia search for each album and adds the URL as a top-level field.
    Removes any existing wikipedia_url_search from the MusicBrainz data.
    If the album Wikipedia URL matches the artist's Wikipedia URL, the field is set to null.
    Now also includes the artist_id from the SQL data in the artist object.
    """
    releases_url = f"{BASE_API_URL}/artists/{artist_id}/releases"
    page = 1
    per_page = 100
    processed_masters = set()

    while True:
        params = {'page': page, 'per_page': per_page}
        url = releases_url + '?' + urlencode(params)
        data = discogs_api_request(url, session, last_request_time)

        for release in data.get('releases', []):
            if release.get('role') != 'Main':
                continue

            print(f"  -> Found release: {release.get('title')} "
                  f"(ID: {release.get('id')}, type: {release.get('type')})")

            try:
                if release['type'] == 'master':
                    full_url = f"{BASE_API_URL}/masters/{release['id']}"
                else:
                    full_url = release.get('resource_url')

                full_data = discogs_api_request(full_url, session, last_request_time)
                discogs_obj = None

                if release['type'] == 'release' and full_data.get('master_id'):
                    master_id = full_data['master_id']
                    if master_id not in processed_masters:
                        master_url = f"{BASE_API_URL}/masters/{master_id}"
                        master_data = discogs_api_request(master_url, session, last_request_time)
                        processed_masters.add(master_id)
                        discogs_obj = master_data
                        print("      -> Fetched master (from child release)")
                    else:
                        print("      -> Skipping, master already processed")
                        continue

                elif release['type'] == 'master':
                    master_id = full_data['id']
                    if master_id not in processed_masters:
                        processed_masters.add(master_id)
                        discogs_obj = full_data
                        print("      -> Fetched master")
                    else:
                        print("      -> Skipping, master already processed")
                        continue

                else:
                    discogs_obj = full_data
                    print("      -> Fetched standalone release")

                if discogs_obj:
                    # ----- Fetch all version IDs for masters -----
                    all_version_ids = []
                    if 'main_release' in discogs_obj:
                        versions_url = discogs_obj.get('versions_url')
                        if versions_url:
                            try:
                                print(f"      -> Fetching versions for master {discogs_obj['id']} "
                                      f"from {versions_url}")
                                vpage = 1
                                while True:
                                    vresp = discogs_api_request(
                                        f"{versions_url}?page={vpage}&per_page=100",
                                        session, last_request_time
                                    )
                                    if 'versions' in vresp:
                                        all_version_ids.extend(v['id'] for v in vresp['versions'])
                                    if vpage >= vresp.get('pagination', {}).get('pages', 1):
                                        break
                                    vpage += 1
                            except Exception as e:
                                print(f"      Warning: failed to fetch versions: {e}")
                        else:
                            print("      Warning: master has no versions_url")

                    discogs_obj['discogs_all_versions_Ids'] = all_version_ids

                    # ----- Wikipedia search for the album -----
                    album_title = discogs_obj.get('title', '')
                    wiki_search_url = search_wikipedia(artist_info['full_name'], album_title)
                    had_wiki_search = wiki_search_url is not None

                    # NEW: Compare album Wikipedia URL with artist Wikipedia URL
                    if had_wiki_search:
                        artist_wiki = artist_info.get('wikipedia_url')
                        if artist_wiki:
                            album_title_part = extract_wiki_title(wiki_search_url)
                            artist_title_part = extract_wiki_title(artist_wiki)
                            if (album_title_part and artist_title_part and
                                    album_title_part == artist_title_part):
                                print("      [Wikipedia] Album URL matches artist URL, setting to null.")
                                wiki_search_url = None  # will be output as null

                    # ----- MusicBrainz enrichment (and remove its wikipedia_url_search) -----
                    album_id = str(discogs_obj['id'])
                    mb_data = mb_lookup.get(album_id)
                    if mb_data:
                        print(f"      -> Found matching MusicBrainz data for album ID {album_id}")
                        # Make a deep copy to avoid modifying the lookup table
                        mb_data_copy = copy.deepcopy(mb_data)
                        # Remove the wikipedia_url_search field from release_group if present
                        if 'release_group' in mb_data_copy and isinstance(mb_data_copy['release_group'], dict):
                            mb_data_copy['release_group'].pop('wikipedia_url_search', None)
                    else:
                        print(f"      -> No MusicBrainz data for album ID {album_id}")
                        mb_data_copy = None

                    # ----- Build output wrapper -----
                    # Build the artist dictionary with all available fields
                    artist_dict = {
                        'artist_id': artist_info['artist_id'],
                        'name': artist_info['full_name'],
                        'wikipedia_url': artist_info['wikipedia_url'],
                        'id': artist_info['musicbrainz_uuid'],
                        'discogs_id': artist_id
                    }


                    wrapper = {
                        'artist': artist_dict,
                        'DiscogsAPIcall': discogs_obj
                    }
                    if mb_data_copy:
                        wrapper['MusicBrainzData'] = mb_data_copy

                    # Always add the wikipedia_url_search field if a search was performed,
                    # even if it ended up as None (null) due to a match with the artist's page.
                    if had_wiki_search:
                        wrapper['wikipedia_url_search'] = wiki_search_url  # may be None

                    with open(output_file, 'a', encoding='utf-8') as outf:
                        json.dump(wrapper, outf, ensure_ascii=False, separators=(',', ':'))
                        outf.write('\n')

            except Exception as e:
                print(f"      Error processing {full_url}: {e}")

        if page >= data.get('pagination', {}).get('pages', 1):
            break
        page += 1


# ----------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(
        description='Fetch Discogs releases and enrich with MusicBrainz data.'
    )
    parser.add_argument('-SqlFile', required=True,
                        help='Path to SQL file with artist INSERTs')
    parser.add_argument('-MusicBrainzFile', default='mb_albums_ofartists.jsonl',
                        help='Path to MusicBrainz JSONL file')
    parser.add_argument('-OutputFile', default='discogs_releases.jsonl',
                        help='Output JSONL file')
    args = parser.parse_args()

    sql_file = args.SqlFile
    mb_file = args.MusicBrainzFile
    output_file = args.OutputFile

    ordered_discogs_ids, artist_map = parse_sql_file(sql_file)
    if not ordered_discogs_ids:
        print("No Discogs IDs found. Exiting.")
        return

    print(f"Found {len(ordered_discogs_ids)} unique Discogs artist IDs with metadata.")

    mb_lookup = load_musicbrainz_lookup(mb_file)

    with open(output_file, 'w', encoding='utf-8') as f:
        f.write('')

    session = requests.Session()
    last_request_time = [time.time() - MIN_REQUEST_INTERVAL]

    total_artists = len(ordered_discogs_ids)
    for idx, artist_id in enumerate(ordered_discogs_ids, 1):
        artist_info = artist_map.get(artist_id)
        if not artist_info:
            print(f"Warning: No metadata found for artist ID {artist_id} – skipping.")
            continue

        print(f"[{idx}/{total_artists}] Processing artist ID {artist_id}")
        try:
            process_artist(artist_id, artist_info, mb_lookup, output_file,
                           session, last_request_time)
        except Exception as e:
            print(f"Error processing artist {artist_id}: {e}")

    print(f"Done. Output saved to {output_file}")


if __name__ == '__main__':
    main()