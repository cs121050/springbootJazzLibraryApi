import sys
import re

def parse_values(values_str):
    """
    Extract individual value tokens from the VALUES list.
    Handles numbers and quoted strings (with optional N prefix).
    Supports escaped single quotes (doubled) inside strings.
    Returns a list of strings as they appear (including quotes and N).
    """
    # Pattern: either a number (\d+) or a quoted string with optional N prefix
    # Inside quotes, allow any sequence of either non-quote characters or two consecutive quotes
    pattern = r"(?:N?'(?:[^']|'')*'|\d+)"
    tokens = re.findall(pattern, values_str)
    print(f"  DEBUG: Parsed tokens: {tokens}")
    return tokens

def strip_quotes(val):
    """
    Remove surrounding quotes and optional N prefix from a string token.
    For numbers, return the token unchanged.
    Also reduces doubled quotes back to single quotes.
    """
    if val.startswith("N'") and val.endswith("'"):
        inner = val[2:-1]
        stripped = inner.replace("''", "'")
        print(f"  DEBUG: Stripped N-quotes from {val} -> {stripped}")
        return stripped
    elif val.startswith("'") and val.endswith("'"):
        inner = val[1:-1]
        stripped = inner.replace("''", "'")
        print(f"  DEBUG: Stripped quotes from {val} -> {stripped}")
        return stripped
    else:
        print(f"  DEBUG: Keeping number as is: {val}")
        return val  # number

def build_mapping(mssql_file):
    """
    Read the MSSQL file and build a dictionary:
        location_id (str) -> video_id (int)
    """
    mapping = {}
    print(f"\n=== Building mapping from MSSQL file: {mssql_file} ===")
    
    with open(mssql_file, 'r', encoding='utf-8') as f:
        line_count = 0
        for line in f:
            line_count += 1
            line = line.strip()
            if not line or not line.upper().startswith('INSERT'):
                continue
                
            print(f"\nDEBUG: Processing MSSQL line {line_count}")
            print(f"DEBUG: Line content: {line[:100]}...")
            
            idx = line.find('VALUES (')
            if idx == -1:
                print("  DEBUG: No VALUES clause found, skipping")
                continue
                
            rest = line[idx + len('VALUES ('):]
            last_paren = rest.rfind(')')
            if last_paren == -1:
                print("  DEBUG: No closing parenthesis found, skipping")
                continue
                
            values_str = rest[:last_paren]
            print(f"  DEBUG: Values string: {values_str[:100]}...")
            
            tokens = parse_values(values_str)
            if len(tokens) < 8:
                print(f"  DEBUG: Not enough tokens (found {len(tokens)}, need 8), skipping")
                continue
                
            try:
                video_id = int(tokens[0])
                print(f"  DEBUG: Extracted video_id: {video_id}")
            except ValueError:
                print(f"  DEBUG: Failed to convert {tokens[0]} to int, skipping")
                continue
                
            location_token = tokens[6]  # location_id is the 7th column (0‑based index 6)
            location_id = strip_quotes(location_token)
            
            mapping[location_id] = video_id
            print(f"  DEBUG: Added to mapping: location_id='{location_id}' -> video_id={video_id}")
    
    print(f"\n=== Mapping complete. Total entries: {len(mapping)} ===")
    return mapping

