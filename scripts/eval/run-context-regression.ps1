param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$CasesFile = ".\\scripts\\eval\\context-regression-cases.json",
    [string]$OutFile = ".\\scripts\\eval\\context-regression-last.json",
    [int]$TopK = 4
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $CasesFile)) {
    throw "Cases file not found: $CasesFile"
}

$cases = Get-Content -Raw -Path $CasesFile | ConvertFrom-Json
if ($null -eq $cases -or $cases.Count -eq 0) {
    throw "No cases in: $CasesFile"
}

$replyUri = "$BaseUrl/api/chat/reply"

function Convert-ToMojibake {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return "" }
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        return [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetString($bytes)
    } catch {
        return ""
    }
}

function Expand-Keys {
    param([object[]]$Keys)
    $set = New-Object System.Collections.Generic.HashSet[string] ([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($k in $Keys) {
        $v = "$k".Trim()
        if ($v.Length -eq 0) { continue }
        [void]$set.Add($v)
        $moj = Convert-ToMojibake -Text $v
        if (-not [string]::IsNullOrWhiteSpace($moj)) {
            [void]$set.Add($moj)
        }
    }
    return @($set)
}

function Product-Text {
    param([object]$Product)
    if ($null -eq $Product) { return "" }
    return (
        "$($Product.name) " +
        "$($Product.category) " +
        "$($Product.categoryLevel1) " +
        "$($Product.categoryLevel2) " +
        "$($Product.categoryLevel3) " +
        "$($Product.description)"
    ).ToLowerInvariant()
}

function Contains-Any {
    param([string]$Text, [object[]]$Keys)
    $expanded = Expand-Keys -Keys $Keys
    foreach ($k in $expanded) {
        $kk = "$k".ToLowerInvariant().Trim()
        if ($kk.Length -eq 0) { continue }
        if ($Text.Contains($kk)) { return $true }
    }
    return $false
}

function Products-Contain-Any {
    param([object[]]$Products, [object[]]$Keys)
    if ($null -eq $Products -or $Products.Count -eq 0) { return $false }
    foreach ($p in $Products) {
        if (Contains-Any -Text (Product-Text -Product $p) -Keys $Keys) {
            return $true
        }
    }
    return $false
}

function Products-Contain-None {
    param([object[]]$Products, [object[]]$Keys)
    if ($null -eq $Products -or $Products.Count -eq 0) { return $true }
    foreach ($p in $Products) {
        if (Contains-Any -Text (Product-Text -Product $p) -Keys $Keys) {
            return $false
        }
    }
    return $true
}

$turnTotal = 0
$turnPass = 0
$intentTotal = 0
$intentHit = 0
$contextTotal = 0
$contextHit = 0
$guidanceTotal = 0
$guidanceHit = 0
$rows = @()

foreach ($session in $cases) {
    $sessionName = "$($session.name)"
    $userId = [long]$session.user_id
    foreach ($turn in $session.turns) {
        $turnTotal++
        $query = "$($turn.input)"
        $body = @{ message = $query; userId = $userId } | ConvertTo-Json
        $resp = Invoke-RestMethod -Uri $replyUri -Method Post -ContentType "application/json; charset=utf-8" -Body $body

        $content = ""
        $products = @()
        if ($resp -and $resp.data) {
            $content = "$($resp.data.content)"
            if ($resp.data.products) {
                $products = @($resp.data.products | Select-Object -First $TopK)
            }
        }
        $contentLower = $content.ToLowerInvariant()

        $ok = $true
        $reasons = New-Object System.Collections.Generic.List[string]

        if ($turn.expect_non_empty_products -eq $true -and $products.Count -eq 0) {
            $ok = $false
            $reasons.Add("expected non-empty products")
        }
        if ($turn.expect_empty_products -eq $true -and $products.Count -ne 0) {
            $ok = $false
            $reasons.Add("expected empty products")
        }

        if ($turn.expect_products_contain_any) {
            $intentTotal++
            $hit = Products-Contain-Any -Products $products -Keys @($turn.expect_products_contain_any)
            if ($hit) {
                $intentHit++
            } else {
                $ok = $false
                $reasons.Add("products not matched expected keywords")
            }
        }
        if ($turn.expect_products_not_contain_any) {
            $none = Products-Contain-None -Products $products -Keys @($turn.expect_products_not_contain_any)
            if (-not $none) {
                $ok = $false
                $reasons.Add("products contain disallowed keywords")
            }
        }

        if ($turn.expect_reply_contains_any) {
            $replyHit = Contains-Any -Text $contentLower -Keys @($turn.expect_reply_contains_any)
            if (-not $replyHit) {
                $ok = $false
                $reasons.Add("reply missing expected hints")
            }
        }
        if ($turn.expect_reply_not_contains_any) {
            $replyBad = Contains-Any -Text $contentLower -Keys @($turn.expect_reply_not_contains_any)
            if ($replyBad) {
                $ok = $false
                $reasons.Add("reply contains disallowed phrase")
            }
        }

        if ($turn.context_turn -eq $true) {
            $contextTotal++
            $contextOk = $true
            if ($turn.expect_products_contain_any) {
                $contextOk = Products-Contain-Any -Products $products -Keys @($turn.expect_products_contain_any)
            } elseif ($products.Count -eq 0) {
                $contextOk = $false
            }
            if ($contextOk) {
                $contextHit++
            } else {
                $reasons.Add("context inheritance failed")
            }
        }

        if ($turn.expect_guidance -eq $true) {
            $guidanceTotal++
            $guidanceOk = $false
            if ($turn.expect_reply_contains_any) {
                $guidanceOk = Contains-Any -Text $contentLower -Keys @($turn.expect_reply_contains_any)
            } else {
                $guidanceOk = Contains-Any -Text $contentLower -Keys @("please first", "start with", "need context", "no prior candidates")
            }
            if ($guidanceOk) {
                $guidanceHit++
            } else {
                $reasons.Add("ambiguity guidance miss")
            }
        }

        if ($ok) { $turnPass++ }

        $rows += [PSCustomObject]@{
            session  = $sessionName
            input    = $query
            pass     = $ok
            returned = $products.Count
            top1     = if ($products.Count -gt 0) { $products[0].name } else { "" }
            reasons  = @($reasons)
        }
    }
}

$passRate = if ($turnTotal -gt 0) { [math]::Round((100.0 * $turnPass / $turnTotal), 2) } else { 0.0 }
$intentAcc = if ($intentTotal -gt 0) { [math]::Round((100.0 * $intentHit / $intentTotal), 2) } else { 0.0 }
$contextAcc = if ($contextTotal -gt 0) { [math]::Round((100.0 * $contextHit / $contextTotal), 2) } else { 0.0 }
$guidanceHitRate = if ($guidanceTotal -gt 0) { [math]::Round((100.0 * $guidanceHit / $guidanceTotal), 2) } else { 0.0 }

$summary = [PSCustomObject]@{
    generated_at = (Get-Date).ToString("s")
    base_url = $BaseUrl
    top_k = $TopK
    total_turns = $turnTotal
    passed_turns = $turnPass
    pass_rate_percent = $passRate
    intent_accuracy_proxy_percent = $intentAcc
    context_inheritance_accuracy_percent = $contextAcc
    ambiguity_guidance_hit_percent = $guidanceHitRate
}

$report = [PSCustomObject]@{
    summary = $summary
    details = $rows
}

$report | ConvertTo-Json -Depth 10 | Set-Content -Path $OutFile -Encoding UTF8
$summary | ConvertTo-Json -Depth 6
Write-Output "saved_report=$OutFile"
