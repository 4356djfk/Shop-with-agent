# Image Health Check

Scan product image URLs (dry-run by default):

```powershell
./scripts/images/check-image-health.ps1
```

Scan and apply auto-fix for recognized broken URL patterns:

```powershell
./scripts/images/check-image-health.ps1 -ApplyFix
```

Optional parameters:

```powershell
./scripts/images/check-image-health.ps1 `
  -Container aishop-postgres `
  -DbUser admin `
  -DbName aishop_db `
  -MaxRows 1000 `
  -TimeoutSec 15 `
  -OutFile .\scripts\images\image-health-last.json `
  -ApplyFix
```

Notes:
- Current auto-fix focuses on legacy Amazon rendering URLs:
  `.../images/W/IMAGERENDERING.../images/I/...` -> `.../images/I/...`
- Report is written to JSON and includes per-row HTTP status and fixability.
