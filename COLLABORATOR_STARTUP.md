# AI-shop Backend Collaboration Guide

## 1. Repositories
- Frontend repo: `https://github.com/zhuiyan666/AI-shop-front`
- Backend repo: `https://github.com/zhuiyan666/AI-shop`

## 2. Clone / Pull Project

### 2.1 First time clone
```powershell
git clone https://github.com/zhuiyan666/AI-shop.git
git clone https://github.com/zhuiyan666/AI-shop-front.git
```

### 2.2 Update local code
```powershell
git -C AI-shop pull --rebase origin master
git -C AI-shop-front pull --rebase origin master
```

## 3. Start Infrastructure (Docker)
Run commands in `AI-shop` directory.

### 3.1 First environment setup (with DB init)
```powershell
docker compose --profile init up -d postgres db-init redis elasticsearch milvus-etcd milvus-minio milvus attu
```

### 3.2 Daily startup (keep existing DB data)
```powershell
docker compose up -d postgres redis elasticsearch milvus-etcd milvus-minio milvus attu
```

Notes:
- `db-init` is a one-shot container. `Exited (0)` means success.
- `db-init` now runs SQL in this fixed order:
  1) `sql/aishop_postgres.sql` (base schema + base seed)
  2) `sql/products_from_es_12165.sql` (import 12165 products from ES export)
  3) `sql/category_quality_all_in_one.sql` (category quality patch)
  4) `sql/category_quality_audit.sql` (mismatch audit query/report)
- Do not use `--profile init` for daily startup, or initialization SQL may run again.
- Backend container is intentionally removed from compose; backend runs in IDEA locally.

## 4. Start Backend in IDEA
- Open `AI-shop` in IDEA.
- Run main class:
  - `com.root.aishopback.AIshopBackApplication`
- Default port: `8080`

## 5. Optional Frontend Local Start
Run in `AI-shop-front/front-shop`:

```powershell
npm install
npm run serve
```

Default URLs:
- Frontend: `http://localhost:8081`
- Backend API: `http://localhost:8080/api`

## 6. Quick Verification
```powershell
Invoke-WebRequest -UseBasicParsing "http://localhost:8080/api/products?page=1&size=5"
Invoke-WebRequest -UseBasicParsing "http://localhost:8080/api/products/categories"
```

## 7. Common Issues
- Port conflict: check `8080/8081/5432/6379/9201/19530`.
- Login/auth issues: check `aishop-redis`.
- Search issues: check `aishop-elasticsearch` and `aishop-milvus`.
- DB data mismatch: ensure `aishop-postgres` uses expected docker volume.

## 8. Stop Infrastructure
```powershell
docker compose down
```

## 9. Commit and Push Backend Code
Run in backend repo (`AI-shop`):

```powershell
git status
git add -A
git commit -m "docs: update collaboration startup and git workflow"
git pull --rebase origin master
git push origin master
```

## 10. Commit and Push Frontend Code (if needed)
Run in frontend repo (`AI-shop-front`):

```powershell
git status
git add -A
git commit -m "feat: update frontend"
git pull --rebase origin master
git push origin master
```
