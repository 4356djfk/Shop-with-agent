# Docker 部署与快速启动（AI-shop）

本文档用于新机器从 0 到可运行的最短步骤。仓库拉下来后，按下面执行即可。

## 1. 前置要求

- 安装并启动 Docker Desktop（Windows/macOS）或 Docker Engine（Linux）
- 可访问你使用的模型服务地址（OpenAI/兼容接口）

## 2. 拉取代码

```bash
git clone https://github.com/4356djfk/Shop-with-agent.git
cd Shop-with-agent
```

## 3. 配置环境变量

项目不会提交真实 `.env`（避免泄漏密钥）。请手动生成：

```bash
cp .env.example .env
```

Windows PowerShell 可用：

```powershell
Copy-Item .env.example .env
```

至少要补齐这些关键项：

- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `OPENAI_CHAT_API_KEY`
- `OPENAI_CHAT_BASE_URL`
- `AGENT_OPENAI_API_BASE`
- `AGENT_OPENAI_API_KEY`
- `AGENT_MODEL_NAME`

如果你只使用一套兼容 OpenAI 的地址/Key，以上 chat/agent 相关字段可填同一套。

## 4. 一键启动（推荐）

在项目根目录执行：

```bash
docker compose up -d --build
```

首次启动会拉取镜像并构建，时间会长一些。

## 5. 服务检查

- 后端（Spring Boot）：`http://127.0.0.1:8080`
- Agent 服务：`http://127.0.0.1:5000/health`

快速检查 Agent：

```bash
curl http://127.0.0.1:5000/health
```

预期返回：

```json
{"status":"ok","message":"Service is running"}
```

## 6. 常用命令

重建并重启某个服务（例如 agent）：

```bash
docker compose up -d --build aishop-agent
```

查看日志：

```bash
docker compose logs -f aishop-agent
docker compose logs -f aishop-back
```

停止所有服务：

```bash
docker compose down
```

## 7. 常见问题

### Q1: 拉下来后直接跑不起来？
通常是 `.env` 未配置完整，或模型接口地址/密钥不可用。优先检查第 3 步关键项。

### Q2: Agent 返回超时/500？
先看 `aishop-agent` 日志，常见是上游模型服务限流、网络抖动或参数错误。

### Q3: 明明有商品却搜不到？
当前已加入关键词回退检索逻辑（组合词失败会自动拆词重搜）。若仍有漏召回，可继续优化检索词和分词规则。

---

如果你只想“拉代码后立刻跑”：

```bash
git clone https://github.com/4356djfk/Shop-with-agent.git
cd Shop-with-agent
cp .env.example .env
# 编辑 .env 补齐 key/base_url
docker compose up -d --build
```
