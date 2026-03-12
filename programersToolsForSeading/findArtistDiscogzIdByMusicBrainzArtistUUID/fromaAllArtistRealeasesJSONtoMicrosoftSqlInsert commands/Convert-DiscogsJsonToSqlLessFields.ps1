<#
.SYNOPSIS
    Converts a Discogs JSON Lines file into SQL INSERT statements for the Album
    and AlbumContainsArtist tables, with proper escaping for SQL Server.

.DESCRIPTION
    Reads a file containing one Discogs JSON object per line (masters or releases),
    extracts the fields defined in the Album entity, and produces single‑line
    INSERT statements. Handles both masters and releases, converting JSON arrays
    to compact strings. Escapes single quotes, doubles backslashes, and **for SQL Server**
    replaces every ';' with ' + CHAR(59) + ' and every '--' with ' + CHAR(45) + CHAR(45) + '
    to prevent the SQL parser from splitting statements prematurely.

    The following fields have been removed from the Album INSERT:
    date_added, date_changed, companies, genres, master_url, thumb.

    The 'images' field is kept but filtered to include only primary images
    (i.e., images with "type": "primary").

.PARAMETER InputFile
    Path to the .jsonl file (output from the Discogs data collection script).

.PARAMETER OutputFile
    Path where the SQL script will be saved. Defaults to "discogs_inserts.sql".

.PARAMETER DatabaseType
    Target database: "SQLServer" (default), "MySQL", or "PostgreSQL".  
    Affects backslash handling and the syntax used for replacing problematic characters.

.PARAMETER NoReplaceDoubleDash
    Switch. If present, the automatic replacement of '--' is disabled. Use only if
    you are certain your database does not treat '--' as a comment inside strings.

.EXAMPLE
    .\Convert-DiscogsJsonToSql.ps1 -InputFile "discogs_releases.jsonl"
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$InputFile,

    [string]$OutputFile = "discogs_inserts.sql",

    [ValidateSet("MySQL", "PostgreSQL", "SQLServer")]
    [string]$DatabaseType = "SQLServer",   # Default changed to SQLServer

    [switch]$NoReplaceDoubleDash
)

# Replacement is ON by default; turn off only if -NoReplaceDoubleDash is used.
$ReplaceDoubleDash = -not $NoReplaceDoubleDash

# ------------------------------------------------------------------------------
# Helper functions
# ------------------------------------------------------------------------------

# Escapes a string for safe inclusion in an SQL statement.
function Escape-SqlString {
    param([string]$value)
    if ($null -eq $value) { return $null }

    # 1. Escape single quotes (SQL standard)
    $value = $value -replace "'", "''"

    # 2. Double backslashes (required for literal backslashes in all databases)
    $value = $value -replace "\\", "\\$0"

    # 3. Replace newlines/carriage returns with spaces to keep each INSERT on one line
    $value = $value -replace "`r`n", " "
    $value = $value -replace "`n", " "
    $value = $value -replace "`r", " "

    # 4. If replacement is enabled, replace '--' with a safe concatenation
    if ($ReplaceDoubleDash) {
        switch ($DatabaseType) {
            "PostgreSQL" { $dashReplacement = "CHR(45) || CHR(45)" }
            "MySQL"      { $dashReplacement = "CONCAT(CHAR(45), CHAR(45))" }
            "SQLServer"  { $dashReplacement = "CHAR(45) + CHAR(45)" }
        }
        $value = $value -replace "--", $dashReplacement
        Write-Verbose "Replaced '--' with $dashReplacement"
    }

    # 5. For SQL Server only: replace every semicolon with ' + CHAR(59) + '
    if ($DatabaseType -eq "SQLServer") {
        $original = $value
        $value = $value -replace ";", "' + CHAR(59) + '"
        if ($original -ne $value) {
            Write-Verbose "Replaced semicolons in string"
        }
    }

    return $value
}

