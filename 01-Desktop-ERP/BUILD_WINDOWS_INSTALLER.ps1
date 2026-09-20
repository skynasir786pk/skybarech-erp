$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogDir = Join-Path $ProjectDir "BUILD_LOGS"
$DistDir = Join-Path $ProjectDir "dist"
$LogFile = Join-Path $LogDir ("desktop-build-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".log")

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Start-Transcript -Path $LogFile -Force | Out-Null

function Stop-Build([string]$Message) {
    throw $Message
}

try {
    Set-Location $ProjectDir
    $PackageJsonPath = Join-Path $ProjectDir "package.json"
    if (-not (Test-Path $PackageJsonPath)) {
        Stop-Build "package.json is missing from the Desktop project."
    }
    try {
        $PackageJson = Get-Content $PackageJsonPath -Raw | ConvertFrom-Json
        $AppVersion = ([string]$PackageJson.version).Trim()
    }
    catch {
        Stop-Build "package.json could not be read: $($_.Exception.Message)"
    }
    if ($AppVersion -notmatch '^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$') {
        Stop-Build "package.json contains an invalid version: $AppVersion"
    }
    $ExpectedInstallerName = "SkyBarech-ERP-Setup-$AppVersion.exe"
    $ExpectedInstaller = Join-Path $DistDir $ExpectedInstallerName

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "  SkyBarech ERP v$AppVersion - Windows Installer Builder"
    Write-Host "============================================================"
    Write-Host ""

    $NodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
    $NpmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $NodeCommand -or -not $NpmCommand) {
        Stop-Build "Node.js 22 LTS or newer is required. Install 64-bit Node.js, restart Windows, then run this builder again."
    }

    $NodeVersionText = (& node.exe -p "process.versions.node").Trim()
    $NodeVersion = [version]$NodeVersionText
    Write-Host "Node.js: $NodeVersionText"
    if ($NodeVersion -lt [version]"22.12.0") {
        Stop-Build "Node.js $NodeVersionText is too old. Install Node.js 22.12 or newer."
    }
    if (-not [Environment]::Is64BitOperatingSystem) {
        Stop-Build "A 64-bit Windows installation is required."
    }
    if (-not (Test-Path (Join-Path $ProjectDir "build\icon.ico"))) {
        Stop-Build "build\icon.ico is missing from the Desktop project."
    }

    Write-Host ""
    Write-Host "[1/4] Installing locked packages without native source compilation..."
    & npm.cmd ci --ignore-scripts --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { Stop-Build "npm package installation failed." }

    $ElectronInstaller = Join-Path $ProjectDir "node_modules\electron\install.js"
    if (-not (Test-Path $ElectronInstaller)) {
        Stop-Build "Electron installer script is missing after npm install."
    }

    Write-Host ""
    Write-Host "[2/4] Downloading/verifying the Electron Windows runtime..."
    & node.exe $ElectronInstaller
    if ($LASTEXITCODE -ne 0) { Stop-Build "Electron runtime download failed. Check the internet connection and antivirus." }

    $ElectronPathFile = Join-Path $ProjectDir "node_modules\electron\path.txt"
    if (-not (Test-Path $ElectronPathFile)) {
        Stop-Build "Electron did not finish installing. path.txt is missing."
    }
    $ElectronRelativePath = (Get-Content $ElectronPathFile -Raw).Trim()
    $ElectronExe = Join-Path (Join-Path $ProjectDir "node_modules\electron\dist") $ElectronRelativePath
    if (-not (Test-Path $ElectronExe)) {
        Stop-Build "Electron executable is incomplete. Delete node_modules and run the builder again."
    }

    $SqlitePrebuild = Join-Path $ProjectDir "node_modules\better-sqlite3\prebuilds\win32-x64.node"
    if (-not (Test-Path $SqlitePrebuild)) {
        Stop-Build "The required 64-bit SQLite prebuilt module is missing."
    }

    Write-Host ""
    Write-Host "[3/4] Creating the professional NSIS setup..."
    if (Test-Path $ExpectedInstaller) {
        Remove-Item -LiteralPath $ExpectedInstaller -Force
    }
    $env:CSC_IDENTITY_AUTO_DISCOVERY = "false"
    $env:npm_config_build_from_source = "false"
    & (Join-Path $ProjectDir "node_modules\.bin\electron-builder.cmd") --win nsis --x64
    if ($LASTEXITCODE -ne 0) { Stop-Build "electron-builder could not create the Windows installer." }

    Write-Host ""
    Write-Host "[4/4] Verifying the generated setup..."
    if (-not (Test-Path $ExpectedInstaller)) {
        $GeneratedInstallers = @(Get-ChildItem -LiteralPath $DistDir -Filter "SkyBarech-ERP-Setup-*.exe" -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTimeUtc -Descending)
        $FoundNames = if ($GeneratedInstallers.Count) { ($GeneratedInstallers.Name -join ", ") } else { "none" }
        Stop-Build "Build finished but $ExpectedInstallerName was not found. Generated installer files: $FoundNames. Check package.json build.artifactName and the build log."
    }
    $InstallerSize = (Get-Item $ExpectedInstaller).Length
    if ($InstallerSize -lt 20MB) {
        Stop-Build "The generated setup is unexpectedly small and may be incomplete."
    }

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "  WINDOWS SETUP BUILD SUCCESSFUL"
    Write-Host "============================================================"
    Write-Host "Output: $ExpectedInstaller"
    Write-Host "Size: $([math]::Round($InstallerSize / 1MB, 1)) MB"
    Write-Host "Log: $LogFile"

    Start-Process explorer.exe -ArgumentList "/select,`"$ExpectedInstaller`""
    Stop-Transcript | Out-Null
    exit 0
}
catch {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Red
    Write-Host "  WINDOWS SETUP BUILD FAILED" -ForegroundColor Red
    Write-Host "============================================================" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host "Log: $LogFile"
    try { Stop-Transcript | Out-Null } catch {}
    exit 1
}
