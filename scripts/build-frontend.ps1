#requires -Version 5.1
<# Build React and copy its output through Maven's declared resource mapping.
   Does not start/stop Eclipse, alter system settings, write DB rows or package a JAR. #>
[CmdletBinding()]
param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$priorJava=$env:JAVA_HOME; $priorPath=$env:Path
$priorLocation=Get-Location
try {
    if(-not $JavaHome) {
        $javac=Get-Command javac.exe -ErrorAction SilentlyContinue
        if($javac) { $candidate=Split-Path (Split-Path $javac.Source -Parent) -Parent; if(Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe')) {$JavaHome=$candidate} }
    }
    if(-not $JavaHome) {
        # Portable discovery of a common JDK installation, without depending on an IDE.
        $jdkRoot=Join-Path $env:ProgramFiles 'Eclipse Adoptium'
        if(Test-Path -LiteralPath $jdkRoot) {
            $candidate=Get-ChildItem -LiteralPath $jdkRoot -Directory | Where-Object { $_.Name -like 'jdk-21*' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe')) } | Sort-Object Name -Descending | Select-Object -First 1
            if($candidate) {$JavaHome=$candidate.FullName}
        }
    }
    if(-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {throw 'Install JDK 21 and set JAVA_HOME, or pass -JavaHome.'}
    $javaVersion=& (Join-Path $JavaHome 'bin\javac.exe') -version 2>&1
    if($LASTEXITCODE -ne 0 -or "$javaVersion" -notmatch '^javac 21(?:\.|\s|$)') {throw 'TPS requires a JDK 21. Set JAVA_HOME or pass -JavaHome.'}
    $env:JAVA_HOME=$JavaHome
    $env:Path=(Join-Path $JavaHome 'bin')+';'+$env:Path
    if(-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        $nodeDir=Join-Path $env:ProgramFiles 'nodejs'
        if(Test-Path -LiteralPath (Join-Path $nodeDir 'npm.cmd')) {$env:Path=$nodeDir+';'+$env:Path}
    }
    if(-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {throw 'Install Node.js 24 (including npm) before building React.'}
    Set-Location -LiteralPath (Join-Path $repo 'frontend')
    & npm.cmd ci
    if($LASTEXITCODE -ne 0) {throw 'npm ci failed; the server resources were not updated.'}
    & npm.cmd run build
    if($LASTEXITCODE -ne 0) {throw 'React build failed; the server resources were not updated.'}
    Set-Location -LiteralPath $repo
    & (Join-Path $repo 'mvnw.cmd') -B -f (Join-Path $repo 'server\pom.xml') process-resources
    if($LASTEXITCODE -ne 0) {throw 'Maven resource copy failed; do not treat the server UI as updated.'}
    $dist=Join-Path $repo 'frontend\dist'; $static=Join-Path $repo 'server\target\classes\static'
    $count=0
    Get-ChildItem -LiteralPath $dist -Recurse -File | ForEach-Object {
        $relative=$_.FullName.Substring($dist.Length).TrimStart('\','/')
        $target=Join-Path $static $relative
        if(-not (Test-Path -LiteralPath $target) -or (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash) {throw ('Frontend resource copy mismatch: '+$relative)}
        $count++
    }
    if($count -eq 0) {throw 'No frontend build files were produced.'}
    Write-Output ('React build: '+$dist)
    Write-Output ('Eclipse server resources: '+$static)
    Write-Output ('Verified copied files: '+$count)
    Write-Output 'In Eclipse: Refresh (F5), run/debug TpsApplication, then reload the browser.'
    Write-Output 'This updates source-run resources. To update a deployment JAR, run scripts\build.ps1.'
} finally {
    Set-Location -LiteralPath $priorLocation.Path
    $env:JAVA_HOME=$priorJava; $env:Path=$priorPath
}
