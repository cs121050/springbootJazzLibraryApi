#!/usr/bin/env python3
"""
Converts a Discogs JSON Lines file (with top-level "release_data" field)
into SQL INSERT statements for the Album and AlbumContainsArtist tables.
Now includes:
- auto‑incrementing album_id
- internal artist_id from top-level "artist" object (main artist link)
- extra_artists: only artist name and discogs_id
- images: only primary image URI (string) from Discogs
- labels: only array of label names
- videos: description field removed
- tracklist: only track title and simplified extraartists (name, id)
- genres: array of genre strings (JSON)
- companies: array of company names (strings)
- coverartarchive_thumb: MusicBrainz cover art thumbnail (from MusicBrainzData.release_group.cover_image_small)
- wikipedia_url: Wikipedia URL (from top-level wikipedia_url_search)
- release_format_description: stores the top-level "format_display" string (e.g., "CD, Album")
- release_type: derived from release_format_description with the following precedence:
    1. if contains 'single' or '7"' → 'single'
    2. else if contains 'compilation' → 'compilation'
    3. else if contains 'promo' → 'promo'
    4. else if contains 'PAL' or 'NTSC' (case‑insensitive) and does NOT contain 'album' → 'video'
    5. else 'album'
- youtube_video_id_for_thumbnail: stores the YouTube video ID of the first video (if any), otherwise NULL
"""

import argparse
import json
import re
import sys
from datetime import datetime

# ------------------------------------------------------------------------------
# Helper functions
# ------------------------------------------------------------------------------

def remove_url_base(text):
    """Remove 'https://www.discogs.com' and 'https://www.youtube.com' from a string."""
    if text is None:
        return None
    text = re.sub(r'https://www\.discogs\.com', '', text)
    text = re.sub(r'https://www\.youtube\.com', '', text)
    return text

def escape_sql_string(value, db_type, replace_double_dash):
    """
    Escape a string for safe inclusion in an SQL statement.
    - Single quotes doubled.
    - Backslashes doubled.
    - Newlines/carriage returns replaced with spaces.
    - If replace_double_dash is True, '--' replaced with database‑specific concatenation.
    - For SQL Server only, every ';' replaced with "' + CHAR(59) + '".
    """
    if value is None:
        return None

    value = value.replace("'", "''")
    value = value.replace('\\', '\\\\')
    value = value.replace('\r\n', ' ').replace('\n', ' ').replace('\r', ' ')

    if replace_double_dash:
        if db_type == 'PostgreSQL':
            value = value.replace('--', 'CHR(45) || CHR(45)')
        elif db_type == 'MySQL':
            value = value.replace('--', 'CONCAT(CHAR(45), CHAR(45))')
        elif db_type == 'SQLServer':
            value = value.replace('--', 'CHAR(45) + CHAR(45)')

    if db_type == 'SQLServer':
        value = value.replace(';', "' + CHAR(59) + '")

    return value

def to_json_string_and_strip_urls(obj):
    """Convert object to compact JSON, then remove Discogs/YouTube base URLs."""
    if obj is None:
        return None
    json_str = json.dumps(obj, separators=(',', ':'), ensure_ascii=False)
    return remove_url_base(json_str)

def simplify_extra_artists(artists):
    """
    Convert an extraartists list to a simplified list containing only
    'name' and 'discogs_id' for each artist.
    """
    if not artists:
        return None
    simplified = []
    for a in artists:
        if a and 'name' in a and 'id' in a:
            simplified.append({
                "name": a['name'],
                "discogs_id": a['id']
            })
    return simplified if simplified else None

def simplify_videos(videos):
    """
    Remove the 'description' field from each video object.
    Keeps all other fields (uri, title, duration, embed, etc.).
    """
    if not videos:
        return None
    simplified = []
    for v in videos:
        if v and isinstance(v, dict):
            new_v = {k: v for k, v in v.items() if k != 'description'}
            if new_v:
                simplified.append(new_v)
    return simplified if simplified else None

