<#
.SYNOPSIS
    Convert discogs_id values in SQL INSERT statements between quoted (string) and unquoted (integer),
    and write the result to a new file.

.DESCRIPTION
    This script reads an SQL file containing INSERT statements for the [dbo].[Artist] table.
    It locates the discogs_id value (the fifth value in the VALUES list) and either adds or removes
    single quotes based on the user's choice. The modified content is written to a new file,
    leaving the original unchanged.

.PARAMETER FilePath
    Path to the source SQL file to process. If not provided, the script will prompt for it.

.PARAMETER Option
    Conversion option: 1 to add quotes (convert from integer to string), 2 to remove quotes (convert from string to integer).
    If not provided, the script will prompt for it.

.PARAMETER OutputFilePath
    Path where the converted SQL file will be saved. If not provided, the script creates a file
    in the same folder as the source, with "_converted" appended before the extension.

.EXAMPLE
    .\Convert-DiscogsId.ps1 -FilePath "C:\scripts\data.sql" -Option 1
    Adds quotes and saves to "C:\scripts\data_converted.sql".

.EXAMPLE
    .\Convert-DiscogsId.ps1 -FilePath "C:\scripts\data.sql" -Option 2 -OutputFilePath "C:\scripts\data_noquotes.sql"
    Removes quotes and saves to the specified output file.

.EXAMPLE
    .\Convert-DiscogsId.ps1
    Prompts for file and option interactively, then generates an output file automatically.
#>

param(
    [string]$FilePath,
    [int]$Option,
    [string]$OutputFilePath
)

# Function to prompt for file if not provided
function Get-FilePath {
    do {
        $path = Read-Host "Please enter the path to the source SQL file"
        if (Test-Path $path) {
            return $path
        } else {
            Write-Host "File not found. Please try again." -ForegroundColor Red
        }
    } while ($true)
}

# Function to prompt for option if not provided
function Get-Option {
    do {
        Write-Host "`nChoose conversion option:"
        Write-Host "  1. Add quotes (convert from integer to string)"
        Write-Host "  2. Remove quotes (convert from string to integer)"
        $choice = Read-Host "Enter 1 or 2"
        if ($choice -eq '1' -or $choice -eq '2') {
            return [int]$choice
        } else {
            Write-Host "Invalid choice. Please enter 1 or 2." -ForegroundColor Red
        }
    } while ($true)
}

# --- Main script ---

# Get source file path
if (-not $FilePath) {
    $FilePath = Get-FilePath
} elseif (-not (Test-Path $FilePath)) {
    Write-Host "Error: File '$FilePath' does not exist." -ForegroundColor Red
    exit 1
}

# Get option
if ($Option -notin 1,2) {
    $Option = Get-Option
}

# Determine output file path
if (-not $OutputFilePath) {
    $directory = [System.IO.Path]::GetDirectoryName($FilePath)
    $filename = [System.IO.Path]::GetFileNameWithoutExtension($FilePath)
    $extension = [System.IO.Path]::GetExtension($FilePath)
    $OutputFilePath = Join-Path $directory ($filename + "_converted" + $extension)
    Write-Host "Output file will be: $OutputFilePath" -ForegroundColor Cyan
}

# Read source file content
try {
    $lines = Get-Content $FilePath -ReadCount 0
} catch {
    Write-Host "Error reading file: $_" -ForegroundColor Red
    exit 1
}

# Regular expression to match an INSERT line and capture the discogs_id with surrounding spaces
# Groups:
#   1 = everything up to and including the comma before discogs_id
#   2 = leading spaces after that comma
#   3 = discogs_id value (could be quoted, unquoted number, or NULL)
#   4 = trailing spaces before the next comma
#   5 = the rest of the line starting with a comma
$pattern = [regex] '(INSERT.*?VALUES\s*\([^,]+,[^,]+,[^,]+,[^,]+,)(\s*)(''[^'']*''|\d+|NULL)(\s*)(,.+\))'

$modified = $false
$newLines = foreach ($line in $lines) {
    $match = $pattern.Match($line)
    if ($match.Success) {
        $prefix = $match.Groups[1].Value
        $leadingSpaces = $match.Groups[2].Value
        $discogs = $match.Groups[3].Value
        $trailingSpaces = $match.Groups[4].Value
        $suffix = $match.Groups[5].Value

        $originalDiscogs = $discogs
        $newDiscogs = $discogs

        if ($Option -eq 1) {
            # Add quotes: if discogs is a plain number (not NULL and not already quoted)
            if ($discogs -match '^\d+$') {
                $newDiscogs = "'$discogs'"
            }
            # If it's already quoted or NULL, leave unchanged
        } elseif ($Option -eq 2) {
            # Remove quotes: if discogs is quoted, strip the quotes
            if ($discogs -match "^'(.+)'$") {
                $newDiscogs = $matches[1]
            }
            # If it's a number or NULL, leave unchanged
        }

        if ($newDiscogs -ne $originalDiscogs) {
            $line = $prefix + $leadingSpaces + $newDiscogs + $trailingSpaces + $suffix
            $modified = $true
        }
    }
    # Output the (possibly modified) line
    $line
}

if (-not $modified) {
    Write-Host "No changes were made. All discogs_id values already match the desired format." -ForegroundColor Yellow
    # Still write the original content to output file? Probably yes, to have a file anyway.
}

# Write the result to the output file
try {
    $newLines | Set-Content $OutputFilePath -Encoding UTF8
    Write-Host "Converted file successfully created: $OutputFilePath" -ForegroundColor Green
} catch {
    Write-Host "Error writing output file: $_" -ForegroundColor Red
    exit 1
}