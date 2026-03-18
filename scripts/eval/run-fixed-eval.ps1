param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$CasesFile = ".\scripts\eval\fixed-eval-cases.json",
    [int]$TopK = 4,
    [string]$OutFile = ".\scripts\eval\fixed-eval-last.json",
    [double]$MinTop1 = 85.0,
    [double]$MinTopK = 86.0,
    [double]$MaxIrrelevant = 14.0,
    [double]$MaxEmpty = 8.0,
    [switch]$FailOnGate
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

function Is-Relevant {
    param(
        [object]$Product,
        [string[]]$ExpectedKeywords
    )

    if ($null -eq $Product) {
        return $false
    }

    $text = (
        "$($Product.name) " +
        "$($Product.category) " +
        "$($Product.categoryLevel1) " +
        "$($Product.categoryLevel2) " +
        "$($Product.categoryLevel3) " +
        "$($Product.description)"
    ).ToLowerInvariant()

    foreach ($kw in $ExpectedKeywords) {
        $k = "$kw".ToLowerInvariant().Trim()
        if ($k.Length -eq 0) {
            continue
        }
        if ($text.Contains($k)) {
            return $true
        }
    }
    return $false
}

function Convert-ToMojibake {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return ""
    }
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        return [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetString($bytes)
    } catch {
        return ""
    }
}

function Expand-ExpectedKeywords {
    param([string[]]$Keywords)
    $set = New-Object System.Collections.Generic.HashSet[string] ([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($kw in $Keywords) {
        $k = "$kw".Trim()
        if ($k.Length -eq 0) {
            continue
        }
        [void]$set.Add($k)
        $moj = Convert-ToMojibake -Text $k
        if (-not [string]::IsNullOrWhiteSpace($moj)) {
            [void]$set.Add($moj)
        }
    }
    return @($set)
}

$totalCases = $cases.Count
$totalReturned = 0
$totalRelevant = 0
$top1Hit = 0
$emptyCount = 0
$caseRows = @()
$idx = 0

foreach ($c in $cases) {
    $idx++
    $query = "$($c.query)".Trim()
    $expected = Expand-ExpectedKeywords -Keywords @($c.expected_keywords)
    # Use isolated user context per case to avoid conversational carry-over between independent eval queries.
    $userId = 910000 + $idx
    $body = @{ message = $query; userId = $userId } | ConvertTo-Json

    $resp = Invoke-RestMethod -Uri $replyUri -Method Post -ContentType "application/json; charset=utf-8" -Body $body
    $products = @()
    if ($resp -and $resp.data -and $resp.data.products) {
        $products = @($resp.data.products)
    }

    if ($products.Count -eq 0) {
        $emptyCount++
    }

    $top = @($products | Select-Object -First $TopK)
    $returned = $top.Count
    $relevant = 0
    foreach ($p in $top) {
        if (Is-Relevant -Product $p -ExpectedKeywords $expected) {
            $relevant++
        }
    }

    $top1 = $false
    if ($returned -gt 0) {
        $top1 = Is-Relevant -Product $top[0] -ExpectedKeywords $expected
        if ($top1) {
            $top1Hit++
        }
    }

    $totalReturned += $returned
    $totalRelevant += $relevant

    $caseRows += [PSCustomObject]@{
        query = $query
        returned = $returned
        relevant = $relevant
        top1_hit = $top1
        top1_name = if ($returned -gt 0) { $top[0].name } else { "" }
    }
}

$top1Pct = [math]::Round((100.0 * $top1Hit / $totalCases), 2)
$topKPct = if ($totalReturned -gt 0) { [math]::Round((100.0 * $totalRelevant / $totalReturned), 2) } else { 0 }
$irrelevantPct = [math]::Round((100.0 - $topKPct), 2)
$emptyPct = [math]::Round((100.0 * $emptyCount / $totalCases), 2)

$summary = [PSCustomObject]@{
    generated_at = (Get-Date).ToString("s")
    base_url = $BaseUrl
    top_k = $TopK
    total_cases = $totalCases
    total_returned = $totalReturned
    total_relevant = $totalRelevant
    top1_intent_proxy_percent = $top1Pct
    topk_relevance_percent = $topKPct
    irrelevant_percent = $irrelevantPct
    empty_query_percent = $emptyPct
}

$gate = [PSCustomObject]@{
    min_top1 = $MinTop1
    min_topk = $MinTopK
    max_irrelevant = $MaxIrrelevant
    max_empty = $MaxEmpty
    pass_top1 = ($top1Pct -ge $MinTop1)
    pass_topk = ($topKPct -ge $MinTopK)
    pass_irrelevant = ($irrelevantPct -le $MaxIrrelevant)
    pass_empty = ($emptyPct -le $MaxEmpty)
}
$gate | Add-Member -NotePropertyName passed -NotePropertyValue ($gate.pass_top1 -and $gate.pass_topk -and $gate.pass_irrelevant -and $gate.pass_empty)

$report = [PSCustomObject]@{
    summary = $summary
    gate = $gate
    details = $caseRows
}

$report | ConvertTo-Json -Depth 8 | Set-Content -Path $OutFile -Encoding UTF8
$summary | ConvertTo-Json -Depth 5
($gate | ConvertTo-Json -Depth 5)
Write-Output "saved_report=$OutFile"

if ($FailOnGate -and -not $gate.passed) {
    Write-Error ("quality gate failed: top1={0} topk={1} irrelevant={2} empty={3}" -f $top1Pct, $topKPct, $irrelevantPct, $emptyPct)
    exit 2
}