# Converts a PowerShell object (array or hashtable) to a compact JSON string.
function ConvertTo-JsonString {
    param($obj)
    if ($obj -eq $null) { return $null }
    return ($obj | ConvertTo-Json -Compress -Depth 10)
}

# Filters the images array to keep only those with type "primary".
function Get-PrimaryImages {
    param($images)
    if (-not $images) { return $null }
    $primary = $images | Where-Object { $_.type -eq "primary" }
    if ($primary.Count -eq 0) { return $null }
    return ConvertTo-JsonString $primary
}

# Extracts rating count from the 'community' object.
function Get-RatingCount {
    param($community)
    if ($community -and $community.rating -and ($community.rating.count -ne $null)) {
        return $community.rating.count
    }
    return $null
}

# Extracts rating average from the 'community' object.
function Get-RatingAverage {
    param($community)
    if ($community -and $community.rating -and ($community.rating.average -ne $null)) {
        return $community.rating.average
    }
    return $null
}

# ------------------------------------------------------------------------------
# Main script
# ------------------------------------------------------------------------------

# Validate input file
if (-not (Test-Path $InputFile)) {
    Write-Error "Input file '$InputFile' not found."
    exit 1
}

# Open input file and prepare output
$reader = New-Object System.IO.StreamReader($InputFile)
$writer = New-Object System.IO.StreamWriter($OutputFile)

