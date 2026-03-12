<#
.SYNOPSIS
    Fetches all releases for a Discogs artist where the artist is the main artist.
.DESCRIPTION
    Retrieves every release of an artist from the Discogs API where the artist's role is "Main",
    then fetches full details for each release, including tracklist, personnel,
    release date, and cover thumbnail.
.PARAMETER ArtistId
    The Discogs artist ID (numeric). Required.
.PARAMETER Token
    Optional Discogs personal access token (increases rate limit to 60/min).
.PARAMETER OutputFile
    Path to output JSON Lines file (default: "discogs_artist_{id}_main_releases.jsonl").
#>

param(
    [Parameter(Mandatory = $true)]
    [int]$ArtistId,

    [string]$Token,

    [string]$OutputFile
)

# Set default output filename if not provided
if (-not $OutputFile) {
    $OutputFile = "discogs_artist_${ArtistId}_main_releases.jsonl"
}

# Configure API headers
$headers = @{
    'User-Agent' = 'MyJazzApp/1.0 ( your-email@example.com )'
}
if ($Token) {
    $headers['Authorization'] = "Discogs token=$Token"
}

# Base URLs
$baseUrl = "https://api.discogs.com"
$artistReleasesUrl = "$baseUrl/artists/$ArtistId/releases?per_page=100"

# Function to invoke API with retry and rate limiting
function Invoke-DiscogsApi {
    param([string]$Uri)
    $retries = 0
    $maxRetries = 5
    while ($retries -lt $maxRetries) {
        try {
            $response = Invoke-RestMethod -Uri $Uri -Headers $headers -Method Get
            # Respect rate limit: wait 1.2 seconds after each successful call
            Start-Sleep -Milliseconds 1200
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

Write-Host "Fetching release list for artist ID $ArtistId ..."

# Collect only main releases (role = "Main")
$releaseItems = @()
$nextUrl = $artistReleasesUrl

while ($nextUrl) {
    Write-Host "  Fetching page: $nextUrl"
    $response = Invoke-DiscogsApi -Uri $nextUrl
    foreach ($rel in $response.releases) {
        # Only include if the artist is the main artist on this release
        if ($rel.role -eq "Main") {
            $releaseItems += [PSCustomObject]@{
                releaseId = $rel.id
                masterId  = if ($rel.PSObject.Properties.Name -contains 'master_id') { $rel.master_id } else { $null }
                title     = $rel.title
                thumb     = $rel.thumb
                year      = $rel.year
            }
            Write-Host "    Included: $($rel.title) (ID: $($rel.id))"
        }
        else {
            Write-Host "    Skipped (role: $($rel.role)): $($rel.title)"
        }
    }
    $nextUrl = $response.pagination.urls.next
}

Write-Host "Found $($releaseItems.Count) main releases. Fetching details..."

# Open output file for writing
$stream = [System.IO.StreamWriter]::new($OutputFile)

$processed = 0
foreach ($item in $releaseItems) {
    $processed++
    $relId = $item.releaseId
    Write-Progress -Activity "Fetching release details" -Status "$processed / $($releaseItems.Count) : $($item.title)" -PercentComplete (($processed / $releaseItems.Count) * 100)

    # Fetch full release data
    try {
        $releaseUrl = "$baseUrl/releases/$relId"
        $release = Invoke-DiscogsApi -Uri $releaseUrl

        # Build personnel list (main artists + extra artists from tracks)
        $personnel = @()

        # Add main artists
        foreach ($artist in $release.artists) {
            $personnel += [PSCustomObject]@{
                name = $artist.name
                role = 'Main Artist'
                id   = $artist.id
            }
        }

        # Add extra artists from tracklist
        foreach ($track in $release.tracklist) {
            if ($track.extraartists) {
                foreach ($extra in $track.extraartists) {
                    $personnel += [PSCustomObject]@{
                        name = $extra.name
                        role = $extra.role
                        id   = $extra.id
                    }
                }
            }
        }

        # Deduplicate personnel by id (if id exists) else by name+role
        $personnel = $personnel | Sort-Object -Property @{Expression={$_.id}; Ascending=$true} -Unique

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

        # Build output object
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
            personnel          = $personnel
        }

        # Convert to JSON and write as one line
        $jsonLine = $out | ConvertTo-Json -Depth 5 -Compress
        $stream.WriteLine($jsonLine)
    }
    catch {
        Write-Warning "Failed to fetch release $relId ($($item.title)): $_"
    }
}

$stream.Close()
Write-Host "Done. Output written to $OutputFile"