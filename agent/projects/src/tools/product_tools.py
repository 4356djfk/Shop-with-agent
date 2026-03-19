"""
商品相关工具：
包含商品查询、商品详情、商品对比等功能。
"""

from typing import Optional, List, Dict, Any
import json
import re

from langchain.tools import tool, ToolRuntime
from coze_coding_utils.runtime_ctx.context import new_context

from tools.api_client import get_api_client


def _norm_text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip().lower().replace(" ", "")


def _safe_num(value: Any) -> float:
    try:
        return float(value)
    except Exception:
        return -1.0


def _pick_better_product(old: Dict[str, Any], new: Dict[str, Any]) -> Dict[str, Any]:
    old_stock = int(old.get("stock") or 0)
    new_stock = int(new.get("stock") or 0)
    if new_stock > old_stock:
        return new
    if new_stock < old_stock:
        return old
    old_sales = int(old.get("sales") or 0)
    new_sales = int(new.get("sales") or 0)
    if new_sales > old_sales:
        return new
    return old


def _dedupe_product_list(items: Any) -> List[Dict[str, Any]]:
    if not isinstance(items, list):
        return []
    merged: Dict[str, Dict[str, Any]] = {}
    order: List[str] = []
    for row in items:
        if not isinstance(row, dict):
            continue
        key = (
            _norm_text(row.get("name")),
            _norm_text(row.get("brand")),
            _safe_num(row.get("price")),
        )
        key_s = f"{key[0]}|{key[1]}|{key[2]}"
        if key_s not in merged:
            merged[key_s] = row
            order.append(key_s)
            continue
        merged[key_s] = _pick_better_product(merged[key_s], row)
    return [merged[k] for k in order]


