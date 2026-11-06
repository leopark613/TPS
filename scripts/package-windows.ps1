param([ValidateSet('Maven','Gradle')][string]$BuildTool='Maven', [string]$Name='TPS-0.1.0-preview-windows-x64')
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
if($Name -notmatch '^[A-Za-z0-9._-]+$'){throw 'Use a plain package name.'}
if(-not $env:JAVA_HOME){throw 'JAVA_HOME must point to a Java 21 JDK with jlink.'}
$jar=if($BuildTool -eq 'Maven'){Join-Path $repo 'server\target\tps-server.jar'}else{Join-Path $repo 'server\build\libs\tps-server.jar'}
if(-not (Test-Path -LiteralPath $jar)){throw 'Build and verify TPS first.'}
$out=Join-Path $repo ('dist\'+$Name)
if(Test-Path -LiteralPath $out){throw 'Package directory exists; preserve it or use a new package name.'}
New-Item -ItemType Directory -Path $out -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $out 'tps-server.jar')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'run-local.ps1') -Destination $out
& (Join-Path $env:JAVA_HOME 'bin\jlink.exe') --add-modules java.se,jdk.crypto.ec,jdk.unsupported,jdk.localedata --include-locales ko,en --strip-debug --no-header-files --no-man-pages --compress=2 --output (Join-Path $out 'runtime')
if($LASTEXITCODE -ne 0){throw 'Java runtime packaging failed'}
$guide=@'
TPS 0.1.0 preview — Windows x64 local development package

PowerShell: .\run-local.ps1 -DbHost <local-TPS-host>
Use -LocalCertificateException only for the authorized local TPS development endpoint.
Supply DB credentials and a 12+ character browser password at masked prompts.
Open http://127.0.0.1:18760; use that browser password.
Java is included. Eclipse, Maven, Gradle and Node.js are not required to run.
SQL Server TPS/dbo must already contain the reviewed A7 schema. No automatic DDL.
This preview supports template save/reopen only; production login, printing,
Excel import and final installation/update workflows are not yet delivered.
The default TLS setting verifies the server certificate.
'@
[IO.File]::WriteAllText((Join-Path $out 'README.txt'),$guide,[Text.UTF8Encoding]::new($false))
Get-ChildItem -LiteralPath $out -Recurse -File | ForEach-Object {
    '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(),[IO.Path]::GetRelativePath($out,$_.FullName)
} | Set-Content -LiteralPath (Join-Path $out 'SHA256SUMS.txt') -Encoding utf8
Compress-Archive -LiteralPath $out -DestinationPath ($out+'.zip')
Write-Output ($out+'.zip')
