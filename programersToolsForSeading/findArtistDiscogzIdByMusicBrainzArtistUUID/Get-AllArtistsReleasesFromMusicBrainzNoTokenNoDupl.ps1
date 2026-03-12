<#
.SYNOPSIS
    Fetches MusicBrainz studio albums for artists listed in an SQL file,
    adds a small cover image URL from the Cover Art Archive, and outputs
    one JSON object per album in JSON Lines format.

.DESCRIPTION
    This script reads an SQL file (e.g., an INSERT dump of an Artist table),
    extracts all MusicBrainz artist MBIDs, queries the MusicBrainz API for
    each artist's release groups, filters to keep only studio albums (primary
    type "Album" and no secondary types), retrieves the release group metadata,
    and then attempts to fetch a 250px thumbnail from the Cover Art Archive.
    Each album is enriched with a "cover_image_small" property and written as
    a JSON line to the output file. It respects MusicBrainz rate limits
    (1 request per second) and adds a small delay for Cover Art Archive calls.

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
$coverArtBaseUrl = "https://coverartarchive.org"
$minRequestIntervalMs = 1100   # 1 request per second + 100ms buffer for MusicBrainz
$coverArtDelayMs = 200         # small delay between Cover Art Archive checks
$userAgent = "MusicBrainzStudioAlbumCollector/1.0 +https://example.com"

# ------------------------------------------------------------
# Rate limiting helper for MusicBrainz
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
# Function to check and return the small cover image URL
# ------------------------------------------------------------
function Get-CoverArtThumbnail {
    param([string]$ReleaseGroupId)

    # Construct the URL for the 250px front thumbnail
    $thumbnailUrl = "$coverArtBaseUrl/release-group/$ReleaseGroupId/front-250.jpg"

    # Use HEAD request to check existence without downloading the image
    try {
        $request = [System.Net.WebRequest]::Create($thumbnailUrl)
        $request.Method = "HEAD"
        $request.UserAgent = $userAgent
        $response = $request.GetResponse()
        $statusCode = [int]$response.StatusCode
        $response.Close()

        if ($statusCode -eq 200) {
            Write-Host "      Cover art found: $thumbnailUrl"
            return $thumbnailUrl
        }
        else {
            Write-Host "      No cover art available (HTTP $statusCode)"
            return $null
        }
    }
    catch {
        # If we get a WebException, check if it's a 404
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode -eq [System.Net.HttpStatusCode]::NotFound) {
            Write-Host "      No cover art available (404)"
        }
        else {
            Write-Warning "      Error checking cover art for $ReleaseGroupId : $_"
        }
        return $null
    }
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

                    # Get the small cover image URL
                    $thumbnailUrl = Get-CoverArtThumbnail -ReleaseGroupId $rg.id

                    # Add the cover_image_small property to the release group object
                    $rg | Add-Member -NotePropertyName 'cover_image_small' -NotePropertyValue $thumbnailUrl -Force

                    # Output the enriched release group as a JSON line
                    $jsonLine = $rg | ConvertTo-Json -Compress -Depth 10
                    Add-Content -Path $OutputFile -Value $jsonLine

                    # Small delay to be gentle to Cover Art Archive
                    Start-Sleep -Milliseconds $coverArtDelayMs
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

    # Extra pause between artists to stay well within rate limits
    Start-Sleep -Milliseconds 200
}

Write-Host "Done. Output saved to $OutputFile"