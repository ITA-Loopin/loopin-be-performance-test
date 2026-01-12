package com.loopone.loopinbe.domain.chat.chatMessage.service;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatMessagePayload;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.res.ChatMessageResponse;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.req.ChatMessageRequest;
import com.loopone.loopinbe.global.common.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ChatMessageService {
    // 채팅방 과거 메시지 조회 [참여자 권한]
    PageResponse<ChatMessageResponse> findByChatRoomId(Long chatRoomId, Pageable pageable, CurrentUserDto currentUser);

    // 채팅방 메시지 검색(내용) [참여자 권한]
    PageResponse<ChatMessageResponse> searchByKeyword(Long chatRoomId, String keyword, Pageable pageable, CurrentUserDto currentUser);

    // Kafka 인바운드 메시지 처리(권한검증 + 멱등 저장 + Mongo 업서트)
    ChatMessagePayload processInbound(ChatMessagePayload in);

    // 채팅 메시지 단일 삭제
    void deleteChatMessage(String messageId, Long memberId);

    // 채팅방의 모든 메시지 삭제
    void deleteAllChatMessages(Long chatRoomId);

    // AI 채팅방 메시지 전송
    void sendChatMessage(Long chatRoomId, ChatMessageRequest request, CurrentUserDto currentUser);

    // 채팅방에서 파일 메시지 전송 [참여자 권한]
    void sendAttachment(Long chatRoomId, UUID clientMessageId, List<MultipartFile> images, List<MultipartFile> files, CurrentUserDto currentUser);
}
