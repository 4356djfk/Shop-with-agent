# Eval Commands

Generate dense eval cases:

```powershell
./scripts/eval/generate-fixed-eval-cases.ps1
```

Run fixed eval with quality gate:

```powershell
./scripts/eval/run-fixed-eval.ps1 `
  -CasesFile .\scripts\eval\fixed-eval-cases-dense.json `
  -TopK 4 `
  -MinTop1 85 `
  -MinTopK 86 `
  -MaxIrrelevant 14 `
  -MaxEmpty 8 `
  -FailOnGate
```

Notes:
- Report JSON is written to `scripts/eval/fixed-eval-last.json` by default.
- With `-FailOnGate`, script exits with code `2` when gate is not met.