def _normalize_products_response(response: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(response, dict):
        return response
    data = response.get("data")
    if isinstance(data, list):
        deduped = _dedupe_product_list(data)
        response["data"] = deduped
        if "total" in response:
            response["total"] = len(deduped)
    return response


def _extract_products(response: Dict[str, Any]) -> List[Dict[str, Any]]:
    if not isinstance(response, dict):
        return []
    data = response.get("data")
    if isinstance(data, list):
        return [x for x in data if isinstance(x, dict)]
    return []


def _build_keyword_fallbacks(keyword: str) -> List[str]:
    text = (keyword or "").strip()
    if not text:
        return []

    stop_terms = {
        "零食", "商品", "推荐", "我要", "我想买", "买", "一下", "给我", "来点",
        "休闲", "食品", "好吃", "热销", "的", "了",
    }

    pieces = [t.strip() for t in re.split(r"[\s,，/|+]+", text) if t.strip()]
    pieces.extend([t.strip() for t in re.split(r"[的了和与及]", text) if t.strip()])

    uniq: List[str] = []
    seen = set()
    for t in pieces:
        if len(t) <= 1:
            continue
        if t in stop_terms:
            continue
        if t in seen:
            continue
        seen.add(t)
        uniq.append(t)

    # 优先更具体的词
    uniq.sort(key=len, reverse=True)
    return uniq[:5]


def _expand_keyword_variants(keyword: str) -> List[str]:
    text = (keyword or "").strip().lower()
    if not text:
        return []
    mapping = {
        "snacks": ["零食", "辣条", "魔芋", "豆干", "卤味"],
        "snack": ["零食", "辣条", "魔芋", "豆干", "卤味"],
        "headphones": ["耳机", "蓝牙耳机", "降噪耳机"],
        "headphone": ["耳机", "蓝牙耳机", "降噪耳机"],
        "shoes": ["鞋", "鞋子", "运动鞋", "跑鞋"],
        "shoe": ["鞋", "鞋子", "运动鞋", "跑鞋"],
        "dress": ["连衣裙", "裙子"],
        "shirt": ["衬衫"],
        "shirts": ["衬衫"],
    }
    out: List[str] = [keyword] if keyword else []
    if text in mapping:
        out.extend(mapping[text])
    # keyword 本身的拆词候选也纳入变体池
    out.extend(_build_keyword_fallbacks(keyword or ""))
    seen = set()
    deduped: List[str] = []
    for k in out:
        v = (k or "").strip()
        if not v:
            continue
        lk = v.lower()
        if lk in seen:
            continue
        seen.add(lk)
        deduped.append(v)
    return deduped[:8]


@tool
def search_products(
    keyword: Optional[str] = None,
    category: Optional[str] = None,
    brand: Optional[str] = None,
    min_price: Optional[float] = None,
    max_price: Optional[float] = None,
    sort_by: Optional[str] = None,
    page: int = 1,
    page_size: int = 10,
    runtime: ToolRuntime = None,
) -> str:
    """
    搜索商品。

    Args:
        keyword: 搜索关键词（商品名称、描述等）
        category: 商品分类
        brand: 品牌
        min_price: 最低价格
        max_price: 最高价格
        sort_by: 排序方式（price_asc, price_desc, sales_desc, rating_desc）
        page: 页码（从1开始）
        page_size: 每页数量

    Returns:
        商品列表 JSON 字符串
    """
    _ = runtime.context if runtime else new_context(method="search_products")
    client = get_api_client()

    params: Dict[str, Any] = {
        "page": page,
        "page_size": page_size,
    }

    if keyword:
        params["keyword"] = keyword
    if category:
        params["category"] = category
    if brand:
        params["brand"] = brand
    if min_price is not None:
        params["min_price"] = min_price
    if max_price is not None:
        params["max_price"] = max_price
    if sort_by:
        params["sort_by"] = sort_by

    try:
        response = client.get("/products", params=params)
        response = _normalize_products_response(response)
        primary_items = _extract_products(response)

        attempted_keywords = []
        fallback_used = False

        # 渐进式回退：组合词命不中或命中太少时自动拆词/同义词搜索
        if keyword and len(primary_items) < min(3, page_size):
            merged_items: List[Dict[str, Any]] = []
            merged_items.extend(primary_items)
            for kw in _expand_keyword_variants(keyword):
                if kw == keyword:
                    attempted_keywords.append(kw)
                    continue
                sub_params = dict(params)
                sub_params["keyword"] = kw
                attempted_keywords.append(kw)
                sub_resp = client.get("/products", params=sub_params)
                sub_resp = _normalize_products_response(sub_resp)
                sub_items = _extract_products(sub_resp)
                if sub_items:
                    fallback_used = True
                    merged_items.extend(sub_items)

            if merged_items:
                deduped = _dedupe_product_list(merged_items)[:page_size]
                response["data"] = deduped
                response["total"] = len(deduped)
        elif keyword:
            attempted_keywords.append(keyword)

        response["search_meta"] = {
            "fallback_used": fallback_used,
            "attempted_keywords": [k for k in attempted_keywords if k],
        }
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps(
            {
                "error": f"搜索商品失败: {str(e)}",
                "products": [],
            },
            ensure_ascii=False,
        )


@tool
def get_product_detail(
    product_id: int,
    runtime: ToolRuntime = None,
) -> str:
    """
    获取商品详情。

    Args:
        product_id: 商品ID

    Returns:
        商品详情 JSON 字符串
    """
    _ = runtime.context if runtime else new_context(method="get_product_detail")
    client = get_api_client()

    try:
        response = client.get(f"/products/{product_id}")
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps(
            {
                "error": f"获取商品详情失败: {str(e)}",
                "product": None,
            },
            ensure_ascii=False,
        )


@tool
def compare_products(
    product_ids: List[int],
    runtime: ToolRuntime = None,
) -> str:
    """
    比较多个商品。

    Args:
        product_ids: 商品ID列表（最多5个）

    Returns:
        商品对比结果 JSON 字符串
    """
    _ = runtime.context if runtime else new_context(method="compare_products")
    client = get_api_client()

    if len(product_ids) > 5:
        return json.dumps(
            {
                "error": "最多只能比较5个商品",
                "comparison": None,
            },
            ensure_ascii=False,
        )

    try:
        products = []
        for pid in product_ids:
            response = client.get(f"/products/{pid}")
            if "data" in response:
                products.append(response["data"])

        comparison = {
            "products": products,
            "comparison_fields": [
                "name", "brand", "price", "original_price",
                "rating", "sales", "stock", "category",
            ],
        }

        return json.dumps(comparison, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps(
            {
                "error": f"商品对比失败: {str(e)}",
                "comparison": None,
            },
            ensure_ascii=False,
        )


@tool
def get_categories(
    runtime: ToolRuntime = None,
) -> str:
    """
    获取所有商品分类。

    Returns:
        分类列表 JSON 字符串
    """
    _ = runtime.context if runtime else new_context(method="get_categories")
    client = get_api_client()

    try:
        response = client.get("/products/categories")
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps(
            {
                "error": f"获取分类失败: {str(e)}",
                "categories": [],
            },
            ensure_ascii=False,
        )


@tool
def get_product_reviews(
    product_id: int,
    page: int = 1,
    page_size: int = 10,
    runtime: ToolRuntime = None,
) -> str:
    """
    获取商品评价。

    Args:
        product_id: 商品ID
        page: 页码
        page_size: 每页数量

    Returns:
        商品评价列表 JSON 字符串
    """
    _ = runtime.context if runtime else new_context(method="get_product_reviews")
    client = get_api_client()

    try:
        response = client.get(
            f"/products/{product_id}/reviews",
            params={
                "page": page,
                "page_size": page_size,
            },
        )
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps(
            {
                "error": f"获取商品评价失败: {str(e)}",
                "reviews": [],
            },
            ensure_ascii=False,
        )
