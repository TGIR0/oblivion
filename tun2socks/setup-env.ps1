# Setup environment variables for CGO development
# Run this before developing: . .\setup-env.ps1

Write-Host "Setting up CGO environment..." -ForegroundColor Cyan

# Add MinGW to PATH for this session
$env:Path = "C:\msys64\ucrt64\bin;" + $env:Path

# Enable CGO
$env:CGO_ENABLED = 1

Write-Host "✓ Added C:\msys64\ucrt64\bin to PATH" -ForegroundColor Green
Write-Host "✓ Enabled CGO (CGO_ENABLED=1)" -ForegroundColor Green
Write-Host "`nEnvironment ready! You can now run go commands." -ForegroundColor Green
Write-Host "Example: go build -v ./..." -ForegroundColor Yellow
