package com.loopone.loopinbe.domain.chat.chatRoom.controller;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUser;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.chat.chatRoom.dto.res.ChatRoomListResponse;
import com.loopone.loopinbe.domain.chat.chatRoom.dto.res.ChatRoomResponse;
import com.loopone.loopinbe.domain.chat.chatRoom.enums.ChatRoomType;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomService;
import com.loopone.loopinbe.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rest-api/v1/chat-room")
@Tag(name = "ChatRoom", description = "채팅방 API")
public class ChatRoomController {
    private final ChatRoomService chatRoomService;

    // AI 채팅방 리스트 조회
    @GetMapping
    @Operation(summary = "채팅방 리스트 조회", description = "채팅방 리스트를 조회합니다.")
    public ApiResponse<ChatRoomListResponse> getChatRooms(
            @Parameter(hidden = true) @CurrentUser CurrentUserDto user,
            @Parameter(description = "채팅방 타입(ALL, TEAM, AI") @RequestParam ChatRoomType chatRoomType
    ) {
        return ApiResponse.success(chatRoomService.getChatRooms(user.id(), chatRoomType));
    }

    // AI 채팅방 생성
    @PostMapping("/create")
    @Operation(summary = "AI 채팅방 생성", description = "AI 채팅방을 생성합니다.")
    public ApiResponse<ChatRoomResponse> createChatRoom(
            @CurrentUser  CurrentUserDto currentUserDto
    ) {
        return ApiResponse.success(chatRoomService.createAiChatRoom(currentUserDto));
    }

    // 팀 ID로 채팅방 조회
    @GetMapping("/team/{teamId}")
    @Operation(summary = "팀 채팅방 조회", description = "팀 ID로 채팅방을 조회합니다.")
    public ApiResponse<ChatRoomResponse> getChatRoomByTeamId(
            @PathVariable Long teamId,
            @CurrentUser CurrentUserDto currentUser
    ) {
        return ApiResponse.success(chatRoomService.findChatRoomByTeamId(teamId, currentUser));
    }
}
