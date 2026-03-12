#!/usr/bin/env python3
"""
Add an auto-increment ID column to INSERT statements from either SQL Server or
PostgreSQL input, and convert to both Microsoft SQL Server and PostgreSQL syntax.

Usage:
    python add_insert_id.py <input.sql>

Output:
    input_mssql.sql    – SQL Server style (brackets, N'...' preserved)
    input_postgres.sql – PostgreSQL style (standard, semicolon terminated)
"""

import re
import sys
import os


def parse_values(values_str):
    """
    Split a VALUES clause into individual value strings, respecting
    single‑quoted strings (including escaped double quotes).
    """
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
            # escaped single quote (two consecutive quotes)
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
    """
    Extract the base table name from a possibly schema‑qualified and/or
    bracket‑enclosed identifier.
    Examples:
        '[dbo].[video]'   -> 'video'
        'public.video'    -> 'video'
        'video'           -> 'video'
        '[video]'         -> 'video'
    """
    # Remove all brackets
    no_brackets = re.sub(r'[\[\]]', '', ident)
    # Split on dot and return the last part
    parts = no_brackets.split('.')
    return parts[-1]


def to_sql_server_identifier(ident):
    """
    Convert an identifier (possibly with schema and/or brackets) into a fully
    bracketed SQL Server identifier.
    Example: 'dbo.video' -> '[dbo].[video]'
             '[dbo].[video]' -> '[dbo].[video]'
    """
    # Split on dots, strip existing brackets from each part, then re‑bracket
    parts = re.split(r'\.', ident)
    bracketed = []
    for part in parts:
        part = part.strip('[]')
        bracketed.append(f'[{part}]')
    return '.'.join(bracketed)


def to_postgres_identifier(ident):
    """
    Convert an identifier (possibly with brackets) into a plain PostgreSQL
    identifier (no brackets).
    """
    return re.sub(r'[\[\]]', '', ident)


def main():
    if len(sys.argv) != 2:
        print("Usage: python add_insert_id.py <input.sql>")
        sys.exit(1)

    input_file = sys.argv[1]

    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Regex to match INSERT statements of either dialect.
    # Captures: table identifier, column list, value list.
    # Lookahead ensures we stop before the next INSERT or end of file.
    pattern = re.compile(
        r'INSERT\s+(?:INTO\s+)?([^\s\(]+)\s*\((.*?)\)\s*VALUES\s*\((.*?)\)\s*;?\s*(?=INSERT\s+(?:INTO\s+)?[^\s\(]+|\Z)',
        re.DOTALL | re.IGNORECASE
    )

    statements = pattern.findall(content)
    if not statements:
        print("No INSERT statements found.")
        sys.exit(1)

    counters = {}          # per‑table ID counters (key = lowercased base table name)
    mssql_lines = []
    pg_lines = []

    for table_ident, cols_str, vals_str in statements:
        # Extract base table name for the ID column and counter
        base_table = extract_table_name(table_ident)
        base_lower = base_table.lower()
        counter = counters.get(base_lower, 0) + 1
        counters[base_lower] = counter

        # Parse columns (simple comma split – identifiers are simple)
        original_cols = [col.strip() for col in cols_str.split(',')]

        # Parse values (handles quoted strings with commas)
        original_vals = parse_values(vals_str)

        if len(original_cols) != len(original_vals):
            print(f"Warning: column/value count mismatch for table {table_ident}. Skipping.")
            continue

        # ----- SQL Server output -----
        # Build table reference (bracketed parts)
        mssql_table_ref = to_sql_server_identifier(table_ident)
        id_column_mssql = f"[{base_lower}_id]"
        mssql_cols = [id_column_mssql] + original_cols   # original columns keep their brackets
        mssql_vals = [str(counter)] + original_vals
        mssql_stmt = f"INSERT {mssql_table_ref} ({', '.join(mssql_cols)}) VALUES ({', '.join(mssql_vals)})"
        mssql_lines.append(mssql_stmt)

        # ----- PostgreSQL output -----
        # Build table reference (no brackets)
        pg_table_ref = to_postgres_identifier(table_ident)
        id_column_pg = f"{base_lower}_id"
        # Strip brackets from original columns
        pg_cols = [id_column_pg] + [col.strip('[]') for col in original_cols]
        # Convert values (strip N prefix etc.)
        pg_vals = [str(counter)] + [convert_value_for_postgres(v) for v in original_vals]
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