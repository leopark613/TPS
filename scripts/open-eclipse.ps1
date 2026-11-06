#requires -Version 5.1
<#
Opens an existing Eclipse installation with process-only TPS settings.
Does not install plugins, edit eclipse.ini, import projects, or persist passwords.
Close that workspace's existing Eclipse instance before using this launcher.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$EclipseExe,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Workspace,
    [string]$DbHost = $env:TPS_DB_HOST,
    [ValidateRange(1,65535)][int]$DbPort = 1433,
    [string]$DbUser = $env:TPS_DB_USER,
    [Security.SecureString]$DbPassword,
    [Security.SecureString]$DeveloperPassword,
    [string]$SiteId = 'TPS_DEV',
    [ValidateSet('RPS','LPS','RPS,LPS')][string]$EnabledModules = 'RPS,LPS',
    [ValidateRange(1,65535)][int]$Port = 18080,
    [switch]$AllowLocalCertificateException,
    [switch]$WindowsSocketFallback,
    [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $Workspace) { $Workspace = $repo }
$Workspace = [IO.Path]::GetFullPath($Workspace)
if (-not $JavaHome) { throw 'Set -JavaHome to a JDK 21 directory, or set JAVA_HOME for this PowerShell process.' }
$JavaHome = [IO.Path]::GetFullPath($JavaHome)
$eclipse = (Resolve-Path -LiteralPath $EclipseExe -ErrorAction Stop).Path
$java = Join-Path $JavaHome 'bin\java.exe'
$javaw = Join-Path $JavaHome 'bin\javaw.exe'
$javac = Join-Path $JavaHome 'bin\javac.exe'
foreach ($file in @($java,$javaw,$javac)) { if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw 'A complete JDK 21 installation is required.' } }
$versionOutput = (& $javac --version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $versionOutput -notmatch '(?m)^javac 21(?:\.|\s|$)') { throw 'This launch configuration requires JDK 21.' }
$ini = Join-Path (Split-Path -Parent $eclipse) (([IO.Path]::GetFileNameWithoutExtension($eclipse))+'.ini')
if (-not (Test-Path -LiteralPath $ini -PathType Leaf)) { throw 'The Eclipse ini file was not found next to the executable.' }
$lines = [IO.File]::ReadAllLines($ini)
$agentLine = $lines | Where-Object { $_ -match '^-javaagent:.*lombok.*\.jar' } | Select-Object -Last 1
$lombokReady = $false
if ($agentLine) {
    $lombok = ($agentLine -replace '^-javaagent:','').Trim('"')
    if (-not [IO.Path]::IsPathRooted($lombok)) { $lombok = Join-Path (Split-Path -Parent $eclipse) $lombok }
    if (Test-Path -LiteralPath $lombok -PathType Leaf) {
        $lombokVersionOutput = (& $java -jar $lombok version 2>&1 | Out-String)
        $lombokReady = $LASTEXITCODE -eq 0 -and $lombokVersionOutput -match '(?m)^v1\.18\.48(?:\s|$)'
    }
}
if ($ValidateOnly) {
    [pscustomobject]@{ Repository=$repo; Workspace=$Workspace; Jdk21=$true; Lombok11848=$lombokReady; Launched=$false }
    return
}
if (-not $lombokReady) { throw 'Install Lombok 1.18.48 into this Eclipse, fully exit Eclipse, and retry. See ide/ECLIPSE-SETUP.md. This launcher does not alter the installation.' }
if (-not (Test-Path -LiteralPath (Join-Path $repo 'server\pom.xml') -PathType Leaf)) { throw 'Place this script in the TPS repository scripts directory.' }
if (-not $DbHost) { $DbHost = Read-Host 'Local TPS SQL Server host' }
if (-not $DbUser) { $DbUser = Read-Host 'Local TPS SQL login' }
if ([string]::IsNullOrWhiteSpace($DbHost) -or $DbHost -notmatch '^[A-Za-z0-9.-]+$') { throw 'A valid local SQL Server host is required.' }
if ([string]::IsNullOrWhiteSpace($DbUser)) { throw 'A SQL login is required.' }
if ([string]::IsNullOrWhiteSpace($SiteId) -or [Text.Encoding]::UTF8.GetByteCount($SiteId) -gt 40) { throw 'SiteId must contain 1..40 UTF-8 bytes.' }
if (-not $DbPassword -and -not $env:TPS_DB_PASSWORD) { $DbPassword = Read-Host 'Local TPS SQL password (not saved)' -AsSecureString }
if (-not $DeveloperPassword -and -not $env:TPS_DEV_PASSWORD) { $DeveloperPassword = Read-Host 'TPS browser password (at least 12 characters; not saved)' -AsSecureString }

