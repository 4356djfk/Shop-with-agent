# AI-shop Backend 协作启动说明

## 1. 项目结构
- 后端目录：`AI-shop`
- 前端目录：`AI-shop-front/front-shop`
- 当前约定：后端由 IDEA 本地启动，不使用 Docker 启动 `aishop-backend` 容器。

## 2. 启动前准备
- JDK：建议使用 JDK 22（与当前项目配置一致）。
- Maven：可直接使用项目自带 `mvnw.cmd`。
- Docker Desktop：用于启动数据库和中间件依赖。

## 3. 启动依赖服务（Docker）
在 `AI-shop` 目录执行：

```powershell
docker compose up -d postgres redis elasticsearch milvus-etcd milvus-minio milvus attu
```

确认关键容器状态：

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}"
```

建议至少确认以下容器是 `Up`：
- `aishop-postgres`
- `aishop-redis`
- `aishop-elasticsearch`
- `aishop-milvus`

说明：
- `aishop-db-init` 是一次性初始化容器，`Exited (0)` 属于正常。
- `aishop-backend` 已从 compose 中移除，避免与 IDEA 本地启动冲突。

## 4. 启动后端（IDEA）
在 IDEA 中打开 `AI-shop`，运行主类：
- `com.root.aishopback.AIshopBackApplication`

默认端口：
- `8080`

## 5. 启动前端（可选）
在 `AI-shop-front/front-shop` 目录执行：

```powershell
npm install
npm run serve
```

默认访问：
- 前端：`http://localhost:8081`
- 后端 API：`http://localhost:8080/api`

## 6. 常用检查
后端健康检查（示例）：

```powershell
Invoke-WebRequest -UseBasicParsing "http://localhost:8080/api/products?page=1&size=5"
```

分类接口检查：

```powershell
Invoke-WebRequest -UseBasicParsing "http://localhost:8080/api/products/categories"
```

## 7. 常见问题
- 端口冲突：检查 `8080/8081/5432/6379/9201/19530` 是否被占用。
- 登录/鉴权异常：确认 `aishop-redis` 正常运行。
- 商品检索异常：确认 `aishop-elasticsearch`、`aishop-milvus` 运行正常。
- 数据不对：确认 `aishop-postgres` 使用的是当前项目数据卷。

## 8. 停止依赖服务
在 `AI-shop` 目录执行：

```powershell
docker compose down
```

