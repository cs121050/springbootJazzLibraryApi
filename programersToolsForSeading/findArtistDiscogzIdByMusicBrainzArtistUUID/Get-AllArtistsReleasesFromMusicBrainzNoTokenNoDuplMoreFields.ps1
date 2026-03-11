<#
.SYNOPSIS
    Fetches MusicBrainz studio albums for artists listed in an SQL file,
    adds cover image URL from Cover Art Archive, tracklist from first release,
    label information, and Wikipedia description. Outputs one JSON object per
    album in JSON Lines format.

.DESCRIPTION
    This script reads an SQL file (e.g., an INSERT dump of an Artist table),
    extracts all MusicBrainz artist MBIDs, queries the MusicBrainz API for
    each artist's release groups, filters to keep only studio albums, retrieves
    the release group metadata, and then enriches each album with:
    - cover_image_small: 250px thumbnail from Cover Art Archive
    - tracks: Track metadata from the album's first/front release
    - label: Record label company that released the first release
    - wikipedia_summary: A short description from Wikipedia via their API
    It respects MusicBrainz rate limits and adds appropriate delays.

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
$wikipediaApiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary"
$minRequestIntervalMs = 1100   # 1 request per second + 100ms buffer for MusicBrainz
$coverArtDelayMs = 200         # small delay between Cover Art Archive checks
$wikipediaDelayMs = 200        # delay for Wikipedia API calls
$userAgent = "MusicBrainzStudioAlbumCollector/1.0 (https://example.com)"

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

    $thumbnailUrl = "$coverArtBaseUrl/release-group/$ReleaseGroupId/front-250.jpg"

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
# Function to get the first release of a release group
# ------------------------------------------------------------
function Get-FirstRelease {
    param([string]$ReleaseGroupId)

    $url = "$baseApiUrl/release?release-group=$ReleaseGroupId&inc=labels&fmt=json"
    
    try {
        $response = Invoke-MusicBrainzApi -Url $url
        
        if ($response.releases -and $response.releases.Count -gt 0) {
            # Sort releases by date to find the first one
            $sortedReleases = $response.releases | Sort-Object { 
                if ($_.date) { 
                    # Convert date string to sortable format (YYYY-MM-DD)
                    $dateStr = $_.date
                    if ($dateStr.Length -eq 4) { "$dateStr-01-01" }
                    elseif ($dateStr.Length -eq 7) { "$dateStr-01" }
                    else { $dateStr }
                } else { "9999-12-31" }  # Put releases without dates at the end
            }
            
            return $sortedReleases[0]  # Return the earliest release
        }
        return $null
    }
    catch {
        Write-Warning "      Failed to fetch releases for release group $ReleaseGroupId : $_"
        return $null
    }
}

# ------------------------------------------------------------
# Function to get tracklist for a release
# ------------------------------------------------------------
function Get-Tracklist {
    param([string]$ReleaseId)

    $url = "$baseApiUrl/release/$ReleaseId?inc=recordings&fmt=json"
    
    try {
        $response = Invoke-MusicBrainzApi -Url $url
        
        $tracks = @()
        if ($response.media) {
            foreach ($medium in $response.media) {
                if ($medium.tracks) {
                    foreach ($track in $medium.tracks) {
                        $trackInfo = @{
                            position = $track.position
                            title = $track.title
                            length = $track.length
                            recording_id = $track.recording.id
                        }
                        
                        # Add artist credits if available
                        if ($track.recording -and $track.recording.'artist-credit') {
                            $artists = @()
                            foreach ($credit in $track.recording.'artist-credit') {
                                if ($credit.artist) {
                                    $artists += $credit.artist.name
                                }
                            }
                            if ($artists.Count -gt 0) {
                                $trackInfo.artists = $artists
                            }
                        }
                        
                        $tracks += $trackInfo
                    }
                }
            }
        }
        return $tracks
    }
    catch {
        Write-Warning "      Failed to fetch tracklist for release $ReleaseId : $_"
        return @()
    }
}

# ------------------------------------------------------------
# Function to get label information from a release
# ------------------------------------------------------------
function Get-LabelInfo {
    param([object]$Release)

    if ($Release.'label-info' -and $Release.'label-info'.Count -gt 0) {
        $labelInfo = $Release.'label-info'[0]
        if ($labelInfo.label) {
            return @{
                name = $labelInfo.label.name
                id = $labelInfo.label.id
                catalog_number = $labelInfo.'catalog-number'
            }
        }
    }
    return $null
}

