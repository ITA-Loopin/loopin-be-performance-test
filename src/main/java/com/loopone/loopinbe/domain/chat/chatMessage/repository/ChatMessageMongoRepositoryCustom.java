package com.loopone.loopinbe.domain.chat.chatMessage.repository;

import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatAttachment;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessage;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface ChatMessageMongoRepositoryCustom {
    // 채팅 내용 저장
    ChatMessage upsertInbound(
            String id,
            String clientMessageId,
            Long chatRoomId,
            Long memberId,
            String content,
            List<ChatAttachment> attachments,
            List<LoopCreateRequest> recommendations,
            ChatMessage.AuthorType authorType,
            Instant createdAt,
            Instant modifiedAt
    );

    // 채팅방 내 내용 검색 (Mongo 텍스트 인덱스 사용)
    Page<ChatMessage> searchByKeyword(Long chatRoomId, String keyword, Pageable pageable);
}
