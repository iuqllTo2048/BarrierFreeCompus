$ErrorActionPreference = 'Stop'
$project = 'barrierfreecampus-e2e'
$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root 'docker-compose.e2e.yml'

try {
  docker compose -p $project -f $compose down --volumes --remove-orphans
  if ($LASTEXITCODE -ne 0) { throw '无法清理旧的隔离 E2E 环境' }
  docker compose -p $project -f $compose up --build --wait
  if ($LASTEXITCODE -ne 0) { throw '隔离 E2E 环境启动失败' }
  Push-Location (Join-Path $root 'frontend')
  try {
    npx playwright test
    if ($LASTEXITCODE -ne 0) { throw 'Playwright E2E 测试失败' }
  } finally {
    Pop-Location
  }
} finally {
  docker compose -p $project -f $compose down --volumes --remove-orphans
}
