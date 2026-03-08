<#
.SYNOPSIS
    Fetches Discogs release data for artists listed in an SQL INSERT file.
.DESCRIPTION
    This script reads a SQL file containing INSERT statements for the [dbo].[Artist] table,
    extracts the discogs_id for each artist, queries the Discogs API for all releases where
    the artist has role "Main", retrieves full metadata for each release, and outputs the
    results as JSON Lines (.jsonl). It respects Discogs rate limits (25 requests/minute for
    unauthenticated calls).
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
# Step 1: Parse SQL file and extract Discogs artist IDs
# ------------------------------------------------------------
Write-Host "Reading SQL file from '$SqlFile'..."
$sqlLines = Get-Content -Path $SqlFile

$discogsIds = @()
$insertPattern = '^\s*INSERT\s+\[dbo\]\.\[Artist\]\s*\([^)]+\)\s*VALUES\s*\((.+)\)\s*$'

foreach ($line in $sqlLines) {
    if ($line -match $insertPattern) {
        $valuesPart = $Matches[1]
        $values = $valuesPart -split ','

        # discogs_id is the 5th value (index 4)
        if ($values.Count -ge 5) {
            $discogsIdRaw = $values[4].Trim()
            $discogsIdRaw = $discogsIdRaw -replace '^''|''$', '' -replace '^"|"$', ''
            if ($discogsIdRaw -match '^\d+$') {
                $discogsIds += [int]$discogsIdRaw
            }
            else {
                Write-Warning "Skipping line: discogs_id not a number ($discogsIdRaw)"
            }
        }
    }
}

$discogsIds = $discogsIds | Select-Object -Unique
Write-Host "Found $($discogsIds.Count) unique Discogs artist IDs."

if ($discogsIds.Count -eq 0) {
    Write-Error "No Discogs IDs found. Exiting."
    exit 1
}

# ------------------------------------------------------------
# Step 2: For each artist, fetch releases and detailed metadata
# ------------------------------------------------------------
Write-Host "Output will be written to '$OutputFile'"

# Ensure output file is empty
if (Test-Path $OutputFile) { Remove-Item $OutputFile }

$totalArtists = $discogsIds.Count
$artistCounter = 0

foreach ($artistId in $discogsIds) {
    $artistCounter++
    Write-Host "[$artistCounter/$totalArtists] Processing artist ID $artistId"

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

    Write-Host "DEBUG: releasesUrl = [$releasesUrl]"   # <-- SEE WHAT IS ACTUALLY HERE

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
            if ($release.role -eq 'Main') {
                Write-Host "  -> Found release: $($release.title) (ID: $($release.id), type: $($release.type))"

                # Determine the correct release URL to fetch full metadata
                if ($release.type -eq 'master' -and $release.main_release_url) {
                    $releaseUrl = $release.main_release_url
                }
                else {
                    $releaseUrl = $release.resource_url
                }

                try {
                    $fullRelease = Invoke-DiscogsApi -Url $releaseUrl
                    $jsonLine = $fullRelease | ConvertTo-Json -Compress -Depth 10
                    Add-Content -Path $OutputFile -Value $jsonLine
                    Write-Host "      -> Fetched and saved"
                }
                catch {
                    Write-Error "      Failed to fetch release from $releaseUrl : $_"
                }
            }
        }

        $page++
    } while ($page -le $response.pagination.pages)
}

Write-Host "Done. Output saved to $OutputFile"