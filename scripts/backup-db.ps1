# 无碍智行 - PostgreSQL 数据库备份脚本（Docker Compose 环境）
# 用法：powershell -ExecutionPolicy Bypass -File scripts\backup-db.ps1 [-Keep 14]
# 备份文件保存到项目根目录 backups/（已被 .gitignore 排除），默认保留最近 14 份。

param(
    [int]$Keep = 14
)

$ErrorActionPreference = "Stop"
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envFile = Join-Path $projectRoot ".env"

if (-not (Test-Path $envFile)) {
    throw "未找到 .env，请先在项目根目录配置"
}
$dbPassword = (Get-Content $envFile | Where-Object { $_ -match '^DB_PASSWORD=' } | Select-Object -First 1) -replace '^DB_PASSWORD=', ''
if ([string]::IsNullOrWhiteSpace($dbPassword)) {
    throw ".env 中缺少 DB_PASSWORD"
}

$dumpDir = Join-Path $projectRoot "backups"
New-Item -ItemType Directory -Force -Path $dumpDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$file = Join-Path $dumpDir "barrierfreecampus-$stamp.dump"

Write-Host "开始备份 -> $file"
$env:PGPASSWORD = $dbPassword
try {
    # 定时任务可能不在项目根目录运行，这里显式指定工作目录
    $docker = (Get-Command docker -ErrorAction SilentlyContinue).Source
    if (-not $docker) {
        $candidates = @(
            "C:\Program Files\Docker\Docker\resources\bin\docker.exe",
            "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin\docker.exe"
        )
        $docker = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    }
    if (-not $docker) {
        throw "未找到 docker 命令，请确认 Docker Desktop 已安装"
    }
    $process = Start-Process -FilePath $docker `
        -WorkingDirectory $projectRoot `
        -ArgumentList @(
            "compose", "exec", "-T", "-e", "PGPASSWORD", "db",
            "pg_dump", "-U", "barrierfree", "-d", "barrierfreecampus", "-Fc"
        ) `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $file
    if ($process.ExitCode -ne 0) {
        if (Test-Path $file) { Remove-Item $file -Force }
        throw "pg_dump 执行失败，退出码 $($process.ExitCode)"
    }
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

$size = (Get-Item $file).Length
Write-Host "备份完成：$([math]::Round($size / 1MB, 2)) MB"

Get-ChildItem $dumpDir -Filter "barrierfreecampus-*.dump" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -Skip $Keep |
    ForEach-Object {
        Remove-Item $_.FullName
        Write-Host "清理旧备份: $($_.Name)"
    }
Write-Host "当前备份数：$((Get-ChildItem $dumpDir -Filter 'barrierfreecampus-*.dump').Count)"
