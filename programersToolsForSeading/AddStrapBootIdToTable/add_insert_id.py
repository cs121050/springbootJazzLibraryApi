#!/usr/bin/env python3
"""
Add an auto-increment ID column to INSERT statements and convert to both
Microsoft SQL Server and PostgreSQL syntax. Handles parentheses inside quoted strings.
Also converts string values for discogs_id and instrument_id to integers where appropriate.
"""

import re
import sys
import os

def strip_sql_comments(sql):
    """Remove SQL comments (-- and /* */)."""
    sql = re.sub(r'--.*$', '', sql, flags=re.MULTILINE)
    sql = re.sub(r'/\*.*?\*/', '', sql, flags=re.DOTALL)
    return sql

def find_matching_parenthesis(text, start_pos):
    """
    Return the index of the closing parenthesis matching the opening parenthesis
    at start_pos, ignoring parentheses inside single-quoted strings.
    """
    i = start_pos
    depth = 0
    in_quote = False
    length = len(text)

    while i < length:
        ch = text[i]

        if ch == "'" and not in_quote:
            in_quote = True
        elif ch == "'" and in_quote:
            # escaped single quote (two consecutive quotes)
            if i + 1 < length and text[i + 1] == "'":
                i += 1
            else:
                in_quote = False

        if not in_quote:
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    return i
        i += 1

    raise ValueError("Matching parenthesis not found")

def parse_values(values_str):
    """Split a VALUES clause into individual value strings, respecting quotes."""
    values = []
    current = []
    in_quote = False
    i = 0
    length = len(values_str)

    while i < length:
        ch = values_str[i]

        if ch == "'" and not in_quote:
            in_quote = True
            current.append(ch)
        elif ch == "'" and in_quote:
            if i + 1 < length and values_str[i + 1] == "'":
                current.append("''")
                i += 1
            else:
                in_quote = False
                current.append(ch)
        elif ch == ',' and not in_quote:
            values.append(''.join(current).strip())
            current = []
        else:
            current.append(ch)

        i += 1

    if current:
        values.append(''.join(current).strip())

    return values

def convert_value_for_postgres(val):
    """
    Convert a SQL Server value (e.g. N'...', 42, NULL) to PostgreSQL syntax.
    Removes the N prefix and keeps the surrounding single quotes.
    """
    val = val.strip()
    if val.upper() == 'NULL':
        return 'NULL'

    # Match N'...' (case‑insensitive)
    if re.match(r"^N'", val, re.IGNORECASE) and val.endswith("'"):
        inner = val[2:-1]          # strip N and outer quotes
        return "'" + inner + "'"

    # Already quoted with single quotes (e.g. 'text')
    if val.startswith("'") and val.endswith("'"):
        return val

    # Numbers or other literals
    return val

def extract_table_name(ident):
    """Extract the base table name from a possibly schema‑qualified identifier."""
    no_brackets = re.sub(r'[\[\]]', '', ident)
    return no_brackets.split('.')[-1]

def to_sql_server_identifier(ident):
    """Convert an identifier into a fully bracketed SQL Server identifier."""
    parts = re.split(r'\.', ident)
    return '.'.join(f'[{part.strip("[]")}]' for part in parts)

def to_postgres_identifier(ident):
    """Convert an identifier (with brackets) into a plain PostgreSQL identifier."""
    return re.sub(r'[\[\]]', '', ident)

def convert_special_columns(val, col_name):
    """
    For columns 'discogs_id' and 'instrument_id', convert quoted numeric strings
    to unquoted numbers, and quoted 'null' (case-insensitive) to unquoted NULL.
    For all other columns, return the value unchanged.
    """
    col_lower = col_name.lower()
    if col_lower not in ('discogs_id', 'instrument_id'):
        return val

    # Value may be like: '123', 'null', 'NULL', or already unquoted? (should be quoted from parse)
    # Check if it's a quoted string
    if val.startswith("'") and val.endswith("'"):
        inner = val[1:-1]  # remove outer quotes
        # If inner is a number (integer)
        if inner.isdigit() or (inner.startswith('-') and inner[1:].isdigit()):
            return inner
        # If inner is 'null' (case-insensitive)
        if inner.upper() == 'NULL':
            return 'NULL'
    # Otherwise, keep as is (maybe already unquoted number or NULL)
    return val

