package com.loopone.loopinbe.domain.chat.chatMessage.dto;

import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessage;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessagePayload(
        String id,   // UUID
        UUID clientMessageId, // UUID (멱등키, UNIQUE)
        Long chatRoomId,
        Long memberId,
        String content,
        List<ChatAttachment> attachments,
        List<LoopCreateRequest> recommendations,
        Long loopRuleId,
        String deleteMessageId,
        ChatMessage.AuthorType authorType,
        boolean isBotRoom,
        Instant createdAt,
        Instant modifiedAt
) {}
