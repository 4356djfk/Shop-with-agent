package com.root.aishopback.service.impl;

final class AgentSemanticParser {

    private AgentSemanticParser() {
    }

    static boolean isAnchoredFollowUp(
        boolean followUp,
        boolean recommendOneFollowUp,
        boolean cheaperFollowUp,
        boolean sameBrandFollowUp,
        boolean switchBrandFollowUp,
        boolean comparativeFollowUp,
        boolean refinementFollowUp,
        boolean predicateOnlyFollowUp,
        boolean dialogFollowUp,
        boolean dialogConfirm,
        boolean explicitIntentSwitch
    ) {
        boolean anchored = followUp
            || recommendOneFollowUp
            || cheaperFollowUp
            || sameBrandFollowUp
            || switchBrandFollowUp
            || comparativeFollowUp
            || refinementFollowUp
            || predicateOnlyFollowUp
            || dialogFollowUp
            || dialogConfirm;
        if (explicitIntentSwitch) {
            return false;
        }
        return anchored;
    }

    static AgentActionRouter.FrameAction detectAction(
        String prompt,
        String lowered,
        boolean greeting,
        LangChain4jShoppingAgentService.DialogAct dialogAct,
        boolean addToCart,
        boolean checkout,
        boolean increment,
        boolean batchAdd,
        boolean removeCart,
        boolean cartQuery,
        boolean clearCart,
        boolean compareTone,
        boolean attributeTone,
        boolean introTone
    ) {
        if (greeting || dialogAct == LangChain4jShoppingAgentService.DialogAct.CHITCHAT) {
            return AgentActionRouter.FrameAction.CHITCHAT;
        }
        if (dialogAct == LangChain4jShoppingAgentService.DialogAct.ACTION
            || addToCart
            || checkout
            || increment
            || batchAdd
            || removeCart
            || cartQuery
            || clearCart) {
            return AgentActionRouter.FrameAction.TRANSACTION;
        }
        if (compareTone) {
            return AgentActionRouter.FrameAction.COMPARE;
        }
        if (attributeTone) {
            return AgentActionRouter.FrameAction.ATTRIBUTE;
        }
        if (introTone) {
            return AgentActionRouter.FrameAction.INTRO;
        }
        return AgentActionRouter.FrameAction.SEARCH;
    }

    static boolean containsCompareTone(String lowered) {
        return containsAny(
            lowered,
            "\u66f4\u597d", "\u6700\u597d", "\u6700\u4fbf\u5b9c", "\u66f4\u4fbf\u5b9c", "\u6700\u706b\u7206", "\u9500\u91cf\u6700\u9ad8", "\u8bc4\u5206\u6700\u9ad8",
            "better", "best", "cheaper", "cheapest", "most popular", "top rated"
        );
    }

    static boolean containsAttributeTone(String lowered) {
        return containsAny(
            lowered,
            "\u8bc4\u5206", "\u9500\u91cf", "\u5e93\u5b58", "\u4ef7\u683c", "\u54c1\u724c", "\u7c7b\u76ee",
            "rating", "sales", "stock", "price", "brand", "category"
        );
    }

    static boolean containsIntroTone(String lowered) {
        return containsAny(
            lowered,
            "\u4ecb\u7ecd", "\u8be6\u60c5", "\u8bf4\u8bf4\u7b2c\u4e00\u4e2a", "\u7b2c\u4e00\u4e2a\u600e\u4e48\u6837",
            "introduce", "details", "tell me about"
        );
    }

    private static boolean containsAny(String text, String... keys) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}