def simplify_tracklist(tracklist):
    """
    For each track, keep only 'title' and a simplified 'extraartists' (name & id).
    All other fields are dropped.
    """
    if not tracklist:
        return None
    simplified = []
    for t in tracklist:
        if t and isinstance(t, dict):
            new_t = {}
            if 'title' in t:
                new_t['title'] = t['title']
            if 'extraartists' in t and t['extraartists']:
                extra = simplify_extra_artists(t['extraartists'])
                if extra:
                    new_t['extraartists'] = extra
            if new_t:
                simplified.append(new_t)
    return simplified if simplified else None

def get_primary_image_uri(images):
    """
    From the images list, return the URI of the first image with type "primary".
    Returns None if no primary image is found.
    """
    if not images:
        return None
    for img in images:
        if img.get('type') == 'primary':
            uri = img.get('uri')
            if uri:
                return uri
    return None

def simplify_labels(labels):
    """
    Convert the labels list to a list containing only the label names.
    Returns a JSON string of the names array, or None if input is empty.
    """
    if not labels:
        return None
    names = [label.get('name') for label in labels if label and label.get('name')]
    if not names:
        return None
    return json.dumps(names, separators=(',', ':'), ensure_ascii=False)

def simplify_companies(companies):
    """
    Convert the companies list to a list containing only the company names.
    Returns a JSON string of the names array, or None if input is empty.
    """
    if not companies:
        return None
    names = [comp.get('name') for comp in companies if comp and comp.get('name')]
    if not names:
        return None
    return json.dumps(names, separators=(',', ':'), ensure_ascii=False)

def get_rating_count(community):
    """Extract rating count from the community object."""
    if community and isinstance(community, dict):
        rating = community.get('rating')
        if rating and isinstance(rating, dict):
            return rating.get('count')
    return None

def get_rating_average(community):
    """Extract rating average from the community object."""
    if community and isinstance(community, dict):
        rating = community.get('rating')
        if rating and isinstance(rating, dict):
            return rating.get('average')
    return None

def get_musicbrainz_thumb(musicbrainz):
    """
    Extract the cover image thumbnail from MusicBrainzData.
    Looks for musicbrainz.release_group.cover_image_small.
    Returns None if not found.
    """
    if not musicbrainz or not isinstance(musicbrainz, dict):
        return None
    release_group = musicbrainz.get('release_group')
    if release_group and isinstance(release_group, dict):
        thumb = release_group.get('cover_image_small')
        return thumb
    return None

def extract_youtube_video_id(url):
    """Extract YouTube video ID from various YouTube URL formats."""
    if not url:
        return None
    patterns = [
        r'(?:youtube\.com/watch\?v=|youtu\.be/)([^&?#]+)',
        r'youtube\.com/embed/([^&?#]+)',
        r'youtube\.com/v/([^&?#]+)'
    ]
    for pattern in patterns:
        match = re.search(pattern, url)
        if match:
            return match.group(1)
    return None

