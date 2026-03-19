"""
购物车相关工具
包含购物车查询、添加、删除、修改等功能
"""
from typing import Optional
from langchain.tools import tool, ToolRuntime
from coze_coding_utils.runtime_ctx.context import new_context
from tools.api_client import get_api_client
import json


@tool
def get_cart(
    user_id: int,
    runtime: ToolRuntime = None
) -> str:
    """
    获取用户购物车
    
    Args:
        user_id: 用户ID
        
    Returns:
        购物车内容的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_cart")
    client = get_api_client()
    
    try:
        response = client.get(f"/cart/{user_id}")
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取购物车失败: {str(e)}",
            "cart": None
        }, ensure_ascii=False)


@tool
def add_to_cart(
    user_id: int,
    product_id: int,
    quantity: int = 1,
    runtime: ToolRuntime = None
) -> str:
    """
    添加商品到购物车
    
    Args:
        user_id: 用户ID
        product_id: 商品ID
        quantity: 数量（默认1）
        
    Returns:
        操作结果的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="add_to_cart")
    client = get_api_client()
    
    try:
        response = client.post("/cart/add", data={
            "user_id": user_id,
            "product_id": product_id,
            "quantity": quantity
        })
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"添加到购物车失败: {str(e)}",
            "success": False
        }, ensure_ascii=False)


@tool
def update_cart_item(
    user_id: int,
    product_id: int,
    quantity: int,
    runtime: ToolRuntime = None
) -> str:
    """
    更新购物车商品数量
    
    Args:
        user_id: 用户ID
        product_id: 商品ID
        quantity: 新的数量（设为0则删除）
        
    Returns:
        操作结果的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="update_cart_item")
    client = get_api_client()
    
    try:
        response = client.put("/cart/update", data={
            "user_id": user_id,
            "product_id": product_id,
            "quantity": quantity
        })
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"更新购物车失败: {str(e)}",
            "success": False
        }, ensure_ascii=False)


@tool
def remove_from_cart(
    user_id: int,
    product_id: int,
    runtime: ToolRuntime = None
) -> str:
    """
    从购物车删除商品
    
    Args:
        user_id: 用户ID
        product_id: 商品ID
        
    Returns:
        操作结果的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="remove_from_cart")
    client = get_api_client()
    
    try:
        response = client.delete(f"/cart/{user_id}/item/{product_id}")
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"从购物车删除失败: {str(e)}",
            "success": False
        }, ensure_ascii=False)


@tool
def clear_cart(
    user_id: int,
    runtime: ToolRuntime = None
) -> str:
    """
    清空购物车
    
    Args:
        user_id: 用户ID
        
    Returns:
        操作结果的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="clear_cart")
    client = get_api_client()
    
    try:
        response = client.delete(f"/cart/{user_id}/clear")
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"清空购物车失败: {str(e)}",
            "success": False
        }, ensure_ascii=False)


@tool
def select_cart_items(
    user_id: int,
    product_ids: list,
    selected: bool = True,
    runtime: ToolRuntime = None
) -> str:
    """
    选择/取消选择购物车商品
    
    Args:
        user_id: 用户ID
        product_ids: 商品ID列表
        selected: 是否选中（True为选中，False为取消选中）
        
    Returns:
        操作结果的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="select_cart_items")
    client = get_api_client()
    
    try:
        response = client.put("/cart/select", data={
            "user_id": user_id,
            "product_ids": product_ids,
            "selected": selected
        })
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"选择购物车商品失败: {str(e)}",
            "success": False
        }, ensure_ascii=False)
