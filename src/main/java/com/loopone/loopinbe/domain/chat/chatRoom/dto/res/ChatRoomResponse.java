package com.loopone.loopinbe.domain.chat.chatRoom.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatRoomResponse {
    private Long id;
    private Long ownerId;
    private String title;
    private boolean loopSelect;
    private boolean isCallUpdateLoop;
    private Instant lastMessageAt;
    private Instant lastReadAt;
}
