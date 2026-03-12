<#
.SYNOPSIS
    Fetches all main releases for Discogs artists listed in a SQL file.
.DESCRIPTION
    Reads a SQL file with INSERT statements for the Artist table,
    extracts all discogs_id values, then fetches all main releases
    for each artist. Outputs one JSON line per release with proper
    personnel credits and renamed main_artist_json field.
.PARAMETER SqlFile
    Path to the SQL file containing artist INSERT statements (required).
.PARAMETER Token
    Discogs personal access token (required for higher rate limit).
.PARAMETER OutputFile
    Path to output JSON Lines file (default: "all_artists_releases.jsonl").
.PARAMETER DelayMs
    Milliseconds between API calls (default 1000 for safety).
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$SqlFile,
    
    [Parameter(Mandatory = $true)]
    [string]$Token,
    
    [string]$OutputFile = "all_artists_releases.jsonl",
    
    [int]$DelayMs = 1000
)

# ----------------------------------------------------------------------
# 1. Check if input file exists
# ----------------------------------------------------------------------
if (-not (Test-Path $SqlFile)) {
    Write-Error "SQL file not found: $SqlFile"
    exit 1
}

Write-Host "Reading artist IDs from: $SqlFile" -ForegroundColor Green

# ----------------------------------------------------------------------
# 2. Extract Discogs artist IDs from the SQL file
# ----------------------------------------------------------------------
$sql = Get-Content $SqlFile -Raw
$pattern = 'VALUES\s*\([^,]*,[^,]*,[^,]*,[^,]*,\s*(\d+)\s*,'
$artistIds = [System.Collections.Generic.HashSet[int]]::new()
$matches = [regex]::Matches($sql, $pattern)

foreach ($match in $matches) {
    $discogsId = [int]$match.Groups[1].Value
    $null = $artistIds.Add($discogsId)
}

Write-Host "Found $($artistIds.Count) unique artist IDs." -ForegroundColor Green

if ($artistIds.Count -eq 0) {
    Write-Error "No artist IDs found in $SqlFile."
    exit 1
}

# ----------------------------------------------------------------------
# 3. Setup API headers and shared variables
# ----------------------------------------------------------------------
$headers = @{
    'User-Agent' = 'MyJazzApp/1.0 ( your-email@example.com )'
    'Authorization' = "Discogs token=$Token"
}
$baseUrl = "https://api.discogs.com"

$processedReleaseIds = [System.Collections.Generic.HashSet[int]]::new()

try {
    $stream = [System.IO.StreamWriter]::new($OutputFile)
    Write-Host "Output will be written to: $OutputFile" -ForegroundColor Green
}
catch {
    Write-Error "Cannot create output file: $OutputFile"
    exit 1
}

# ----------------------------------------------------------------------
# 4. Function to invoke API with retry and throttling
# ----------------------------------------------------------------------
function Invoke-DiscogsApi {
    param([string]$Uri)
    $retries = 0
    $maxRetries = 5
    while ($retries -lt $maxRetries) {
        try {
            $response = Invoke-RestMethod -Uri $Uri -Headers $headers -Method Get
            Start-Sleep -Milliseconds $DelayMs
            return $response
        }
        catch {
            $retries++
            if ($retries -ge $maxRetries) { throw }
            Write-Warning "API call failed ($Uri). Retry $retries/$maxRetries in 5 seconds..."
            Start-Sleep -Seconds 5
        }
    }
}

# ----------------------------------------------------------------------
# 5. Process each artist
# ----------------------------------------------------------------------
$totalArtists = $artistIds.Count
$currentArtist = 0
$totalReleases = 0

