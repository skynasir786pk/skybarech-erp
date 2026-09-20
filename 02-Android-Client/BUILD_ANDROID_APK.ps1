$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ApiSetting = Get-Content (Join-Path $ProjectDir 'gradle.properties') | Where-Object { $_ -match '^SKYBARECH_API_BASE_URL=' } | Select-Object -Last 1
$ApiUrl = ($ApiSetting -replace '^SKYBARECH_API_BASE_URL=', '').Trim().TrimEnd('/')
if (-not $ApiUrl.StartsWith('https://')) { throw 'Set SKYBARECH_API_BASE_URL in gradle.properties to your HTTPS API address.' }
$LogDir = Join-Path $ProjectDir "BUILD_LOGS"
$ApkSource = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
$OutputDir = Join-Path $ProjectDir "APK_OUTPUT"
$LogFile = Join-Path $LogDir ("android-build-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".log")

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Start-Transcript -Path $LogFile -Force | Out-Null

function Stop-Build([string]$Message) {
    throw $Message
}

function Get-JavaVersionText([string]$JavaExe) {
    $StartInfo = New-Object System.Diagnostics.ProcessStartInfo
    $StartInfo.FileName = $JavaExe
    $StartInfo.Arguments = "-version"
    $StartInfo.UseShellExecute = $false
    $StartInfo.RedirectStandardOutput = $true
    $StartInfo.RedirectStandardError = $true
    $StartInfo.CreateNoWindow = $true
    $Process = New-Object System.Diagnostics.Process
    $Process.StartInfo = $StartInfo
    if (-not $Process.Start()) { Stop-Build "Java could not be started." }
    $Stdout = $Process.StandardOutput.ReadToEnd()
    $Stderr = $Process.StandardError.ReadToEnd()
    $Process.WaitForExit()
    if ($Process.ExitCode -ne 0) { Stop-Build "Java version command failed with exit code $($Process.ExitCode)." }
    return ($Stdout + [Environment]::NewLine + $Stderr).Trim()
}

function Find-JavaHome {
    $Candidates = @()
    # Prefer Android Studio's tested bundled JDK. A system JAVA_HOME can point to
    # another distribution that breaks Gradle's Windows loopback daemon channel.
    if ($env:ProgramFiles) { $Candidates += (Join-Path $env:ProgramFiles "Android\Android Studio\jbr") }
    if ($env:LOCALAPPDATA) { $Candidates += (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr") }
    if ($env:JAVA_HOME) { $Candidates += $env:JAVA_HOME }

    foreach ($Candidate in $Candidates) {
        if ($Candidate -and
            (Test-Path (Join-Path $Candidate "bin\java.exe")) -and
            (Test-Path (Join-Path $Candidate "bin\jlink.exe"))) {
            return (Resolve-Path $Candidate).Path
        }
    }

    $JavaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($JavaCommand) {
        $CommandJavaHome = Split-Path -Parent (Split-Path -Parent $JavaCommand.Source)
        if (Test-Path (Join-Path $CommandJavaHome "bin\jlink.exe")) {
            return $CommandJavaHome
        }
    }
    return $null
}

function Find-AndroidSdk {
    $Candidates = @()
    if ($env:ANDROID_HOME) { $Candidates += $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { $Candidates += $env:ANDROID_SDK_ROOT }
    if ($env:LOCALAPPDATA) { $Candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk") }
    if ($env:USERPROFILE) { $Candidates += (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk") }

    foreach ($Candidate in $Candidates) {
        if ($Candidate -and (Test-Path $Candidate)) {
            return (Resolve-Path $Candidate).Path
        }
    }
    return $null
}

try {
    Set-Location $ProjectDir
    $AppGradlePath = Join-Path $ProjectDir "app\build.gradle.kts"
    if (-not (Test-Path $AppGradlePath)) { Stop-Build "app\build.gradle.kts is missing." }
    $AppGradleText = Get-Content $AppGradlePath -Raw
    if ($AppGradleText -notmatch 'versionName\s*=\s*"([^"]+)"') {
        Stop-Build "Android versionName could not be read from app\build.gradle.kts."
    }
    $AppVersion = $Matches[1]
    $SafeVersion = $AppVersion -replace '[^0-9A-Za-z._-]', '-'
    $OutputApk = Join-Path $OutputDir "SkyBarech_ERP_v$SafeVersion`_Debug.apk"

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "  SkyBarech ERP v$AppVersion - Android APK Builder"
    Write-Host "============================================================"
    Write-Host "API: $ApiUrl"
    Write-Host ""

    if (-not (Test-Path (Join-Path $ProjectDir "gradlew.bat"))) {
        Stop-Build "gradlew.bat is missing from the Android project."
    }
    if (-not (Test-Path (Join-Path $ProjectDir "gradle\wrapper\gradle-wrapper.jar"))) {
        Stop-Build "gradle-wrapper.jar is missing from the Android project."
    }

    Write-Host "[1/5] Detecting Android Studio JDK..."
    $JavaHome = Find-JavaHome
    if (-not $JavaHome) {
        Stop-Build "A full JDK was not found. Install Android Studio with its bundled JDK 17/21, then run this builder again."
    }
    $env:JAVA_HOME = $JavaHome
    $env:Path = (Join-Path $JavaHome "bin") + ";" + $env:Path
    $JavaExe = Join-Path $JavaHome "bin\java.exe"
    $JavaVersionText = Get-JavaVersionText $JavaExe
    Write-Host $JavaVersionText.Trim()
    if ($JavaVersionText -notmatch 'version\s+"(\d+)') {
        Stop-Build "The Java version could not be detected."
    }
    $JavaMajor = [int]$Matches[1]
    if ($JavaMajor -lt 17 -or $JavaMajor -gt 21) {
        Stop-Build "Java $JavaMajor is incompatible. Use Android Studio's bundled JDK 17 or JDK 21."
    }

    Write-Host ""
    Write-Host "[2/5] Detecting and preparing Android SDK 35..."
    $SdkRoot = Find-AndroidSdk
    if (-not $SdkRoot) {
        Stop-Build "Android SDK was not found. Open Android Studio once and install Android SDK, Platform 35 and Build Tools 35."
    }
    $env:ANDROID_HOME = $SdkRoot
    $env:ANDROID_SDK_ROOT = $SdkRoot
    Write-Host "Android SDK: $SdkRoot"

    $PlatformJar = Join-Path $SdkRoot "platforms\android-35\android.jar"
    $BuildToolsDir = Join-Path $SdkRoot "build-tools\35.0.0"
    if (-not (Test-Path $PlatformJar) -or -not (Test-Path $BuildToolsDir)) {
        $SdkManager = Get-ChildItem -Path (Join-Path $SdkRoot "cmdline-tools") -Filter "sdkmanager.bat" -Recurse -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if (-not $SdkManager) {
            Stop-Build "SDK Platform 35 is missing and sdkmanager.bat is unavailable. In Android Studio SDK Manager, install Android SDK Platform 35, Build-Tools 35.0.0 and Android SDK Command-line Tools."
        }

        Write-Host "Installing missing Android SDK packages. Internet is required..."
        1..80 | ForEach-Object { "y" } | & $SdkManager.FullName "--sdk_root=$SdkRoot" --licenses | Out-Host
        & $SdkManager.FullName "--sdk_root=$SdkRoot" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
        if ($LASTEXITCODE -ne 0) { Stop-Build "Android SDK package installation failed." }
    }
    if (-not (Test-Path $PlatformJar)) { Stop-Build "Android SDK Platform 35 is still missing." }
    if (-not (Test-Path $BuildToolsDir)) { Stop-Build "Android Build Tools 35.0.0 are still missing." }

    $EscapedSdkRoot = $SdkRoot.Replace('\', '\\').Replace(':', '\:')
    [System.IO.File]::WriteAllText((Join-Path $ProjectDir "local.properties"), "sdk.dir=$EscapedSdkRoot`r`n", [System.Text.Encoding]::ASCII)

    Write-Host ""
    Write-Host "[3/5] Downloading Gradle dependencies and compiling the app..."
    if (Test-Path $ApkSource) { Remove-Item -LiteralPath $ApkSource -Force }
    & (Join-Path $ProjectDir "gradlew.bat") --no-daemon --stacktrace --console=plain clean lintDebug testDebugUnitTest assembleDebug "-PSKYBARECH_API_BASE_URL=$ApiUrl"
    if ($LASTEXITCODE -ne 0) {
        Stop-Build "Gradle could not build the Android APK. If the output says 'Unable to establish loopback connection', Windows is blocking its local 127.0.0.1 connection: restart Windows first; if it remains, open PowerShell as Administrator, run 'netsh winsock reset', restart Windows, then run this builder again."
    }

    Write-Host ""
    Write-Host "[4/5] Verifying the generated APK..."
    if (-not (Test-Path $ApkSource)) {
        Stop-Build "Gradle finished but app-debug.apk was not found."
    }
    $ApkSize = (Get-Item $ApkSource).Length
    if ($ApkSize -lt 5MB) {
        Stop-Build "The generated APK is unexpectedly small and may be incomplete."
    }

    $ApkSigner = Join-Path $BuildToolsDir "apksigner.bat"
    if (Test-Path $ApkSigner) {
        & $ApkSigner verify $ApkSource
        if ($LASTEXITCODE -ne 0) { Stop-Build "APK signature verification failed." }
    }

    Write-Host ""
    Write-Host "[5/5] Copying the installable APK..."
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
    Copy-Item -Force $ApkSource $OutputApk

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "  ANDROID APK BUILD SUCCESSFUL"
    Write-Host "============================================================"
    Write-Host "Output: $OutputApk"
    Write-Host "Size: $([math]::Round($ApkSize / 1MB, 1)) MB"
    Write-Host "Log: $LogFile"

    Start-Process explorer.exe -ArgumentList "/select,`"$OutputApk`""
    Stop-Transcript | Out-Null
    exit 0
}
catch {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Red
    Write-Host "  ANDROID APK BUILD FAILED" -ForegroundColor Red
    Write-Host "============================================================" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host "Log: $LogFile"
    try { Stop-Transcript | Out-Null } catch {}
    exit 1
}
