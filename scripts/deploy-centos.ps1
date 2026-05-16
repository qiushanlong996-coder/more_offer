param(
    [string]$SshTarget = "root@www.lovenuaa.xyz",
    [string]$RemoteDir = "/opt/more-offer",
    [string]$ReleaseName = ("m1-" + (Get-Date -Format "yyyyMMddHHmmss"))
)

$ErrorActionPreference = "Stop"

function Run-Step {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    Write-Host ""
    Write-Host "==> $Name"
    & $Command
}

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$stage = Join-Path $root ".deploy\stage"
$package = Join-Path $root ".deploy\more-offer-$ReleaseName.tar.gz"

Run-Step "Build frontend" {
    Push-Location (Join-Path $root "frontend")
    npm run build
    Pop-Location
}

Run-Step "Build backend" {
    Push-Location (Join-Path $root "backend")
    mvn -DskipTests package
    Pop-Location
}

Run-Step "Stage release files" {
    if (Test-Path $stage) {
        Remove-Item -LiteralPath $stage -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path "$stage\frontend", "$stage\backend", "$stage\newcoder-mcp-server-main", "$stage\deploy" | Out-Null
    Copy-Item -Recurse -Force (Join-Path $root "frontend\dist") "$stage\frontend\dist"
    Copy-Item -Force (Join-Path $root "backend\target\more-offer-backend-0.1.0.jar") "$stage\backend\more-offer-backend-0.1.0.jar"
    Copy-Item -Recurse -Force (Join-Path $root "newcoder-mcp-server-main\dist") "$stage\newcoder-mcp-server-main\dist"
    Copy-Item -Force (Join-Path $root "newcoder-mcp-server-main\package.json") "$stage\newcoder-mcp-server-main\package.json"
    Copy-Item -Force (Join-Path $root "newcoder-mcp-server-main\package-lock.json") "$stage\newcoder-mcp-server-main\package-lock.json"
    Copy-Item -Force (Join-Path $root "deploy\more-offer-backend.service") "$stage\deploy\more-offer-backend.service"
    Copy-Item -Force (Join-Path $root "deploy\nginx-more-offer.conf") "$stage\deploy\nginx-more-offer.conf"
}

Run-Step "Create release package" {
    if (Test-Path $package) {
        Remove-Item -LiteralPath $package -Force
    }
    Push-Location $stage
    tar -czf $package .
    Pop-Location
}

Run-Step "Upload package" {
    ssh $SshTarget "mkdir -p $RemoteDir/releases/$ReleaseName $RemoteDir/runtime"
    scp $package "${SshTarget}:/tmp/more-offer-$ReleaseName.tar.gz"
}

Run-Step "Activate release" {
    $remote = @"
set -e
mkdir -p $RemoteDir/releases/$ReleaseName
tar -xzf /tmp/more-offer-$ReleaseName.tar.gz -C $RemoteDir/releases/$ReleaseName
ln -sfn $RemoteDir/releases/$ReleaseName $RemoteDir/current
cp $RemoteDir/current/deploy/more-offer-backend.service /etc/systemd/system/more-offer-backend.service
if [ -d /www/server/panel/vhost/nginx ] && [ -x /www/server/nginx/sbin/nginx ]; then
  cp $RemoteDir/current/deploy/nginx-more-offer.conf /www/server/panel/vhost/nginx/more-offer.conf
  /www/server/nginx/sbin/nginx -t -c /www/server/nginx/conf/nginx.conf
  /www/server/nginx/sbin/nginx -s reload -c /www/server/nginx/conf/nginx.conf
elif command -v nginx >/dev/null 2>&1; then
  mkdir -p /etc/nginx/conf.d
  cp $RemoteDir/current/deploy/nginx-more-offer.conf /etc/nginx/conf.d/more-offer.conf
  nginx -t
  systemctl reload nginx || nginx -s reload
fi
systemctl daemon-reload
if [ -x "$RemoteDir/runtime/node/bin/node" ]; then
  (cd $RemoteDir/current/newcoder-mcp-server-main && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 $RemoteDir/runtime/node/bin/node $RemoteDir/runtime/node/lib/node_modules/npm/bin/npm-cli.js ci --omit=dev --ignore-scripts)
fi
systemctl enable more-offer-backend
systemctl restart more-offer-backend
systemctl --no-pager --full status more-offer-backend
curl -fsS http://127.0.0.1:8080/actuator/health
"@
    $remote | ssh $SshTarget "bash -s"
}

Write-Host ""
Write-Host "Deployment finished: $ReleaseName"
