<#
.SYNOPSIS
    Builds a signed, installable Lodestar APK on this machine without CI.

The GitHub workflow does three things a plain `assembleRelease` does not: it injects the release
signing key, it unpacks the runtime accessory identity that the CarPlay receiver needs, and it
verifies both ended up in the APK. This script does the same locally:

  1. fetches the official DiPlay APK and extracts offline-mfi/identity.pk8 + certificate.p7b into
     .private/ (never committed),
  2. builds :mobile:assembleStandaloneRelease with signing/lodestar-release.properties,
  3. verifies the identity assets and the signing certificate, then writes
     .private/dist/Lodestar-<versionName>.apk plus its SHA-256.
#>
[CmdletBinding()]
param(
    [string]$OfficialTag = 'v0.2.14',
    [string]$OfficialSha256 = '62b31f79db32bc7c85013ae830460b697a5952fad571ed0030b97341dde0b2e3'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$private = Join-Path $root '.private'
$authAssets = Join-Path $private 'runtime-auth'
$cache = Join-Path $private 'cache'
$dist = Join-Path $private 'dist'

foreach ($required in @(
        (Join-Path $root 'signing/lodestar-release.properties'),
        (Join-Path $root 'signing/lodestar-release.jks')
    )) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing $required. The release build needs the Lodestar signing key."
    }
}

New-Item -ItemType Directory -Force -Path $authAssets, $cache, $dist | Out-Null

$identity = @('identity.pk8', 'certificate.p7b') | ForEach-Object { Join-Path $authAssets "offline-mfi/$_" }
$missingIdentity = @($identity | Where-Object { -not (Test-Path -LiteralPath $_) })
if ($missingIdentity.Count -gt 0) {
    $official = Join-Path $cache "DiPlay-$OfficialTag.apk"
    $cached = Test-Path -LiteralPath $official
    if ($cached) {
        $known = (Get-FileHash -LiteralPath $official -Algorithm SHA256).Hash.ToLowerInvariant()
        $cached = $known -eq $OfficialSha256.ToLowerInvariant()
        if (-not $cached) { Write-Host "Cached copy is incomplete or stale; resuming the download." }
    }
    if (-not $cached) {
        # The upstream release tags the APK without the leading "v" (DiPlay-0.2.14.apk under v0.2.14).
        $asset = "DiPlay-$($OfficialTag -replace '^v', '').apk"
        $url = "https://github.com/shihabal3amri/DiPlay/releases/download/$OfficialTag/$asset"
        Write-Host "Downloading official $OfficialTag APK for the runtime identity..."
        # Direct GitHub downloads are reset from mainland networks, and the mirrors drop the
        # connection part way through, so resume in bounded attempts instead of one long fetch.
        # The SHA-256 check below still has to pass before anything is extracted.
        $fetched = $false
        foreach ($candidate in @("https://ghproxy.net/$url", "https://gh-proxy.com/$url", $url)) {
            for ($attempt = 1; $attempt -le 30; $attempt++) {
                $before = if (Test-Path -LiteralPath $official) { (Get-Item -LiteralPath $official).Length } else { 0 }
                # --ssl-no-revoke: Windows schannel aborts when it cannot fetch a CRL on this network.
                curl.exe -fsSL --ssl-no-revoke --retry 2 --max-time 300 -C - -o $official $candidate
                if ($LASTEXITCODE -eq 0) { $fetched = $true; break }
                $after = if (Test-Path -LiteralPath $official) { (Get-Item -LiteralPath $official).Length } else { 0 }
                if ($after -le $before) { break }
                Write-Host ("  resuming at {0:N1} MB" -f ($after / 1MB))
            }
            if ($fetched) { break }
            Write-Host "  mirror failed: $candidate"
        }
        if (-not $fetched) { throw "Could not download the official APK from GitHub or its mirrors." }
    }
    $digest = (Get-FileHash -LiteralPath $official -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($digest -ne $OfficialSha256.ToLowerInvariant()) {
        throw "Official APK SHA-256 mismatch: $digest"
    }
    python (Join-Path $root 'scripts/extract_official_identity.py') $official $authAssets $OfficialSha256
} else {
    Write-Host "Runtime identity already extracted under $authAssets"
}

if (-not $env:ANDROID_HOME -and -not (Test-Path -LiteralPath (Join-Path $root 'local.properties'))) {
    throw 'Set ANDROID_HOME (or add sdk.dir to local.properties) before building.'
}

$env:DIPLAY_AUTH_ASSETS_DIR = $authAssets
Push-Location $root
try {
    & (Join-Path $root 'gradlew.bat') --no-daemon :mobile:assembleStandaloneRelease
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }

    $built = Join-Path $root 'mobile/build/outputs/apk/release/mobile-release.apk'
    if (-not (Test-Path -LiteralPath $built)) {
        throw "Expected $built. A release without the signing key is written as *-unsigned.apk."
    }
    python (Join-Path $root 'scripts/verify_apk_identity.py') $built

    $version = (Select-String -Path (Join-Path $root 'mobile/build.gradle.kts') -Pattern 'versionName = "([^"]+)"' |
        Select-Object -First 1).Matches[0].Groups[1].Value
    $target = Join-Path $dist "Lodestar-$version.apk"
    Copy-Item -LiteralPath $built -Destination $target -Force
    $sha = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    "$sha  Lodestar-$version.apk" | Set-Content -LiteralPath "$target.sha256"

    $apksigner = Get-ChildItem -Path (Join-Path $env:ANDROID_HOME 'build-tools/*/apksigner.bat') -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($apksigner) {
        & $apksigner.FullName verify --print-certs $target
    }

    Write-Host ""
    Write-Host "Signed release APK: $target"
    Write-Host "SHA-256: $sha"
}
finally {
    Pop-Location
}