# Write header comments
$writer.WriteLine("-- SQL INSERT statements generated by Convert-DiscogsJsonToSql.ps1")
$writer.WriteLine("-- Source file: $InputFile")
$writer.WriteLine("-- Generated on: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$writer.WriteLine("-- Database dialect: $DatabaseType")
$writer.WriteLine("-- Fields excluded from Album: date_added, date_changed, companies, genres, master_url, thumb")
$writer.WriteLine("-- Images column: only primary images are kept (type='primary')")
if ($ReplaceDoubleDash) {
    $writer.WriteLine("-- Double dash replacement enabled")
}
if ($DatabaseType -eq "SQLServer") {
    $writer.WriteLine("-- Semicolon replacement enabled (semicolons inside strings become ' + CHAR(59) + ')")
}
$writer.WriteLine("")

# Start transaction
$writer.WriteLine("START TRANSACTION;")
$writer.WriteLine("")

$lineNumber = 0
while (($line = $reader.ReadLine()) -ne $null) {
    $lineNumber++
    Write-Progress -Activity "Processing lines" -Status "Line $lineNumber" -PercentComplete -1

    try {
        $obj = $line | ConvertFrom-Json
    }
    catch {
        Write-Warning "Line $lineNumber is not valid JSON. Skipping."
        continue
    }

    # Determine if this is a master or a release
    $isMaster = ($obj.PSObject.Properties.Name -contains "main_release")

    if ($isMaster) {
        # Master object: use main_release as release_id, and store master_id
        $release_id = $obj.main_release
        $master_id = $obj.id
        $title = $obj.title
        $year = if ($obj.year -ne $null) { $obj.year } else { $null }
        $country = $null
        $released = $null
        $released_formatted = $null
        $notes = $obj.notes
        $styles = ConvertTo-JsonString $obj.styles
        $uri = $obj.uri
        $extra_artists = $null
        $labels = ConvertTo-JsonString $obj.labels
        $tracklist = ConvertTo-JsonString $obj.tracklist
        $videos = ConvertTo-JsonString $obj.videos
        $rating_count = Get-RatingCount $obj.community
        $rating_average = Get-RatingAverage $obj.community
        # Filter images to only primary
        $images = Get-PrimaryImages $obj.images

        # Fields excluded: date_added, date_changed, companies, genres, master_url, thumb
    }
    else {
        # Release object
        $release_id = $obj.id
        $master_id = if ($obj.PSObject.Properties.Name -contains "master_id") { $obj.master_id } else { $null }
        $title = $obj.title
        $year = $obj.year
        $country = $obj.country
        $released = $obj.released
        $released_formatted = $obj.released_formatted
        $notes = $obj.notes
        $styles = ConvertTo-JsonString $obj.styles
        $uri = $obj.uri
        $extra_artists = ConvertTo-JsonString $obj.extraartists
        $labels = ConvertTo-JsonString $obj.labels
        $tracklist = ConvertTo-JsonString $obj.tracklist
        $videos = ConvertTo-JsonString $obj.videos
        $rating_count = Get-RatingCount $obj.community
        $rating_average = Get-RatingAverage $obj.community
        # Filter images to only primary
        $images = Get-PrimaryImages $obj.images

        # Fields excluded: date_added, date_changed, companies, genres, master_url, thumb
    }

    # Escape all string values (only those still used; thumb removed)
    $title_esc = Escape-SqlString $title
    $country_esc = Escape-SqlString $country
    $released_esc = Escape-SqlString $released
    $released_formatted_esc = Escape-SqlString $released_formatted
    $notes_esc = Escape-SqlString $notes
    $styles_esc = Escape-SqlString $styles
    $uri_esc = Escape-SqlString $uri
    $extra_artists_esc = Escape-SqlString $extra_artists
    $labels_esc = Escape-SqlString $labels
    $tracklist_esc = Escape-SqlString $tracklist
    $videos_esc = Escape-SqlString $videos
    $images_esc = Escape-SqlString $images

    # Build the INSERT statement for Album as a single line (thumb column removed)
    $sqlAlbum = "INSERT INTO Album (release_id, title, year, country, released, released_formatted, notes, styles, master_id, uri, extra_artists, labels, tracklist, videos, images, rating_count, rating_average) VALUES ("
    $sqlAlbum += "$release_id, "
    $sqlAlbum += "'$title_esc', "
    $sqlAlbum += "$(if ($year -ne $null) { $year } else { "NULL" }), "
    $sqlAlbum += "$(if ($country_esc) { "'$country_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($released_esc) { "'$released_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($released_formatted_esc) { "'$released_formatted_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($notes_esc) { "'$notes_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($styles_esc) { "'$styles_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($master_id -ne $null) { $master_id } else { "NULL" }), "
    $sqlAlbum += "$(if ($uri_esc) { "'$uri_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($extra_artists_esc) { "'$extra_artists_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($labels_esc) { "'$labels_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($tracklist_esc) { "'$tracklist_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($videos_esc) { "'$videos_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($images_esc) { "'$images_esc'" } else { "NULL" }), "
    $sqlAlbum += "$(if ($rating_count -ne $null) { $rating_count } else { "NULL" }), "
    $sqlAlbum += "$(if ($rating_average -ne $null) { $rating_average } else { "NULL" })"
    $sqlAlbum += ");"

    $writer.WriteLine($sqlAlbum)

    # AlbumContainsArtist records (each on one line)
    if ($obj.artists -and $obj.artists.Count -gt 0) {
        foreach ($artist in $obj.artists) {
            $discogsArtistId = $artist.id
            if (-not $discogsArtistId) { continue }
            $writer.WriteLine("INSERT INTO AlbumContainsArtist (discogs_artist_id, discogs_release_id, is_main) VALUES ($discogsArtistId, $release_id, 1);")
        }
    }

    if ($obj.extraartists -and $obj.extraartists.Count -gt 0) {
        foreach ($artist in $obj.extraartists) {
            $discogsArtistId = $artist.id
            if (-not $discogsArtistId) { continue }
            $writer.WriteLine("INSERT INTO AlbumContainsArtist (discogs_artist_id, discogs_release_id, is_main) VALUES ($discogsArtistId, $release_id, 0);")
        }
    }

    # Blank line between records for readability
    $writer.WriteLine("")
}

# Commit transaction
$writer.WriteLine("COMMIT;")

$reader.Close()
$writer.Close()

Write-Host "SQL script written to $OutputFile"
Write-Host "Database dialect: $DatabaseType"
if ($DatabaseType -eq "SQLServer") {
    Write-Host "Semicolon replacement was enabled – all semicolons inside strings have been replaced with ' + CHAR(59) + '."
}