def main():
    if len(sys.argv) != 2:
        print("Usage: python add_insert_id.py <input.sql>")
        sys.exit(1)

    input_file = sys.argv[1]

    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()

    content = strip_sql_comments(content)

    # Split into individual INSERT statements (simple split on "INSERT" keyword)
    raw_statements = re.split(r'\bINSERT\s+', content, flags=re.IGNORECASE)
    statements = []
    for stmt in raw_statements[1:]:  # first chunk is empty or leading text
        if stmt.strip():
            statements.append("INSERT " + stmt.strip())

    if not statements:
        print("No INSERT statements found.")
        sys.exit(1)

    counters = {}          # per‑table ID counters (key = lowercased base table name)
    mssql_lines = []
    pg_lines = []

    for full_stmt in statements:
        # ---- Extract table identifier ----
        m = re.match(r'INSERT\s+(?:INTO\s+)?([^\s\(]+)', full_stmt, re.IGNORECASE)
        if not m:
            print(f"Could not parse table from: {full_stmt[:60]}...")
            continue
        table_ident = m.group(1)

        # ---- Extract column list (inside first parentheses) ----
        col_start = full_stmt.find('(', m.end())
        if col_start == -1:
            print(f"No column list found in: {full_stmt[:60]}...")
            continue
        col_end = find_matching_parenthesis(full_stmt, col_start)
        cols_str = full_stmt[col_start+1:col_end].strip()

        # ---- Find VALUES keyword and extract value list ----
        values_pos = full_stmt.upper().find('VALUES', col_end)
        if values_pos == -1:
            print(f"No VALUES keyword in: {full_stmt[:60]}...")
            continue
        val_start = full_stmt.find('(', values_pos + 6)
        if val_start == -1:
            print(f"No value list parentheses in: {full_stmt[:60]}...")
            continue
        val_end = find_matching_parenthesis(full_stmt, val_start)
        vals_str = full_stmt[val_start+1:val_end].strip()

        # Parse columns and values
        original_cols = [c.strip() for c in cols_str.split(',') if c.strip()]
        original_vals = parse_values(vals_str)

        if len(original_cols) != len(original_vals):
            print(f"Warning: column/value count mismatch for table {table_ident}. Skipping.")
            continue

        # Apply special conversion for discogs_id and instrument_id
        converted_vals = []
        for i, val in enumerate(original_vals):
            col_name = original_cols[i].strip('[]')  # remove brackets for name matching
            converted_vals.append(convert_special_columns(val, col_name))

        # Determine ID and counter
        base_table = extract_table_name(table_ident)
        base_lower = base_table.lower()
        counter = counters.get(base_lower, 0) + 1
        counters[base_lower] = counter

        # ----- SQL Server output -----
        mssql_table_ref = to_sql_server_identifier(table_ident)
        id_column_mssql = f"[{base_lower}_id]"
        mssql_cols = [id_column_mssql] + original_cols
        mssql_vals = [str(counter)] + converted_vals   # use converted values
        mssql_stmt = f"INSERT {mssql_table_ref} ({', '.join(mssql_cols)}) VALUES ({', '.join(mssql_vals)})"
        mssql_lines.append(mssql_stmt)

        # ----- PostgreSQL output -----
        pg_table_ref = to_postgres_identifier(table_ident)
        id_column_pg = f"{base_lower}_id"
        pg_cols = [id_column_pg] + [c.strip('[]') for c in original_cols]
        # Convert each value for PostgreSQL (handles N'...', keeps numbers/NULL as is)
        pg_vals = [str(counter)] + [convert_value_for_postgres(v) for v in converted_vals]
        pg_stmt = f"INSERT INTO {pg_table_ref} ({', '.join(pg_cols)}) VALUES ({', '.join(pg_vals)});"
        pg_lines.append(pg_stmt)

    # Write output files
    base, ext = os.path.splitext(input_file)
    mssql_file = base + "_mssql" + ext
    pg_file = base + "_postgres" + ext

    with open(mssql_file, 'w', encoding='utf-8') as f:
        f.write('\n'.join(mssql_lines))

    with open(pg_file, 'w', encoding='utf-8') as f:
        f.write('\n'.join(pg_lines))

    print(f"Processed {len(statements)} INSERT statements.")
    print(f"SQL Server output: {mssql_file}")
    print(f"PostgreSQL output: {pg_file}")

if __name__ == "__main__":
    main()