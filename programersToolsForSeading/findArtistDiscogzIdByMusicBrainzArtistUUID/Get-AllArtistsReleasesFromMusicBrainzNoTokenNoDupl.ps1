<#
.SYNOPSIS
    Fetches MusicBrainz studio albums for artists listed in an SQL file,
    outputting one JSON object per album in JSON Lines format.

.DESCRIPTION
    This script reads an SQL file (e.g., an INSERT dump of an Artist table),
    extracts all MusicBrainz artist MBIDs (UUIDs) using a simple regex,
    queries the MusicBrainz API for each artist's release groups,
    filters to keep only release groups with primary type "Album" and no secondary types,
    retrieves the release group metadata (including artist credits, URLs, first release date),
    and writes each album as a JSON line to the output file.
    It respects MusicBrainz rate limits (1 request per second).

.PARAMETER SqlFile
    Path to the SQL file containing the artist MBIDs (e.g., INSERT statements).

.PARAMETER OutputFile
    Path where the output .jsonl file will be saved. Defaults to "mb_studio_albums.jsonl".

.EXAMPLE
    .\Get-MusicBrainzStudioAlbums.ps1 -SqlFile "artists.sql" -OutputFile "albums.jsonl"
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$SqlFile,

    [string]$OutputFile = ".\mb_studio_albums.jsonl"
)

# ------------------------------------------------------------
# Configuration
# ------------------------------------------------------------
$baseApiUrl = "https://musicbrainz.org/ws/2"
$minRequestIntervalMs = 1100   # 1 request per second + 100ms buffer
$userAgent = "MusicBrainzStudioAlbumCollector/1.0 +https://example.com"

# ------------------------------------------------------------
# Rate limiting helper
# ------------------------------------------------------------
if (-not (Get-Variable -Name 'lastRequestTime' -Scope Script -ErrorAction SilentlyContinue)) {
    $script:lastRequestTime = (Get-Date).AddMilliseconds(-$minRequestIntervalMs)
}

function Invoke-MusicBrainzApi {
    param([string]$Url)

    $now = Get-Date
    $timeSinceLast = ($now - $script:lastRequestTime).TotalMilliseconds
    if ($timeSinceLast -lt $minRequestIntervalMs) {
        $sleepMs = $minRequestIntervalMs - $timeSinceLast
        Write-Host "Rate limiting: sleeping $([math]::Round($sleepMs)) ms"
        Start-Sleep -Milliseconds $sleepMs
    }

    $headers = @{
        "User-Agent" = $userAgent
        "Accept"     = "application/json"
    }

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
            throw
        }
    }
    throw "Maximum retries exceeded for $Url"
}

# ------------------------------------------------------------
# Step 1: Extract all MBIDs (UUIDs) from the SQL file
# ------------------------------------------------------------
Write-Host "Reading SQL file from '$SqlFile'..."
$sqlContent = Get-Content -Path $SqlFile -Raw

$uuidPattern = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
$matches = [regex]::Matches($sqlContent, $uuidPattern)

$mbids = $matches | ForEach-Object { $_.Value } | Select-Object -Unique
Write-Host "Found $($mbids.Count) unique MusicBrainz artist MBIDs."

if ($mbids.Count -eq 0) {
    Write-Error "No MBIDs found in the SQL file. Exiting."
    exit 1
}

# ------------------------------------------------------------
# Step 2: For each artist, fetch release groups and filter for studio albums
# ------------------------------------------------------------
Write-Host "Output will be written to '$OutputFile'"

if (Test-Path $OutputFile) { Remove-Item $OutputFile }

$totalArtists = $mbids.Count
$artistCounter = 0

foreach ($mbid in $mbids) {
    $artistCounter++
    Write-Host "[$artistCounter/$totalArtists] Processing artist ID $mbid"

    $page = 1
    $perPage = 100
    $totalPages = 1  # will be updated from first response

    do {
        $offset = ($page - 1) * $perPage
        # IMPORTANT: 'releases' is NOT allowed in a release-group browse.
        # We include only artist-credits and url-rels (plus maybe annotations if needed).
        $browseUrl = "$baseApiUrl/release-group?artist=$mbid&limit=$perPage&offset=$offset&inc=artist-credits+url-rels&fmt=json"

        try {
            $response = Invoke-MusicBrainzApi -Url $browseUrl

            if ($page -eq 1) {
                $totalCount = $response.'release-group-count'
                $totalPages = [math]::Ceiling($totalCount / $perPage)
                Write-Host "  Artist has $totalCount release groups across $totalPages pages."
            }

            foreach ($rg in $response.'release-groups') {
                # Check primary type = Album and no secondary types
                $isAlbum = $rg.'primary-type' -eq 'Album'
                $hasSecondary = $rg.'secondary-types' -and $rg.'secondary-types'.Count -gt 0

                if ($isAlbum -and -not $hasSecondary) {
                    Write-Host "    -> Found studio album: $($rg.title) (ID: $($rg.id))"

                    # Output the release group object as a JSON line.
                    # It already contains artist-credit, first-release-date, etc.
                    $jsonLine = $rg | ConvertTo-Json -Compress -Depth 10
                    Add-Content -Path $OutputFile -Value $jsonLine
                }
                else {
                    # Optional debug: log skipped items
                    # Write-Host "    Skipping $($rg.title) - type: $($rg.'primary-type') / secondary: $($rg.'secondary-types' -join ',')"
                }
            }

            $page++
        }
        catch {
            Write-Error "  Failed to fetch release groups for artist $mbid on page $page : $_"
            break
        }

    } while ($page -le $totalPages)

    Start-Sleep -Milliseconds 200
}

Write-Host "Done. Output saved to $OutputFile"