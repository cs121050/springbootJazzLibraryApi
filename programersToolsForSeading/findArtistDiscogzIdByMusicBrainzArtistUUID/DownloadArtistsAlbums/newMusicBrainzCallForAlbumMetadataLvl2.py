#!/usr/bin/env python3
"""
Extracts artist MBIDs from an SQL file, fetches all studio albums from MusicBrainz,
enriches with Wikipedia URLs, Discogs master data, and Cover Art Archive thumbnails,
and outputs as JSON Lines.
"""

import argparse
import json
import re
import time
import sys
from datetime import datetime
from urllib.parse import quote

import requests

# ------------------------------------------------------------
# Configuration
# ------------------------------------------------------------
MUSICBRAINZ_API_URL = "https://musicbrainz.org/ws/2"
DISCOGS_API_URL = "https://api.discogs.com"
COVERART_BASE_URL = "https://coverartarchive.org"

MB_MIN_INTERVAL_MS = 1100          # 1 second + buffer
DISCOGS_MIN_INTERVAL_MS = 1000     # 1 request per second (60/min with token)
WIKIPEDIA_MIN_INTERVAL_MS = 1000    # 1 request per second
COVERART_MIN_INTERVAL_MS = 200      # 200 ms between Cover Art checks

USER_AGENT = "MusicBrainzAlbumCollector/1.0 (https://example.com)"
MAX_RETRIES = 3
BASE_RETRY_DELAY_SEC = 5

# Global last‑request timestamps (as mutable lists for easy updating)
_last_mb_request = [datetime.now()]
_last_discogs_request = [datetime.now()]
_last_wikipedia_request = [datetime.now()]
_last_coverart_request = [datetime.now()]

# ------------------------------------------------------------
# Logging helpers (with timestamps)
# ------------------------------------------------------------
def log_debug(msg, indent=0):
    ts = datetime.now().strftime("%H:%M:%S.%f")[:-3]
    print(f"[{ts}] {'  ' * indent}{msg}")

def log_info(msg, indent=0):
    log_debug(f"ℹ️ {msg}", indent)

def log_warning(msg, indent=0):
    log_debug(f"⚠️ {msg}", indent)

def log_error(msg, indent=0):
    log_debug(f"❌ {msg}", indent)

def log_success(msg, indent=0):
    log_debug(f"✅ {msg}", indent)

# ------------------------------------------------------------
# Rate‑limited API callers with retries
# ------------------------------------------------------------
def _rate_limit(min_interval_ms, last_time_list):
    now = datetime.now()
    elapsed_ms = (now - last_time_list[0]).total_seconds() * 1000
    if elapsed_ms < min_interval_ms:
        sleep_ms = min_interval_ms - elapsed_ms
        log_debug(f"Rate limiting: sleeping {sleep_ms:.0f} ms", indent=2)
        time.sleep(sleep_ms / 1000.0)
    last_time_list[0] = datetime.now()

def _request_with_retries(url, headers, method='GET', session=None, acceptable_status=None):
    if acceptable_status is None:
        acceptable_status = {200}
    retries = 0
    while retries <= MAX_RETRIES:
        try:
            if session:
                resp = session.request(method, url, headers=headers, timeout=30)
            else:
                resp = requests.request(method, url, headers=headers, timeout=30)

            if resp.status_code in acceptable_status:
                return resp
            if resp.status_code == 429:
                log_warning(f"Rate limit exceeded (429). Waiting 60 seconds...", indent=2)
                time.sleep(60)
                retries += 1
                continue
            if 500 <= resp.status_code < 600:
                wait = BASE_RETRY_DELAY_SEC * (2 ** retries)
                log_warning(f"Server error {resp.status_code}. Retry {retries+1}/{MAX_RETRIES} after {wait}s", indent=2)
                time.sleep(wait)
                retries += 1
                continue
            if resp.status_code == 404:
                return None
            resp.raise_for_status()
        except requests.exceptions.RequestException as e:
            log_warning(f"Request failed: {e}", indent=2)
            if retries < MAX_RETRIES:
                wait = BASE_RETRY_DELAY_SEC * (2 ** retries)
                log_warning(f"Retry {retries+1}/{MAX_RETRIES} after {wait}s", indent=2)
                time.sleep(wait)
                retries += 1
            else:
                raise
    raise Exception(f"Maximum retries exceeded for {url}")

def call_musicbrainz_api(url):
    _rate_limit(MB_MIN_INTERVAL_MS, _last_mb_request)
    headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    log_info(f"Calling MusicBrainz: {url}", indent=2)
    resp = _request_with_retries(url, headers)
    return resp.json() if resp else None

