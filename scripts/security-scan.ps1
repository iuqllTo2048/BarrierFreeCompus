$ErrorActionPreference = 'Stop'
$tracked = git -c core.quotepath=false ls-files --cached --others --exclude-standard
if ($LASTEXITCODE -ne 0) { throw '无法读取 Git 跟踪文件' }

$forbiddenFiles = $tracked | Where-Object {
  $_ -match '(^|/)\.env($|\.)' -and $_ -ne '.env.example' -or
  $_ -match '\.(pem|key|p12|pfx)$'
}
if ($forbiddenFiles) {
  $forbiddenFiles | ForEach-Object { Write-Error "禁止提交敏感文件：$_" }
  exit 1
}

$patterns = @(
  '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----',
  'sk-[A-Za-z0-9_-]{20,}',
  '(AI_API_KEY|JWT_SECRET|DB_PASSWORD)\s*=\s*[^<{$\s][^\s]{8,}'
)
$violations = @()
foreach ($pattern in $patterns) {
  $scanFiles = $tracked | Where-Object {
    $_ -ne '.env.example' -and $_ -notlike 'docs/*' -and $_ -ne 'scripts/security-scan.ps1'
  }
  $violations += Select-String -Path $scanFiles -Pattern $pattern -ErrorAction SilentlyContinue |
    ForEach-Object { "$($_.Path):$($_.LineNumber):$($_.Line.Trim())" }
}
if ($violations) {
  $violations | Sort-Object -Unique | ForEach-Object { Write-Error "疑似敏感信息：$_" }
  exit 1
}

Write-Output 'SECURITY_SCAN_OK tracked secrets/private keys: 0'
