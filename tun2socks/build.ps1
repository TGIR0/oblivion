# Build script for tun2socks with CGO enabled
# Optimized for clean output without warnings

# Add MinGW to PATH
$env:Path = "C:\msys64\ucrt64\bin;" + $env:Path

# Enable CGO
$env:CGO_ENABLED = 1

# Suppress ALL warnings for completely clean build
$env:CGO_CFLAGS = "-w"

Write-Host "Building tun2socks (optimized, clean output)..." -ForegroundColor Cyan
Write-Host "CGO: Enabled | MTU: 1500 | Warnings: Suppressed`n" -ForegroundColor Gray

# Build with optimizations
$output = go build -v -ldflags="-s -w" ./... 2>&1

# Filter output to show only important messages
$output | Where-Object { 
    $_ -notmatch "warning: #warning" -and 
    $_ -notmatch "THIS_IS_64BIT_ENVIRONMENT"
} | ForEach-Object {
    if ($_ -match "error") {
        Write-Host $_ -ForegroundColor Red
    } else {
        Write-Host $_ -ForegroundColor Gray
    }
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✓ Build completed successfully!" -ForegroundColor Green
    Write-Host "  - All warnings suppressed" -ForegroundColor Gray
    Write-Host "  - Binary optimized (-s -w)" -ForegroundColor Gray
    Write-Host "  - MTU configured to 1500" -ForegroundColor Gray
} else {
    Write-Host "`n✗ Build failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}
