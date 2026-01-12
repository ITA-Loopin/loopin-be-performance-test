package com.loopone.loopinbe.domain.chat.chatMessage.dto;

import com.loopone.loopinbe.domain.chat.chatMessage.enums.AttachmentType;

public record ChatAttachment(
        AttachmentType type,
        String key,              // S3 object key (예: chat-files/uuid_name.pdf)
        String originalFileName, // 원본 파일명
        String contentType,
        long size
) {}
