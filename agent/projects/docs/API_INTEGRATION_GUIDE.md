# 商城导购智能体 - 使用指南

## 📋 概述

这是一个基于 LangGraph 构建的智能电商导购 Agent，能够：
- 🔍 商品搜索与推荐
- 🛒 购物车管理
- 📦 订单查询与监控
- 💬 用户需求分析与个性化服务

## 🚀 快速开始

### 1. 环境配置

复制环境变量示例文件并配置：

```bash
cp .env.example .env
```

编辑 `.env` 文件，配置以下关键参数：

```env
# Spring Boot 后端 API 地址（必填）
SPRING_BOOT_API_URL=http://localhost:8080/api

# API 请求超时时间（可选，默认30秒）
API_TIMEOUT=30

# API Key（如果后端需要认证）
API_KEY=your_api_key_here
```

### 2. 启动 Agent

Agent 已经通过 `src/agents/agent.py` 中的 `build_agent()` 方法集成。

### 3. 使用示例

```python
from agents.agent import build_agent

# 构建 Agent
agent = build_agent()

# 调用 Agent
response = agent.invoke({
    "messages": [{"role": "user", "content": "我想买一款笔记本电脑，预算5000-8000元"}]
})

print(response)
```

## 🔌 Spring Boot 后端 API 要求

为了让 Agent 正常工作，您的 Spring Boot 后端需要提供以下 API 端点：

### 商品相关 API

#### 1. 搜索商品
```
GET /api/products
参数：
  - keyword: 搜索关键词
  - category: 商品分类
  - brand: 品牌
  - min_price: 最低价格
  - max_price: 最高价格
  - sort_by: 排序方式 (price_asc, price_desc, sales_desc, rating_desc)
  - page: 页码
  - page_size: 每页数量

返回格式：
{
  "data": [
    {
      "id": 1,
      "name": "商品名称",
      "brand": "品牌",
      "price": 5999.00,
      "original_price": 6999.00,
      "stock": 100,
      "sales": 500,
      "rating": 4.8,
      "image_url": "https://...",
      "category": "分类"
    }
  ],
  "total": 100,
  "page": 1,
  "page_size": 10
}
```

#### 2. 商品详情
```
GET /api/products/{id}

返回格式：
{
  "data": {
    "id": 1,
    "name": "商品名称",
    "description": "商品描述",
    "brand": "品牌",
    "price": 5999.00,
    "stock": 100,
    ...
  }
}
```

#### 3. 商品分类
```
GET /api/products/categories

返回格式：
{
  "data": [
    {"name": "分类1", "count": 100},
    {"name": "分类2", "count": 80}
  ]
}
```

#### 4. 商品评价
```
GET /api/products/{id}/reviews
参数：
  - page: 页码
  - page_size: 每页数量

返回格式：
{
  "data": [
    {
      "id": 1,
      "user_id": 123,
      "rating": 5,
      "content": "评价内容",
      "created_at": "2024-01-01"
    }
  ]
}
```

### 购物车相关 API

#### 1. 获取购物车
```
GET /api/cart/{user_id}

返回格式：
{
  "data": {
    "user_id": 123,
    "items": [
      {
        "product_id": 1,
        "product_name": "商品名称",
        "quantity": 2,
        "unit_price": 5999.00,
        "total_price": 11998.00,
        "selected": true
      }
    ],
    "total_amount": 11998.00
  }
}
```

#### 2. 添加到购物车
```
POST /api/cart/add
Body:
{
  "user_id": 123,
  "product_id": 1,
  "quantity": 1
}

返回格式：
{
  "success": true,
  "message": "添加成功"
}
```

#### 3. 更新购物车商品
```
PUT /api/cart/update
Body:
{
  "user_id": 123,
  "product_id": 1,
  "quantity": 2
}

返回格式：
{
  "success": true,
  "message": "更新成功"
}
```

#### 4. 删除购物车商品
```
DELETE /api/cart/{user_id}/item/{product_id}

返回格式：
{
  "success": true,
  "message": "删除成功"
}
```

#### 5. 清空购物车
```
DELETE /api/cart/{user_id}/clear

返回格式：
{
  "success": true,
  "message": "清空成功"
}
```

#### 6. 选择/取消选择商品
```
PUT /api/cart/select
Body:
{
  "user_id": 123,
  "product_ids": [1, 2, 3],
  "selected": true
}

返回格式：
{
  "success": true,
  "message": "操作成功"
}
```

### 订单相关 API

#### 1. 获取订单列表
```
GET /api/orders
参数：
  - user_id: 用户ID
  - order_status: 订单状态（可选）
  - page: 页码
  - page_size: 每页数量

返回格式：
{
  "data": [
    {
      "id": 1,
      "order_no": "ORD202401010001",
      "user_id": 123,
      "order_status": "paid",
      "total_amount": 11998.00,
      "created_at": "2024-01-01",
      "items": [...]
    }
  ]
}
```

