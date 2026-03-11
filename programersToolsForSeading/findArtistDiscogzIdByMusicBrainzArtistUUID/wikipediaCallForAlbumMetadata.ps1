<#
.SYNOPSIS
    Extracts artist MBIDs from SQL file, fetches artist metadata, then uses Wikidata
    to find all albums by the artist and their individual Wikipedia pages.

.DESCRIPTION
    This script reads an SQL file containing INSERT statements for an Artist table,
    extracts all MusicBrainz artist MBIDs, and for each artist:
      - Gets artist metadata from MusicBrainz
      - Extracts Wikidata ID from artist relations
      - Uses SPARQL query on Wikidata to find all albums by this artist
      - Gets English Wikipedia page for each album
      - Optionally scrapes album Wikipedia content
      - Exports all data as JSON Lines (.jsonl)

.PARAMETER SqlFile
    Path to the SQL file containing the artist INSERT statements.

.PARAMETER OutputFile
    Path where the output .jsonl file will be saved. Defaults to "artists_with_albums.jsonl".

.PARAMETER ScrapeAlbumContent
    Switch to enable scraping full Wikipedia content for each album.

.EXAMPLE
    .\Get-ArtistAlbums.ps1 -SqlFile "_artists_without_quotes.sql" -ScrapeAlbumContent
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$SqlFile,
    
    [string]$OutputFile = ".\artists_with_albums.jsonl",
    
    [switch]$ScrapeAlbumContent
)

# ------------------------------------------------------------
# Configuration
# ------------------------------------------------------------
$musicBrainzApiUrl = "https://musicbrainz.org/ws/2"
$wikidataApiUrl = "https://www.wikidata.org/wiki/Special:EntityData"
$wikidataQueryUrl = "https://query.wikidata.org/sparql"
$wikipediaApiUrl = "https://en.wikipedia.org/api/rest_v1"
$mediaWikiApiUrl = "https://en.wikipedia.org/w/api.php"
$mbMinRequestIntervalMs = 1100          # 1 second + buffer for MusicBrainz
$wikidataMinRequestIntervalMs = 2000     # 2 seconds between Wikidata queries
$wikipediaMinRequestIntervalMs = 2000     # 2 seconds between Wikipedia API calls
$userAgent = "ArtistAlbumCollector/1.0 (https://github.com/yourusername; for research purposes)"
$maxRetries = 3
$baseRetryDelaySec = 5

# Global variables for rate limiting
$script:lastMBRequestTime = (Get-Date).AddMilliseconds(-$mbMinRequestIntervalMs)
$script:lastWikidataRequestTime = (Get-Date).AddMilliseconds(-$wikidataMinRequestIntervalMs)
$script:lastWikipediaRequestTime = (Get-Date).AddMilliseconds(-$wikipediaMinRequestIntervalMs)

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
# Rate‑limited SPARQL query to Wikidata
# ------------------------------------------------------------
function Invoke-WikidataSparqlQuery {
    param([string]$Query)

    $now = Get-Date
    $timeSinceLast = ($now - $script:lastWikidataRequestTime).TotalMilliseconds
    if ($timeSinceLast -lt $wikidataMinRequestIntervalMs) {
        $sleepMs = $wikidataMinRequestIntervalMs - $timeSinceLast
        Write-Host "    Rate limiting (Wikidata Query): sleeping $([math]::Round($sleepMs)) ms"
        Start-Sleep -Milliseconds $sleepMs
    }

    $headers = @{
        "User-Agent" = $userAgent
        "Accept"     = "application/json"
    }

    $body = @{
        query = $Query
        format = "json"
    }

    try {
        Write-Host "    Executing Wikidata SPARQL query"
        $response = Invoke-RestMethod -Uri $wikidataQueryUrl -Method Post -Body $body -Headers $headers -ErrorAction Stop
        $script:lastWikidataRequestTime = Get-Date
        return $response
    }
    catch {
        Write-Warning "    Wikidata SPARQL query failed: $_"
        return $null
    }
}

