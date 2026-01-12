package com.loopone.loopinbe.domain.chat.chatMessage.controller;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUser;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.req.AttachmentRequest;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.res.ChatMessageResponse;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.req.ChatMessageRequest;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessagePage;
import com.loopone.loopinbe.domain.chat.chatMessage.service.ChatMessageService;
import com.loopone.loopinbe.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/rest-api/v1/chat-message")
@RequiredArgsConstructor
@Tag(name = "ChatMessage", description = "채팅 메시지 API")
public class ChatMessageController {
    private final ChatMessageService chatMessageService;

    // 채팅방 과거 메시지 조회 [참여자 권한]
    @GetMapping("/{chatRoomId}")
    @Operation(summary = "채팅방 과거 메시지 조회", description = "채팅방의 과거 메시지를 조회합니다.(기본설정: page=0, size=20)")
    public ApiResponse<List<ChatMessageResponse>> findByChatRoomId(@ModelAttribute ChatMessagePage request,
                                                                   @PathVariable("chatRoomId") Long chatRoomId, @CurrentUser CurrentUserDto currentUser) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        return ApiResponse.success(chatMessageService.findByChatRoomId(chatRoomId, pageable, currentUser));
    }

    // 채팅방 메시지 검색(내용) [참여자 권한]
    @GetMapping("/{chatRoomId}/search")
    @Operation(summary = "채팅방 메시지 검색(내용)", description = "채팅방에서 채팅방 메시지를 검색합니다.")
    public ApiResponse<List<ChatMessageResponse>> searchChatMessage(@ModelAttribute ChatMessagePage request, @PathVariable("chatRoomId") Long chatRoomId,
                                                                    @RequestParam("keyword") String keyword, @CurrentUser CurrentUserDto currentUser) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        return ApiResponse.success(chatMessageService.searchByKeyword(chatRoomId, keyword, pageable, currentUser));
    }

    // AI 채팅방 메시지 전송
    @PostMapping("/{chatRoomId}/chat")
    @Operation(summary = "AI 채팅방 메시지 전송", description = "AI 채팅방에 메시지를 전송합니다.")
    public ApiResponse<Void> sendChatMessage(
            @PathVariable("chatRoomId") Long chatRoomId,
            @RequestBody @Valid ChatMessageRequest request,
            @CurrentUser CurrentUserDto currentUser
            ) {
        chatMessageService.sendChatMessage(chatRoomId, request, currentUser);
        return ApiResponse.success();
    }

    // 채팅방에서 첨부 파일 전송 [참여자 권한]
    @PostMapping(value ="/attachments/{chatRoomId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "채팅방에 파일 메시지 전송", description = "채팅방에 파일 메시지를 전송합니다.")
    public ApiResponse<Void> sendAttachment(@PathVariable("chatRoomId") Long chatRoomId,
                                            @RequestPart(value = "attachmentRequest") @Valid AttachmentRequest attachmentRequest,
                                            @RequestPart(value = "images", required = false) List<MultipartFile> images,
                                            @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                            @CurrentUser CurrentUserDto currentUser) {
        chatMessageService.sendAttachment(chatRoomId, attachmentRequest.clientMessageId(), images, files, currentUser);
        return ApiResponse.success();
    }
}
