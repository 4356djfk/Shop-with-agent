package com.root.aishopback.service.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

final class AgentActionRouter {

    enum FrameAction {
        SEARCH, COMPARE, ATTRIBUTE, INTRO, TRANSACTION, CHITCHAT
    }

    enum ActionHandlerType {
        CHOOSE, WHY_NOT, REJECT, TRANSACTION, COMPARE, CONFIRM, ATTRIBUTE, ATTRIBUTE_OTHER, INTRO
    }

    private final EnumMap<FrameAction, List<ActionHandlerType>> pipelines;

    AgentActionRouter() {
        this.pipelines = buildPipelines();
    }

    List<ActionHandlerType> pipelineFor(FrameAction action) {
        FrameAction key = action == null ? FrameAction.SEARCH : action;
        List<ActionHandlerType> list = pipelines.getOrDefault(key, pipelines.get(FrameAction.SEARCH));
        return list == null ? List.of() : list;
    }

    private EnumMap<FrameAction, List<ActionHandlerType>> buildPipelines() {
        EnumMap<FrameAction, List<ActionHandlerType>> m = new EnumMap<>(FrameAction.class);
        List<ActionHandlerType> commonLead = List.of(ActionHandlerType.CHOOSE, ActionHandlerType.WHY_NOT, ActionHandlerType.REJECT);
        m.put(FrameAction.TRANSACTION, concat(commonLead, List.of(ActionHandlerType.TRANSACTION)));
        m.put(FrameAction.COMPARE, concat(commonLead, List.of(ActionHandlerType.COMPARE, ActionHandlerType.CONFIRM)));
        m.put(FrameAction.ATTRIBUTE, concat(commonLead, List.of(ActionHandlerType.ATTRIBUTE, ActionHandlerType.ATTRIBUTE_OTHER)));
        m.put(FrameAction.INTRO, concat(commonLead, List.of(ActionHandlerType.INTRO, ActionHandlerType.ATTRIBUTE, ActionHandlerType.ATTRIBUTE_OTHER)));
        List<ActionHandlerType> searchFallback = concat(commonLead, List.of(
            ActionHandlerType.TRANSACTION,
            ActionHandlerType.COMPARE,
            ActionHandlerType.CONFIRM,
            ActionHandlerType.ATTRIBUTE,
            ActionHandlerType.ATTRIBUTE_OTHER,
            ActionHandlerType.INTRO
        ));
        m.put(FrameAction.SEARCH, searchFallback);
        m.put(FrameAction.CHITCHAT, searchFallback);
        return m;
    }

    private List<ActionHandlerType> concat(List<ActionHandlerType> a, List<ActionHandlerType> b) {
        List<ActionHandlerType> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }
}

