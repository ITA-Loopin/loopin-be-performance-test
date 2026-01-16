package com.loopone.loopinbe.domain.chat.chatMessage.dto;

import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessage;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record MessageContext(
        UUID msgId,
        String content,
        ChatMessage.AuthorType author,
        List<LoopCreateRequest>recommendations,
        Long loopRuleId,
        String deleteMessageId
) {
}
