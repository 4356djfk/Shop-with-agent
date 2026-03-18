package com.root.aishopback.service.impl;

import com.root.aishopback.service.AiShoppingAgentService;

final class AgentActionExecutorRegistry {

    AiShoppingAgentService.AgentReply execute(
        AgentActionRouter.ActionHandlerType handler,
        LangChain4jShoppingAgentService service,
        String prompt,
        Long userId,
        LangChain4jShoppingAgentService.ConversationMemory memory
    ) {
        return switch (handler) {
            case CHOOSE -> service.executeChoose(prompt, userId, memory);
            case WHY_NOT -> service.executeWhyNot(prompt, memory);
            case REJECT -> service.executeReject(prompt, memory);
            case TRANSACTION -> service.executeTransaction(prompt, userId, memory);
            case COMPARE -> service.executeCompare(prompt, memory);
            case CONFIRM -> service.executeConfirm(prompt, memory);
            case ATTRIBUTE -> service.executeAttribute(prompt, memory);
            case ATTRIBUTE_OTHER -> service.executeOtherAttribute(prompt, memory);
            case INTRO -> service.executeIntro(prompt, memory);
        };
    }
}