def process_postgres_file(postgres_file, output_file, mapping):
    """
    Read the PostgreSQL file line by line.
    For lines containing an INSERT, update video_id if location_id matches.
    Skip all lines that are not INSERTs (including blank lines, comments, etc.).
    Write the modified INSERTs without extra blank lines.
    """
    print(f"\n=== Processing PostgreSQL file: {postgres_file} ===")
    print(f"=== Output will be written to: {output_file} ===")
    
    with open(postgres_file, 'r', encoding='utf-8') as f_in, \
         open(output_file, 'w', encoding='utf-8') as f_out:

        line_count = 0
        processed_count = 0
        modified_count = 0
        
        for line in f_in:
            line_count += 1
            # Skip completely empty lines (including lines with only whitespace)
            if not line.strip():
                print(f"DEBUG: Line {line_count}: Empty line, skipping")
                continue
            
            # Check if the line (ignoring leading whitespace) starts with INSERT
            stripped_line = line.lstrip()
            if not stripped_line.upper().startswith('INSERT'):
                print(f"DEBUG: Line {line_count}: Not an INSERT line (stripped: {stripped_line[:50]}...), skipping")
                continue

            # It's an INSERT line – process it
            processed_count += 1
            print(f"\nDEBUG: Line {line_count}: Processing INSERT line")
            idx = line.find('VALUES (')
            if idx == -1:
                print(f"  DEBUG: No VALUES clause found, skipping line")
                continue

            prefix = line[:idx]
            rest = line[idx + len('VALUES ('):]
            last_paren = rest.rfind(')')
            if last_paren == -1:
                print(f"  DEBUG: No closing parenthesis found, skipping line")
                continue

            values_str = rest[:last_paren]
            # suffix includes everything after the closing parenthesis,
            # including the semicolon and the original newline.
            suffix = rest[last_paren + 1:]  # e.g. ";\n"
            
            ends_with_newline = suffix.endswith('\n')
            print(f"  DEBUG: Values string: {values_str[:100]}...")
            
            tokens = parse_values(values_str)
            if len(tokens) < 8:
                print(f"  DEBUG: Not enough tokens (found {len(tokens)}, need 8), skipping line")
                continue

            location_token = tokens[6]
            location_id = strip_quotes(location_token)
            
            print(f"  DEBUG: Looking up location_id: '{location_id}'")
            
            if location_id in mapping:
                old_video_id = tokens[0]
                new_video_id = mapping[location_id]
                tokens[0] = str(new_video_id)
                print(f"  DEBUG: MATCH FOUND! Replacing video_id {old_video_id} -> {new_video_id}")
                modified_count += 1
            else:
                print(f"  DEBUG: No match found for location_id '{location_id}', keeping original video_id")

            # Rebuild the VALUES list with the same spacing style
            new_values_str = ', '.join(tokens)
            # Reconstruct the line: prefix + "VALUES (" + new_values_str + ")" + suffix
            new_line = f"{prefix}VALUES ({new_values_str}){suffix}"
            
            # If suffix did not already contain a newline, add one
            if not ends_with_newline:
                new_line += '\n'
                
            f_out.write(new_line)
        
        print(f"\n=== PostgreSQL processing complete ===")
        print(f"=== Total lines read: {line_count} ===")
        print(f"=== INSERT lines processed: {processed_count} ===")
        print(f"=== Lines modified: {modified_count} ===")

def main():
    if len(sys.argv) != 4:
        print("Usage: python update_video_ids.py <mssql_file> <postgres_file> <output_file>")
        print("Example: python update_video_ids.py mssql_dump.sql postgres_dump.sql updated_postgres.sql")
        sys.exit(1)

    mssql_file = sys.argv[1]
    postgres_file = sys.argv[2]
    output_file = sys.argv[3]

    print("=" * 60)
    print("VIDEO ID UPDATER SCRIPT")
    print("=" * 60)
    print(f"MSSQL input file: {mssql_file}")
    print(f"PostgreSQL input file: {postgres_file}")
    print(f"Output file: {output_file}")
    print("=" * 60)

    try:
        mapping = build_mapping(mssql_file)
        
        print("\n=== Mapping contents ===")
        for location_id, video_id in mapping.items():
            print(f"  {location_id} -> {video_id}")
        
        process_postgres_file(postgres_file, output_file, mapping)
        
        print("\n" + "=" * 60)
        print(f"SUCCESS! Output written to {output_file}")
        print("=" * 60)
        
    except FileNotFoundError as e:
        print(f"\nERROR: File not found - {e}")
        sys.exit(1)
    except Exception as e:
        print(f"\nERROR: An unexpected error occurred - {e}")
        sys.exit(1)

if __name__ == '__main__':
    main()