function Read-PlainSecret([Security.SecureString]$Secure,[string]$Inherited) {
    if (-not $Secure) { return $Inherited }
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
# Windows paths cannot contain a quote. Quote one native argument, including a trailing slash safely.
function Quote-NativePath([string]$Value) {
    if ($Value.Contains('"')) { throw 'Unexpected quote in a native path argument.' }
    return '"' + ($Value -replace '(\\+)$','$1$1') + '"'
}

$sqlSecret = $null
$uiSecret = $null
$fallback = $null
$process = $null
try {
    $sqlSecret = Read-PlainSecret $DbPassword $env:TPS_DB_PASSWORD
    $uiSecret = Read-PlainSecret $DeveloperPassword $env:TPS_DEV_PASSWORD
    if ([string]::IsNullOrEmpty($sqlSecret)) { throw 'The SQL password must not be empty.' }
    if (-not $uiSecret -or $uiSecret.Length -lt 12) { throw 'The TPS browser password must be at least 12 characters.' }
    [IO.Directory]::CreateDirectory($Workspace) | Out-Null
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $eclipse
    $start.WorkingDirectory = Split-Path -Parent $eclipse
    $start.UseShellExecute = $false
    $start.Arguments = '-data '+(Quote-NativePath $Workspace)+' -vm '+(Quote-NativePath $javaw)+' -showlocation'
    $childEnvironment = @{
        JAVA_HOME=$JavaHome; SPRING_PROFILES_ACTIVE='local';
        TPS_DB_HOST=$DbHost; TPS_DB_PORT=[string]$DbPort; TPS_DB_NAME='TPS';
        TPS_DB_USER=$DbUser; TPS_DB_PASSWORD=$sqlSecret;
        TPS_DB_TRUST_SERVER_CERTIFICATE=([string]$AllowLocalCertificateException.IsPresent).ToLowerInvariant();
        TPS_DEV_PASSWORD=$uiSecret; TPS_SITE_ID=$SiteId; TPS_ENABLED_MODULES=$EnabledModules; TPS_PORT=[string]$Port
    }
    foreach ($key in $childEnvironment.Keys) { $start.EnvironmentVariables[$key] = [string]$childEnvironment[$key] }
    if ($WindowsSocketFallback) {
        # This machine's Windows AF_UNIX connection fails; a path exceeding the socket-address
        # limit triggers the tested JDK PipeImpl TCP-loopback fallback. No OS setting is altered.
        $ownedBase = [IO.Path]::GetFullPath((Join-Path $repo '.local\eclipse-nio'))
        $fallback = Join-Path $ownedBase (('tps-selector-tcp-fallback-'*4)+[Guid]::NewGuid().ToString('N'))
        [IO.Directory]::CreateDirectory($fallback) | Out-Null
        $option = '-Djdk.net.unixdomain.tmpdir='+(Quote-NativePath $fallback)
        $start.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = (($start.EnvironmentVariables['JAVA_TOOL_OPTIONS']+' '+$option).Trim())
    }
    Write-Host 'Opening Eclipse. Credentials are inherited by this Eclipse process and its Run/Debug children only.'
    Write-Host 'Keep this launcher open until Eclipse exits. Do not save passwords into Eclipse launch configurations.'
    $process = [Diagnostics.Process]::Start($start)
    # Remove this launcher's extra references. The child already owns its private environment copy.
    $start.EnvironmentVariables.Remove('TPS_DB_PASSWORD')
    $start.EnvironmentVariables.Remove('TPS_DEV_PASSWORD')
    $childEnvironment['TPS_DB_PASSWORD'] = $null
    $childEnvironment['TPS_DEV_PASSWORD'] = $null
    $sqlSecret = $null
    $uiSecret = $null
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'Eclipse exited unsuccessfully. Inspect its normal workspace log without sharing credentials.' }
}
finally {
    $sqlSecret = $null
    $uiSecret = $null
    if ($process) { $process.Dispose() }
    if ($fallback) {
        $full = [IO.Path]::GetFullPath($fallback)
        $base = [IO.Path]::GetFullPath((Join-Path $repo '.local\eclipse-nio'))+[IO.Path]::DirectorySeparatorChar
        if (-not $full.StartsWith($base,[StringComparison]::OrdinalIgnoreCase)) { throw 'Refusing cleanup outside the owned Eclipse NIO directory.' }
        $item = Get-Item -LiteralPath $full -Force -ErrorAction SilentlyContinue
        if ($item -and -not ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            if (@(Get-ChildItem -LiteralPath $full -Force).Count -eq 0) { Remove-Item -LiteralPath $full -Force }
            else { Write-Warning 'The owned Eclipse NIO directory is not empty; it was preserved for inspection.' }
        }
    }
}
