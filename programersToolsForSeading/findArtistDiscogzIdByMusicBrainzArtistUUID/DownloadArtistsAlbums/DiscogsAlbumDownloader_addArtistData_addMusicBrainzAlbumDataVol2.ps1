<#
.SYNOPSIS
    Fetches Discogs release data for artists listed in an SQL INSERT file,
    enriches each album with matching MusicBrainz data from a JSONL file,
    and outputs one JSON object per album.
.DESCRIPTION
    This script:
      1. Reads a SQL file containing INSERT statements for the [dbo].[Artist] table,
         extracting discogs_id, musicbrainz_uuid, artist_name, artist_surname, wikipedia_url.
         The SQL now includes an `artist_id` field as the first column.
      2. Reads a JSONL file (mb_albums_ofartists.jsonl) containing album metadata from MusicBrainz,
         and builds a lookup table keyed by the Discogs album ID found inside each record.
         The ID is taken from `release_group.discogs_id` if present, otherwise from
         `release_group.release_discogs_id`.
      3. For each unique Discogs artist ID, queries the Discogs API for all releases where
         the artist has role "Main", retrieves full metadata for each album (master or standalone),
         and if the album's Discogs ID exists in the lookup table, injects the corresponding
         MusicBrainz data under a new property "MusicBrainzData".
      4. Outputs JSON Lines (.jsonl) where each line contains:
         - "artist": artist metadata from the SQL file
         - "DiscogsAPIcall": the full Discogs release/master data
         - "MusicBrainzData": (optional) the matching MusicBrainz album data
      5. Deduplicates albums that belong to the same master.
      6. For each master release, also fetches all version IDs from the Discogs
         "/masters/{id}/versions" endpoint and adds them as a new field
         "discogs_all_versions_Ids" inside the DiscogsAPIcall object.
      7. Respects Discogs rate limits (25 requests/minute for unauthenticated calls).
.PARAMETER SqlFile
    Path to the SQL file containing the INSERT statements (now includes `artist_id` as first column).
.PARAMETER MusicBrainzFile
    Path to the JSONL file containing MusicBrainz album data (default: "mb_albums_ofartists.jsonl").
.PARAMETER OutputFile
    Path where the output .jsonl file will be saved (default: "discogs_releases.jsonl").
.EXAMPLE
    .\DiscogsAlbumDownloader_addArtistData_addMusicBrainzAlbumDataVol2.ps1 -SqlFile "artists_with_wiki_INPUT.sql" -MusicBrainzFile "mb_albums_enriched.jsonl"
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$SqlFile,

    [string]$MusicBrainzFile = "mb_albums_ofartists.jsonl",

    [string]$OutputFile = ".\discogs_releases.jsonl"
)

# ------------------------------------------------------------
# Configuration – HARDCODED BASE URL
# ------------------------------------------------------------
$baseApiUrl = "https://api.discogs.com"
$minRequestIntervalMs = 2500                                # 25 requests/minute = 2.5 seconds
$userAgent = "DiscogsDataCollector/1.0 +https://example.com"

if ([string]::IsNullOrWhiteSpace($baseApiUrl)) {
    Write-Error "FATAL: baseApiUrl is not set. Check the script configuration."
    exit 1
}
Write-Host "Base API URL: $baseApiUrl"

# ------------------------------------------------------------
# Initialize last request time (PowerShell 5.1 compatible)
# ------------------------------------------------------------
if (-not (Get-Variable -Name 'lastRequestTime' -Scope Script -ErrorAction SilentlyContinue)) {
    $script:lastRequestTime = (Get-Date).AddMilliseconds(-$minRequestIntervalMs)
}

