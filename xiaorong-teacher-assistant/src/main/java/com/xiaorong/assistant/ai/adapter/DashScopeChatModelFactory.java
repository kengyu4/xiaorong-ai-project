package com.xiaorong.assistant.ai.adapter;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.xiaorong.assistant.ai.dto.AiDtos.AiChatRequest;
import com.xiaorong.assistant.config.XiaorongProperties;

@FunctionalInterface
public interface DashScopeChatModelFactory {
    DashScopeChatModel create(AiChatRequest request, XiaorongProperties.Provider provider);
}
