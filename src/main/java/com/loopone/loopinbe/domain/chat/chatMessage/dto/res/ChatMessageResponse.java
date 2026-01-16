package com.loopone.loopinbe.domain.chat.chatMessage.dto.res;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessage;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String id;
    private Long memberId;
    private String nickname;
    private String profileImageUrl;
    private String content;
    private List<ChatAttachmentResponse> attachments;
    private List<LoopCreateRequest> recommendations;
    private Long loopRuleId;
    private String deleteMessageId;
    private ChatMessage.AuthorType authorType;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
