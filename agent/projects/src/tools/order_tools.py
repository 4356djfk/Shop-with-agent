"""
订单相关工具
包含订单查询、订单状态监控、历史订单分析等功能
"""
from typing import Optional, List
from langchain.tools import tool, ToolRuntime
from coze_coding_utils.runtime_ctx.context import new_context
from tools.api_client import get_api_client
import json


@tool
def get_order_detail(
    order_id: int,
    runtime: ToolRuntime = None
) -> str:
    """
    获取订单详情
    
    Args:
        order_id: 订单ID
        
    Returns:
        订单详情的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_order_detail")
    client = get_api_client()
    
    try:
        response = client.get(f"/orders/{order_id}")
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取订单详情失败: {str(e)}",
            "order": None
        }, ensure_ascii=False)


@tool
def get_user_orders(
    user_id: int,
    order_status: Optional[str] = None,
    page: int = 1,
    page_size: int = 10,
    runtime: ToolRuntime = None
) -> str:
    """
    获取用户订单列表
    
    Args:
        user_id: 用户ID
        order_status: 订单状态筛选（可选）
                     - pending_payment: 待支付
                     - paid: 已支付
                     - shipped: 已发货
                     - delivered: 已送达
                     - cancelled: 已取消
        page: 页码
        page_size: 每页数量
        
    Returns:
        订单列表的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_user_orders")
    client = get_api_client()
    
    params = {
        "user_id": user_id,
        "page": page,
        "page_size": page_size
    }
    
    if order_status:
        params["order_status"] = order_status
    
    try:
        response = client.get("/orders", params=params)
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取订单列表失败: {str(e)}",
            "orders": []
        }, ensure_ascii=False)


@tool
def check_order_status(
    order_id: int,
    runtime: ToolRuntime = None
) -> str:
    """
    检查订单状态
    
    Args:
        order_id: 订单ID
        
    Returns:
        订单状态的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="check_order_status")
    client = get_api_client()
    
    try:
        response = client.get(f"/orders/{order_id}/status")
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"检查订单状态失败: {str(e)}",
            "status": None
        }, ensure_ascii=False)


@tool
def analyze_user_purchase_history(
    user_id: int,
    runtime: ToolRuntime = None
) -> str:
    """
    分析用户购买历史
    
    Args:
        user_id: 用户ID
        
    Returns:
        购买历史分析的JSON字符串，包含：
        - 总订单数
        - 总消费金额
        - 常购商品类别
        - 购买频率
        - 最常购买的商品
    """
    ctx = runtime.context if runtime else new_context(method="analyze_user_purchase_history")
    client = get_api_client()
    
    try:
        # 获取用户所有订单
        response = client.get("/orders", params={
            "user_id": user_id,
            "page": 1,
            "page_size": 100  # 获取足够多的订单进行分析
        })
        
        if "error" in response:
            return json.dumps(response, ensure_ascii=False)
        
        orders = response.get("data", [])
        
        # 分析数据
        total_orders = len(orders)
        total_amount = sum(float(order.get("total_amount", 0)) for order in orders)
        
        # 统计购买的商品类别
        category_count = {}
        product_count = {}
        
        for order in orders:
            items = order.get("items", [])
            for item in items:
                # 统计类别
                category = item.get("category", "未分类")
                category_count[category] = category_count.get(category, 0) + 1
                
                # 统计商品
                product_name = item.get("product_name", "")
                product_count[product_name] = product_count.get(product_name, 0) + 1
        
        # 找出最常购买的类别和商品
        top_categories = sorted(category_count.items(), key=lambda x: x[1], reverse=True)[:5]
        top_products = sorted(product_count.items(), key=lambda x: x[1], reverse=True)[:5]
        
        analysis = {
            "user_id": user_id,
            "total_orders": total_orders,
            "total_amount": total_amount,
            "average_order_amount": total_amount / total_orders if total_orders > 0 else 0,
            "top_categories": [
                {"category": cat, "count": count}
                for cat, count in top_categories
            ],
            "top_products": [
                {"product": prod, "count": count}
                for prod, count in top_products
            ]
        }
        
        return json.dumps(analysis, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"分析购买历史失败: {str(e)}",
            "analysis": None
        }, ensure_ascii=False)


@tool
def track_order(
    order_id: int,
    runtime: ToolRuntime = None
) -> str:
    """
    物流追踪
    
    Args:
        order_id: 订单ID
        
    Returns:
        物流信息的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="track_order")
    client = get_api_client()
    
    try:
        response = client.get(f"/orders/{order_id}/tracking")
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取物流信息失败: {str(e)}",
            "tracking": None
        }, ensure_ascii=False)