# ------------------------------------------------------------
# Helper function: Invoke-DiscogsApi (with rate limiting & retries)
# ------------------------------------------------------------
function Invoke-DiscogsApi {
    param([string]$Url)

    # Enforce minimum time between requests
    $now = Get-Date
    $timeSinceLast = ($now - $script:lastRequestTime).TotalMilliseconds
    if ($timeSinceLast -lt $minRequestIntervalMs) {
        $sleepMs = $minRequestIntervalMs - $timeSinceLast
        Write-Host "Rate limiting: sleeping $sleepMs ms"
        Start-Sleep -Milliseconds $sleepMs
    }

    $headers = @{ "User-Agent" = $userAgent }
    $retryCount = 0
    $maxRetries = 3
    $baseDelaySec = 5

    while ($retryCount -le $maxRetries) {
        try {
            Write-Host "Calling API: $Url"
            $response = Invoke-RestMethod -Uri $Url -Headers $headers -ErrorAction Stop
            $script:lastRequestTime = Get-Date
            return $response
        }
        catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            Write-Warning "Request to $Url failed with HTTP $statusCode"

            # Retry on 429 (rate limit) or 5xx errors
            if ($statusCode -eq 429 -or ($statusCode -ge 500 -and $statusCode -le 599)) {
                if ($retryCount -lt $maxRetries) {
                    $retryCount++
                    $waitSec = $baseDelaySec * [math]::Pow(2, $retryCount - 1)
                    Write-Warning "Retry $retryCount/$maxRetries after $waitSec seconds"
                    Start-Sleep -Seconds $waitSec
                    continue
                }
            }
            # For other errors, rethrow
            throw
        }
    }
    throw "Maximum retries exceeded for $Url"
}

# ------------------------------------------------------------
# Step 1: Parse SQL file and extract Discogs artist IDs + full artist metadata
# ------------------------------------------------------------
Write-Host "Reading SQL file from '$SqlFile'..."
$sqlLines = Get-Content -Path $SqlFile

$discogsIds = @()
$artistMap = @{}   # key = discogs_id, value = PSObject with artist metadata

# Updated pattern for INSERT with 8 columns (artist_id added at the beginning)
$insertPattern = '^\s*INSERT\s+\[dbo\]\.\[Artist\]\s*\([^)]+\)\s*VALUES\s*\((.+)\)\s*$'

foreach ($line in $sqlLines) {
    if ($line -match $insertPattern) {
        $valuesPart = $Matches[1]

        # Split values by comma and trim quotes
        $values = $valuesPart -split ',' | ForEach-Object { $_.Trim().Trim("'") }

        # Now expecting 8 values: artist_id, spotify_playlist_id, artist_name, artist_surname,
        # musicbrainz_uuid, discogs_id, instrument_id, wikipedia_url
        if ($values.Count -ge 8) {
            $artistId      = $values[0]   # not used directly, but could be stored if needed
            $spotifyId     = $values[1]
            $artistName    = $values[2]
            $artistSurname = $values[3]
            $musicbrainzId = $values[4]
            $discogsIdRaw  = $values[5]
            $instrumentId  = $values[6]
            $wikipediaUrl  = $values[7]

            # Validate discogs_id is numeric
            if ($discogsIdRaw -match '^\d+$') {
                $discogsId = [int]$discogsIdRaw

                # Store artist metadata
                $artistMap[$discogsId] = [PSCustomObject]@{
                    discogs_id       = $discogsId
                    musicbrainz_uuid = $musicbrainzId
                    artist_name      = $artistName
                    artist_surname   = $artistSurname
                    wikipedia_url    = $wikipediaUrl
                    full_name        = "$artistName $artistSurname".Trim()
                    # Optionally store other fields if needed later:
                    # artist_id        = $artistId
                    # spotify_id       = $spotifyId
                    # instrument_id    = $instrumentId
                }

                # Also keep list of unique Discogs IDs
                $discogsIds += $discogsId
            }
            else {
                Write-Warning "Skipping line: discogs_id not a number ($discogsIdRaw)"
            }
        }
        else {
            Write-Warning "Skipping line: not enough values (expected 8, got $($values.Count))"
        }
    }
}

$discogsIds = $discogsIds | Select-Object -Unique
Write-Host "Found $($discogsIds.Count) unique Discogs artist IDs with metadata."

if ($discogsIds.Count -eq 0) {
    Write-Error "No Discogs IDs found. Exiting."
    exit 1
}

# ------------------------------------------------------------
# Step 2: Read MusicBrainz JSONL file and build lookup table
#          (key = Discogs album ID, value = full parsed object)
# ------------------------------------------------------------
Write-Host "Reading MusicBrainz data from '$MusicBrainzFile'..."
$musicBrainzLookup = @{}   # key = discogs_album_id (string), value = parsed JSON object (the whole line)

