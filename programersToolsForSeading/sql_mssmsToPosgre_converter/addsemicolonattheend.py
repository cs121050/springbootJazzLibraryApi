import os
import sys

def add_semicolons_to_sql(input_file, output_file=None):
    """
    Reads an SQL file and adds a semicolon at the end of each non-empty line.
    
    Args:
        input_file (str): Path to the input SQL file
        output_file (str): Path to the output file (if None, overwrites input file)
    """
    
    # If no output file specified, create a backup and overwrite input
    if output_file is None:
        output_file = input_file
        backup_file = input_file + '.backup'
        
        # Create backup
        import shutil
        shutil.copy2(input_file, backup_file)
        print(f"Backup created: {backup_file}")
    
    try:
        # Read the input file
        with open(input_file, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        # Process each line
        processed_lines = []
        line_count = 0
        semicolon_added_count = 0
        
        for i, line in enumerate(lines):
            # Remove trailing whitespace
            stripped_line = line.rstrip()
            
            # Check if line is not empty and doesn't already end with semicolon
            if stripped_line and not stripped_line.endswith(';'):
                # Add semicolon and preserve original line ending
                processed_line = stripped_line + ';' + line[len(stripped_line):]
                semicolon_added_count += 1
            else:
                # Keep line as is (empty lines or lines already ending with ;)
                processed_line = line
            
            processed_lines.append(processed_line)
            line_count += 1
        
        # Write to output file
        with open(output_file, 'w', encoding='utf-8') as f:
            f.writelines(processed_lines)
        
        print(f"Successfully processed {line_count} lines")
        print(f"Added semicolons to {semicolon_added_count} lines")
        print(f"Output file: {output_file}")
        
    except FileNotFoundError:
        print(f"Error: File '{input_file}' not found.")
    except Exception as e:
        print(f"Error: {str(e)}")

def main():
    # Check command line arguments
    if len(sys.argv) < 2:
        print("Usage: python add_semicolons.py <input_file> [output_file]")
        print("  input_file   : Path to the SQL file to process")
        print("  output_file  : (Optional) Path for output file")
        print("                If not specified, overwrites input file with backup")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else None
    
    # Validate input file
    if not os.path.exists(input_file):
        print(f"Error: File '{input_file}' does not exist.")
        sys.exit(1)
    
    add_semicolons_to_sql(input_file, output_file)

if __name__ == "__main__":
    main()