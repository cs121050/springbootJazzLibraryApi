import re
import csv
from io import StringIO
import sys

def transform_insert_line(line):
    """
    Transforms a single INSERT line for VideoContainsArtist:
    - Changes the column name [artist_id] to [jazzli_artist_id]
    - Changes the corresponding value from an integer to a quoted string 'a' + integer
    """
    # Regex to capture column list and values list
    pattern = re.compile(
        r"INSERT\s+\[dbo\]\.\[VideoContainsArtist\]\s*\((.*?)\)\s*VALUES\s*\((.*)\)",
        re.IGNORECASE
    )
    match = pattern.search(line)
    if not match:
        return None  # Return None if the line doesn't match the expected pattern

    columns_str = match.group(1).strip()
    values_str = match.group(2).strip()

    # Extract column names (inside square brackets)
    col_pattern = re.compile(r'\[([^\]]+)\]')
    columns = col_pattern.findall(columns_str)

    # Parse the values using csv.reader to handle commas and quotes properly
    f = StringIO(values_str)
    reader = csv.reader(f, quotechar="'", skipinitialspace=True)
    try:
        values = next(reader)
    except StopIteration:
        return None

    # Locate the position of 'artist_id' in the column list
    try:
        artist_idx = columns.index('artist_id')
    except ValueError:
        # If 'artist_id' is not found, leave the line unchanged
        return line

    # Build new values list
    new_values = []
    for i, val in enumerate(values):
        val = val.strip()
        if i == artist_idx:
            # Transform to 'a' + original number, then quote
            new_values.append(f"'a{val}'")
        else:
            # Other values (assumed integers) remain unquoted
            new_values.append(val)

    # Rename the column in the column list
    new_columns = [
        '[jazzli_artist_id]' if col == 'artist_id' else f'[{col}]'
        for col in columns
    ]

    # Rebuild the INSERT statement
    new_line = f"INSERT [dbo].[VideoContainsArtist] ({', '.join(new_columns)}) VALUES ({', '.join(new_values)})"
    return new_line

def main():
    if len(sys.argv) < 2:
        print("Usage: python p.py input_file [output_file]")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else None

    with open(input_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    output_lines = []
    for line in lines:
        line = line.strip()
        if not line:
            output_lines.append('')
            continue
        transformed = transform_insert_line(line)
        if transformed is None:
            # If parsing fails, keep the original line (with warning)
            print(f"Warning: Could not parse line, keeping original: {line}", file=sys.stderr)
            output_lines.append(line)
        else:
            output_lines.append(transformed)

    if output_file:
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write('\n'.join(output_lines))
    else:
        print('\n'.join(output_lines))

if __name__ == '__main__':
    main()