# ------------------------------------------------------------
# Rate‑limited Wikipedia API caller
# ------------------------------------------------------------
function Invoke-WikipediaApi {
    param([string]$Url)

    $now = Get-Date
    $timeSinceLast = ($now - $script:lastWikipediaRequestTime).TotalMilliseconds
    if ($timeSinceLast -lt $wikipediaMinRequestIntervalMs) {
        $sleepMs = $wikipediaMinRequestIntervalMs - $timeSinceLast
        Write-Host "      Rate limiting (Wikipedia): sleeping $([math]::Round($sleepMs)) ms"
        Start-Sleep -Milliseconds $sleepMs
    }

    $headers = @{
        "User-Agent" = $userAgent
        "Accept"     = "application/json"
    }

    try {
        Write-Host "      Calling Wikipedia API: $Url"
        $response = Invoke-RestMethod -Uri $Url -Headers $headers -ErrorAction Stop
        $script:lastWikipediaRequestTime = Get-Date
        return $response
    }
    catch {
        Write-Warning "      Wikipedia API request failed: $_"
        return $null
    }
}

# ------------------------------------------------------------
# Extract Wikidata ID from artist relations
# ------------------------------------------------------------
function Get-WikidataId {
    param([object]$Artist)

    if (-not $Artist.relations) {
        return $null
    }

    foreach ($relation in $Artist.relations) {
        if ($relation.type -eq "wikidata") {
            if ($relation.url -and $relation.url.resource) {
                $url = $relation.url.resource
                if ($url -match "/(Q\d+)$") {
                    $wikidataId = $matches[1]
                    Write-Host "    Found Wikidata ID: $wikidataId"
                    return $wikidataId
                }
            }
        }
    }
    return $null
}

# ------------------------------------------------------------
# Get English Wikipedia title from Wikidata ID (for artist)
# ------------------------------------------------------------
function Get-WikipediaTitleFromWikidata {
    param([string]$WikidataId)

    if ([string]::IsNullOrEmpty($WikidataId)) {
        return $null
    }

    $url = "$wikidataApiUrl/$WikidataId.json"
    $wikidataResponse = Invoke-WikidataApi -Url $url

    if (-not $wikidataResponse) {
        return $null
    }

    try {
        $entities = $wikidataResponse.entities
        $entity = $entities.PSObject.Properties.Value | Where-Object { $_.id -eq $WikidataId } | Select-Object -First 1
        
        if (-not $entity) {
            return $null
        }

        if ($entity.sitelinks -and $entity.sitelinks.enwiki) {
            $enwiki = $entity.sitelinks.enwiki
            if ($enwiki.title) {
                Write-Host "    Found English Wikipedia title: $($enwiki.title)"
                return $enwiki.title
            }
        }
        
        Write-Host "    No English Wikipedia sitelink found for Wikidata ID: $WikidataId"
        return $null
    }
    catch {
        Write-Warning "    Error processing Wikidata response: $_"
        return $null
    }
}

# ------------------------------------------------------------
# Rate‑limited Wikidata entity fetch
# ------------------------------------------------------------
function Invoke-WikidataApi {
    param([string]$Url)

    $now = Get-Date
    $timeSinceLast = ($now - $script:lastWikidataRequestTime).TotalMilliseconds
    if ($timeSinceLast -lt $wikidataMinRequestIntervalMs) {
        $sleepMs = $wikidataMinRequestIntervalMs - $timeSinceLast
        Write-Host "    Rate limiting (Wikidata): sleeping $([math]::Round($sleepMs)) ms"
        Start-Sleep -Milliseconds $sleepMs
    }

    $headers = @{
        "User-Agent" = $userAgent
        "Accept"     = "application/json"
    }

    try {
        Write-Host "    Calling Wikidata: $Url"
        $response = Invoke-RestMethod -Uri $Url -Headers $headers -ErrorAction Stop
        $script:lastWikidataRequestTime = Get-Date
        return $response
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 404) {
            Write-Host "    Wikidata entity not found (404)"
            return $null
        }
        Write-Warning "    Wikidata API request failed: $_"
        return $null
    }
}

