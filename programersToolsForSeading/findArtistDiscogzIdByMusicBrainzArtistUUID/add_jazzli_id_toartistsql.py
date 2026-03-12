import re
import csv
import sys
from io import StringIO

def process_insert(insert_statement, counter):
    """
    Takes a single INSERT statement and returns a modified version with:
      - A new 'jazzli_artist_id' column at the beginning.
      - discogs_id and instrument_id values converted from strings to integers.
    """
    # Regex to capture the column list and the values list
    pattern = re.compile(
        r"INSERT\s+\[dbo\]\.\[Artist\]\s*\((.*?)\)\s*VALUES\s*\((.*)\)",
        re.IGNORECASE | re.DOTALL
    )
    match = pattern.search(insert_statement)
    if not match:
        return None  # Skip if pattern doesn't match

    columns_str = match.group(1).strip()
    values_str = match.group(2).strip()

    # Extract column names (without brackets)
    col_pattern = re.compile(r'\[([^\]]+)\]')
    columns = col_pattern.findall(columns_str)

    # Parse the values using csv.reader to handle quoting and commas properly
    f = StringIO(values_str)
    reader = csv.reader(f, quotechar="'", skipinitialspace=True)
    try:
        values = next(reader)
    except StopIteration:
        return None

    # Find positions of the columns that need conversion
    try:
        discogs_idx = columns.index('discogs_id')
        instr_idx = columns.index('instrument_id')
    except ValueError:
        # If columns are missing, we cannot convert; fallback to original
        discogs_idx = instr_idx = None

    # Build new values list
    new_id = f"a{counter}"
    new_values = [f"'{new_id}'"]

    for i, val in enumerate(values):
        if i == discogs_idx or i == instr_idx:
            # Convert to integer and output without quotes
            try:
                int_val = int(val)
                new_values.append(str(int_val))
            except ValueError:
                # If conversion fails, keep as quoted string (fallback)
                new_values.append(f"'{val.replace('\'', '\'\'')}'")
        else:
            # Escape single quotes and wrap in quotes
            escaped = val.replace("'", "''")
            new_values.append(f"'{escaped}'")

    # Build new column list (add jazzli_artist_id at the beginning)
    new_columns = ['[jazzli_artist_id]'] + [f'[{col}]' for col in columns]

    # Construct the new INSERT statement
    new_insert = f"INSERT [dbo].[Artist] ({', '.join(new_columns)}) VALUES ({', '.join(new_values)})"
    return new_insert

def main():
    # Determine input file (default: inserts.sql)
    input_file = sys.argv[1] if len(sys.argv) > 1 else 'inserts.sql'
    try:
        with open(input_file, 'r', encoding='utf-8') as f:
            content = f.read()
    except FileNotFoundError:
        print(f"Error: File '{input_file}' not found.", file=sys.stderr)
        sys.exit(1)

    # Split content into individual INSERT statements
    # (Simple split by "INSERT" – works if statements are separated by newlines)
    parts = content.split('INSERT')
    inserts = []
    for part in parts:
        part = part.strip()
        if part:
            inserts.append('INSERT ' + part)

    counter = 1
    output_lines = []
    for ins in inserts:
        new_ins = process_insert(ins, counter)
        if new_ins:
            output_lines.append(new_ins)
            counter += 1

    # Output to file if second argument provided, otherwise print to console
    if len(sys.argv) > 2:
        with open(sys.argv[2], 'w', encoding='utf-8') as f:
            f.write('\n'.join(output_lines))
    else:
        print('\n'.join(output_lines))

if __name__ == '__main__':
    main()