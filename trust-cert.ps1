<#
.SYNOPSIS
    从 HTTPS 服务器抓取自签名证书并导入 Windows 受信任根证书
.DESCRIPTION
    TCP+TLS 连接目标 → 无视自签名错误抓取证书 → 导入 LocalMachine\Root
    同时导出 PEM 文件到桌面，供 Docker / 浏览器等使用
.PARAMETER HostPort
    HTTPS 服务器地址，格式 host:port (默认端口 443)
.EXAMPLE
    powershell -File trust-cert.ps1 -HostPort 192.168.1.100:5000
#>
param(
    [Parameter(Mandatory=$true)]
    [string]$HostPort
)

$ErrorActionPreference = 'Continue'

# ── 解析地址 ────────────────────────────────
$input = $HostPort -replace '^https?://', '' -replace '/$', ''
$parts = $input -split ':'
if ($parts.Count -eq 2) {
    $HostName = $parts[0]
    $Port     = [int]$parts[1]
} else {
    $HostName = $parts[0]
    $Port     = 443
}

# ── 检查管理员权限 ──────────────────────────
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host '[WARNING] Not running as Administrator. Import to LocalMachine may fail.' -ForegroundColor DarkYellow
    Write-Host '          CurrentUser trust store will be used as fallback.' -ForegroundColor DarkYellow
}

Write-Host ''
Write-Host '========================================'
Write-Host '  Fetch & Trust Self-Signed Certificate'
Write-Host '========================================'
Write-Host ''
Write-Host ('  Target: {0}:{1}' -f $HostName, $Port)
Write-Host ''

# ── [1/3] 连接并抓取证书 ────────────────────
Write-Host '  [1/3] Connecting to server...' -ForegroundColor Yellow

try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect($HostName, $Port)
    $ssl = New-Object System.Net.Security.SslStream(
        $tcp.GetStream(),
        $false,
        [System.Net.Security.RemoteCertificateValidationCallback]{ param($a,$b,$c,$d) $true }
    )
    $ssl.AuthenticateAsClient($HostName)
    $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($ssl.RemoteCertificate)
    $ssl.Close()
    $tcp.Close()

    Write-Host ''
    Write-Host '  Certificate Info:' -ForegroundColor Cyan
    Write-Host ('    Subject  : {0}' -f $cert.Subject)
    Write-Host ('    Issuer   : {0}' -f $cert.Issuer)
    Write-Host ('    Valid    : {0:yyyy-MM-dd} ~ {1:yyyy-MM-dd}' -f $cert.NotBefore, $cert.NotAfter)
    Write-Host ('    Thumbprint: {0}' -f $cert.Thumbprint)

    # SAN
    $san = ''
    try {
        foreach ($ext in $cert.Extensions) {
            if ($ext.Oid.Value -eq '2.5.29.17') {
                $san = $ext.Format($false)
            }
        }
    } catch {}
    if ($san) {
        Write-Host ('    SAN      : {0}' -f ($san -replace '\r?\n', ' '))
    } else {
        Write-Host '    SAN      : (none - this may cause issues!)' -ForegroundColor DarkRed
    }

    if ($cert.Subject -eq $cert.Issuer) {
        Write-Host '    Type     : Self-Signed' -ForegroundColor DarkYellow
    } else {
        Write-Host '    Type     : CA-Issued (import the ROOT CA, not the server cert)' -ForegroundColor DarkYellow
    }
    Write-Host ''

}
catch {
    Write-Host ''
    Write-Host ('  [FAIL] {0}' -f $_.Exception.Message) -ForegroundColor Red
    Write-Host ''
    Write-Host '  Possible causes:'
    Write-Host '    1. Server is not running (docker ps)'
    Write-Host '    2. Port is blocked by firewall'
    Write-Host '    3. Target is not an HTTPS/TLS service'
    Write-Host ('    4. Cannot reach {0}:{1}' -f $HostName, $Port)
    exit 1
}

