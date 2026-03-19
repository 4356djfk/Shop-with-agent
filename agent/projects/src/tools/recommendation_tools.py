"""
商品推荐相关工具
基于用户历史订单、聊天记录和商品相似度的智能推荐
"""
from typing import Optional, List
from langchain.tools import tool, ToolRuntime
from coze_coding_utils.runtime_ctx.context import new_context
from tools.api_client import get_api_client
import json


def _norm_text(value) -> str:
    if value is None:
        return ""
    return str(value).strip().lower().replace(" ", "")


def _safe_num(value) -> float:
    try:
        return float(value)
    except Exception:
        return -1.0


def _pick_better_product(old: dict, new: dict) -> dict:
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


def _dedupe_product_list(items):
    if not isinstance(items, list):
        return []
    merged = {}
    order = []
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


def _normalize_response(response: dict) -> dict:
    if not isinstance(response, dict):
        return response
    data = response.get("data")
    if isinstance(data, list):
        deduped = _dedupe_product_list(data)
        response["data"] = deduped
        if "total" in response:
            response["total"] = len(deduped)
    return response


@tool
def get_personalized_recommendations(
    user_id: int,
    limit: int = 10,
    runtime: ToolRuntime = None
) -> str:
    """
    获取个性化商品推荐（基于用户历史订单和聊天记录）
    
    Args:
        user_id: 用户ID
        limit: 推荐商品数量（默认10个）
        
    Returns:
        推荐商品列表的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_personalized_recommendations")
    client = get_api_client()
    
    try:
        response = client.get(f"/recommendations/{user_id}", params={"limit": limit})
        response = _normalize_response(response)
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取个性化推荐失败: {str(e)}",
            "recommendations": []
        }, ensure_ascii=False)


@tool
def get_similar_products(
    product_id: int,
    limit: int = 5,
    runtime: ToolRuntime = None
) -> str:
    """
    获取相似商品推荐（基于商品向量相似度）
    
    Args:
        product_id: 商品ID
        limit: 推荐商品数量（默认5个）
        
    Returns:
        相似商品列表的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_similar_products")
    client = get_api_client()
    
    try:
        response = client.get(f"/products/{product_id}/similar", params={"limit": limit})
        response = _normalize_response(response)
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取相似商品失败: {str(e)}",
            "similar_products": []
        }, ensure_ascii=False)


@tool
def get_hot_products(
    category: Optional[str] = None,
    limit: int = 10,
    runtime: ToolRuntime = None
) -> str:
    """
    获取热门商品
    
    Args:
        category: 商品分类（可选）
        limit: 商品数量（默认10个）
        
    Returns:
        热门商品列表的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_hot_products")
    client = get_api_client()
    
    params = {"limit": limit}
    if category:
        params["category"] = category
    
    try:
        response = client.get("/products/hot", params=params)
        response = _normalize_response(response)
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取热门商品失败: {str(e)}",
            "products": []
        }, ensure_ascii=False)


@tool
def get_new_arrivals(
    category: Optional[str] = None,
    limit: int = 10,
    runtime: ToolRuntime = None
) -> str:
    """
    获取新品上架
    
    Args:
        category: 商品分类（可选）
        limit: 商品数量（默认10个）
        
    Returns:
        新品列表的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_new_arrivals")
    client = get_api_client()
    
    params = {"limit": limit}
    if category:
        params["category"] = category
    
    try:
        response = client.get("/products/new", params=params)
        response = _normalize_response(response)
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取新品失败: {str(e)}",
            "products": []
        }, ensure_ascii=False)


@tool
def get_deal_products(
    limit: int = 10,
    runtime: ToolRuntime = None
) -> str:
    """
    获取优惠商品（折扣力度大的商品）
    
    Args:
        limit: 商品数量（默认10个）
        
    Returns:
        优惠商品列表的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_deal_products")
    client = get_api_client()
    
    try:
        response = client.get("/products/deals", params={"limit": limit})
        response = _normalize_response(response)
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取优惠商品失败: {str(e)}",
            "products": []
        }, ensure_ascii=False)


@tool
def get_bought_together(
    product_id: int,
    limit: int = 5,
    runtime: ToolRuntime = None
) -> str:
    """
    获取经常一起购买的商品
    
    Args:
        product_id: 商品ID
        limit: 商品数量（默认5个）
        
    Returns:
        经常一起购买的商品列表的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_bought_together")
    client = get_api_client()
    
    try:
        response = client.get(f"/products/{product_id}/bought-together", params={"limit": limit})
        response = _normalize_response(response)
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取相关商品失败: {str(e)}",
            "products": []
        }, ensure_ascii=False)
