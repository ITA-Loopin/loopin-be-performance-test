package com.loopone.loopinbe.domain.chat.chatMessage.dto.res;

import com.loopone.loopinbe.domain.chat.chatMessage.enums.AttachmentType;

public record ChatAttachmentResponse(
        AttachmentType type,
        String url,              // 최종 접근 URL (image는 보기용, file은 download presigned)
        String originalFileName,
        String contentType,
        long size
) {}