foreach ($artistId in $artistIds) {
    $currentArtist++
    Write-Host "[$currentArtist/$totalArtists] Processing artist ID $artistId ..." -ForegroundColor Cyan

    # Get list of main releases for this artist
    $releasesUrl = "$baseUrl/artists/$artistId/releases?per_page=100"
    $releaseItems = @()
    $nextUrl = $releasesUrl

    while ($nextUrl) {
        try {
            $resp = Invoke-DiscogsApi -Uri $nextUrl
            foreach ($rel in $resp.releases) {
                if ($rel.role -eq "Main") {
                    $releaseItems += [PSCustomObject]@{
                        releaseId = $rel.id
                        masterId  = if ($rel.PSObject.Properties.Name -contains 'master_id') { $rel.master_id } else { $null }
                        title     = $rel.title
                        thumb     = $rel.thumb
                        year      = $rel.year
                    }
                }
            }
            $nextUrl = $resp.pagination.urls.next
        }
        catch {
            Write-Warning "Failed to fetch release list for artist $artistId. Skipping..."
            break
        }
    }

    Write-Host "  Found $($releaseItems.Count) main releases."

    # Fetch details for each release
    foreach ($item in $releaseItems) {
        $relId = $item.releaseId
        if ($processedReleaseIds.Contains($relId)) {
            Write-Host "    Skipping already processed release $relId - $($item.title)" -ForegroundColor DarkGray
            continue
        }
        $null = $processedReleaseIds.Add($relId)
        $totalReleases++

        Write-Host "    Fetching release $relId - $($item.title)" -ForegroundColor Gray
        try {
            $releaseUrl = "$baseUrl/releases/$relId"
            $release = Invoke-DiscogsApi -Uri $releaseUrl

            # ------------------------------------------------------------------
            # Build main_artist_json (renamed from personnel_json)
            # This contains ONLY the main artists (album leaders)
            # ------------------------------------------------------------------
            $mainArtists = @()
            foreach ($artist in $release.artists) {
                $mainArtists += [PSCustomObject]@{
                    name = $artist.name
                    role = 'Main Artist'
                    id   = $artist.id
                }
            }

            # ------------------------------------------------------------------
            # Build credits array with ALL personnel (including main artists
            # and extra artists from tracks, formatted like Discogs credits)
            # ------------------------------------------------------------------
            $credits = @()
            
            # Add main artists with their roles
            foreach ($artist in $release.artists) {
                $creditEntry = [PSCustomObject]@{
                    name = $artist.name
                    role = 'Main Artist'
                    tracks = $null  # Main artists appear on entire album
                    id = $artist.id
                }
                $credits += $creditEntry
            }
            
            # Add extra artists from tracklist (these have specific track assignments)
            $trackIndex = 0
            foreach ($track in $release.tracklist) {
                $trackIndex++
                if ($track.extraartists) {
                    foreach ($extra in $track.extraartists) {
                        # Check if we already have this artist+role combination
                        $existingCredit = $credits | Where-Object { 
                            $_.id -eq $extra.id -and $_.role -eq $extra.role 
                        }
                        
                        if ($existingCredit) {
                            # Append track number to existing credit
                            $trackPosition = if ($track.position) { $track.position } else { "Track $trackIndex" }
                            if ($existingCredit.tracks) {
                                $existingCredit.tracks += ", $trackPosition"
                            } else {
                                $existingCredit.tracks = "$trackPosition"
                            }
                        } else {
                            # Create new credit entry
                            $trackPosition = if ($track.position) { $track.position } else { "Track $trackIndex" }
                            $creditEntry = [PSCustomObject]@{
                                name = $extra.name
                                role = $extra.role
                                tracks = $trackPosition
                                id = $extra.id
                            }
                            $credits += $creditEntry
                        }
                    }
                }
            }

            # Format credits as strings like "Clarinet, Soprano Saxophone – Louis Sclavis (tracks: 4, 5)"
            $creditsFormatted = @()
            foreach ($credit in $credits) {
                if ($credit.tracks) {
                    $creditsFormatted += "$($credit.role) – $($credit.name) (tracks: $($credit.tracks))"
                } else {
                    $creditsFormatted += "$($credit.role) – $($credit.name)"
                }
            }

            # Build tracklist
            $tracklist = @()
            foreach ($track in $release.tracklist) {
                $tracklist += [PSCustomObject]@{
                    position = $track.position
                    title    = $track.title
                    duration = $track.duration
                    artists  = if ($track.artists) { $track.artists.name } else { $null }
                }
            }

            # Build output object with renamed field
            $out = [PSCustomObject]@{
                discogs_release_id = $relId
                discogs_master_id  = $item.masterId
                title              = $release.title
                artists            = $release.artists | ForEach-Object { $_.name }
                released           = $release.released
                year               = $release.year
                country            = $release.country
                labels             = $release.labels | ForEach-Object { $_.name }
                genres             = $release.genres
                styles             = $release.styles
                thumb              = $item.thumb
                cover_url          = if ($release.images) { $release.images[0].uri } else { $null }
                tracklist          = $tracklist
                main_artist_json   = $mainArtists      # ← RENAMED from personnel_json
                credits            = $creditsFormatted # ← NEW: formatted credits
                credits_raw        = $credits          # ← RAW credits data (if needed)
            }

            # Write as compressed JSON line
            $jsonLine = $out | ConvertTo-Json -Depth 5 -Compress
            $stream.WriteLine($jsonLine)
        }
        catch {
            Write-Warning "    Failed to fetch release $relId ($($item.title)): $_"
        }
    }
}

$stream.Close()
Write-Host "=" * 60 -ForegroundColor Green
Write-Host "COMPLETE!" -ForegroundColor Green
Write-Host "Output file: $OutputFile" -ForegroundColor Green
Write-Host "Artists processed: $totalArtists" -ForegroundColor Green
Write-Host "Unique releases fetched: $totalReleases" -ForegroundColor Green
Write-Host "=" * 60 -ForegroundColor Green