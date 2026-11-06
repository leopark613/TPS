param([string]$Jar=(Join-Path $PSScriptRoot 'tps-server.jar'), [int]$Port=18760, [string]$DbHost=$env:TPS_DB_HOST, [int]$DbPort=1433, [switch]$LocalCertificateException, [switch]$WindowsSocketFallback)
$ErrorActionPreference='Stop'
if(-not (Test-Path -LiteralPath $Jar)){throw 'Supply -Jar with the built TPS JAR path.'}
$keys=@('TPS_DB_HOST','TPS_DB_PORT','TPS_DB_NAME','TPS_DB_USER','TPS_DB_PASSWORD','TPS_DB_TRUST_SERVER_CERTIFICATE','TPS_DEV_PASSWORD','TPS_SITE_ID','SPRING_PROFILES_ACTIVE','SERVER_PORT','JAVA_TOOL_OPTIONS')
$prior=@{}; foreach($key in $keys){$prior[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
function Read-PlainSecret([string]$Prompt){
    $secret=Read-Host $Prompt -AsSecureString
    $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
try {
    if($WindowsSocketFallback){
        # Process-only workaround for this Windows host's AF_UNIX selector error.
        # An over-length AF_UNIX path makes the JDK use its normal TCP loopback fallback.
        $socketDir=Join-Path $PSScriptRoot ('.local\socket-fallback-'+('x'*80))
        New-Item -ItemType Directory -Force -Path $socketDir | Out-Null
        $env:JAVA_TOOL_OPTIONS=($env:JAVA_TOOL_OPTIONS+' "-Djdk.net.unixdomain.tmpdir='+$socketDir+'"').Trim()
    }
    if(-not $DbHost){$DbHost=Read-Host 'Local TPS DB host'}
    $env:TPS_DB_HOST=$DbHost; $env:TPS_DB_PORT="$DbPort"; $env:TPS_DB_NAME='TPS'
    if(-not $env:TPS_DB_USER){$env:TPS_DB_USER=Read-Host 'Local TPS DB user'}
    if(-not $env:TPS_DB_PASSWORD){$env:TPS_DB_PASSWORD=Read-PlainSecret 'Local TPS DB password'}
    if(-not $env:TPS_DEV_PASSWORD){$env:TPS_DEV_PASSWORD=Read-PlainSecret 'TPS browser login password (tps-developer, 12+ characters)'}
    if(-not $env:TPS_SITE_ID){$env:TPS_SITE_ID='TPS_DEV'}
    $env:TPS_DB_TRUST_SERVER_CERTIFICATE=([bool]$LocalCertificateException).ToString().ToLowerInvariant()
    $env:SPRING_PROFILES_ACTIVE='local'; $env:SERVER_PORT="$Port"
    $java='java'; if($env:JAVA_HOME){$java=Join-Path $env:JAVA_HOME 'bin\java.exe'}
    if(Test-Path -LiteralPath (Join-Path $PSScriptRoot 'runtime\bin\java.exe')){$java=Join-Path $PSScriptRoot 'runtime\bin\java.exe'}
    Write-Output "TPS local development: http://127.0.0.1:$Port (Ctrl+C to stop)"
    & $java -jar $Jar
    if($LASTEXITCODE -ne 0){throw 'TPS stopped with a failure; review the console.'}
} finally { foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$prior[$key],'Process')} }
