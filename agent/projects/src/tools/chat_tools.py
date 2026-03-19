"""
聊天记录相关工具
包含聊天历史查询、用户需求分析等功能
"""
from typing import Optional
from langchain.tools import tool, ToolRuntime
from coze_coding_utils.runtime_ctx.context import new_context
from tools.api_client import get_api_client
import json


@tool
def get_chat_history(
    user_id: int,
    limit: int = 50,
    runtime: ToolRuntime = None
) -> str:
    """
    获取用户聊天历史
    
    Args:
        user_id: 用户ID
        limit: 获取的记录数量（默认50条）
        
    Returns:
        聊天历史的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_chat_history")
    client = get_api_client()
    
    try:
        response = client.get(f"/chat/{user_id}/history", params={"limit": limit})
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取聊天历史失败: {str(e)}",
            "messages": []
        }, ensure_ascii=False)


@tool
def save_chat_message(
    user_id: int,
    role: str,
    content: str,
    products_json: Optional[str] = None,
    runtime: ToolRuntime = None
) -> str:
    """
    保存聊天消息
    
    Args:
        user_id: 用户ID
        role: 角色（user 或 assistant）
        content: 消息内容
        products_json: 关联的商品信息（JSON字符串）
        
    Returns:
        操作结果的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="save_chat_message")
    client = get_api_client()
    
    try:
        data = {
            "user_id": user_id,
            "role": role,
            "content": content
        }
        
        if products_json:
            data["products_json"] = products_json
        
        response = client.post("/chat/save", data=data)
        return json.dumps(response, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"保存聊天消息失败: {str(e)}",
            "success": False
        }, ensure_ascii=False)


@tool
def analyze_user_interests(
    user_id: int,
    runtime: ToolRuntime = None
) -> str:
    """
    分析用户兴趣和偏好（基于聊天记录）
    
    Args:
        user_id: 用户ID
        
    Returns:
        用户兴趣分析的JSON字符串，包含：
        - 常提及的商品类别
        - 价格敏感度
        - 关注的商品特性
        - 常见问题类型
    """
    ctx = runtime.context if runtime else new_context(method="analyze_user_interests")
    client = get_api_client()
    
    try:
        # 获取聊天历史
        response = client.get(f"/chat/{user_id}/history", params={"limit": 100})
        
        if "error" in response:
            return json.dumps(response, ensure_ascii=False)
        
        messages = response.get("data", [])
        
        # 分析用户消息
        user_messages = [msg for msg in messages if msg.get("role") == "user"]
        user_text = " ".join([msg.get("content", "") for msg in user_messages])
        
        # 这里可以进行更复杂的NLP分析
        # 简化版本：提取关键词和统计信息
        
        analysis = {
            "user_id": user_id,
            "total_messages": len(messages),
            "user_messages": len(user_messages),
            "sample_keywords": [],  # 可以通过NLP提取关键词
            "interests_summary": f"用户共发送了{len(user_messages)}条消息，表现出对商品的咨询兴趣"
        }
        
        return json.dumps(analysis, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"分析用户兴趣失败: {str(e)}",
            "analysis": None
        }, ensure_ascii=False)


@tool
def get_user_context(
    user_id: int,
    runtime: ToolRuntime = None
) -> str:
    """
    获取用户上下文信息（聊天历史 + 购买历史 + 购物车状态）
    
    Args:
        user_id: 用户ID
        
    Returns:
        用户上下文的JSON字符串
    """
    ctx = runtime.context if runtime else new_context(method="get_user_context")
    client = get_api_client()
    
    try:
        # 获取聊天历史
        chat_response = client.get(f"/chat/{user_id}/history", params={"limit": 20})
        chat_history = chat_response.get("data", []) if "data" in chat_response else []
        
        # 获取购物车
        cart_response = client.get(f"/cart/{user_id}")
        cart = cart_response.get("data", {}) if "data" in cart_response else {}
        
        # 获取最近订单
        orders_response = client.get("/orders", params={
            "user_id": user_id,
            "page": 1,
            "page_size": 5
        })
        recent_orders = orders_response.get("data", []) if "data" in orders_response else []
        
        context = {
            "user_id": user_id,
            "chat_history": chat_history,
            "cart": cart,
            "recent_orders": recent_orders
        }
        
        return json.dumps(context, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({
            "error": f"获取用户上下文失败: {str(e)}",
            "context": None
        }, ensure_ascii=False)
