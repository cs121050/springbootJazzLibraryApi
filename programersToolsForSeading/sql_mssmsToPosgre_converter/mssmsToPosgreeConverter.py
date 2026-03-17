#!/usr/bin/env python3
"""
Convert MSSQL INSERT statements to PostgreSQL format.
Usage: python mssql_to_postgres.py input.sql output.sql
"""

import sys

def convert_line(line):
    """Simple string replacement approach"""
    line = line.strip()
    if not line:
        return line
    
    # Check if this is an INSERT line we want to convert
    if "INSERT [dbo]." in line:
        # Replace the pattern
        # FROM: INSERT [dbo].[Artist] ([artist_id], [spotify_playlist_id], ...) VALUES (...)
        # TO:   INSERT INTO Artist (artist_id, spotify_playlist_id, ...) VALUES (...);
        
        # Remove [dbo].
        line = line.replace("[dbo].", "")
        
        # Remove brackets from column names
        line = line.replace("[", "").replace("]", "")
        
        # Add INTO after INSERT
        line = line.replace("INSERT ", "INSERT INTO ")
        
        # Add semicolon at the end if not present
        if not line.endswith(';'):
            line = line + ';'
    
    return line

def main():
    if len(sys.argv) != 3:
        print("Usage: python mssql_to_postgres.py input.sql output.sql")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    try:
        with open(input_file, 'r', encoding='utf-8') as infile:
            lines = infile.readlines()
        
        with open(output_file, 'w', encoding='utf-8') as outfile:
            for line in lines:
                converted = convert_line(line)
                outfile.write(converted + '\n')
        
        print(f"Done! Output written to {output_file}")
    
    except FileNotFoundError:
        print(f"Error: File '{input_file}' not found")
        sys.exit(1)

if __name__ == "__main__":
    main()