# ------------------------------------------------------------
# Get all albums for an artist from Wikidata using SPARQL
# ------------------------------------------------------------
function Get-ArtistAlbumsFromWikidata {
    param([string]$WikidataId)

    if ([string]::IsNullOrEmpty($WikidataId)) {
        return @()
    }

    Write-Host "    Querying Wikidata for albums by artist: $WikidataId"
    
    # SPARQL query to get all albums by this artist
    # This finds items that:
    # - Are instances of album (Q482994) or any subclass
    # - Have the artist as performer (P175)
    # - Also gets the album's English Wikipedia title if available
    $sparqlQuery = @"
    SELECT ?album ?albumLabel ?wikiTitle ?releaseDate WHERE {
      ?album wdt:P31/wdt:P279* wd:Q482994 ;  # instance of album or subclass
             wdt:P175 wd:$WikidataId .       # performer is this artist
      
      # Optional: get release date
      OPTIONAL { ?album wdt:P577 ?releaseDate . }
      
      # Get English Wikipedia title if available
      OPTIONAL {
        ?wikiSchema schema:about ?album ;
                    schema:isPartOf <https://en.wikipedia.org/> ;
                    schema:name ?wikiTitle .
      }
      
      SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
    }
    ORDER BY ?releaseDate
"@

    $response = Invoke-WikidataSparqlQuery -Query $sparqlQuery
    
    if (-not $response -or -not $response.results -or -not $response.results.bindings) {
        Write-Host "    No albums found in Wikidata for this artist"
        return @()
    }

    $albums = @()
    foreach ($binding in $response.results.bindings) {
        $album = @{
            wikidata_id = if ($binding.album -and $binding.album.value) { 
                ($binding.album.value -replace '.*/','') 
            } else { $null }
            name = if ($binding.albumLabel -and $binding.albumLabel.value) { 
                $binding.albumLabel.value 
            } else { $null }
            release_date = if ($binding.releaseDate -and $binding.releaseDate.value) { 
                $binding.releaseDate.value 
            } else { $null }
            wikipedia_title = if ($binding.wikiTitle -and $binding.wikiTitle.value) { 
                $binding.wikiTitle.value 
            } else { $null }
            wikipedia_url = if ($binding.wikiTitle -and $binding.wikiTitle.value) { 
                "https://en.wikipedia.org/wiki/" + ($binding.wikiTitle.value -replace ' ', '_')
            } else { $null }
        }
        
        # Only add if we have at least a name
        if ($album.name) {
            $albums += $album
        }
    }

    Write-Host "    Found $($albums.Count) albums in Wikidata"
    return $albums
}

# ------------------------------------------------------------
# Get Wikipedia article content for an album
# ------------------------------------------------------------
function Get-WikipediaArticleContent {
    param([string]$PageTitle)

    if ([string]::IsNullOrEmpty($PageTitle)) {
        return $null
    }

    Write-Host "      Fetching Wikipedia article: $PageTitle"
    
    # Use TextExtracts API for plain text content
    $url = "$mediaWikiApiUrl?action=query&prop=extracts&explaintext=&titles=$([uri]::EscapeDataString($PageTitle))&format=json"
    
    $response = Invoke-WikipediaApi -Url $url
    
    if (-not $response -or -not $response.query -or -not $response.query.pages) {
        Write-Host "      No content returned from Wikipedia API"
        return $null
    }

    $pages = $response.query.pages
    $pageId = ($pages.PSObject.Properties | Select-Object -First 1).Name
    
    if ($pageId -eq "-1") {
        Write-Host "      Page not found"
        return $null
    }
    
    $page = $pages.$pageId
    
    if (-not $page.extract) {
        Write-Host "      No extract available for this page"
        return $null
    }

    # Get links from the page
    $linksUrl = "$mediaWikiApiUrl?action=query&prop=links&pllimit=500&titles=$([uri]::EscapeDataString($PageTitle))&format=json"
    $linksResponse = Invoke-WikipediaApi -Url $linksUrl
    
    $links = @()
    if ($linksResponse -and $linksResponse.query -and $linksResponse.query.pages) {
        $linksPage = $linksResponse.query.pages.$pageId
        if ($linksPage.links) {
            foreach ($link in $linksPage.links) {
                $links += @{
                    title = $link.title
                    url = "https://en.wikipedia.org/wiki/" + ($link.title -replace ' ', '_')
                }
            }
        }
    }

    return @{
        title = $page.title
        page_id = $page.pageid
        content = $page.extract
        word_count = $page.extract.Split(' ', [StringSplitOptions]::RemoveEmptyEntries).Count
        character_count = $page.extract.Length
        links = $links
        url = "https://en.wikipedia.org/wiki/" + ($page.title -replace ' ', '_')
        retrieved_at = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssK")
    }
}

# ------------------------------------------------------------
# Extract all unique artists with MBIDs from the SQL file
# ------------------------------------------------------------
Write-Host "Reading SQL file '$SqlFile'..."
$sqlContent = Get-Content -Path $SqlFile -Raw

# Pattern to match UUID in the SQL INSERT statements
$uuidPattern = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
$matches = [regex]::Matches($sqlContent, $uuidPattern)