if (Test-Path $MusicBrainzFile) {
    $mbLines = Get-Content -Path $MusicBrainzFile
    $lineCount = 0
    $skippedLines = 0
    foreach ($mbLine in $mbLines) {
        $lineCount++
        try {
            $mbObject = $mbLine | ConvertFrom-Json
            # Extract Discogs ID from the new structure
            $discogsAlbumId = $null
            if ($mbObject.release_group) {
                # Try discogs_id first (master ID)
                if ($mbObject.release_group.discogs_id -and $mbObject.release_group.discogs_id -ne 'null') {
                    $discogsAlbumId = $mbObject.release_group.discogs_id
                }
                # Fall back to release_discogs_id (release ID)
                elseif ($mbObject.release_group.release_discogs_id -and $mbObject.release_group.release_discogs_id -ne 'null') {
                    $discogsAlbumId = $mbObject.release_group.release_discogs_id
                }
            }

            if ($discogsAlbumId) {
                # Store as string for consistent key matching
                $musicBrainzLookup[$discogsAlbumId.ToString()] = $mbObject
                Write-Host "  Line $lineCount : extracted Discogs ID = $discogsAlbumId"
            } else {
                $skippedLines++
                $props = ($mbObject.PSObject.Properties.Name) -join ', '
                Write-Warning "Line $lineCount : no recognizable Discogs ID (discogs_id and release_discogs_id both missing/null). Top-level properties: $props"
            }
        }
        catch {
            Write-Warning "Failed to parse line $lineCount in $MusicBrainzFile : $_"
        }
    }

    Write-Host "Loaded $($musicBrainzLookup.Count) unique Discogs album IDs from MusicBrainz data. Skipped $skippedLines lines."

    # DEBUG: Print out the entire lookup table (keys and a preview of values)
    Write-Host "`n--- Lookup table contents (first 100 chars of each value) ---"
    foreach ($key in $musicBrainzLookup.Keys) {
        $valPreview = ($musicBrainzLookup[$key] | ConvertTo-Json -Compress -Depth 2).Substring(0, [Math]::Min(100, ($musicBrainzLookup[$key] | ConvertTo-Json -Compress -Depth 2).Length)) + "..."
        Write-Host "  Key: $key -> Value preview: $valPreview"
    }
    Write-Host "--- End of lookup table ---`n"
} else {
    Write-Warning "MusicBrainz file '$MusicBrainzFile' not found. Proceeding without enrichment."
}

# ------------------------------------------------------------
# Step 3: For each artist, fetch releases and detailed metadata,
#          deduplicating by master, and wrap with artist info and optional MusicBrainz data.
#          Also fetch all version IDs for each master and add them.
# ------------------------------------------------------------
Write-Host "Output will be written to '$OutputFile'"

# Ensure output file is empty
if (Test-Path $OutputFile) { Remove-Item $OutputFile }

$totalArtists = $discogsIds.Count
$artistCounter = 0

