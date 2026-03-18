param(
    [string]$Container = "aishop-postgres",
    [string]$DbUser = "admin",
    [string]$DbName = "aishop_db",
    [int]$MaxRows = 400,
    [int]$TimeoutSec = 15,
    [string]$OutFile = ".\scripts\images\image-health-last.json",
    [switch]$ApplyFix
)

$ErrorActionPreference = "Stop"

function Invoke-Psql {
    param([string]$Sql)
    return (& docker exec $Container psql -U $DbUser -d $DbName -At -c $Sql)
}

function Normalize-ImageUrl {
    param([string]$Raw)
    if ([string]::IsNullOrWhiteSpace($Raw)) {
        return ""
    }
    $url = $Raw.Trim()
    if ($url.StartsWith("//")) {
        $url = "https:$url"
    }
    $url = [regex]::Replace(
        $url,
        "^https?://m\.media-amazon\.com/images/W/[^/]+/images/",
        "https://m.media-amazon.com/images/",
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    return $url
}

function Get-HttpCode {
    param([string]$Url)
    if ([string]::IsNullOrWhiteSpace($Url)) {
        return 0
    }
    try {
        $code = & curl.exe -s -L -I -o NUL -w "%{http_code}" --max-time $TimeoutSec --globoff "$Url"
        $intCode = 0
        [void][int]::TryParse(($code | Out-String).Trim(), [ref]$intCode)
        return $intCode
    } catch {
        return 0
    }
}

function Is-HealthyCode {
    param([int]$Code)
    return $Code -ge 200 -and $Code -lt 400
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker not found"
}

$productsSql = @"
SELECT 'products' AS src, id::text AS row_id, id::text AS product_id, image_url
FROM products
WHERE image_url IS NOT NULL AND image_url <> ''
ORDER BY id
LIMIT $MaxRows
"@

$productImagesSql = @"
SELECT 'product_images' AS src, id::text AS row_id, product_id::text AS product_id, image_url
FROM product_images
WHERE image_url IS NOT NULL AND image_url <> ''
ORDER BY id
LIMIT $MaxRows
"@

$rowsRaw = @()
$rowsRaw += Invoke-Psql -Sql $productsSql
$rowsRaw += Invoke-Psql -Sql $productImagesSql

$rows = @()
foreach ($line in $rowsRaw) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $parts = $line -split "\|", 4
    if ($parts.Count -lt 4) { continue }
    $rows += [PSCustomObject]@{
        source = $parts[0]
        row_id = [long]$parts[1]
        product_id = [long]$parts[2]
        image_url = $parts[3]
    }
}

$checked = 0
$broken = 0
$fixable = 0
$applied = 0
$details = @()

foreach ($r in $rows) {
    $checked++
    $orig = "$($r.image_url)"
    $origCode = Get-HttpCode -Url $orig
    $origHealthy = Is-HealthyCode -Code $origCode

    $normalized = Normalize-ImageUrl -Raw $orig
    $normCode = 0
    $normHealthy = $false
    if ($normalized -ne $orig) {
        $normCode = Get-HttpCode -Url $normalized
        $normHealthy = Is-HealthyCode -Code $normCode
    }

    if (-not $origHealthy) {
        $broken++
    }
    $canFix = (-not $origHealthy) -and ($normalized -ne $orig) -and $normHealthy
    if ($canFix) {
        $fixable++
    }

    $didApply = $false
    if ($ApplyFix -and $canFix) {
        $escaped = $normalized.Replace("'", "''")
        if ($r.source -eq "products") {
            [void](Invoke-Psql -Sql "UPDATE products SET image_url = '$escaped' WHERE id = $($r.row_id);")
        } else {
            [void](Invoke-Psql -Sql "UPDATE product_images SET image_url = '$escaped' WHERE id = $($r.row_id);")
        }
        $applied++
        $didApply = $true
    }

    $details += [PSCustomObject]@{
        source = $r.source
        row_id = $r.row_id
        product_id = $r.product_id
        original_url = $orig
        original_code = $origCode
        normalized_url = $normalized
        normalized_code = $normCode
        can_fix = $canFix
        applied = $didApply
    }
}

$summary = [PSCustomObject]@{
    generated_at = (Get-Date).ToString("s")
    checked = $checked
    broken = $broken
    fixable = $fixable
    applied = $applied
    apply_fix = [bool]$ApplyFix
    container = $Container
    database = $DbName
}

$report = [PSCustomObject]@{
    summary = $summary
    details = $details
}

$dir = Split-Path -Parent $OutFile
if (-not [string]::IsNullOrWhiteSpace($dir) -and -not (Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir | Out-Null
}

$report | ConvertTo-Json -Depth 8 | Set-Content -Path $OutFile -Encoding UTF8
$summary | ConvertTo-Json -Depth 5
Write-Output "saved_report=$OutFile"
