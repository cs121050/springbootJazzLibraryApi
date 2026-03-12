<#
.SYNOPSIS
    Fetches main release data for master albums of artists listed in an SQL INSERT file.
.DESCRIPTION
    This script reads a SQL file containing INSERT statements for the [dbo].[Artist] table,
    extracts the discogs_id for each artist, queries the Discogs API for all releases where
    the artist has role "Main", identifies unique master releases, retrieves the full metadata
    of the main release for each master, and outputs the results as JSON Lines (.jsonl).
    It respects Discogs rate limits (25 requests/minute for unauthenticated calls).
.PARAMETER SqlFile
    Path to the SQL file containing the INSERT statements.
.PARAMETER OutputFile
    Path where the output .jsonl file will be saved. Defaults to "discogs_main_releases.jsonl"
    in the script folder.
.EXAMPLE
    .\Get-ArtistMainReleases.ps1 -SqlFile "_artists_without_quotes.sql" 
#>
     
param(
    [Parameter(Mandatory=$true)]
    [string]$SqlFile,

    [string]$OutputFile = ".\discogs_main_releases.jsonl"
)

# ------------------------------------------------------------
# Configuration – Discogs API base URL and rate limiting
# ------------------------------------------------------------
$baseApiUrl = "https://api.discogs.com"
$minRequestIntervalMs = 2500      # 25 requests/minute = 2.5 seconds
$userAgent = "DiscogsDataCollector/1.0 +https://yourdomain.com/contact"

if ([string]::IsNullOrWhiteSpace($baseApiUrl)) {
    Write-Error "FATAL: baseApiUrl is not set. Check the script configuration."
    exit 1
}

# ------------------------------------------------------------
# Initialize last request time
# ------------------------------------------------------------
if (-not (Get-Variable -Name 'lastRequestTime' -Scope Script -ErrorAction SilentlyContinue)) {
    $script:lastRequestTime = (Get-Date).AddMilliseconds(-$minRequestIntervalMs)
}

# ------------------------------------------------------------
# Helper: Invoke-DiscogsApi (rate limiting & retries)
# ------------------------------------------------------------
function Invoke-DiscogsApi {
    param([string]$Url)

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

            if ($statusCode -eq 429 -or ($statusCode -ge 500 -and $statusCode -le 599)) {
                if ($retryCount -lt $maxRetries) {
                    $retryCount++
                    $waitSec = $baseDelaySec * [math]::Pow(2, $retryCount - 1)
                    Write-Warning "Retry $retryCount/$maxRetries after $waitSec seconds"
                    Start-Sleep -Seconds $waitSec
                    continue
                }
            }
            throw
        }
    }
    throw "Maximum retries exceeded for $Url"
}

# ------------------------------------------------------------
# Step 1: Parse SQL file and extract Discogs artist IDs
# ------------------------------------------------------------
if (-not (Test-Path $SqlFile)) {
    Write-Error "SQL file '$SqlFile' not found."
    exit 1
}

Write-Host "Reading SQL file from '$SqlFile'..."
$sqlLines = Get-Content -Path $SqlFile

$discogsIds = @()
$insertPattern = '^\s*INSERT\s+\[dbo\]\.\[Artist\]\s*\([^)]+\)\s*VALUES\s*\((.*)\)\s*$'

foreach ($line in $sqlLines) {
    if ($line -match $insertPattern) {
        $valuesPart = $Matches[1]
        $values = $valuesPart -split ','

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
# Step 2: For each artist, gather master releases and fetch main release data
# ------------------------------------------------------------
Write-Host "Output will be written to '$OutputFile'"
if (Test-Path $OutputFile) { Remove-Item $OutputFile }

$totalArtists = $discogsIds.Count
$artistCounter = 0

foreach ($artistId in $discogsIds) {
    $artistCounter++
    Write-Host "[$artistCounter/$totalArtists] Processing artist ID $artistId"

    $baseUri = [System.Uri]::new($baseApiUrl)
    $releasesUri = [System.Uri]::new($baseUri, "/artists/$artistId/releases")
    $releasesBaseUrl = $releasesUri.ToString()

    # Track processed master IDs to avoid duplicates
    $processedMasters = New-Object 'System.Collections.Generic.HashSet[int]'

    $page = 1
    $perPage = 100

    do {
        $uriBuilder = [System.UriBuilder]::new($releasesBaseUrl)
        $uriBuilder.Query = "page=$page&per_page=$perPage"
        $url = $uriBuilder.Uri.ToString()

        try {
            $response = Invoke-DiscogsApi -Url $url
        }
        catch {
            Write-Error "Failed to fetch releases for artist $artistId, page $page : $_"
            break
        }

        foreach ($release in $response.releases) {
            if ($release.role -ne 'Main') {
                continue
            }

            Write-Host "  -> Found release: $($release.title) (ID: $($release.id), type: $($release.type))"

            try {
                # Determine the master ID
                $masterId = $null
                if ($release.type -eq 'master') {
                    $masterId = $release.id
                }
                elseif ($release.type -eq 'release') {
                    # For a release, we need to fetch its details to get master_id
                    $releaseDetail = Invoke-DiscogsApi -Url $release.resource_url
                    if ($releaseDetail.PSObject.Properties.Name -contains 'master_id' -and $releaseDetail.master_id) {
                        $masterId = $releaseDetail.master_id
                    }
                }

                if ($masterId -and -not $processedMasters.Contains($masterId)) {
                    # Fetch master details to get main_release_url
                    $masterUrl = "$baseApiUrl/masters/$masterId"
                    $masterData = Invoke-DiscogsApi -Url $masterUrl

                    if ($masterData.PSObject.Properties.Name -contains 'main_release_url') {
                        $mainReleaseUrl = $masterData.main_release_url
                        Write-Host "      -> Fetching main release: $mainReleaseUrl"
                        $mainReleaseData = Invoke-DiscogsApi -Url $mainReleaseUrl

                        # Output the full release JSON
                        $jsonLine = $mainReleaseData | ConvertTo-Json -Compress -Depth 10
                        Add-Content -Path $OutputFile -Value $jsonLine
                        Write-Host "      -> Saved main release for master $masterId"

                        $processedMasters.Add($masterId) | Out-Null
                    }
                    else {
                        Write-Warning "      Master $masterId has no main_release_url"
                    }
                }
                elseif ($masterId) {
                    Write-Host "      -> Skipping master $masterId (already processed)"
                }
                # If no master (standalone release), skip (user wants masters only)
            }
            catch {
                Write-Error "      Failed to process release $($release.id) : $_"
            }
        }

        $page++
    } while ($page -le $response.pagination.pages)
}

Write-Host "Done. Output saved to $OutputFile"