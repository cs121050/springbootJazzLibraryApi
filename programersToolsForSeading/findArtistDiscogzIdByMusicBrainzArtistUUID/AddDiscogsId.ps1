$inputFile = 'artists.sql'
$outputFile = 'artists_with_discogs.sql'

# Force TLS 1.2
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$lines = Get-Content $inputFile
$newLines = @()

foreach ($line in $lines) {
    Write-Host "Processing: $line"
    
    # Extract MBID (36-character UUID inside single quotes after VALUES)
    if ($line -match 'VALUES\s*\([^)]*''([0-9a-f-]{36})''') {
        $mbid = $matches[1]
        Write-Host "  MBID found: '$mbid'"
        
        # Build URL safely with concatenation
        $url = 'https://musicbrainz.org/ws/2/artist/' + $mbid + '?inc=url-rels&fmt=json'
        Write-Host "  URL: $url"
        
        $discogsId = $null
        $maxRetries = 10
        $retryCount = 0
        $success = $false

        while (-not $success -and $retryCount -lt $maxRetries) {
            try {
                Write-Host "    Attempt $($retryCount+1): Calling API..."
                $resp = Invoke-RestMethod -Uri $url -UserAgent "MyJazzApp/1.0 ( nicko.sarr@gmail.com )"
                Write-Host "    API call succeeded"
                if ($resp.relations) {
                    foreach ($rel in $resp.relations) {
                        if ($rel.type -eq 'discogs' -and $rel.url.resource -match 'discogs\.com/artist/(\d+)') {
                            $discogsId = $matches[1]
                            Write-Host "    Discogs ID found: $discogsId"
                            break
                        }
                    }
                }
                $success = $true
            }
            catch {
                $retryCount++
                $errorMessage = $_.Exception.Message
                Write-Host "    API call failed: $errorMessage"
                if ($retryCount -lt $maxRetries) {
                    Write-Host "    Retrying in 2 seconds... (attempt $retryCount/$maxRetries)"
                    Start-Sleep -Seconds 2
                } else {
                    Write-Host "    API call failed after $maxRetries attempts. Giving up."
                }
            }
        }

        # Insert [discogs_id] column after [musicbrainz_uuid]
        $newLine = $line -replace '\[musicbrainz_uuid\]', '[musicbrainz_uuid], [discogs_id]'
        
        # Insert Discogs ID (quoted) or NULL (unquoted) after the MBID value
        if ($success -and $discogsId) {
            # Wrap Discogs ID in single quotes
            $newLine = $newLine -replace "('$mbid')(\s*,\s*\d+\s*\))", "`$1, '$discogsId'`$2"
        } else {
            # Insert NULL (no quotes)
            $newLine = $newLine -replace "('$mbid')(\s*,\s*\d+\s*\))", "`$1, NULL`$2"
        }

        $newLines += $newLine
        Start-Sleep -Seconds 1
    } else {
        Write-Host "  No MBID found – leaving line unchanged"
        $newLines += $line
    }
}

$newLines | Set-Content $outputFile
Write-Host "Done. Output written to $outputFile"