$artists = @()
foreach ($match in $matches) {
    $cleanMbid = $match.Value.Trim()
    
    # Find the line containing this MBID to extract names
    $linePattern = "VALUES\s*\([^)]*'$cleanMbid'[^)]*\)"
    $lineMatch = [regex]::Match($sqlContent, $linePattern)
    
    if ($lineMatch.Success) {
        $line = $lineMatch.Value
        $valuePattern = "'([^']*)'|(\d+)"
        $valueMatches = [regex]::Matches($line, $valuePattern)
        
        $values = @()
        foreach ($valMatch in $valueMatches) {
            $values += $valMatch.Value.Trim("'")
        }
        
        if ($values.Count -ge 6) {
            $spotifyId = $values[0]
            $firstName = $values[1]
            $lastName = $values[2]
            $mbid = $values[3]
            $discogsId = $values[4]
            $instrumentId = $values[5]
            
            $fullName = if ($lastName) { "$firstName $lastName".Trim() } else { $firstName }
            
            $artists += [PSCustomObject]@{
                MBID = $mbid
                FullName = $fullName
                FirstName = $firstName
                LastName = $lastName
                SpotifyPlaylistId = $spotifyId
                DiscogsId = $discogsId
                InstrumentId = $instrumentId
            }
        }
    }
    else {
        $artists += [PSCustomObject]@{
            MBID = $cleanMbid
            FullName = "Unknown"
            FirstName = ""
            LastName = ""
            SpotifyPlaylistId = ""
            DiscogsId = ""
            InstrumentId = ""
        }
    }
}

$artists = $artists | Sort-Object MBID | Get-Unique -AsString

Write-Host "Found $($artists.Count) unique artists with MBIDs."
if ($artists.Count -eq 0) {
    Write-Error "No MBIDs found. Exiting."
    exit 1
}

Write-Host "First 5 artists:"
for ($i = 0; $i -lt [math]::Min(5, $artists.Count); $i++) {
    Write-Host "  $($i+1): $($artists[$i].FullName) - '$($artists[$i].MBID)'"
}

# ------------------------------------------------------------
# Prepare output file
# ------------------------------------------------------------
if (Test-Path $OutputFile) { Remove-Item $OutputFile }
Write-Host "Output will be written to '$OutputFile'"
Write-Host "Album Wikipedia scraping is: $(if($ScrapeAlbumContent){'ENABLED'}else{'DISABLED'})"

$totalArtists = $artists.Count
$artistCounter = 0
$totalAlbumsFound = 0

