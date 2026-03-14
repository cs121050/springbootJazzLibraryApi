<#
.SYNOPSIS
    Fetches Discogs release data for artists listed in an SQL INSERT file,
    outputting one JSON object per album where the artist is the main artist.
    Each output line includes the artist metadata from the SQL file.
.DESCRIPTION
    This script reads a SQL file containing INSERT statements for the [dbo].[Artist] table,
    extracts the discogs_id, musicbrainz_uuid, artist_name, artist_surname, and wikipedia_url
    for each artist, queries the Discogs API for all releases where the artist has role "Main",
    retrieves full metadata for each album (master or standalone release), and outputs
    the results as JSON Lines (.jsonl). Each line is an object with two properties:
    "artist" (containing the artist's musicbrainz id, full name, discogs id, and wikipedia url) and
    "DiscogsAPIcall" (the full Discogs release/master data).
    It deduplicates albums that belong to the same master.
    It respects Discogs rate limits (25 requests/minute for unauthenticated calls).
.PARAMETER SqlFile
    Path to the SQL file containing the INSERT statements.
.PARAMETER OutputFile
    Path where the output .jsonl file will be saved. Defaults to "discogs_releases.jsonl".
.EXAMPLE
    .\Get-AllArtistsReleasesNoToken.ps1 -SqlFile "_artists_without_quotes.sql"
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$SqlFile,

    [string]$OutputFile = ".\discogs_releases.jsonl"
)

# ------------------------------------------------------------
# Configuration – HARDCODED BASE URL
# ------------------------------------------------------------
$baseApiUrl = "https://api.discogs.com"                     # <-- Ensure this line is present
$minRequestIntervalMs = 2500                                # 25 requests/minute = 2.5 seconds
$userAgent = "DiscogsDataCollector/1.0 +https://example.com"

# Quick self-test: if $baseApiUrl is empty, stop immediately
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

$insertPattern = '^\s*INSERT\s+\[dbo\]\.\[Artist\]\s*\([^)]+\)\s*VALUES\s*\((.+)\)\s*$'

foreach ($line in $sqlLines) {
    if ($line -match $insertPattern) {
        $valuesPart = $Matches[1]

        # Split values by comma and trim quotes
        $values = $valuesPart -split ',' | ForEach-Object { $_.Trim().Trim("'") }

        # Expecting at least 7 values (spotify_playlist_id, artist_name, artist_surname, musicbrainz_uuid, discogs_id, instrument_id, wikipedia_url)
        if ($values.Count -ge 7) {
            $spotifyId    = $values[0]
            $artistName   = $values[1]
            $artistSurname= $values[2]
            $musicbrainzId= $values[3]
            $discogsIdRaw = $values[4]
            $instrumentId = $values[5]
            $wikipediaUrl = $values[6]

            # Validate discogs_id is numeric
            if ($discogsIdRaw -match '^\d+$') {
                $discogsId = [int]$discogsIdRaw

                # Store artist metadata
                $artistMap[$discogsId] = [PSCustomObject]@{
                    discogs_id      = $discogsId
                    musicbrainz_uuid = $musicbrainzId
                    artist_name      = $artistName
                    artist_surname   = $artistSurname
                    wikipedia_url    = $wikipediaUrl
                    full_name        = "$artistName $artistSurname".Trim()
                }

                # Also keep list of unique Discogs IDs (original script used this)
                $discogsIds += $discogsId
            }
            else {
                Write-Warning "Skipping line: discogs_id not a number ($discogsIdRaw)"
            }
        }
        else {
            Write-Warning "Skipping line: not enough values (expected 7, got $($values.Count))"
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
# Step 2: For each artist, fetch releases and detailed metadata,
#          deduplicating by master, and wrap with artist info.
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
                    # Use ordered dictionary to ensure "artist" appears first in the JSON
                    $wrapper = [ordered]@{
                        artist = [ordered]@{
                            id            = $artistInfo.musicbrainz_uuid
                            name          = $artistInfo.full_name
                            discogs_id    = $artistId
                            wikipedia_url = $artistInfo.wikipedia_url
                        }
                        DiscogsAPIcall = $discogsObject
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