def call_discogs_api(url, token=None):
    _rate_limit(DISCOGS_MIN_INTERVAL_MS, _last_discogs_request)
    headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Discogs token={token}"
        log_info("Using authenticated Discogs access (60 requests/min)", indent=3)
    log_info(f"Calling Discogs: {url}", indent=3)
    resp = _request_with_retries(url, headers, acceptable_status={200, 404})
    if resp and resp.status_code == 200:
        remaining = resp.headers.get('X-Discogs-Ratelimit-Remaining')
        limit = resp.headers.get('X-Discogs-Ratelimit')
        if remaining and limit:
            log_info(f"Discogs rate limit: {remaining}/{limit} remaining", indent=3)
        return resp.json()
    elif resp and resp.status_code == 404:
        log_info("Discogs resource not found (404)", indent=3)
        return None
    return None

def call_wikipedia_search_api(artist_name, album_title):
    _rate_limit(WIKIPEDIA_MIN_INTERVAL_MS, _last_wikipedia_request)   # FIXED TYPO
    query = f"{album_title} {artist_name} album"
    encoded = quote(query)
    url = f"https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch={encoded}&format=json&utf8=1"
    headers = {"User-Agent": USER_AGENT}
    log_info(f"Searching Wikipedia for: {query}", indent=4)
    try:
        resp = _request_with_retries(url, headers, acceptable_status={200})
        if resp:
            data = resp.json()
            if data.get('query', {}).get('search'):
                title = data['query']['search'][0]['title']
                wiki_url = "https://en.wikipedia.org/wiki/" + title.replace(' ', '_')
                log_success(f"Found Wikipedia page: {title} -> {wiki_url}", indent=4)
                return wiki_url
        log_info("No Wikipedia search results found.", indent=4)
        return None
    except Exception as e:
        log_warning(f"Wikipedia search API request failed: {e}", indent=4)
        return None

def check_coverart_thumbnail(release_group_id):
    _rate_limit(COVERART_MIN_INTERVAL_MS, _last_coverart_request)
    url = f"{COVERART_BASE_URL}/release-group/{release_group_id}/front-250.jpg"
    log_info(f"Checking cover art: {url}", indent=4)
    headers = {"User-Agent": USER_AGENT}
    try:
        with requests.Session() as s:
            resp = s.head(url, headers=headers, timeout=10, allow_redirects=True)
        if resp.status_code == 200:
            log_success("Cover art found.", indent=4)
            return url
        else:
            log_info(f"Cover art not available (HTTP {resp.status_code})", indent=4)
            return None
    except requests.RequestException as e:
        log_warning(f"Error checking cover art: {e}", indent=4)
        return None

# ------------------------------------------------------------
# Helper functions for data extraction
# ------------------------------------------------------------
def extract_wikipedia_from_relations(release_group):
    for rel in release_group.get('relations', []):
        rel_type = rel.get('type', '')
        if 'wikipedia' in rel_type.lower() or 'wikidata' in rel_type.lower():
            url = rel.get('url', {}).get('resource')
            if url:
                log_info(f"Found Wikipedia/Wikidata URL: {url}", indent=4)
                return url
    return None

def extract_discogs_ids_from_relations(relations):
    master_id = None
    release_id = None
    for rel in relations or []:
        if rel.get('type') == 'discogs':
            url = rel.get('url', {}).get('resource', '')
            m = re.search(r'/master/(\d+)', url)
            if m:
                master_id = m.group(1)
                log_info(f"Extracted Discogs master ID from relation: {master_id}", indent=4)
            else:
                m = re.search(r'/release/(\d+)', url)
                if m:
                    release_id = m.group(1)
                    log_info(f"Extracted Discogs release ID from relation: {release_id}", indent=4)
    return master_id, release_id

def get_discogs_master_data(discogs_id, token):
    if not discogs_id:
        return None
    master_url = f"{DISCOGS_API_URL}/masters/{discogs_id}"
    data = call_discogs_api(master_url, token)
    if data:
        return {
            'type': 'master',
            'id': discogs_id,
            'rating_count': data.get('community', {}).get('rating', {}).get('count'),
            'rating_average': data.get('community', {}).get('rating', {}).get('average'),
            'images': data.get('images'),
            'year': data.get('year'),
            'genres': data.get('genres'),
            'styles': data.get('styles'),
            'tracklist': data.get('tracklist')
        }
    log_info("Master not found, trying as release ID...", indent=3)
    release_url = f"{DISCOGS_API_URL}/releases/{discogs_id}"
    data = call_discogs_api(release_url, token)
    if data:
        return {
            'type': 'release',
            'id': discogs_id,
            'rating_count': data.get('community', {}).get('rating', {}).get('count'),
            'rating_average': data.get('community', {}).get('rating', {}).get('average'),
            'images': data.get('images'),
            'year': data.get('year'),
            'genres': data.get('genres'),
            'styles': data.get('styles'),
            'tracklist': data.get('tracklist')
        }
    return None

