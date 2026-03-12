#!/usr/bin/env python3
"""
Add an auto-increment ID column to INSERT statements and convert to
Microsoft SQL Server and PostgreSQL syntax.

Usage:
    python add_insert_id.py input.sql

Output:
    input_mssql.sql    – SQL Server style (brackets, N'...')
    input_postgres.sql – PostgreSQL style (standard, semicolon)
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


def main():
    if len(sys.argv) != 2:
        print("Usage: python add_insert_id.py <input.sql>")
        sys.exit(1)

    input_file = sys.argv[1]

    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Improved regex: matches each INSERT statement, allowing an optional semicolon
    # before the next INSERT or end of file.
    pattern = re.compile(
        r'INSERT\s+\[dbo\]\.\[(\w+)\]\s*\((.*?)\)\s*VALUES\s*\((.*?)\)\s*;?\s*(?=INSERT\s+\[dbo\]\.\[\w+\]|\Z)',
        re.DOTALL | re.IGNORECASE
    )

    statements = pattern.findall(content)
    if not statements:
        print("No INSERT statements found.")
        sys.exit(1)

    counters = {}          # per‑table ID counters
    mssql_lines = []
    pg_lines = []

    for table, cols_str, vals_str in statements:
        # Next ID for this table
        counter = counters.get(table, 0) + 1
        counters[table] = counter

        # Parse columns (simple comma split, identifiers are simple)
        original_cols = [col.strip() for col in cols_str.split(',')]

        # Parse values (handles quoted strings with commas)
        original_vals = parse_values(vals_str)

        if len(original_cols) != len(original_vals):
            print(f"Warning: column/value count mismatch for table {table}. Skipping.")
            continue

        # ----- SQL Server output -----
        # Create lowercase column name for the ID
        id_column_lower = f"[{table.lower()}_id]"
        mssql_cols = [id_column_lower] + original_cols
        mssql_vals = [str(counter)] + original_vals
        mssql_stmt = f"INSERT [dbo].[{table}] ({', '.join(mssql_cols)}) VALUES ({', '.join(mssql_vals)})"
        mssql_lines.append(mssql_stmt)

        # ----- PostgreSQL output -----
        # Strip brackets from column names, add the new id column (lowercase)
        pg_cols = [f"{table.lower()}_id"] + [col.strip('[]') for col in original_cols]
        pg_vals = [str(counter)] + [convert_value_for_postgres(v) for v in original_vals]
        pg_stmt = f"INSERT INTO {table} ({', '.join(pg_cols)}) VALUES ({', '.join(pg_vals)});"
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