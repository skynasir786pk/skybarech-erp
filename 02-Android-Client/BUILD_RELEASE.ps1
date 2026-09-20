$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
foreach ($name in @('SKYBARECH_KEYSTORE_PATH','SKYBARECH_KEYSTORE_PASSWORD','SKYBARECH_KEY_ALIAS','SKYBARECH_KEY_PASSWORD')) {
    if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Set signing variable: $name" }
}
& .\gradlew.bat --no-daemon --console=plain lintRelease testDebugUnitTest assembleRelease
if ($LASTEXITCODE -ne 0) { throw 'Release checks/build failed. Do not distribute this build.' }
Write-Host 'Signed release output: app\build\outputs\apk\release\app-release.apk'
Write-Host 'Install and test on real devices before distribution. Preserve the signing key for every future update.'