# ------------------------------------------------------------
# Process each artist
# ------------------------------------------------------------
foreach ($artistInfo in $artists) {
    $artistCounter++
    $currentMbid = $artistInfo.MBID
    $artistFullName = $artistInfo.FullName

    Write-Host "[$artistCounter/$totalArtists] Processing artist: $artistFullName (MBID: $currentMbid)"

    if ([string]::IsNullOrEmpty($currentMbid)) {
        Write-Warning "  Skipping empty MBID for $artistFullName"
        continue
    }

    # 1. Get artist metadata from MusicBrainz with relations
    $artistUrl = "${musicBrainzApiUrl}/artist/${currentMbid}?fmt=json&inc=url-rels+aliases"
    try {
        $artist = Invoke-MusicBrainzApi -Url $artistUrl
        Write-Host "  Artist name from MusicBrainz: $($artist.name)"
    }
    catch {
        Write-Error "  Failed to fetch artist $currentMbid : $_"
        
        $output = [PSCustomObject]@{
            artist = @{
                id = $currentMbid
                name = $artistFullName
                first_name = $artistInfo.FirstName
                last_name = $artistInfo.LastName
                spotify_playlist_id = $artistInfo.SpotifyPlaylistId
                discogs_id = $artistInfo.DiscogsId
                instrument_id = $artistInfo.InstrumentId
                error = "Failed to fetch from MusicBrainz: $_"
            }
            musicbrainz_url = "https://musicbrainz.org/artist/$currentMbid"
            wikidata_id = $null
            wikipedia = $null
            albums = @()
            last_updated = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssK")
        }
        
        $jsonLine = $output | ConvertTo-Json -Compress -Depth 10
        Add-Content -Path $OutputFile -Value $jsonLine
        continue
    }

    # 2. Extract Wikidata ID from artist relations
    $wikidataId = Get-WikidataId -Artist $artist

    # 3. Get artist Wikipedia info
    $artistWikipediaTitle = $null
    $artistWikipediaUrl = $null
    
    if ($wikidataId) {
        $artistWikipediaTitle = Get-WikipediaTitleFromWikidata -WikidataId $wikidataId
        if ($artistWikipediaTitle) {
            $artistWikipediaUrl = "https://en.wikipedia.org/wiki/" + ($artistWikipediaTitle -replace ' ', '_')
        }
    }

    # 4. Get all albums from Wikidata
    $albums = @()
    if ($wikidataId) {
        $wikidataAlbums = Get-ArtistAlbumsFromWikidata -WikidataId $wikidataId
        
        foreach ($album in $wikidataAlbums) {
            $albumObj = @{
                name = $album.name
                wikidata_id = $album.wikidata_id
                release_date = $album.release_date
                wikipedia_title = $album.wikipedia_title
                wikipedia_url = $album.wikipedia_url
            }
            
            # 5. Optionally scrape album Wikipedia content
            if ($ScrapeAlbumContent -and $album.wikipedia_title) {
                Write-Host "      Scraping album: $($album.name)"
                $albumContent = Get-WikipediaArticleContent -PageTitle $album.wikipedia_title
                if ($albumContent) {
                    $albumObj.scrap = $albumContent
                }
            }
            
            $albums += $albumObj
        }
        
        $totalAlbumsFound += $albums.Count
    }

    # 6. Build enhanced output object with albums
    $output = [PSCustomObject]@{
        artist = @{
            id = $currentMbid
            mbid = $currentMbid
            name = $artist.name
            full_name = $artistFullName
            first_name = $artistInfo.FirstName
            last_name = $artistInfo.LastName
            spotify_playlist_id = $artistInfo.SpotifyPlaylistId
            discogs_id = $artistInfo.DiscogsId
            instrument_id = $artistInfo.InstrumentId
            type = $artist.type
            gender = $artist.gender
            country = $artist.country
            disambiguation = $artist.disambiguation
            begin_date = if ($artist.'life-span' -and $artist.'life-span'.begin) { $artist.'life-span'.begin } else { $null }
            end_date = if ($artist.'life-span' -and $artist.'life-span'.end) { $artist.'life-span'.end } else { $null }
            ended = if ($artist.'life-span') { $artist.'life-span'.ended } else { $false }
        }
        musicbrainz_url = "https://musicbrainz.org/artist/$currentMbid"
        wikidata_id = $wikidataId
        wikidata_url = if ($wikidataId) { "https://www.wikidata.org/wiki/$wikidataId" } else { $null }
        wikipedia = @{
            title = $artistWikipediaTitle
            url = $artistWikipediaUrl
        }
        albums = $albums
        relations = $artist.relations
        aliases = $artist.aliases
        last_updated = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssK")
    }

    $jsonLine = $output | ConvertTo-Json -Compress -Depth 10
    Add-Content -Path $OutputFile -Value $jsonLine
    Write-Host "  Successfully wrote artist data with $($albums.Count) albums to output file"

    # Small delay between artists
    Start-Sleep -Milliseconds 500
}

Write-Host "`n========================================="
Write-Host "Done! Output saved to: $OutputFile"
Write-Host "Total artists processed: $artistCounter"
Write-Host "Total albums found: $totalAlbumsFound"

# Show summary statistics
$outputContent = Get-Content $OutputFile | ConvertFrom-Json
$withWikidata = $outputContent | Where-Object { $_.wikidata_id } | Measure-Object | Select-Object -ExpandProperty Count
$withAlbums = $outputContent | Where-Object { $_.albums -and $_.albums.Count -gt 0 } | Measure-Object | Select-Object -ExpandProperty Count
$totalAlbumsVerified = ($outputContent | ForEach-Object { $_.albums.Count } | Measure-Object -Sum).Sum

Write-Host "Summary:"
Write-Host "  - Artists with Wikidata ID: $withWikidata"
Write-Host "  - Artists with albums found: $withAlbums"
Write-Host "  - Total albums in dataset: $totalAlbumsVerified"

Write-Host "`nImportant Notes:"
Write-Host "  - This script uses Wikidata SPARQL queries to find albums [citation:3]"
Write-Host "  - Album data comes from Wikipedia via Wikidata, not MusicBrainz"
Write-Host "  - Each album includes its own Wikipedia URL and optional scraped content"
Write-Host "  - Rate limiting is enforced to respect API terms [citation:6]"