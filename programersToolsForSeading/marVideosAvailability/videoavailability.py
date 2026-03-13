#!/usr/bin/env python3
"""
Line‑by‑line YouTube video availability checker for SQL files.
Improved detection for private, unavailable, and age‑restricted videos.
"""

import os
import sys
import re
import time
import argparse
import logging
from collections import OrderedDict

import requests

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

# ANSI colors
class Colors:
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    BLUE = '\033[94m'
    MAGENTA = '\033[95m'
    BOLD = '\033[1m'
    END = '\033[0m'

if not sys.stdout.isatty():
    for attr in dir(Colors):
        if not attr.startswith('__'):
            setattr(Colors, attr, '')


def extract_youtube_id(line):
    """Extract YouTube ID from a line using multiple patterns."""
    patterns = [
        r",\s*'([a-zA-Z0-9_-]{11})'\s*,",
        r",\s*N'([a-zA-Z0-9_-]{11})'\s*,",
        r"https?://(?:www\.)?youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})",
        r"https?://youtu\.be/([a-zA-Z0-9_-]{11})",
        r"'([a-zA-Z0-9_-]{11})'",
    ]
    for pat in patterns:
        match = re.search(pat, line)
        if match:
            return match.group(1)

    # NCHAR concatenation
    nchar_nums = re.findall(r"NCHAR\((\d+)\)", line)
    if nchar_nums:
        result = ''.join(chr(int(n)) for n in nchar_nums)
        result = re.sub(r"'|\+", '', result).strip()
        if result:
            return result
    return None


def get_video_name(line):
    """Extract video name (second quoted string) for display."""
    matches = re.findall(r"'([^']*)'", line)
    if len(matches) >= 2:
        return matches[1]
    return "Unknown"


def replace_last_value(line, new_value):
    """Replace the last quoted value in the line with new_value."""
    pattern = r"(N?'[^']*')"
    matches = list(re.finditer(pattern, line))
    if not matches:
        return line
    last = matches[-1]
    start, end = last.span()
    old = last.group(0)
    if old.startswith("N'") and old.endswith("'"):
        new_quoted = f"N'{new_value}'"
    else:
        new_quoted = f"'{new_value}'"
    return line[:start] + new_quoted + line[end:]


def check_video_status(video_id):
    """
    Check YouTube video availability using oEmbed + page scraping.
    Returns (status_code, message)
    """
    # 1. Try oEmbed
    oembed_url = f"https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v={video_id}&format=json"
    try:
        resp = requests.get(oembed_url, timeout=5)
        if resp.status_code == 200:
            return 1, "Available"
        elif resp.status_code == 404:
            return -2, "Not found"
        # 401/403 -> need to scrape
    except:
        pass

    # 2. Scrape video page
    watch_url = f"https://www.youtube.com/watch?v={video_id}"
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept-Language': 'en-US,en;q=0.9',
    }
    try:
        resp = requests.get(watch_url, headers=headers, timeout=5)
        if resp.status_code == 200:
            content = resp.text.lower()
            # Check for various error messages
            if 'video unavailable' in content or 'this video is not available' in content:
                return -2, "Video unavailable"
            if 'private video' in content or 'this video is private' in content:
                return -3, "Private video"
            if 'sign in to confirm your age' in content:
                return -5, "Age restricted"
            if 'embeddable":false' in content:
                return -1, "Not embeddable"
            if 'members only' in content:
                return -4, "Members only"
            # If none of the above, assume available
            return 1, "Available"
        elif resp.status_code == 404:
            return -2, "Page not found"
        else:
            return -5, f"HTTP {resp.status_code}"
    except Exception as e:
        return -5, str(e)[:50]


