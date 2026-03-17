#!/usr/bin/env python3
"""
Fetch Discogs releases for artists from a SQL file, enrich with MusicBrainz data,
and output JSON Lines. Preserves artist order from SQL file.
"""

import argparse
import json
import os
import re
import time
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


# ----------------------------------------------------------------------
# Step 1: Parse SQL file (preserve order) – fixed encoding: utf-8-sig
# ----------------------------------------------------------------------
def parse_sql_file(sql_path):
    """
    Reads the SQL INSERT statements and extracts artist metadata.
    Returns:
        ordered_discogs_ids: list of unique Discogs artist IDs in order of first appearance
        artist_map:          dict discogs_id -> artist metadata (dict)
    """
    ordered_ids = []
    seen = set()
    artist_map = {}

    # Pattern matches INSERT statements with any column list
    insert_pattern = re.compile(
        r'^\s*INSERT\s+\[dbo\]\.\[Artist\]\s*\([^)]+\)\s*VALUES\s*\((.+)\)\s*$',
        re.IGNORECASE
    )

    # Use utf-8-sig to handle BOM at the beginning of the file
    with open(sql_path, 'r', encoding='utf-8-sig') as f:
        for line in f:
            match = insert_pattern.search(line)
            if not match:
                continue

            values_part = match.group(1)
            # Simple split by comma (values are simple: integers and quoted strings)
            values = [v.strip().strip("'") for v in values_part.split(',')]

            if len(values) >= 8:
                artist_id = values[0]          # not used directly
                spotify_id = values[1]
                artist_name = values[2]
                artist_surname = values[3]
                musicbrainz_uuid = values[4]
                discogs_id_raw = values[5]
                instrument_id = values[6]
                wikipedia_url = values[7]

                if discogs_id_raw.isdigit():
                    discogs_id = int(discogs_id_raw)

                    # Store only first occurrence (order preservation)
                    if discogs_id not in seen:
                        seen.add(discogs_id)
                        ordered_ids.append(discogs_id)

                    # Always update map (in case of duplicate lines, same metadata)
                    artist_map[discogs_id] = {
                        'discogs_id': discogs_id,
                        'musicbrainz_uuid': musicbrainz_uuid,
                        'artist_name': artist_name,
                        'artist_surname': artist_surname,
                        'wikipedia_url': wikipedia_url,
                        'full_name': f"{artist_name} {artist_surname}".strip()
                    }
                else:
                    print(f"Warning: discogs_id not a number ({discogs_id_raw})")
            else:
                print(f"Warning: not enough values (expected 8, got {len(values)})")

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

    # Try common encodings (Windows often uses cp1252)
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
                # Prefer discogs_id (master), fall back to release_discogs_id
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
                # Retry on 429 (rate limit) or 5xx errors
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
# Step 3: Process a single artist
# ----------------------------------------------------------------------
def process_artist(artist_id, artist_info, mb_lookup, output_file, session, last_request_time):
    """
    Fetches all releases for the given artist, enriches with MusicBrainz,
    and appends JSON lines to output_file.
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
                # Determine the URL to fetch full details
                if release['type'] == 'master':
                    full_url = f"{BASE_API_URL}/masters/{release['id']}"
                else:
                    full_url = release.get('resource_url')

                full_data = discogs_api_request(full_url, session, last_request_time)
                discogs_obj = None

                # If it's a release that belongs to a master, fetch the master instead
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
                    # Standalone release
                    discogs_obj = full_data
                    print("      -> Fetched standalone release")

                if discogs_obj:
                    # ----- Fetch all version IDs for masters -----
                    all_version_ids = []
                    if 'main_release' in discogs_obj:          # heuristic for master
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

                    # ----- MusicBrainz enrichment -----
                    album_id = str(discogs_obj['id'])
                    mb_data = mb_lookup.get(album_id)
                    if mb_data:
                        print(f"      -> Found matching MusicBrainz data for album ID {album_id}")
                    else:
                        print(f"      -> No MusicBrainz data for album ID {album_id}")

                    # ----- Build output wrapper -----
                    wrapper = {
                        'artist': {
                            'id': artist_info['musicbrainz_uuid'],
                            'name': artist_info['full_name'],
                            'discogs_id': artist_id,
                            'wikipedia_url': artist_info['wikipedia_url']
                        },
                        'DiscogsAPIcall': discogs_obj
                    }
                    if mb_data:
                        wrapper['MusicBrainzData'] = mb_data

                    # Append to output file
                    with open(output_file, 'a', encoding='utf-8') as outf:
                        json.dump(wrapper, outf, ensure_ascii=False, separators=(',', ':'))
                        outf.write('\n')

            except Exception as e:
                print(f"      Error processing {full_url}: {e}")

        # Pagination
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

    # Step 1: Parse SQL (preserves order) – now with utf-8-sig encoding
    ordered_discogs_ids, artist_map = parse_sql_file(sql_file)
    if not ordered_discogs_ids:
        print("No Discogs IDs found. Exiting.")
        return

    print(f"Found {len(ordered_discogs_ids)} unique Discogs artist IDs with metadata.")

    # Step 2: Load MusicBrainz lookup
    mb_lookup = load_musicbrainz_lookup(mb_file)

    # Step 3: Prepare output file (clear if exists)
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write('')          # truncate

    # Shared session and rate‑limiting tracker
    session = requests.Session()
    last_request_time = [time.time() - MIN_REQUEST_INTERVAL]   # mutable list

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