# ------------------------------------------------------------------------------
# Main
# ------------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description='Convert Discogs JSONL (with release_data) to SQL INSERTs.')
    parser.add_argument('input_file', help='Path to the .jsonl file')
    parser.add_argument('--output', '-o', default='discogs_inserts.sql',
                        help='Output SQL file (default: discogs_inserts.sql)')
    parser.add_argument('--db-type', choices=['SQLServer', 'MySQL', 'PostgreSQL'],
                        default='SQLServer', help='Target database dialect (default: SQLServer)')
    parser.add_argument('--no-replace-double-dash', action='store_true',
                        help='Disable automatic replacement of double dashes')
    args = parser.parse_args()

    replace_double_dash = not args.no_replace_double_dash

    try:
        with open(args.input_file, 'r', encoding='utf-8') as infile:
            lines = infile.readlines()
    except FileNotFoundError:
        print(f"Error: Input file '{args.input_file}' not found.", file=sys.stderr)
        sys.exit(1)

    with open(args.output, 'w', encoding='utf-8') as outfile:
        # Write header with updated column names and release_type logic
        outfile.write("-- SQL INSERT statements generated by convert_discogs_jsonl_to_sql.py\n")
        outfile.write(f"-- Source file: {args.input_file}\n")
        outfile.write(f"-- Generated on: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        outfile.write(f"-- Database dialect: {args.db_type}\n")
        outfile.write("-- Fields excluded from Album: date_added, date_changed\n")
        outfile.write("-- Images column: only primary image URI (string) is kept.\n")
        outfile.write("-- Labels column: only an array of label names (strings) is kept.\n")
        outfile.write("-- Videos column: 'description' field removed from each video.\n")
        outfile.write("-- Tracklist column: only track title and simplified extraartists (name, id) are kept.\n")
        outfile.write("-- Genres column: array of genre strings (JSON).\n")
        outfile.write("-- Companies column: array of company names (strings).\n")
        outfile.write("-- coverartarchive_thumb column: MusicBrainz cover art thumbnail (from release_group.cover_image_small).\n")
        outfile.write("-- wikipedia_url column: Wikipedia URL (from top-level wikipedia_url_search).\n")
        outfile.write("-- release_format_description column: stores the top-level 'format_display' string (e.g., 'CD, Album').\n")
        outfile.write("-- release_type column: derived from release_format_description with precedence:\n")
        outfile.write("--   1. if contains 'single' or '7\"' → 'single'\n")
        outfile.write("--   2. else if contains 'compilation' → 'compilation'\n")
        outfile.write("--   3. else if contains 'promo' → 'promo'\n")
        outfile.write("--   4. else if contains 'PAL' or 'NTSC' (case‑insensitive) and does NOT contain 'album' → 'video'\n")
        outfile.write("--   5. else 'album'\n")
        outfile.write("-- youtube_video_id_for_thumbnail column: stores the YouTube video ID of the first video (if any), otherwise NULL.\n")
        outfile.write("-- Discogs and YouTube base URLs have been stripped from all text fields.\n")
        outfile.write("-- extra_artists column now contains only artist name and discogs_id.\n")
        if replace_double_dash:
            outfile.write("-- Double dash replacement enabled\n")
        if args.db_type == 'SQLServer':
            outfile.write("-- Semicolon replacement enabled (semicolons inside strings become ' + CHAR(59) + ')\n")
        outfile.write("\nSTART TRANSACTION;\n\n")

        line_number = 0
        album_counter = 0  # will increment for each valid Album record

        for line in lines:
            line = line.strip()
            if not line:
                continue
            line_number += 1

            try:
                full_obj = json.loads(line)
            except json.JSONDecodeError:
                print(f"Warning: Line {line_number} is not valid JSON. Skipping.", file=sys.stderr)
                continue

            # Extract the Discogs release data – it is nested under "release_data"
            discogs = full_obj.get('release_data')
            if discogs is None:
                print(f"Warning: Line {line_number} has no 'release_data' field. Skipping.", file=sys.stderr)
                continue

            # Extract the top-level artist (main artist)
            top_artist = full_obj.get('artist')
            if top_artist is None:
                print(f"Warning: Line {line_number} has no top-level 'artist' object. Skipping.", file=sys.stderr)
                continue

            # Get internal artist_id from the top-level artist
            artist_id_str = top_artist.get('artist_id')
            if artist_id_str is None:
                print(f"Warning: Line {line_number} top-level artist has no 'artist_id'. Skipping.", file=sys.stderr)
                continue
            try:
                artist_id = int(artist_id_str)
            except ValueError:
                print(f"Warning: Line {line_number} artist_id '{artist_id_str}' is not an integer. Skipping.", file=sys.stderr)
                continue

            # Extract top-level format_display (will become release_format_description)
            format_display = full_obj.get('format_display')

            # Determine release_type based on format_display with enhanced logic
            release_type_value = 'album'  # default
            if format_display:
                fd_lower = format_display.lower()
                # 1. single or 7"
                if 'single' in fd_lower or '7"' in format_display:
                    release_type_value = 'single'
                # 2. compilation
                elif 'compilation' in fd_lower:
                    release_type_value = 'compilation'
                # 3. promo
                elif 'promo' in fd_lower:
                    release_type_value = 'promo'
                # 4. video (PAL or NTSC without 'album')
                elif ('pal' in fd_lower or 'ntsc' in fd_lower) and 'album' not in fd_lower:
                    release_type_value = 'video'
                # else remains 'album'

            # Extract MusicBrainz data (optional)
            musicbrainz = full_obj.get('MusicBrainzData')
            # Extract Wikipedia URL (optional) – becomes wikipedia_url
            wikipedia_url = full_obj.get('wikipedia_url_search')

            # Extract fields from release_data
            release_id = discogs.get('id')
            master_id = discogs.get('master_id') if 'master_id' in discogs else None
            title = remove_url_base(discogs.get('title'))
            year = discogs.get('year')
            # release_format_description will be set to format_display (not the Discogs country)
            released = remove_url_base(discogs.get('released'))
            released_formatted = remove_url_base(discogs.get('released_formatted'))
            styles = to_json_string_and_strip_urls(discogs.get('styles'))
            # Simplify extraartists
            extra_artists_raw = discogs.get('extraartists')
            extra_artists_simplified = simplify_extra_artists(extra_artists_raw)
            extra_artists = to_json_string_and_strip_urls(extra_artists_simplified) if extra_artists_simplified else None
            # Process labels – simplify to names
            raw_labels = discogs.get('labels')
            labels_names_json = simplify_labels(raw_labels) if raw_labels else None
            # Simplify tracklist – get Python list first
            raw_tracklist = discogs.get('tracklist')
            tracklist_list = simplify_tracklist(raw_tracklist) if raw_tracklist else None
            tracklist_simplified = to_json_string_and_strip_urls(tracklist_list) if tracklist_list else None
            # Simplify videos – get Python list first (for video ID extraction)
            raw_videos = discogs.get('videos')
            videos_list = simplify_videos(raw_videos) if raw_videos else None
            # Extract YouTube video ID from first video (if any) – becomes youtube_video_id_for_thumbnail
            first_video_id = None
            if videos_list and len(videos_list) > 0:
                first_video_uri = videos_list[0].get('uri')
                if first_video_uri:
                    first_video_id = extract_youtube_video_id(first_video_uri)
            # Now convert videos_list to JSON string for the videos column
            videos_simplified = to_json_string_and_strip_urls(videos_list) if videos_list else None
            community = discogs.get('community')
            rating_count = get_rating_count(community)
            rating_average = get_rating_average(community)
            # Process images – get primary image URI
            raw_images = discogs.get('images')
            images_uri = get_primary_image_uri(raw_images) if raw_images else None
            # Genres
            raw_genres = discogs.get('genres')
            genres_json = to_json_string_and_strip_urls(raw_genres) if raw_genres else None
            # Companies – simplify to names
            raw_companies = discogs.get('companies')
            companies_names_json = simplify_companies(raw_companies) if raw_companies else None

            # Get MusicBrainz thumb – becomes coverartarchive_thumb
            thumb = get_musicbrainz_thumb(musicbrainz)

            # release_id must be present
            if release_id is None:
                print(f"Warning: Line {line_number} has no release_id. Skipping.", file=sys.stderr)
                continue

            album_counter += 1
            current_album_id = album_counter

            # Escape each string value
            def esc(val):
                if val is None:
                    return None
                escaped = escape_sql_string(val, args.db_type, replace_double_dash)
                return escaped if escaped else None

            title_esc = esc(title)
            release_format_description_esc = esc(format_display)      # corresponds to release_format_description
            release_type_esc = esc(release_type_value)                # corresponds to release_type
            released_esc = esc(released)
            released_formatted_esc = esc(released_formatted)
            styles_esc = esc(styles)
            extra_artists_esc = esc(extra_artists)
            labels_esc = esc(labels_names_json)
            tracklist_esc = esc(tracklist_simplified)
            videos_esc = esc(videos_simplified)
            images_esc = esc(images_uri)
            coverartarchive_thumb_esc = esc(thumb)                    # corresponds to coverartarchive_thumb
            wikipedia_url_esc = esc(wikipedia_url)                    # corresponds to wikipedia_url
            genres_esc = esc(genres_json)
            companies_esc = esc(companies_names_json)
            youtube_video_id_for_thumbnail_esc = esc(first_video_id)  # corresponds to youtube_video_id_for_thumbnail

            # Build Album INSERT with updated column names
            album_sql = (
                "INSERT INTO Album (album_id, release_id, title, year, release_format_description, release_type, "
                "released, released_formatted, styles, master_id, youtube_video_id_for_thumbnail, extra_artists, "
                "labels, tracklist, videos, images, coverartarchive_thumb, wikipedia_url, genres, companies, "
                "rating_count, rating_average) VALUES ("
                f"{current_album_id}, "
                f"{release_id}, "
                f"{'NULL' if title_esc is None else f"'{title_esc}'"}, "
                f"{year if year is not None else 'NULL'}, "
                f"{'NULL' if release_format_description_esc is None else f"'{release_format_description_esc}'"}, "
                f"{'NULL' if release_type_esc is None else f"'{release_type_esc}'"}, "
                f"{'NULL' if released_esc is None else f"'{released_esc}'"}, "
                f"{'NULL' if released_formatted_esc is None else f"'{released_formatted_esc}'"}, "
                f"{'NULL' if styles_esc is None else f"'{styles_esc}'"}, "
                f"{master_id if master_id is not None else 'NULL'}, "
                f"{'NULL' if youtube_video_id_for_thumbnail_esc is None else f"'{youtube_video_id_for_thumbnail_esc}'"}, "
                f"{'NULL' if extra_artists_esc is None else f"'{extra_artists_esc}'"}, "
                f"{'NULL' if labels_esc is None else f"'{labels_esc}'"}, "
                f"{'NULL' if tracklist_esc is None else f"'{tracklist_esc}'"}, "
                f"{'NULL' if videos_esc is None else f"'{videos_esc}'"}, "
                f"{'NULL' if images_esc is None else f"'{images_esc}'"}, "
                f"{'NULL' if coverartarchive_thumb_esc is None else f"'{coverartarchive_thumb_esc}'"}, "
                f"{'NULL' if wikipedia_url_esc is None else f"'{wikipedia_url_esc}'"}, "
                f"{'NULL' if genres_esc is None else f"'{genres_esc}'"}, "
                f"{'NULL' if companies_esc is None else f"'{companies_esc}'"}, "
                f"{rating_count if rating_count is not None else 'NULL'}, "
                f"{rating_average if rating_average is not None else 'NULL'}"
                ");"
            )
            outfile.write(album_sql + "\n")

            # Insert main artist into AlbumContainsArtist using internal IDs
            outfile.write(
                f"INSERT INTO AlbumContainsArtist (artist_id, album_id, is_main) "
                f"VALUES ({artist_id}, {current_album_id}, 1);\n"
            )

            # NOTE: Extra artists are not inserted here because we lack a mapping
            # from Discogs artist IDs to internal artist_id.
            # If you have such a mapping, you can extend the script accordingly.

            outfile.write("\n")

        outfile.write("COMMIT;\n")

    print(f"SQL script written to {args.output}")
    print(f"Database dialect: {args.db_type}")
    print(f"Total albums processed: {album_counter}")
    if args.db_type == 'SQLServer':
        print("Semicolon replacement was enabled – all semicolons inside strings have been replaced with ' + CHAR(59) + '.")

if __name__ == "__main__":
    main()