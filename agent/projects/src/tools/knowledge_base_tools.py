"""
Knowledge-base tools for shopping guidance.
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from langchain.tools import ToolRuntime, tool

from tools.api_client import get_api_client

_KB_CACHE: dict[str, Any] | None = None


def _project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _kb_path() -> Path:
    return _project_root() / "src" / "knowledge" / "shopping_guide_kb.json"


def _load_kb() -> dict[str, Any]:
    global _KB_CACHE
    if _KB_CACHE is not None:
        return _KB_CACHE
    path = _kb_path()
    if not path.exists():
        _KB_CACHE = {"meta": {}, "global_rules": {}, "topics": []}
        return _KB_CACHE
    # Accept both UTF-8 and UTF-8 BOM files to avoid runtime JSON decode failures.
    with path.open("r", encoding="utf-8-sig") as f:
        _KB_CACHE = json.load(f)
    return _KB_CACHE


def _match_topics(query: str, topics: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not query:
        return []
    q = query.lower()
    scored: list[tuple[int, dict[str, Any]]] = []
    for t in topics:
        score = 0
        for kw in t.get("keywords", []):
            if str(kw).lower() in q:
                score += 1
        if score > 0:
            scored.append((score, t))
    scored.sort(key=lambda x: x[0], reverse=True)
    return [item[1] for item in scored[:2]]


def _detect_gender(text: str) -> str:
    t = (text or "").lower()
    if any(k in t for k in ["男装", "男士", "男生", "man", "men", "male"]):
        return "male"
    if any(k in t for k in ["女装", "女士", "女生", "woman", "women", "female"]):
        return "female"
    return ""


def _detect_budget(text: str) -> dict[str, int]:
    t = text or ""
    budget: dict[str, int] = {}

    m_range = re.search(r"(\d{2,5})\s*[-~到]\s*(\d{2,5})", t)
    if m_range:
        budget["min"] = int(m_range.group(1))
        budget["max"] = int(m_range.group(2))
        return budget

    m_around = re.search(r"(\d{2,5})\s*(元|块)?\s*(左右|上下)", t)
    if m_around:
        v = int(m_around.group(1))
        budget["min"] = max(0, int(v * 0.8))
        budget["max"] = int(v * 1.2)
        return budget

    m_max = re.search(r"(\d{2,5})\s*(元|块)?\s*(以内|以下|不超过)", t)
    if m_max:
        budget["max"] = int(m_max.group(1))
        return budget

    return budget


def _detect_core_preference(text: str) -> str:
    t = (text or "").lower()
    prefs = [
        "透气", "轻便", "显瘦", "防水", "耐穿",
        "百搭", "性价比", "版型", "材质",
        "breathable", "lightweight", "waterproof", "durable", "value",
    ]
    for p in prefs:
        if p in t:
            return p
    return ""


def _detect_use_case(text: str) -> str:
    t = (text or "").lower()
    cases = {
        "commute": ["通勤", "上班", "办公室", "office", "work"],
        "daily": ["日常", "休闲", "逛街", "daily", "casual"],
        "sport": ["跑步", "健身", "运动", "sport", "running", "gym"],
        "travel": ["旅行", "出游", "旅游", "travel"],
    }
    for key, terms in cases.items():
        if any(k in t for k in terms):
            return key
    return ""


def _detect_snack_taste(text: str) -> str:
    t = (text or "").lower()
    mapping = {
        "spicy": ["辣", "麻辣", "香辣"],
        "sweet": ["甜", "甜口"],
        "savory": ["咸", "咸香"],
        "healthy": ["低脂", "低糖", "代餐", "健康"],
    }
    for key, terms in mapping.items():
        if any(k in t for k in terms):
            return key
    return ""


def _detect_packaging(text: str) -> str:
    t = (text or "").lower()
    if any(k in t for k in ["独立小包装", "小包装", "多袋装", "独立包装"]):
        return "small_pack"
    if any(k in t for k in ["大包装", "整箱", "散装"]):
        return "bulk_pack"
    return ""


def _is_accept_defaults(text: str) -> bool:
    t = (text or "").lower()
    signals = [
        "都可以", "随便", "你看着办", "按你的", "你决定",
        "不用确认", "差不多就行", "直接推荐",
        "是的", "对", "可以", "yes", "ok", "fine",
    ]
    return any(s in t for s in signals)


def _required_slots_for_category(category_or_scene: str) -> list[str]:
    c = (category_or_scene or "").lower()
    if c in {"snacks", "snack", "零食", "小吃"}:
        return ["category_or_scene", "budget", "core_preference"]
    if c in {"dress", "shirt", "shirts", "coat", "pants", "clothes", "clothing", "shoes", "shoe"}:
        return ["category_or_scene", "gender", "budget", "core_preference"]
    return ["category_or_scene", "budget", "core_preference"]


@tool
def retrieve_shopping_guidance(
    user_message: str,
    guidance_round: int = 1,
    runtime: ToolRuntime = None,
) -> str:
    """
    Retrieve knowledge-base guidance for current shopping turn.
    """
    kb = _load_kb()
    topics = kb.get("topics", [])
    matched = _match_topics(user_message or "", topics)

    max_rounds = int(kb.get("meta", {}).get("max_guidance_rounds", 3) or 3)
    current_round = max(1, guidance_round)
    clamped_round = min(current_round, max_rounds)
    stage_actions = kb.get("global_rules", {}).get("stage_actions", {})

    question_pool: list[str] = []
    search_keywords: list[str] = []
    must_match_factors: list[str] = []

    for topic in matched:
        for q in topic.get("high_value_questions", []):
            if q not in question_pool:
                question_pool.append(q)
        hints = topic.get("search_hints", {})
        for kw in hints.get("keywords", []):
            if kw not in search_keywords:
                search_keywords.append(kw)
        for factor in topic.get("must_match_factors", []):
            if factor not in must_match_factors:
                must_match_factors.append(factor)

    response = {
        "matched_topics": [t.get("id", "") for t in matched],
        "guidance": {
            "round": clamped_round,
            "max_rounds": max_rounds,
            "current_action": stage_actions.get(str(clamped_round), "Continue narrowing candidates."),
            "should_finalize_recommendation": clamped_round >= max_rounds,
        },
        "required_slots": kb.get("global_rules", {}).get("required_slots", []),
        "next_questions": question_pool[:2],
        "search_hints": {
            "keywords": search_keywords,
            "must_match_factors": must_match_factors,
        },
        "output_rules": kb.get("global_rules", {}).get("output_rules", []),
    }
    return json.dumps(response, ensure_ascii=False, indent=2)


@tool
def plan_shopping_turn(
    user_message: str,
    session_slots_json: str = "{}",
    runtime: ToolRuntime = None,
) -> str:
    """
    Decide whether to continue guiding or start product search based on required slots.
    """
    kb = _load_kb()
    default_required = kb.get("global_rules", {}).get("required_slots", [])
    topics = kb.get("topics", [])

    try:
        prev_slots = json.loads(session_slots_json) if session_slots_json else {}
        if not isinstance(prev_slots, dict):
            prev_slots = {}
    except Exception:
        prev_slots = {}

    text = user_message or ""
    matched = _match_topics(text, topics)

    slots = dict(prev_slots)
    if matched and not slots.get("category_or_scene"):
        slots["category_or_scene"] = matched[0].get("id", "")

    gender = _detect_gender(text)
    if gender:
        slots["gender"] = gender

    budget = _detect_budget(text)
    if budget:
        slots["budget"] = budget

    use_case = _detect_use_case(text)
    if use_case:
        slots["use_case"] = use_case

    pref = _detect_core_preference(text)
    if pref:
        slots["core_preference"] = pref

    snack_taste = _detect_snack_taste(text)
    if snack_taste:
        slots["snack_taste"] = snack_taste
        if slots.get("core_preference") in (None, "", "default"):
            slots["core_preference"] = snack_taste

    packaging = _detect_packaging(text)
    if packaging:
        slots["packaging"] = packaging

    required = _required_slots_for_category(str(slots.get("category_or_scene", "")))
    if not required:
        required = default_required

    missing = []
    for key in required:
        value = slots.get(key)
        if value in (None, "", {}, []):
            missing.append(key)

    accept_defaults = _is_accept_defaults(text)
    if accept_defaults:
        if "core_preference" in missing:
            slots["core_preference"] = "default"
            missing.remove("core_preference")
        if "use_case" not in slots:
            slots["use_case"] = "daily"

    question_map = {
        "category_or_scene": "你主要想买哪一类，以及用于什么场景？",
        "gender": "是买男装还是女装？",
        "budget": "预算大概多少（例如100内、100-200）？",
        "core_preference": "你最看重的一点是什么（例如透气、轻便、版型、性价比）？",
    }
    next_questions = [] if accept_defaults else [question_map[m] for m in missing[:1] if m in question_map]

    search_keyword = ""
    if slots.get("category_or_scene"):
        search_keyword = str(slots.get("category_or_scene"))
    elif matched:
        search_keyword = str(matched[0].get("id", ""))

    out = {
        "slots": slots,
        "required_slots": required,
        "missing_slots": missing,
        "should_search": len(missing) == 0 or accept_defaults,
        "accept_defaults": accept_defaults,
        "next_questions": next_questions,
        "question_policy": {
            "max_questions_this_turn": 1,
            "min_followup_rounds": 4,
            "max_followup_rounds": 5,
            "avoid_repeat_confirmation": True,
        },
        "search_plan": {
            "keyword": search_keyword,
            "max_primary_search_calls_per_turn": 1,
        },
        "guardrails": kb.get("global_rules", {}).get("search_guardrails", []),
    }
    return json.dumps(out, ensure_ascii=False, indent=2)


@tool
def inspect_inventory_signals(
    category_or_scene: str,
    budget_json: str = "{}",
    limit: int = 50,
    runtime: ToolRuntime = None,
) -> str:
    """
    Inspect inventory for a category and return only askable dimensions that truly exist.

    Args:
        category_or_scene: Category keyword (e.g. snacks, shirts, shoes).
        budget_json: Optional budget JSON, e.g. {"max": 50}.
        limit: Max records to inspect.

    Returns:
        JSON string with availability stats and suggested askable dimensions.
    """
    client = get_api_client()
    keyword = (category_or_scene or "").strip()
    if not keyword:
        return json.dumps(
            {
                "ok": False,
                "reason": "missing_category",
                "askable_dimensions": [],
                "recommended_question_count": 0,
            },
            ensure_ascii=False,
            indent=2,
        )

    budget = {}
    try:
        budget = json.loads(budget_json) if budget_json else {}
        if not isinstance(budget, dict):
            budget = {}
    except Exception:
        budget = {}

    params: dict[str, Any] = {
        "page": 1,
        "page_size": max(10, min(100, int(limit))),
        "keyword": keyword,
        "sort_by": "sales_desc",
    }
    if isinstance(budget.get("max"), (int, float)):
        params["max_price"] = budget["max"]
    if isinstance(budget.get("min"), (int, float)):
        params["min_price"] = budget["min"]

    try:
        response = client.get("/products", params=params)
    except Exception as e:
        return json.dumps(
            {
                "ok": False,
                "reason": "api_error",
                "error": str(e),
                "askable_dimensions": [],
                "recommended_question_count": 0,
            },
            ensure_ascii=False,
            indent=2,
        )

    items = response.get("data", []) if isinstance(response, dict) else []
    if not isinstance(items, list):
        items = []

    names = " ".join([str(x.get("name", "")) for x in items if isinstance(x, dict)]).lower()
    categories = list({str(x.get("category", "")).strip() for x in items if isinstance(x, dict) and x.get("category")})

    taste_hits = sum(k in names for k in ["辣", "麻辣", "香辣", "甜", "咸"])
    pack_hits = sum(k in names for k in ["小包装", "独立", "整箱", "大包装", "多袋"])
    sleeve_hits = sum(k in names for k in ["短袖", "长袖"])
    fit_hits = sum(k in names for k in ["宽松", "修身", "直筒", "a字"])

    askable_dimensions = []
    if taste_hits > 0:
        askable_dimensions.append("taste")
    if pack_hits > 0:
        askable_dimensions.append("packaging")
    if sleeve_hits > 0:
        askable_dimensions.append("sleeve")
    if fit_hits > 0:
        askable_dimensions.append("fit")

    if len(items) >= 20:
        recommended_question_count = 4
    elif len(items) >= 10:
        recommended_question_count = 3
    elif len(items) >= 3:
        recommended_question_count = 2
    else:
        recommended_question_count = 1

    return json.dumps(
        {
            "ok": True,
            "keyword": keyword,
            "inventory_count": len(items),
            "top_categories": categories[:5],
            "askable_dimensions": askable_dimensions,
            "recommended_question_count": recommended_question_count,
            "should_search_now": len(items) >= 3,
        },
        ensure_ascii=False,
        indent=2,
    )