# ------------------------------------------------------------
# Function to get Wikipedia summary for an album
# ------------------------------------------------------------
function Get-WikipediaSummary {
    param(
        [string]$ArtistName,
        [string]$AlbumTitle
    )

    # Clean up artist and album names for Wikipedia search
    $searchTerm = "$ArtistName $AlbumTitle album"
    $encodedTerm = [uri]::EscapeDataString($searchTerm)
    
    # Wikipedia API URL for search
    $searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedTerm&format=json"
    
    try {
        # Small delay to be respectful to Wikipedia
        Start-Sleep -Milliseconds $wikipediaDelayMs
        
        $headers = @{ "User-Agent" = $userAgent }
        $searchResponse = Invoke-RestMethod -Uri $searchUrl -Headers $headers
        
        if ($searchResponse.query.search -and $searchResponse.query.search.Count -gt 0) {
            # Get the first search result
            $pageTitle = $searchResponse.query.search[0].title
            $encodedTitle = [uri]::EscapeDataString($pageTitle)
            
            # Get the summary for that page
            $summaryUrl = "$wikipediaApiUrl/$encodedTitle"
            
            # Another small delay
            Start-Sleep -Milliseconds $wikipediaDelayMs
            
            $summaryResponse = Invoke-RestMethod -Uri $summaryUrl -Headers $headers
            
            if ($summaryResponse.extract) {
                Write-Host "      Wikipedia summary found for '$pageTitle'"
                return @{
                    title = $pageTitle
                    extract = $summaryResponse.extract
                    url = $summaryResponse.content_urls.desktop.page
                }
            }
        }
    }
    catch {
        Write-Host "      No Wikipedia summary found for '$ArtistName - $AlbumTitle'"
    }
    
    return $null
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

                    # Get the artist name from artist-credit
                    $artistName = ""
                    if ($rg.'artist-credit' -and $rg.'artist-credit'.Count -gt 0) {
                        $artistCredit = $rg.'artist-credit'[0]
                        if ($artistCredit.artist) {
                            $artistName = $artistCredit.artist.name
                        }
                    }

                    # Get the first release
                    $firstRelease = Get-FirstRelease -ReleaseGroupId $rg.id
                    
                    # Initialize enrichment fields
                    $tracks = @()
                    $label = $null
                    
                    if ($firstRelease) {
                        Write-Host "      First release found: $($firstRelease.title) (ID: $($firstRelease.id), Date: $($firstRelease.date))"
                        
                        # Get tracklist from the first release
                        $tracks = Get-Tracklist -ReleaseId $firstRelease.id
                        Write-Host "      Found $($tracks.Count) tracks"
                        
                        # Get label information
                        $label = Get-LabelInfo -Release $firstRelease
                        if ($label) {
                            Write-Host "      Label: $($label.name) (Catalog: $($label.catalog_number))"
                        }
                    }

                    # Get the small cover image URL
                    $thumbnailUrl = Get-CoverArtThumbnail -ReleaseGroupId $rg.id

                    # Get Wikipedia summary
                    $wikipedia = Get-WikipediaSummary -ArtistName $artistName -AlbumTitle $rg.title

                    # Add enrichment properties to the release group object
                    $rg | Add-Member -NotePropertyName 'cover_image_small' -NotePropertyValue $thumbnailUrl -Force
                    $rg | Add-Member -NotePropertyName 'tracks' -NotePropertyValue $tracks -Force
                    $rg | Add-Member -NotePropertyName 'label' -NotePropertyValue $label -Force
                    $rg | Add-Member -NotePropertyName 'wikipedia_summary' -NotePropertyValue $wikipedia -Force
                    
                    # Also add first release info for reference
                    $rg | Add-Member -NotePropertyName 'first_release' -NotePropertyValue $firstRelease -Force

                    # Output the enriched release group as a JSON line
                    $jsonLine = $rg | ConvertTo-Json -Compress -Depth 10
                    Add-Content -Path $OutputFile -Value $jsonLine

                    # Small delay to be gentle to APIs
                    Start-Sleep -Milliseconds $coverArtDelayMs
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