<#
.SYNOPSIS
    Fetches Discogs release data for artists listed in an SQL INSERT file,
    enriches each album with matching MusicBrainz data from a JSONL file,
    and outputs one JSON object per album.
.DESCRIPTION
    [Description unchanged]
.PARAMETER SqlFile
    Path to the SQL file containing the INSERT statements.
.PARAMETER MusicBrainzFile
    Path to the JSONL file containing MusicBrainz album data (default: "mb_albums_ofartists.jsonl").
.PARAMETER OutputFile
    Path where the output .jsonl file will be saved (default: "discogs_releases.jsonl").
.EXAMPLE
    .\Get-AllArtistsReleasesNoToken.ps1 -SqlFile "_artists_without_quotes.sql" -MusicBrainzFile "mb_albums_ofartists.jsonl"
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
# Initialize last request time
# ------------------------------------------------------------
if (-not (Get-Variable -Name 'lastRequestTime' -Scope Script -ErrorAction SilentlyContinue)) {
    $script:lastRequestTime = (Get-Date).AddMilliseconds(-$minRequestIntervalMs)
}

# ------------------------------------------------------------
# Helper function: Invoke-DiscogsApi (with rate limiting & retries)
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
        $values = $valuesPart -split ',' | ForEach-Object { $_.Trim().Trim("'") }

        if ($values.Count -ge 7) {
            $spotifyId    = $values[0]
            $artistName   = $values[1]
            $artistSurname= $values[2]
            $musicbrainzId= $values[3]
            $discogsIdRaw = $values[4]
            $instrumentId = $values[5]
            $wikipediaUrl = $values[6]

            if ($discogsIdRaw -match '^\d+$') {
                $discogsId = [int]$discogsIdRaw
                $artistMap[$discogsId] = [PSCustomObject]@{
                    discogs_id      = $discogsId
                    musicbrainz_uuid = $musicbrainzId
                    artist_name      = $artistName
                    artist_surname   = $artistSurname
                    wikipedia_url    = $wikipediaUrl
                    full_name        = "$artistName $artistSurname".Trim()
                }
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
# Step 2: Read MusicBrainz JSONL file and build lookup table
# ------------------------------------------------------------
Write-Host "Reading MusicBrainz data from '$MusicBrainzFile'..."
$musicBrainzLookup = @{}

if (Test-Path $MusicBrainzFile) {
    $mbLines = Get-Content -Path $MusicBrainzFile
    $lineCount = 0
    foreach ($mbLine in $mbLines) {
        $lineCount++
        try {
            $mbObject = $mbLine | ConvertFrom-Json
            $discogsAlbumId = $null
            if ($mbObject.DiscogsAPIcall -and $mbObject.DiscogsAPIcall.id) {
                $discogsAlbumId = $mbObject.DiscogsAPIcall.id
            } elseif ($mbObject.release_group -and $mbObject.release_group.discogs_id) {
                $discogsAlbumId = $mbObject.release_group.discogs_id
            } elseif ($mbObject.discogs -and $mbObject.discogs.id) {
                $discogsAlbumId = $mbObject.discogs.id
            } else {
                $props = ($mbObject.PSObject.Properties.Name) -join ', '
                Write-Warning "Line $lineCount in $MusicBrainzFile has no recognizable discogs_id. Top-level properties: $props"
                continue
            }
            $musicBrainzLookup[$discogsAlbumId.ToString()] = $mbObject
        }
        catch {
            Write-Warning "Failed to parse line $lineCount in $MusicBrainzFile : $_"
        }
    }
    Write-Host "Loaded $($musicBrainzLookup.Count) unique Discogs album IDs from MusicBrainz data."
} else {
    Write-Warning "MusicBrainz file '$MusicBrainzFile' not found. Proceeding without enrichment."
}

# ------------------------------------------------------------
# Step 3: For each artist, fetch releases and detailed metadata
# ------------------------------------------------------------
Write-Host "Output will be written to '$OutputFile'"
if (Test-Path $OutputFile) { Remove-Item $OutputFile }

$totalArtists = $discogsIds.Count
$artistCounter = 0

foreach ($artistId in $discogsIds) {
    $artistCounter++
    Write-Host "[$artistCounter/$totalArtists] Processing artist ID $artistId"

    $artistInfo = $artistMap[$artistId]
    if (-not $artistInfo) {
        Write-Warning "No metadata found for artist ID $artistId – skipping."
        continue
    }

    try {
        $baseUri = [System.Uri]::new($baseApiUrl)
        $releasesUri = [System.Uri]::new($baseUri, "/artists/$artistId/releases")
        $releasesUrl = $releasesUri.ToString()
    }
    catch {
        Write-Error "Failed to build URI for artist $artistId : $_"
        continue
    }

    $processedMasters = New-Object 'System.Collections.Generic.HashSet[int]'
    $page = 1
    $perPage = 100

    do {
        $uriBuilder = [System.UriBuilder]::new($releasesUrl)
        $uriBuilder.Query = "page=$page&per_page=$perPage"
        $url = $uriBuilder.Uri.ToString()

        Write-Host "DEBUG: Full URL = $url"
        $response = Invoke-DiscogsApi -Url $url

        foreach ($release in $response.releases) {
            if ($release.role -ne 'Main') { continue }

            Write-Host "  -> Found release: $($release.title) (ID: $($release.id), type: $($release.type))"

            try {
                if ($release.type -eq 'master') {
                    $fullUrl = "$baseApiUrl/masters/$($release.id)"
                } else {
                    $fullUrl = $release.resource_url
                }

                $fullData = Invoke-DiscogsApi -Url $fullUrl
                $discogsObject = $null

                if ($release.type -eq 'release' -and $fullData.PSObject.Properties.Name -contains 'master_id' -and $fullData.master_id) {
                    $masterId = $fullData.master_id
                    if (-not $processedMasters.Contains($masterId)) {
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
                    $discogsObject = $fullData
                    Write-Host "      -> Fetched standalone release"
                }

                if ($discogsObject) {
                    # ----- Fetch all version IDs for this master (if it's a master) -----
                    $allVersionIds = @()
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
                        }
                        $discogsObject | Add-Member -MemberType NoteProperty -Name 'discogs_all_versions_Ids' -Value $allVersionIds -Force
                    } else {
                        $discogsObject | Add-Member -MemberType NoteProperty -Name 'discogs_all_versions_Ids' -Value @() -Force
                    }

                    # MusicBrainz enrichment
                    $albumId = $discogsObject.id.ToString()
                    $musicBrainzData = $null
                    if ($musicBrainzLookup.ContainsKey($albumId)) {
                        $musicBrainzData = $musicBrainzLookup[$albumId]
                        Write-Host "      -> Found matching MusicBrainz data for album ID $albumId"
                    }

                    $wrapper = [ordered]@{
                        artist = [ordered]@{
                            id            = $artistInfo.musicbrainz_uuid
                            name          = $artistInfo.full_name
                            discogs_id    = $artistId
                            wikipedia_url = $artistInfo.wikipedia_url
                        }
                        DiscogsAPIcall = $discogsObject
                    }

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