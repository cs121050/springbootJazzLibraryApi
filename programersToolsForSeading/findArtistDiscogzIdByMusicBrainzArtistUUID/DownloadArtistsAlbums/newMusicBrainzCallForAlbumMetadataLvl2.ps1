<#
.SYNOPSIS
    Extracts artist MBIDs from an SQL file, fetches all studio albums from MusicBrainz,
    enriches with Wikipedia URLs, Discogs master data, and Cover Art Archive thumbnails,
    and outputs as JSON Lines.

.DESCRIPTION
    This script reads an SQL file, extracts artist MBIDs, and for each album:
      - Gets MusicBrainz release group and first release metadata
      - Extracts Wikipedia URL from relations and via Wikipedia API search
      - Calls Discogs API for master data (rating count, average rating, images)
      - **NEW:** Fetches a 250px thumbnail from the Cover Art Archive and adds `cover_image_small`
    Results are saved as .jsonl file.

.PARAMETER SqlFile
    Path to the SQL file containing the artist MBIDs.

.PARAMETER OutputFile
    Path where the output .jsonl file will be saved. Defaults to "mb_albums_enriched.jsonl".

.PARAMETER IncludeAllReleaseGroups
    If specified, includes all release groups (not just studio albums).

.PARAMETER DiscogsToken
    Your Discogs personal access token for authenticated API access (60 requests/minute).

.EXAMPLE
    .\Get-MusicBrainzAlbums.ps1 -SqlFile "artists.sql" -DiscogsToken "YOUR_TOKEN_HERE"
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$SqlFile,

    [string]$OutputFile = ".\mb_albums_enriched.jsonl",

    [switch]$IncludeAllReleaseGroups,

    [string]$DiscogsToken = ""
)

# ------------------------------------------------------------
# Configuration
# ------------------------------------------------------------
$musicBrainzApiUrl = "https://musicbrainz.org/ws/2"
$discogsApiUrl = "https://api.discogs.com"
$coverArtBaseUrl = "https://coverartarchive.org"
$mbMinRequestIntervalMs = 1100          # 1 second + buffer for MusicBrainz
$discogsMinRequestIntervalMs = 1000      # 1 request per second (60/minute max)
$wikipediaMinRequestIntervalMs = 1000    # 1 request per second for Wikipedia API
$coverArtMinRequestIntervalMs = 200       # 200ms between Cover Art Archive checks
$userAgent = "MusicBrainzAlbumCollector/1.0 (https://example.com)"
$maxRetries = 3
$baseRetryDelaySec = 5

# Global variables for rate limiting
if (-not (Get-Variable -Name 'lastMBRequestTime' -Scope Script -ErrorAction SilentlyContinue)) {
    $script:lastMBRequestTime = (Get-Date).AddMilliseconds(-$mbMinRequestIntervalMs)
}
if (-not (Get-Variable -Name 'lastDiscogsRequestTime' -Scope Script -ErrorAction SilentlyContinue)) {
    $script:lastDiscogsRequestTime = (Get-Date).AddMilliseconds(-$discogsMinRequestIntervalMs)
}
if (-not (Get-Variable -Name 'lastWikipediaRequestTime' -Scope Script -ErrorAction SilentlyContinue)) {
    $script:lastWikipediaRequestTime = (Get-Date).AddMilliseconds(-$wikipediaMinRequestIntervalMs)
}
if (-not (Get-Variable -Name 'lastCoverArtRequestTime' -Scope Script -ErrorAction SilentlyContinue)) {
    $script:lastCoverArtRequestTime = (Get-Date).AddMilliseconds(-$coverArtMinRequestIntervalMs)
}

