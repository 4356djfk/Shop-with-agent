"""
Shopping guide agent entry.
"""

import json
import os
from typing import Annotated

from coze_coding_utils.runtime_ctx.context import default_headers
from langchain.agents import create_agent
from langchain_core.messages import AnyMessage
from langchain_openai import ChatOpenAI
from langgraph.graph import MessagesState
from langgraph.graph.message import add_messages

from storage.memory.memory_saver import get_memory_saver
from tools.cart_tools import (
    add_to_cart,
    clear_cart,
    get_cart,
    remove_from_cart,
    select_cart_items,
    update_cart_item,
)
from tools.chat_tools import (
    analyze_user_interests,
    get_chat_history,
    get_user_context,
    save_chat_message,
)
from tools.order_tools import (
    analyze_user_purchase_history,
    check_order_status,
    get_order_detail,
    get_user_orders,
    track_order,
)
from tools.product_tools import (
    compare_products,
    get_categories,
    get_product_detail,
    get_product_reviews,
    search_products,
)
from tools.knowledge_base_tools import (
    inspect_inventory_signals,
    plan_shopping_turn,
    retrieve_shopping_guidance,
)
from tools.recommendation_tools import (
    get_bought_together,
    get_deal_products,
    get_hot_products,
    get_new_arrivals,
    get_personalized_recommendations,
    get_similar_products,
)

LLM_CONFIG = "config/agent_llm_config.json"
MAX_MESSAGES = 40


def _windowed_messages(old, new):
    return add_messages(old, new)[-MAX_MESSAGES:]


class AgentState(MessagesState):
    messages: Annotated[list[AnyMessage], _windowed_messages]


def build_agent(ctx=None):
    workspace_path = os.getenv("COZE_WORKSPACE_PATH", "/workspace/projects").strip()
    config_path = os.path.join(workspace_path, LLM_CONFIG)
    with open(config_path, "r", encoding="utf-8") as f:
        cfg = json.load(f)

    api_key = os.getenv("OPENAI_API_KEY") or os.getenv("COZE_WORKLOAD_IDENTITY_API_KEY")
    base_url = os.getenv("OPENAI_API_BASE") or os.getenv("COZE_INTEGRATION_MODEL_BASE_URL")
    model_name = os.getenv("MODEL_NAME") or cfg["config"].get("model")

    llm = ChatOpenAI(
        model=model_name,
        api_key=api_key,
        base_url=base_url,
        temperature=cfg["config"].get("temperature", 0.7),
        streaming=True,
        timeout=cfg["config"].get("timeout", 600),
        extra_body={
            "thinking": {
                "type": cfg["config"].get("thinking", "enabled"),
            }
        },
        default_headers=default_headers(ctx) if ctx else {},
    )

    tools = [
        search_products,
        get_product_detail,
        compare_products,
        get_categories,
        get_product_reviews,
        plan_shopping_turn,
        retrieve_shopping_guidance,
        inspect_inventory_signals,
        get_cart,
        add_to_cart,
        update_cart_item,
        remove_from_cart,
        clear_cart,
        select_cart_items,
        get_order_detail,
        get_user_orders,
        check_order_status,
        analyze_user_purchase_history,
        track_order,
        get_chat_history,
        save_chat_message,
        analyze_user_interests,
        get_user_context,
        get_personalized_recommendations,
        get_similar_products,
        get_hot_products,
        get_new_arrivals,
        get_deal_products,
        get_bought_together,
    ]

    system_prompt = (
        "你是电商导购智能体，目标是高效成交，不是做问卷。"
        "每轮先调用 plan_shopping_turn，再决定提问或检索。"
        "当已识别品类时，禁止再问“你想买什么”。"
        "如果用户表达“都可以/按你来/随便/你决定/不用确认”，立即停止追问并直接检索推荐。"
        "每轮最多问1个问题，导购阶段默认连续追问4-5个关键问题后再推荐；禁止重复确认同一条件。"
        "在 should_search=true 时，优先调用 inspect_inventory_signals 看当前库存是否支持继续细问。"
        "在导购阶段未达到4个关键问题前，不要提前给最终推荐；若用户明确说“直接推荐/别问了”，可提前收口。"
        "只问库存中可区分的维度（askable_dimensions），不要问库里没有辨识度的条件。"
        "只要用户提供了新条件（预算/场景/颜色/口味/尺码等），就用新条件立即重搜并更新推荐。"
        "禁止输出固定菜单模板或机械编号提问。"
        "推荐输出必须给：Top1 + 2-3个备选，每个包含商品ID、价格、关键卖点、匹配理由。"
        "如果结果不足3个，也要给当前最优项并明确说明放宽哪个条件可扩大结果。"
        "全程使用自然中文，简洁，不啰嗦。"
    )

    agent = create_agent(
        model=llm,
        system_prompt=system_prompt,
        tools=tools,
        checkpointer=get_memory_saver(),
        state_schema=AgentState,
    )
    return agent

