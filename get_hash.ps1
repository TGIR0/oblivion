$ErrorActionPreference = "Stop"
try {
    $response = Invoke-WebRequest -Uri "https://api.github.com/repos/voidr3aper-anon/Vwarp/commits/master" -UseBasicParsing
    $json = $response.Content | ConvertFrom-Json
    $json.sha | Out-File "hash.txt" -Encoding ASCII
} catch {
    $_ | Out-File "hash_error.txt"
}