# ------------------------------------------------------------
# Rate‑limited API caller for MusicBrainz
# ------------------------------------------------------------
function Invoke-MusicBrainzApi {
    param([string]$Url)

    $now = Get-Date
    $timeSinceLast = ($now - $script:lastMBRequestTime).TotalMilliseconds
    if ($timeSinceLast -lt $mbMinRequestIntervalMs) {
        $sleepMs = $mbMinRequestIntervalMs - $timeSinceLast
        Write-Host "  Rate limiting (MB): sleeping $([math]::Round($sleepMs)) ms"
        Start-Sleep -Milliseconds $sleepMs
    }

    $headers = @{
        "User-Agent" = $userAgent
        "Accept"     = "application/json"
    }

    $retryCount = 0
    while ($retryCount -le $maxRetries) {
        try {
            Write-Host "  Calling MusicBrainz: $Url"
            $response = Invoke-RestMethod -Uri $Url -Headers $headers -ErrorAction Stop
            $script:lastMBRequestTime = Get-Date
            return $response
        }
        catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            Write-Warning "  Request to MusicBrainz failed with HTTP $statusCode"

            if ($statusCode -eq 429 -or ($statusCode -ge 500 -and $statusCode -le 599)) {
                if ($retryCount -lt $maxRetries) {
                    $retryCount++
                    $waitSec = $baseRetryDelaySec * [math]::Pow(2, $retryCount - 1)
                    Write-Warning "  Retry $retryCount/$maxRetries after $waitSec seconds"
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
# Rate‑limited API caller for Discogs
# ------------------------------------------------------------
function Invoke-DiscogsApi {
    param([string]$Url)

    $now = Get-Date
    $timeSinceLast = ($now - $script:lastDiscogsRequestTime).TotalMilliseconds
    if ($timeSinceLast -lt $discogsMinRequestIntervalMs) {
        $sleepMs = $discogsMinRequestIntervalMs - $timeSinceLast
        Write-Host "    Rate limiting (Discogs): sleeping $([math]::Round($sleepMs)) ms"
        Start-Sleep -Milliseconds $sleepMs
    }

    $headers = @{
        "User-Agent" = $userAgent
        "Accept"     = "application/json"
    }

    if (-not [string]::IsNullOrEmpty($DiscogsToken)) {
        $headers["Authorization"] = "Discogs token=$DiscogsToken"
        Write-Host "    Using authenticated Discogs access (60 requests/min)"
    }

    $retryCount = 0
    while ($retryCount -le $maxRetries) {
        try {
            Write-Host "    Calling Discogs: $Url"
            $response = Invoke-RestMethod -Uri $Url -Headers $headers -ErrorAction Stop
            $script:lastDiscogsRequestTime = Get-Date

            if ($response.Headers) {
                $remaining = $response.Headers['X-Discogs-Ratelimit-Remaining']
                $limit = $response.Headers['X-Discogs-Ratelimit']
                if ($remaining -and $limit) {
                    Write-Host "    Discogs rate limit: $remaining/$limit remaining"
                }
            }

            return $response
        }
        catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            
            if ($statusCode -eq 429) {
                Write-Warning "    Discogs rate limit exceeded (429). Waiting 60 seconds..."
                Start-Sleep -Seconds 60
                if ($retryCount -lt $maxRetries) {
                    $retryCount++
                    continue
                }
            }
            elseif ($statusCode -eq 404) {
                Write-Host "    Discogs master not found (404)"
                return $null
            }
            elseif ($statusCode -eq 403) {
                Write-Warning "    Discogs authentication failed (403). Check your token."
                return $null
            }
            elseif ($statusCode -ge 500 -and $statusCode -le 599) {
                if ($retryCount -lt $maxRetries) {
                    $retryCount++
                    $waitSec = $baseRetryDelaySec * [math]::Pow(2, $retryCount - 1)
                    Write-Warning "    Discogs server error. Retry $retryCount/$maxRetries after $waitSec seconds"
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
# Rate‑limited Wikipedia search via their public API
# ------------------------------------------------------------
function Invoke-WikipediaSearchApi {
    param(
        [string]$ArtistName,
        [string]$AlbumTitle
    )

    $now = Get-Date
    $timeSinceLast = ($now - $script:lastWikipediaRequestTime).TotalMilliseconds
    if ($timeSinceLast -lt $wikipediaMinRequestIntervalMs) {
        $sleepMs = $wikipediaMinRequestIntervalMs - $timeSinceLast
        Write-Host "      Rate limiting (Wikipedia): sleeping $([math]::Round($sleepMs)) ms"
        Start-Sleep -Milliseconds $sleepMs
    }

    $query = "$AlbumTitle $ArtistName album"
    $encodedQuery = [uri]::EscapeDataString($query)
    $url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json&utf8=1"

    $headers = @{
        "User-Agent" = $userAgent
    }

    try {
        Write-Host "      Searching Wikipedia for: $query"
        $response = Invoke-RestMethod -Uri $url -Headers $headers -ErrorAction Stop
        $script:lastWikipediaRequestTime = Get-Date

        if ($response.query.search -and $response.query.search.Count -gt 0) {
            $firstResult = $response.query.search[0]
            $title = $firstResult.title
            $wikiUrl = "https://en.wikipedia.org/wiki/" + ($title -replace ' ', '_')
            Write-Host "      Found Wikipedia page: $title -> $wikiUrl"
            return $wikiUrl
        }
        else {
            Write-Host "      No Wikipedia search results found."
            return $null
        }
    }
    catch {
        Write-Warning "      Wikipedia search API request failed: $_"
        return $null
    }
}

# ------------------------------------------------------------
# NEW: Fetch Cover Art Archive 250px thumbnail
# ------------------------------------------------------------
function Get-CoverArtThumbnail {
    param([string]$ReleaseGroupId)

    $now = Get-Date
    $timeSinceLast = ($now - $script:lastCoverArtRequestTime).TotalMilliseconds
    if ($timeSinceLast -lt $coverArtMinRequestIntervalMs) {
        $sleepMs = $coverArtMinRequestIntervalMs - $timeSinceLast
        Write-Host "      Rate limiting (Cover Art): sleeping $([math]::Round($sleepMs)) ms"
        Start-Sleep -Milliseconds $sleepMs
    }

    $thumbnailUrl = "$coverArtBaseUrl/release-group/$ReleaseGroupId/front-250.jpg"
    Write-Host "      Checking cover art: $thumbnailUrl"

    try {
        # Use HEAD request to check existence without downloading image
        $request = [System.Net.WebRequest]::Create($thumbnailUrl)
        $request.Method = "HEAD"
        $request.UserAgent = $userAgent
        $response = $request.GetResponse()
        $statusCode = [int]$response.StatusCode
        $response.Close()

        if ($statusCode -eq 200) {
            Write-Host "      Cover art found."
            $script:lastCoverArtRequestTime = Get-Date
            return $thumbnailUrl
        }
        else {
            Write-Host "      Cover art not available (HTTP $statusCode)"
            $script:lastCoverArtRequestTime = Get-Date
            return $null
        }
    }
    catch {
        # If it's a 404, that's expected for missing art
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode -eq [System.Net.HttpStatusCode]::NotFound) {
            Write-Host "      Cover art not found (404)"
        } else {
            Write-Warning "      Error checking cover art: $_"
        }
        $script:lastCoverArtRequestTime = Get-Date
        return $null
    }
}

# ------------------------------------------------------------
# Extract Wikipedia URL from release group relations
# ------------------------------------------------------------
function Get-WikipediaUrl {
    param([object]$ReleaseGroup)

    if (-not $ReleaseGroup.relations) {
        return $null
    }

    foreach ($relation in $ReleaseGroup.relations) {
        if ($relation.type -eq "wikidata" -or 
            $relation.type -eq "wikipedia" -or 
            $relation.type -like "*wikipedia*" -or
            $relation.type -like "*wikidata*") {
            
            if ($relation.url -and $relation.url.resource) {
                Write-Host "      Found Wikipedia/Wikidata URL: $($relation.url.resource)"
                return $relation.url.resource
            }
        }
    }
    return $null
}

# ------------------------------------------------------------
# Extract Discogs master ID from release group relations
# (kept for backward compatibility, but we now also extract both IDs separately)
# ------------------------------------------------------------
function Get-DiscogsMasterId {
    param([object]$ReleaseGroup)

    if (-not $ReleaseGroup.relations) {
        return $null
    }

    foreach ($relation in $ReleaseGroup.relations) {
        if ($relation.type -eq "discogs") {
            if ($relation.url -and $relation.url.resource) {
                $url = $relation.url.resource
                if ($url -match "/master/(\d+)") {
                    Write-Host "      Found Discogs master ID: $($matches[1])"
                    return $matches[1]
                }
                elseif ($url -match "/release/(\d+)") {
                    Write-Host "      Found Discogs release ID (fallback): $($matches[1])"
                    return $matches[1]
                }
            }
        }
    }
    return $null
}

# ------------------------------------------------------------
# New helper: Extract both master and release Discogs IDs from relations
# ------------------------------------------------------------
function Get-DiscogsIdsFromRelations {
    param([array]$Relations)

    $masterId = $null
    $releaseId = $null

    if (-not $Relations) {
        return @{ Master = $masterId; Release = $releaseId }
    }

    foreach ($relation in $Relations) {
        if ($relation.type -eq "discogs" -and $relation.url -and $relation.url.resource) {
            $url = $relation.url.resource
            if ($url -match "/master/(\d+)") {
                $masterId = $matches[1]
                Write-Host "      Extracted Discogs master ID from relation: $masterId"
            }
            elseif ($url -match "/release/(\d+)") {
                $releaseId = $matches[1]
                Write-Host "      Extracted Discogs release ID from relation: $releaseId"
            }
        }
    }

    return @{ Master = $masterId; Release = $releaseId }
}

# ------------------------------------------------------------
# Fetch Discogs master metadata
# ------------------------------------------------------------
function Get-DiscogsMasterData {
    param([string]$DiscogsId)

    if ([string]::IsNullOrEmpty($DiscogsId)) {
        return $null
    }

    $endpoint = if ($DiscogsId.Length -gt 0) { 
        $masterUrl = "$discogsApiUrl/masters/$DiscogsId"
        try {
            $masterData = Invoke-DiscogsApi -Url $masterUrl
            if ($masterData) {
                return @{
                    type = "master"
                    id = $DiscogsId
                    rating_count = $masterData.community.rating.count
                    rating_average = $masterData.community.rating.average
                    images = $masterData.images
                    year = $masterData.year
                    genres = $masterData.genres
                    styles = $masterData.styles
                    tracklist = $masterData.tracklist
                }
            }
        }
        catch {
            Write-Host "    Master not found, trying as release ID..."
            $releaseUrl = "$discogsApiUrl/releases/$DiscogsId"
            $releaseData = Invoke-DiscogsApi -Url $releaseUrl
            if ($releaseData) {
                return @{
                    type = "release"
                    id = $DiscogsId
                    rating_count = $releaseData.community.rating.count
                    rating_average = $releaseData.community.rating.average
                    images = $releaseData.images
                    year = $releaseData.year
                    genres = $releaseData.genres
                    styles = $releaseData.styles
                    tracklist = $releaseData.tracklist
                }
            }
        }
    }
    return $null
}

# ------------------------------------------------------------
# Extract all unique MusicBrainz UUIDs from the SQL file
# ------------------------------------------------------------
Write-Host "Reading SQL file '$SqlFile'..."
$sqlContent = Get-Content -Path $SqlFile -Raw

$uuidPattern = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
$matches = [regex]::Matches($sqlContent, $uuidPattern)

$mbids = @()
foreach ($match in $matches) {
    $cleanMbid = $match.Value.Trim()
    $cleanMbid = $cleanMbid -replace '[^\x20-\x7E]', ''
    if (-not [string]::IsNullOrEmpty($cleanMbid)) {
        $mbids += $cleanMbid
    }
}
$mbids = $mbids | Select-Object -Unique

Write-Host "Found $($mbids.Count) unique artist MBIDs."
if ($mbids.Count -eq 0) {
    Write-Error "No MBIDs found. Exiting."
    exit 1
}

Write-Host "First 5 MBIDs:"
for ($i = 0; $i -lt [math]::Min(5, $mbids.Count); $i++) {
    Write-Host "  $($i+1): '$($mbids[$i])' (length: $($mbids[$i].Length))"
}

# ------------------------------------------------------------
# Prepare output file
# ------------------------------------------------------------
if (Test-Path $OutputFile) { Remove-Item $OutputFile }
Write-Host "Output will be written to '$OutputFile'"

$totalArtists = $mbids.Count
$artistCounter = 0

# ------------------------------------------------------------
# Process each artist
# ------------------------------------------------------------
foreach ($rawMbid in $mbids) {
    $artistCounter++
    $currentMbid = $rawMbid.Trim()
    $currentMbid = $currentMbid -replace '[^\x20-\x7E]', ''

    Write-Host "[$artistCounter/$totalArtists] Processing artist $currentMbid"
    if ([string]::IsNullOrEmpty($currentMbid)) {
        Write-Warning "  Skipping empty MBID"
        continue
    }

    # 1. Get artist metadata
    $artistUrl = "${musicBrainzApiUrl}/artist/${currentMbid}?fmt=json"
    try {
        $artist = Invoke-MusicBrainzApi -Url $artistUrl
        $artistName = $artist.name
        Write-Host "  Artist: $artistName"
    }
    catch {
        Write-Error "  Failed to fetch artist $currentMbid : $_"
        continue
    }

    # 2. Browse release groups for this artist (with url-rels for external links)
    $page = 1
    $perPage = 100
    $totalPages = 1

    do {
        $offset = ($page - 1) * $perPage
        $rgBrowseUrl = "${musicBrainzApiUrl}/release-group?artist=${currentMbid}&limit=${perPage}&offset=${offset}&inc=artist-credits+url-rels&fmt=json"

        try {
            $rgResponse = Invoke-MusicBrainzApi -Url $rgBrowseUrl

            if ($page -eq 1) {
                $totalCount = $rgResponse.'release-group-count'
                $totalPages = [math]::Ceiling($totalCount / $perPage)
                Write-Host "  Artist has $totalCount release groups across $totalPages pages."
            }

            foreach ($rg in $rgResponse.'release-groups') {
                $isAlbum = $rg.'primary-type' -eq 'Album'
                $hasSecondary = $rg.'secondary-types' -and $rg.'secondary-types'.Count -gt 0

                if ($IncludeAllReleaseGroups -or ($isAlbum -and -not $hasSecondary)) {
                    Write-Host "    -> Processing release group: $($rg.title) (ID: $($rg.id))"

                    # 3. Extract Wikipedia URL from release group relations
                    $wikipediaUrlFromMB = Get-WikipediaUrl -ReleaseGroup $rg

                    # 4. Fetch Wikipedia URL via Wikipedia search API
                    $wikipediaUrlFromSearch = Invoke-WikipediaSearchApi -ArtistName $artistName -AlbumTitle $rg.title

                    # 5. Extract Discogs master ID (legacy) and both master/release IDs separately
                    $discogsId = Get-DiscogsMasterId -ReleaseGroup $rg   # legacy discogs_id
                    $discogsIdsFromRG = Get-DiscogsIdsFromRelations -Relations $rg.relations

                    $discogsIdsFromRelease = $null

                    # 6. Get all releases for this release group
                    $releasesUrl = "${musicBrainzApiUrl}/release?release-group=$($rg.id)&fmt=json"
                    try {
                        $releasesResponse = Invoke-MusicBrainzApi -Url $releasesUrl

                        if ($releasesResponse.releases -and $releasesResponse.releases.Count -gt 0) {
                            $sortedReleases = $releasesResponse.releases | Sort-Object {
                                if ($_.date) {
                                    $dateStr = $_.date
                                    if ($dateStr.Length -eq 4) { "$dateStr-01-01" }
                                    elseif ($dateStr.Length -eq 7) { "$dateStr-01" }
                                    else { $dateStr }
                                } else { "9999-12-31" }
                            }
                            $firstRelease = $sortedReleases[0]

                            Write-Host "      First release: $($firstRelease.title) (ID: $($firstRelease.id), Date: $($firstRelease.date))"

                            # 7. Fetch full metadata of the first release (including relations)
                            $releaseDetailUrl = "${musicBrainzApiUrl}/release/$($firstRelease.id)?inc=recordings+labels+artist-credits+url-rels&fmt=json"
                            try {
                                $releaseDetail = Invoke-MusicBrainzApi -Url $releaseDetailUrl

                                $discogsIdsFromRelease = Get-DiscogsIdsFromRelations -Relations $releaseDetail.relations

                                # 8. Fetch Discogs master data using the discogs_id we already have
                                $discogsData = $null
                                if ($discogsId) {
                                    Write-Host "    Fetching Discogs metadata for ID: $discogsId"
                                    $discogsData = Get-DiscogsMasterData -DiscogsId $discogsId
                                    if ($discogsData) {
                                        Write-Host "      Found Discogs data: rating count=$($discogsData.rating_count), avg=$($discogsData.rating_average)"
                                    }
                                }

                                $masterDiscogsId = if ($discogsIdsFromRG.Master) { $discogsIdsFromRG.Master } else { $discogsIdsFromRelease.Master }
                                $releaseDiscogsId = if ($discogsIdsFromRG.Release) { $discogsIdsFromRG.Release } else { $discogsIdsFromRelease.Release }

                                # --- NEW: Get Cover Art Archive thumbnail ---
                                $coverImage = Get-CoverArtThumbnail -ReleaseGroupId $rg.id
                                # ---------------------------------------------

                                # Build enhanced output object with ordered hashtable for release_group
                                $releaseGroupObj = [ordered]@{
                                    id                     = $rg.id
                                    title                  = $rg.title
                                    'first-release-date'   = $rg.'first-release-date'
                                    'primary-type'         = $rg.'primary-type'
                                    'secondary-types'      = $rg.'secondary-types'
                                    discogs_id             = $discogsId                   # legacy, kept for compatibility
                                    master_discogs_id      = $masterDiscogsId             # new field
                                    release_discogs_id     = $releaseDiscogsId            # new field
                                    relations              = $rg.relations
                                    wikipedia_url          = $wikipediaUrlFromMB          # from MusicBrainz relations
                                    wikipedia_url_search   = $wikipediaUrlFromSearch    # from Wikipedia API search
                                    cover_image_small      = $coverImage                 # NEW: thumbnail URL or null
                                }

                                $output = [PSCustomObject]@{
                                    artist = @{
                                        id   = $currentMbid
                                        name = $artistName
                                    }
                                    release_group = $releaseGroupObj
                                    discogs = $discogsData
                                    first_release = $releaseDetail
                                }

                                $jsonLine = $output | ConvertTo-Json -Compress -Depth 10
                                Add-Content -Path $OutputFile -Value $jsonLine
                                Write-Host "      Successfully wrote album data to output file"
                            }
                            catch {
                                Write-Warning "      Failed to fetch release details for $($firstRelease.id) : $_"
                            }
                        }
                        else {
                            Write-Host "      No releases found for this release group."
                        }
                    }
                    catch {
                        Write-Warning "      Failed to fetch releases for release group $($rg.id) : $_"
                    }

                    Start-Sleep -Milliseconds 200
                }
            }

            $page++
        }
        catch {
            Write-Error "  Failed to fetch release groups for artist $currentMbid on page $page : $_"
            break
        }

    } while ($page -le $totalPages)

    Start-Sleep -Milliseconds 500
}

Write-Host "Done. Output saved to $OutputFile"
Write-Host "Total artists processed: $artistCounter"