# ── [2/3] 导入证书存储 ──────────────────────
Write-Host '  [2/3] Importing to Windows trust store...' -ForegroundColor Yellow

$importedLocal = $false
$importedUser  = $false
$alreadyThere  = $false

# --- LocalMachine ---
if ($isAdmin) {
    $storeLM = New-Object System.Security.Cryptography.X509Certificates.X509Store('Root', 'LocalMachine')
    try {
        $storeLM.Open('ReadWrite')
        $found = $storeLM.Certificates | Where-Object { $_.Thumbprint -eq $cert.Thumbprint }
        if ($found) {
            Write-Host '    LocalMachine : already exists (skip)' -ForegroundColor Gray
            $alreadyThere = $true
        } else {
            $storeLM.Add($cert)
            Write-Host '    LocalMachine : OK' -ForegroundColor Green
            $importedLocal = $true
        }
    } catch {
        Write-Host ('    LocalMachine : FAIL - {0}' -f $_.Exception.Message) -ForegroundColor Red
    } finally {
        $storeLM.Close()
    }
}

# --- CurrentUser ---
$storeCU = New-Object System.Security.Cryptography.X509Certificates.X509Store('Root', 'CurrentUser')
try {
    $storeCU.Open('ReadWrite')
    $found = $storeCU.Certificates | Where-Object { $_.Thumbprint -eq $cert.Thumbprint }
    if ($found) {
        Write-Host '    CurrentUser  : already exists (skip)' -ForegroundColor Gray
        if (-not $alreadyThere) { $alreadyThere = $true }
    } else {
        $storeCU.Add($cert)
        Write-Host '    CurrentUser  : OK' -ForegroundColor Green
        $importedUser = $true
    }
} catch {
    Write-Host ('    CurrentUser  : FAIL - {0}' -f $_.Exception.Message) -ForegroundColor Red
} finally {
    $storeCU.Close()
}

Write-Host ''

# ── [3/3] 导出 PEM 到桌面 ───────────────────
Write-Host '  [3/3] Exporting PEM to Desktop...' -ForegroundColor Yellow

$desktop = [Environment]::GetFolderPath('Desktop')
$fileName = ('{0}_{1}.crt' -f $HostName, $Port)
$pemPath  = Join-Path $desktop $fileName

$der = $cert.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert)
$b64 = [Convert]::ToBase64String($der, 'InsertLineBreaks')
$pem = "-----BEGIN CERTIFICATE-----`r`n$b64`r`n-----END CERTIFICATE-----`r`n"
[IO.File]::WriteAllText($pemPath, $pem)
Write-Host ('    Saved to: Desktop\{0}' -f $fileName) -ForegroundColor Gray
Write-Host ''

# ── 结果汇总 ────────────────────────────────
Write-Host '========================================' -ForegroundColor Green
Write-Host '  Done!' -ForegroundColor Green
Write-Host '========================================' -ForegroundColor Green
Write-Host ''

if (-not $importedLocal -and -not $importedUser -and -not $alreadyThere) {
    Write-Host '  [WARNING] Certificate was NOT imported. Run as Administrator.' -ForegroundColor Red
    Write-Host ''
}

Write-Host '  Next steps:'
Write-Host '    1. Restart Docker Desktop (tray icon -> Restart)'
if ($Port -ne 443) {
    Write-Host ('    2. (Linux host) mkdir -p /etc/docker/certs.d/{0}:{1}' -f $HostName, $Port)
    Write-Host ('                   cp {0} /etc/docker/certs.d/{1}:{2}/ca.crt' -f $fileName, $HostName, $Port)
} else {
    Write-Host ('    2. (Linux host) mkdir -p /etc/docker/certs.d/{0}' -f $HostName)
    Write-Host ('                   cp {0} /etc/docker/certs.d/{1}/ca.crt' -f $fileName, $HostName)
}
Write-Host ('    3. Test: docker login {0}:{1}' -f $HostName, $Port)
Write-Host ('    4. Verify: certutil -verifyStore Root {0}' -f $cert.Thumbprint)
Write-Host ''
