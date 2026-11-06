param([ValidateSet('Maven','Gradle')][string]$BuildTool='Maven', [switch]$WindowsSocketFallback)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$priorJavaOptions=$env:JAVA_TOOL_OPTIONS
if($WindowsSocketFallback){
    $socketDir=Join-Path $repo ('.local\socket-fallback-'+('x'*80))
    New-Item -ItemType Directory -Force -Path $socketDir | Out-Null
    $env:JAVA_TOOL_OPTIONS=($env:JAVA_TOOL_OPTIONS+' "-Djdk.net.unixdomain.tmpdir='+$socketDir+'"').Trim()
}
Push-Location $repo
try {
    Push-Location (Join-Path $repo 'frontend')
    try {
        & npm.cmd ci; if($LASTEXITCODE -ne 0){throw 'npm ci failed'}
        & npm.cmd run test; if($LASTEXITCODE -ne 0){throw 'UI tests failed'}
        & npm.cmd run build; if($LASTEXITCODE -ne 0){throw 'UI build failed'}
    } finally { Pop-Location }
    if($BuildTool -eq 'Maven') {
        & .\mvnw.cmd -B clean verify; if($LASTEXITCODE -ne 0){throw 'Maven build failed'}
        $jar=Join-Path $repo 'server\target\tps-server.jar'
    } else {
        & .\gradlew.bat --no-daemon clean build; if($LASTEXITCODE -ne 0){throw 'Gradle build failed'}
        $jar=Join-Path $repo 'server\build\libs\tps-server.jar'
    }
    $out=Join-Path $repo ('dist\'+$BuildTool.ToLowerInvariant())
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    Copy-Item -LiteralPath $jar -Destination (Join-Path $out 'tps-server.jar')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'run-local.ps1') -Destination $out
    (Get-FileHash -LiteralPath (Join-Path $out 'tps-server.jar') -Algorithm SHA256).Hash | Set-Content -LiteralPath (Join-Path $out 'tps-server.jar.sha256')
    Write-Output "Package: $out"
} finally { Pop-Location; $env:JAVA_TOOL_OPTIONS=$priorJavaOptions }