foreach ($artistId in $discogsIds) {
    $artistCounter++
    Write-Host "[$artistCounter/$totalArtists] Processing artist ID $artistId"

    # Retrieve the artist metadata for this ID
    $artistInfo = $artistMap[$artistId]
    if (-not $artistInfo) {
        Write-Warning "No metadata found for artist ID $artistId – skipping."
        continue
    }

    # Build the releases list URL – use .NET Uri to avoid concatenation issues
    try {
        $baseUri = [System.Uri]::new($baseApiUrl)
        $releasesUri = [System.Uri]::new($baseUri, "/artists/$artistId/releases")
        $releasesUrl = $releasesUri.ToString()
    }
    catch {
        Write-Error "Failed to build URI for artist $artistId : $_"
        continue
    }

    # Track masters already processed for this artist (to avoid duplicates)
    $processedMasters = New-Object 'System.Collections.Generic.HashSet[int]'

    $page = 1
    $perPage = 100

    do {
        # Build full URL with query parameters using UriBuilder
        $uriBuilder = [System.UriBuilder]::new($releasesUrl)
        $uriBuilder.Query = "page=$page&per_page=$perPage"
        $url = $uriBuilder.Uri.ToString()

        Write-Host "DEBUG: Full URL = $url"
        $response = Invoke-DiscogsApi -Url $url

        foreach ($release in $response.releases) {
            if ($release.role -ne 'Main') {
                continue   # skip sideman/guest appearances
            }

            Write-Host "  -> Found release: $($release.title) (ID: $($release.id), type: $($release.type))"

            try {
                # Determine the URL to fetch full details
                if ($release.type -eq 'master') {
                    $fullUrl = "$baseApiUrl/masters/$($release.id)"
                } else {
                    $fullUrl = $release.resource_url   # individual release URL
                }

                $fullData = Invoke-DiscogsApi -Url $fullUrl

                # Variable to hold the Discogs object we will output (master or release)
                $discogsObject = $null

                # If we fetched a release and it belongs to a master
                if ($release.type -eq 'release' -and $fullData.PSObject.Properties.Name -contains 'master_id' -and $fullData.master_id) {
                    $masterId = $fullData.master_id
                    if (-not $processedMasters.Contains($masterId)) {
                        # Fetch the master object
                        $masterUrl = "$baseApiUrl/masters/$masterId"
                        $masterData = Invoke-DiscogsApi -Url $masterUrl
                        $processedMasters.Add($masterId) | Out-Null
                        $discogsObject = $masterData
                        Write-Host "      -> Fetched master (from child release)"
                    } else {
                        Write-Host "      -> Skipping, master already processed"
                        continue
                    }
                }
                elseif ($release.type -eq 'master') {
                    $masterId = $fullData.id
                    if (-not $processedMasters.Contains($masterId)) {
                        $processedMasters.Add($masterId) | Out-Null
                        $discogsObject = $fullData
                        Write-Host "      -> Fetched master"
                    } else {
                        Write-Host "      -> Skipping, master already processed"
                        continue
                    }
                }
                else {
                    # Standalone release (no master_id)
                    $discogsObject = $fullData
                    Write-Host "      -> Fetched standalone release"
                }

                # If we have a Discogs object to output, wrap it with artist metadata
                if ($discogsObject) {
                    # ----- Fetch all version IDs for this master (if it's a master) -----
                    $allVersionIds = @()
                    # Only fetch versions if the object looks like a master (has 'main_release' property)
                    if ($discogsObject.PSObject.Properties.Name -contains 'main_release') {
                        try {
                            if (-not $discogsObject.versions_url) {
                                Write-Warning "      Master $($discogsObject.id) has no versions_url, skipping versions fetch."
                            } else {
                                Write-Host "      -> Fetching versions for master $($discogsObject.id) from $($discogsObject.versions_url)"
                                $versionsPage = 1
                                do {
                                    $versionsResponse = Invoke-DiscogsApi -Url "$($discogsObject.versions_url)?page=$versionsPage&per_page=100"
                                    if ($versionsResponse.versions) {
                                        $pageIds = $versionsResponse.versions | ForEach-Object { $_.id }
                                        $allVersionIds += $pageIds
                                    }
                                    $versionsPage++
                                } while ($versionsPage -le $versionsResponse.pagination.pages)
                            }
                        }
                        catch {
                            Write-Warning "      Failed to fetch versions for master $($discogsObject.id): $_"
                            # $allVersionIds remains empty
                        }
                        # Add the array as a new property
                        $discogsObject | Add-Member -MemberType NoteProperty -Name 'discogs_all_versions_Ids' -Value $allVersionIds -Force
                    } else {
                        # For standalone releases, add an empty array to keep the field present
                        $discogsObject | Add-Member -MemberType NoteProperty -Name 'discogs_all_versions_Ids' -Value @() -Force
                    }

                    # Check if we have MusicBrainz data for this album's ID
                    $albumId = $discogsObject.id.ToString()
                    $musicBrainzData = $null
                    if ($musicBrainzLookup.ContainsKey($albumId)) {
                        $musicBrainzData = $musicBrainzLookup[$albumId]
                        Write-Host "      -> Found matching MusicBrainz data for album ID $albumId"
                    } else {
                        Write-Host "      -> No MusicBrainz data for album ID $albumId"
                    }

                    # Build ordered wrapper to control property order in JSON
                    $wrapper = [ordered]@{
                        artist = [ordered]@{
                            id            = $artistInfo.musicbrainz_uuid
                            name          = $artistInfo.full_name
                            discogs_id    = $artistId
                            wikipedia_url = $artistInfo.wikipedia_url
                        }
                        DiscogsAPIcall = $discogsObject
                    }

                    # Add MusicBrainzData if available
                    if ($musicBrainzData) {
                        $wrapper['MusicBrainzData'] = $musicBrainzData
                    }

                    $jsonLine = $wrapper | ConvertTo-Json -Compress -Depth 10
                    Add-Content -Path $OutputFile -Value $jsonLine
                }
            }
            catch {
                Write-Error "      Failed to fetch or process $fullUrl : $_"
            }
        }

        $page++
    } while ($page -le $response.pagination.pages)
}

Write-Host "Done. Output saved to $OutputFile"