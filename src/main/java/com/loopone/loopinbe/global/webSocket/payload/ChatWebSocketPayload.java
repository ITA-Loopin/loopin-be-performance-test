package com.loopone.loopinbe.global.webSocket.payload;

import com.loopone.loopinbe.domain.chat.chatMessage.dto.res.ChatMessageResponse;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.type.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatWebSocketPayload {
    private MessageType messageType;
    // 어떤 채팅방에 브로드캐스트할지 식별용 (MESSAGE(파일) / READ_UP_TO 공통)
    private Long chatRoomId;

    // MESSAGE일 때만 존재
    private UUID clientMessageId;     // UUID (멱등키, UNIQUE)
    private ChatMessageResponse chatMessageResponse;

    // READ_UP_TO일 때만 존재
    private Long memberId;
    private Instant lastReadAt;

    // DELETE일 때만 존재
    private String deleteId;
}