# ------------------------------------------------------------
# Main script
# ------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(description="Fetch MusicBrainz albums enriched with Wikipedia, Discogs, and Cover Art.")
    parser.add_argument("SqlFile", help="Path to the SQL file containing the artist MBIDs.")
    parser.add_argument("-OutputFile", default="mb_albums_enriched.jsonl", help="Output .jsonl file path.")
    parser.add_argument("-IncludeAllReleaseGroups", action="store_true", help="Include all release groups (not just studio albums).")
    parser.add_argument("-DiscogsToken", default="", help="Your Discogs personal access token.")
    args = parser.parse_args()

    # ------------------------------------------------------------
    # Extract MBIDs from SQL file
    # ------------------------------------------------------------
    log_info(f"Reading SQL file '{args.SqlFile}'...")
    try:
        with open(args.SqlFile, 'r', encoding='utf-8') as f:
            sql_content = f.read()
    except Exception as e:
        log_error(f"Cannot read SQL file: {e}")
        sys.exit(1)

    uuid_pattern = r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
    matches = re.findall(uuid_pattern, sql_content, re.IGNORECASE)
    mbids = set()
    for m in matches:
        clean = m.strip()
        clean = re.sub(r'[^\x20-\x7E]', '', clean)
        if clean:
            mbids.add(clean)
    mbids = sorted(mbids)

    log_info(f"Found {len(mbids)} unique artist MBIDs.")
    if not mbids:
        log_error("No MBIDs found. Exiting.")
        sys.exit(1)

    log_info("First 5 MBIDs:")
    for i, mbid in enumerate(mbids[:5]):
        log_info(f"  {i+1}: '{mbid}' (length: {len(mbid)})")

    # ------------------------------------------------------------
    # Prepare output file
    # ------------------------------------------------------------
    if args.OutputFile:
        open(args.OutputFile, 'w').close()
    log_info(f"Output will be written to '{args.OutputFile}'")

    total_artists = len(mbids)
    artist_counter = 0
    albums_written = 0

    # ------------------------------------------------------------
    # Process each artist
    # ------------------------------------------------------------
    for raw_mbid in mbids:
        artist_counter += 1
        current_mbid = raw_mbid.strip()
        current_mbid = re.sub(r'[^\x20-\x7E]', '', current_mbid)

        log_info(f"[{artist_counter}/{total_artists}] Processing artist {current_mbid}")
        if not current_mbid:
            log_warning("Skipping empty MBID", indent=1)
            continue

        # 1. Get artist metadata
        artist_url = f"{MUSICBRAINZ_API_URL}/artist/{current_mbid}?fmt=json"
        try:
            artist = call_musicbrainz_api(artist_url)
            if not artist:
                log_error(f"Failed to fetch artist {current_mbid}: no data", indent=1)
                continue
            artist_name = artist.get('name', 'Unknown')
            log_info(f"Artist: {artist_name}", indent=1)
        except Exception as e:
            log_error(f"Failed to fetch artist {current_mbid}: {e}", indent=1)
            continue

        # 2. Browse release groups for this artist (with url-rels)
        page = 1
        per_page = 100
        total_pages = 1

        while True:
            offset = (page - 1) * per_page
            rg_browse_url = f"{MUSICBRAINZ_API_URL}/release-group?artist={current_mbid}&limit={per_page}&offset={offset}&inc=artist-credits+url-rels&fmt=json"
            try:
                rg_response = call_musicbrainz_api(rg_browse_url)
                if not rg_response:
                    log_error(f"Failed to fetch release groups page {page}", indent=1)
                    break

                if page == 1:
                    total_count = rg_response.get('release-group-count', 0)
                    total_pages = (total_count + per_page - 1) // per_page
                    log_info(f"Artist has {total_count} release groups across {total_pages} pages.", indent=1)

                for rg in rg_response.get('release-groups', []):
                    is_album = rg.get('primary-type') == 'Album'
                    has_secondary = rg.get('secondary-types') and len(rg.get('secondary-types', [])) > 0

                    if args.IncludeAllReleaseGroups or (is_album and not has_secondary):
                        rg_id = rg.get('id')
                        rg_title = rg.get('title', 'Unknown')
                        log_info(f"-> Processing release group: {rg_title} (ID: {rg_id})", indent=2)

                        # 3. Extract Wikipedia URL from release group relations
                        wikipedia_url_from_mb = extract_wikipedia_from_relations(rg)

                        # 4. Fetch Wikipedia URL via Wikipedia search API
                        wikipedia_url_from_search = call_wikipedia_search_api(artist_name, rg_title)

                        # 5. Extract Discogs IDs from release group relations
                        discogs_master_id, discogs_release_id = extract_discogs_ids_from_relations(rg.get('relations', []))

                        # 6. Get all releases for this release group
                        releases_url = f"{MUSICBRAINZ_API_URL}/release?release-group={rg_id}&fmt=json"
                        try:
                            releases_response = call_musicbrainz_api(releases_url)
                            if releases_response and releases_response.get('releases'):
                                releases = releases_response['releases']
                                def sort_key(r):
                                    date = r.get('date')
                                    if not date:
                                        return "9999-12-31"
                                    if len(date) == 4:
                                        return f"{date}-01-01"
                                    if len(date) == 7:
                                        return f"{date}-01"
                                    return date
                                releases.sort(key=sort_key)
                                first_release = releases[0]
                                log_info(f"First release: {first_release.get('title')} (ID: {first_release.get('id')}, Date: {first_release.get('date')})", indent=3)

                                # 7. Fetch full metadata of the first release
                                release_detail_url = f"{MUSICBRAINZ_API_URL}/release/{first_release['id']}?inc=recordings+labels+artist-credits+url-rels&fmt=json"
                                try:
                                    release_detail = call_musicbrainz_api(release_detail_url)
                                    if release_detail:
                                        rel_master, rel_release = extract_discogs_ids_from_relations(release_detail.get('relations', []))
                                        master_discogs_id = discogs_master_id or rel_master
                                        release_discogs_id = discogs_release_id or rel_release

                                        discogs_data = None
                                        discogs_id_to_use = master_discogs_id or release_discogs_id
                                        if discogs_id_to_use:
                                            log_info(f"Fetching Discogs metadata for ID: {discogs_id_to_use}", indent=3)
                                            discogs_data = get_discogs_master_data(discogs_id_to_use, args.DiscogsToken)
                                            if discogs_data:
                                                log_info(f"Found Discogs data: rating count={discogs_data.get('rating_count')}, avg={discogs_data.get('rating_average')}", indent=3)

                                        cover_image = check_coverart_thumbnail(rg_id)

                                        release_group_obj = {
                                            'id': rg_id,
                                            'title': rg_title,
                                            'first-release-date': rg.get('first-release-date'),
                                            'primary-type': rg.get('primary-type'),
                                            'secondary-types': rg.get('secondary-types'),
                                            'discogs_id': discogs_id_to_use,
                                            'master_discogs_id': master_discogs_id,
                                            'release_discogs_id': release_discogs_id,
                                            'relations': rg.get('relations'),
                                            'wikipedia_url': wikipedia_url_from_mb,
                                            'wikipedia_url_search': wikipedia_url_from_search,
                                            'cover_image_small': cover_image
                                        }

                                        output = {
                                            'artist': {
                                                'id': current_mbid,
                                                'name': artist_name
                                            },
                                            'release_group': release_group_obj,
                                            'discogs': discogs_data,
                                            'first_release': release_detail
                                        }

                                        with open(args.OutputFile, 'a', encoding='utf-8') as out_f:
                                            json_line = json.dumps(output, separators=(',', ':'), ensure_ascii=False)
                                            out_f.write(json_line + '\n')
                                        albums_written += 1
                                        log_success(f"Successfully wrote album data (total now: {albums_written})", indent=3)
                                except Exception as e:
                                    log_warning(f"Failed to fetch release details for {first_release['id']}: {e}", indent=3)
                            else:
                                log_info("No releases found for this release group.", indent=3)
                        except Exception as e:
                            log_warning(f"Failed to fetch releases for release group {rg_id}: {e}", indent=3)

                        time.sleep(0.2)

                page += 1
                if page > total_pages:
                    break
            except Exception as e:
                log_error(f"Failed to fetch release groups for artist {current_mbid} on page {page}: {e}", indent=1)
                break

        time.sleep(0.5)

    if albums_written == 0:
        log_warning("No albums were written to the output file. Check the logs above for reasons.")
    else:
        log_success(f"Done. {albums_written} albums written to {args.OutputFile}")
    log_info(f"Total artists processed: {artist_counter}")

if __name__ == "__main__":
    main()