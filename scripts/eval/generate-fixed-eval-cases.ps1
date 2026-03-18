param(
    [string]$OutFile = ".\scripts\eval\fixed-eval-cases-dense.json"
)

$ErrorActionPreference = "Stop"

function New-Case {
    param(
        [string]$Query,
        [string[]]$ExpectedKeywords
    )
    return [PSCustomObject]@{
        query = $Query
        expected_keywords = $ExpectedKeywords
    }
}

$intents = @(
    @{
        expected = @("mouse")
        queries = @("mouse","wireless mouse","gaming mouse","ergonomic mouse","bluetooth mouse","office mouse","usb mouse","silent mouse","2.4g mouse","rgb mouse")
    },
    @{
        expected = @("keyboard")
        queries = @("keyboard","mechanical keyboard","gaming keyboard","wireless keyboard","silent keyboard","compact keyboard","rgb keyboard","office keyboard","usb keyboard","bluetooth keyboard")
    },
    @{
        expected = @("headphone","earbud","earphone","headset")
        queries = @("headphone","bluetooth headphone","wireless earbuds","gaming headset","noise cancelling headphone","tws earbuds","sports earphone","in-ear headphone","over-ear headphone","anc headset")
    },
    @{
        expected = @("shoe","sneaker","boot","sandal")
        queries = @("running shoes","sneakers","walking shoes","training shoes","basketball shoes","casual shoes","hiking shoes","tennis shoes","slip-on shoes","comfort shoes")
    },
    @{
        expected = @("bag","backpack","handbag","luggage","tote")
        queries = @("backpack","travel bag","tote bag","laptop backpack","duffel bag","school bag","crossbody bag","handbag","carry-on luggage","messenger bag")
    },
    @{
        expected = @("lamp","light","lighting","led","bulb")
        queries = @("desk lamp","night light","led lamp","floor lamp","table lamp","reading light","bedside lamp","smart light","ceiling light","wall light")
    },
    @{
        expected = @("bicycle","bike","cycling","mtb","bmx")
        queries = @("bicycle","mountain bike","road bike","bmx bike","city bike","kids bike","adult bike","cycling bike","commuter bike","hybrid bike")
    },
    @{
        expected = @("computer","laptop","notebook","desktop","macbook")
        queries = @("laptop computer","desktop pc","gaming laptop","office laptop","ultrabook","notebook computer","all-in-one pc","business laptop","student laptop","portable computer")
    },
    @{
        expected = @("pet","dog","cat")
        queries = @("pet supplies","dog food","cat food","pet toy","pet leash","pet bowl","pet bed","dog treats","cat litter","pet grooming")
    },
    @{
        expected = @("baby","infant","newborn","stroller","diaper","toddler")
        queries = @("baby stroller","infant supplies","newborn essentials","baby diaper","baby carrier","baby bottle","baby wipes","baby crib","toddler toy","baby monitor")
    },
    @{
        expected = @("book","books","novel","textbook","literature","kindle")
        queries = @("book novel","textbook","literature book","fiction books","history book","science book","children book","kindle book","study guide","paperback book")
    },
    @{
        expected = @("toy","lego","puzzle","doll","board game")
        queries = @("toy puzzle","children toys","lego set","board game","doll toy","educational toy","building blocks","card game toy","kids puzzle","family game")
    },
    @{
        expected = @("makeup","cosmetic","lipstick","foundation","eyeliner","serum")
        queries = @("makeup lipstick","cosmetic set","foundation makeup","eyeliner pen","face serum","beauty makeup","matte lipstick","liquid foundation","makeup kit","skin serum")
    },
    @{
        expected = @("home","household","daily","kitchen","bathroom","storage","cleaning")
        queries = @("home daily supplies","kitchen storage","bathroom organizer","cleaning tools","household essentials","home storage box","kitchen rack","laundry basket","cleaning brush","home organizer")
    }
)

$modifiers = @(
    "", "recommend", "show me", "i want to buy", "best", "cheap", "best value",
    "top rated", "for beginner", "for daily use", "under budget", "high quality"
)

$rows = New-Object System.Collections.Generic.List[object]
foreach ($intent in $intents) {
    foreach ($q in $intent.queries) {
        foreach ($m in $modifiers) {
            $query = if ([string]::IsNullOrWhiteSpace($m)) { $q } else { "$m $q" }
            $rows.Add((New-Case -Query $query.Trim() -ExpectedKeywords $intent.expected))
        }
    }
}

$seen = New-Object System.Collections.Generic.HashSet[string] ([System.StringComparer]::OrdinalIgnoreCase)
$dedup = New-Object System.Collections.Generic.List[object]
foreach ($r in $rows) {
    $q = "$($r.query)".Trim()
    if ($seen.Add($q)) {
        $dedup.Add($r)
    }
}

$dedup | ConvertTo-Json -Depth 6 | Set-Content -Path $OutFile -Encoding UTF8
Write-Output ("generated_cases={0}" -f $dedup.Count)
Write-Output ("saved_file={0}" -f $OutFile)
