# Unified Build Script - Support Service Configuration
# Usage: .\build-with-config.ps1 [-Profile dev|prod] [-ConfigFile config.env]

param(
    [string]$Profile = "prod",
    [string]$ConfigFile = ""
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "ERP Unified Build Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check Node.js and npm
Write-Host "Checking Node.js environment..." -ForegroundColor Yellow
try {
    $nodeVersion = node --version
    $npmVersion = npm --version
    Write-Host "[OK] Node.js: $nodeVersion" -ForegroundColor Green
    Write-Host "[OK] npm: $npmVersion" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Node.js not found, please install Node.js first" -ForegroundColor Red
    exit 1
}

# Check Maven
Write-Host "Checking Maven environment..." -ForegroundColor Yellow
try {
    $mvnVersion = mvn --version | Select-Object -First 1
    Write-Host "[OK] $mvnVersion" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Maven not found, please install Maven first" -ForegroundColor Red
    exit 1
}

# Load configuration file
$envVars = @{}
if ($ConfigFile -and (Test-Path $ConfigFile)) {
    Write-Host "Loading config file: $ConfigFile" -ForegroundColor Yellow
    Get-Content $ConfigFile | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()
            $envVars[$key] = $value
            Write-Host "  $key = $value" -ForegroundColor Gray
        }
    }
} else {
    Write-Host "Using default config or environment variables" -ForegroundColor Yellow
}

# Set environment variables
foreach ($key in $envVars.Keys) {
    [Environment]::SetEnvironmentVariable($key, $envVars[$key], "Process")
}

# Set default config (if not provided)
if (-not $env:DB_HOST) { $env:DB_HOST = "localhost" }
if (-not $env:DB_PORT) { $env:DB_PORT = "5432" }
if (-not $env:DB_NAME) { $env:DB_NAME = "erp_db" }
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = "postgres" }
if (-not $env:DB_PASSWORD) { Write-Host "Warning: DB_PASSWORD not set" -ForegroundColor Yellow }
if (-not $env:SERVER_PORT) { $env:SERVER_PORT = "8080" }

Write-Host ""
Write-Host "Current configuration:" -ForegroundColor Cyan
Write-Host "  Database: $env:DB_HOST`:$env:DB_PORT/$env:DB_NAME" -ForegroundColor Gray
Write-Host "  Username: $env:DB_USERNAME" -ForegroundColor Gray
Write-Host "  Server Port: $env:SERVER_PORT" -ForegroundColor Gray
Write-Host ""

# Build frontend
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Step 1/3: Building frontend..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Push-Location frontend
try {
    # Check and install dependencies
    if (-not (Test-Path "node_modules")) {
        Write-Host "Installing frontend dependencies..." -ForegroundColor Yellow
        npm install
    }
    
    # Build frontend
    Write-Host "Executing frontend build..." -ForegroundColor Yellow
    npm run build
    
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed"
    }
    Write-Host "[OK] Frontend build completed" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Frontend build failed: $_" -ForegroundColor Red
    Pop-Location
    exit 1
} finally {
    Pop-Location
}

Write-Host ""

# Build backend
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Step 2/3: Building backend..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Push-Location backend
try {
    # Prepare configuration file
    $configFile = "src/main/resources/application.yaml"
    $templateFile = "src/main/resources/application-template.yaml"
    
    if (Test-Path $templateFile) {
        Write-Host "Generating config file from template..." -ForegroundColor Yellow
        # Use UTF-8 encoding to read and write
        $content = Get-Content $templateFile -Raw -Encoding UTF8
        
        # Replace configuration variables
        $content = $content -replace '\$\{db\.host:localhost\}', $env:DB_HOST
        $content = $content -replace '\$\{db\.port:5432\}', $env:DB_PORT
        $content = $content -replace '\$\{db\.name:erp_db\}', $env:DB_NAME
        $content = $content -replace '\$\{db\.username:postgres\}', $env:DB_USERNAME
        $content = $content -replace '\$\{db\.password:\}', $env:DB_PASSWORD
        $content = $content -replace '\$\{server\.port:8080\}', $env:SERVER_PORT
        
        # Save configuration file with UTF-8 encoding
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText((Resolve-Path $configFile -ErrorAction SilentlyContinue).Path, $content, $utf8NoBom)
        Write-Host "[OK] Config file generated" -ForegroundColor Green
    } else {
        Write-Host "Warning: Template file not found, using existing application.yaml" -ForegroundColor Yellow
    }
    
    # Execute Maven package (will automatically execute frontend build)
    Write-Host "Executing Maven package..." -ForegroundColor Yellow
    mvn clean package -DskipTests
    
    if ($LASTEXITCODE -ne 0) {
        throw "Backend build failed"
    }
    Write-Host "[OK] Backend build completed" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Backend build failed: $_" -ForegroundColor Red
    Pop-Location
    exit 1
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Step 3/3: Build completed" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$jarFile = "backend/target/erp-0.0.1-SNAPSHOT.jar"
if (Test-Path $jarFile) {
    $jarSize = (Get-Item $jarFile).Length / 1MB
    Write-Host "[OK] Build successful!" -ForegroundColor Green
    Write-Host ""
    Write-Host "JAR file location: $jarFile" -ForegroundColor Cyan
    Write-Host "File size: $([math]::Round($jarSize, 2)) MB" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Run command:" -ForegroundColor Yellow
    Write-Host "  java -jar $jarFile" -ForegroundColor White
    Write-Host ""
    Write-Host "Or with config file:" -ForegroundColor Yellow
    Write-Host "  java -jar $jarFile --spring.config.location=classpath:/application.yaml" -ForegroundColor White
} else {
    Write-Host "[ERROR] JAR file not found" -ForegroundColor Red
    exit 1
}
