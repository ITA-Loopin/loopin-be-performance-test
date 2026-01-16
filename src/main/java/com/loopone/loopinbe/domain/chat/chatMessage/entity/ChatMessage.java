package com.loopone.loopinbe.domain.chat.chatMessage.entity;

import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatAttachment;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;
import com.loopone.loopinbe.global.mongo.BaseDocument;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Document(collection = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        // 채팅방별 페이징 조회 최적화 (createdAt desc 정렬을 많이 함)
        @CompoundIndex(name = "chatRoom_createdAt_idx", def = "{'chatRoomId': 1, 'createdAt': -1}"),
        // 채팅방 + 멤버 조회가 잦다면
        @CompoundIndex(name = "chatRoom_member_createdAt_idx", def = "{'chatRoomId': 1, 'memberId': 1, 'createdAt': -1}"),
        // 채팅방 내 메시지 검색
        @CompoundIndex(name = "room_content_text_idx", def = "{'chatRoomId': 1, 'content': 'text'}")
})
public class ChatMessage extends BaseDocument {
    private UUID clientMessageId;

    private Long chatRoomId;

    private Long memberId; // BOT이면 null 허용

    private String content;

    private List<ChatAttachment> attachments;

    private List<LoopCreateRequest> recommendations;

    private Long loopRuleId;

    private String deleteMessageId;

    @Indexed
    private AuthorType authorType;
    public enum AuthorType {
        USER, BOT
    }
}