def process_file(file_path, output_dir='.', backup=False, delay=0.5, no_color=False):
    if no_color:
        for attr in dir(Colors):
            if not attr.startswith('__'):
                setattr(Colors, attr, '')

    logger.info(f"Processing {file_path} ...")

    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    # Collect video IDs and line info
    video_id_map = {}      # id -> first video name
    line_info = []         # (idx, id, old_line, name)

    for idx, line in enumerate(lines):
        if not re.search(r'INSERT\s+INTO\s+video|INSERT\s+\[dbo\]\.\[Video\]', line, re.IGNORECASE):
            continue
        vid = extract_youtube_id(line)
        if vid:
            video_id_map.setdefault(vid, get_video_name(line))
            line_info.append((idx, vid, line, get_video_name(line)))
        else:
            logger.warning(f"Line {idx+1}: Could not extract video ID, keeping original.")

    if not line_info:
        logger.warning("No video lines found.")
        out_path = os.path.join(output_dir, os.path.basename(file_path).replace('.sql', '_checked.sql'))
        with open(out_path, 'w', encoding='utf-8') as f:
            f.writelines(lines)
        logger.info(f"Wrote {out_path} (unchanged)")
        return

    logger.info(f"Found {len(line_info)} video lines, {len(video_id_map)} unique IDs.")

    # Check all unique videos
    print(f"\n{Colors.BOLD}{'='*80}{Colors.END}")
    print(f"{Colors.BOLD}Checking YouTube Video Availability{Colors.END}")
    print(f"{Colors.BOLD}{'='*80}{Colors.END}\n")
    print(f"{'#':<4} {'Video ID':<12} {'Status':<15} {'Video Name':<40}")
    print(f"{'-'*4} {'-'*12} {'-'*15} {'-'*40}")

    status_results = {}
    unique_ids = list(video_id_map.keys())
    for i, vid in enumerate(unique_ids, 1):
        name = video_id_map[vid][:37] + ('...' if len(video_id_map[vid]) > 37 else '')
        status, msg = check_video_status(vid)
        status_results[vid] = status

        # Color mapping
        if status == 1:
            color = Colors.GREEN; stat_str = "AVAILABLE"
        elif status == -1:
            color = Colors.YELLOW; stat_str = "NOT EMBEDDABLE"
        elif status == -2:
            color = Colors.RED; stat_str = "DOES NOT EXIST"
        elif status == -3:
            color = Colors.RED; stat_str = "PRIVATE"
        elif status == -4:
            color = Colors.YELLOW; stat_str = "MEMBERS ONLY"
        else:
            color = Colors.MAGENTA; stat_str = "OTHER ERROR"

        print(f"{i:<4} {color}{vid:<12}{Colors.END} {color}{stat_str:<15}{Colors.END} {name:<40}")
        if status != 1:
            print(f"     {Colors.BLUE}→ {msg}{Colors.END}")

        if i < len(unique_ids):
            time.sleep(delay)

    # Summary
    counts = {1:0, -1:0, -2:0, -3:0, -4:0, -5:0}
    for s in status_results.values():
        counts[s] = counts.get(s, 0) + 1
    print(f"\n{Colors.BOLD}{'='*80}{Colors.END}")
    print(f"\n{Colors.BOLD}Summary:{Colors.END}")
    print(f"  {Colors.GREEN}✓ Available:{Colors.END} {counts[1]}")
    print(f"  {Colors.YELLOW}⚠ Not embeddable:{Colors.END} {counts[-1]}")
    print(f"  {Colors.RED}✗ Does not exist:{Colors.END} {counts[-2]}")
    print(f"  {Colors.RED}🔒 Private:{Colors.END} {counts[-3]}")
    print(f"  {Colors.YELLOW}👥 Members only:{Colors.END} {counts[-4]}")
    print(f"  {Colors.MAGENTA}? Other error:{Colors.END} {counts[-5]}\n")

    # Update lines
    new_lines = lines[:]
    modified = 0
    for idx, vid, old_line, _ in line_info:
        new_status = status_results.get(vid, -5)
        # Check if it actually changed (avoid unnecessary writes)
        old_avail_match = re.search(r"'(-?\d)'\)?;?\s*$", old_line)
        if old_avail_match:
            old_avail = old_avail_match.group(1)
            if str(old_avail) == str(new_status):
                continue
        new_line = replace_last_value(old_line, new_status)
        if new_line != old_line:
            new_lines[idx] = new_line
            modified += 1

    # Write output
    out_name = os.path.basename(file_path).replace('.sql', '_checked.sql')
    out_path = os.path.join(output_dir, out_name)

    if backup:
        backup_path = file_path + '.bak'
        os.rename(file_path, backup_path)
        logger.info(f"Original backed up to {backup_path}")
        with open(file_path, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
        logger.info(f"Updated original file {file_path}")
    else:
        with open(out_path, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
        logger.info(f"Checked file written to {out_path}")

    logger.info(f"Modified {modified} entries.\n")


def main():
    parser = argparse.ArgumentParser(description='Check YouTube video availability in SQL files')
    parser.add_argument('files', nargs='+', help='SQL files to process')
    parser.add_argument('--output-dir', default='.', help='Output directory')
    parser.add_argument('--backup', action='store_true', help='Backup and overwrite original')
    parser.add_argument('--delay', type=float, default=0.5, help='Delay between requests (seconds)')
    parser.add_argument('--no-color', action='store_true', help='Disable colored output')
    args = parser.parse_args()

    if not os.path.exists(args.output_dir):
        os.makedirs(args.output_dir)

    for fpath in args.files:
        if not os.path.isfile(fpath):
            logger.error(f"File not found: {fpath}")
            continue
        process_file(fpath, args.output_dir, args.backup, args.delay, args.no_color)


if __name__ == '__main__':
    main()