#### 2. 获取订单详情
```
GET /api/orders/{id}

返回格式：
{
  "data": {
    "id": 1,
    "order_no": "ORD202401010001",
    "user_id": 123,
    "order_status": "paid",
    "total_amount": 11998.00,
    "items": [...],
    "consignee_name": "收货人",
    "consignee_phone": "13800138000",
    "consignee_address": "收货地址"
  }
}
```

#### 3. 检查订单状态
```
GET /api/orders/{id}/status

返回格式：
{
  "data": {
    "order_id": 1,
    "order_status": "shipped",
    "payment_status": "paid"
  }
}
```

#### 4. 物流追踪
```
GET /api/orders/{id}/tracking

返回格式：
{
  "data": {
    "order_id": 1,
    "tracking_no": "SF1234567890",
    "status": "运输中",
    "traces": [
      {
        "time": "2024-01-02 10:00:00",
        "location": "北京",
        "status": "已发货"
      }
    ]
  }
}
```

### 聊天记录相关 API

#### 1. 获取聊天历史
```
GET /api/chat/{user_id}/history
参数：
  - limit: 记录数量

返回格式：
{
  "data": [
    {
      "id": 1,
      "user_id": 123,
      "role": "user",
      "content": "我想买笔记本",
      "created_at": "2024-01-01"
    }
  ]
}
```

#### 2. 保存聊天消息
```
POST /api/chat/save
Body:
{
  "user_id": 123,
  "role": "user",
  "content": "消息内容",
  "products_json": "{\"product_ids\": [1, 2]}"
}

返回格式：
{
  "success": true,
  "message": "保存成功"
}
```

### 推荐相关 API

#### 1. 个性化推荐
```
GET /api/recommendations/{user_id}
参数：
  - limit: 推荐数量

返回格式：
{
  "data": [
    {
      "id": 1,
      "name": "推荐商品",
      "price": 5999.00,
      "recommend_reason": "基于您的购买历史"
    }
  ]
}
```

#### 2. 相似商品
```
GET /api/products/{id}/similar
参数：
  - limit: 推荐数量

返回格式：
{
  "data": [...]
}
```

#### 3. 热门商品
```
GET /api/products/hot
参数：
  - category: 分类（可选）
  - limit: 数量

返回格式：
{
  "data": [...]
}
```

#### 4. 新品上架
```
GET /api/products/new
参数：
  - category: 分类（可选）
  - limit: 数量

返回格式：
{
  "data": [...]
}
```

#### 5. 优惠商品
```
GET /api/products/deals
参数：
  - limit: 数量

返回格式：
{
  "data": [...]
}
```

#### 6. 经常一起购买
```
GET /api/products/{id}/bought-together
参数：
  - limit: 数量

返回格式：
{
  "data": [...]
}
```

## 🎯 功能特性

### 1. 智能商品搜索
- 支持关键词、分类、品牌、价格区间等多维度搜索
- 自动理解用户意图并提取搜索参数

### 2. 购物车管理
- 添加、删除、修改购物车商品
- 查看购物车详情
- 批量选择商品

### 3. 订单监控
- 查询订单状态
- 物流追踪
- 分析购买历史

### 4. 个性化推荐
- 基于历史订单推荐
- 基于聊天记录理解用户偏好
- 热门商品、新品、优惠商品推荐

### 5. 短期记忆
- 保留最近 20 轮对话（40 条消息）
- 自动维护对话上下文

## 🛠️ 技术栈

- **LangGraph**: Agent 框架
- **LangChain**: 工具集成
- **ChatOpenAI**: LLM 模型接口
- **PostgreSQL**: 对话记忆存储（可选）
- **Milvus**: 向量检索（通过 Spring Boot API）
- **Redis**: 缓存（通过 Spring Boot API）

## 📝 注意事项

1. **API 认证**: 如果您的 Spring Boot 后端需要认证，请在 `.env` 文件中配置 `API_KEY`

2. **数据库配置**: Agent 使用 PostgreSQL 存储对话记忆（可选，会自动退化到内存存储）

3. **错误处理**: 所有工具都包含完善的错误处理机制，当后端服务不可用时会返回友好的错误提示

4. **性能优化**: 
   - API 客户端使用连接池
   - 支持请求超时配置
   - 支持批量操作

## 📞 支持

如有问题，请参考以下资源：
- Agent 代码: `src/agents/agent.py`
- 工具定义: `src/tools/`
- 配置文件: `config/agent_llm_config.json`
