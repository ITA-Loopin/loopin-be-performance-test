package com.loopone.loopinbe.domain.chat.chatRoom.service;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.chat.chatRoom.dto.req.ChatRoomRequest;
import com.loopone.loopinbe.domain.chat.chatRoom.dto.res.ChatRoomListResponse;
import com.loopone.loopinbe.domain.chat.chatRoom.dto.res.ChatRoomResponse;
import com.loopone.loopinbe.domain.chat.chatRoom.enums.ChatRoomType;
import com.loopone.loopinbe.domain.loop.loop.dto.res.LoopDetailResponse;
import com.loopone.loopinbe.domain.team.team.entity.Team;

import java.time.Instant;
import java.util.List;

public interface ChatRoomService {
    // 채팅방 생성(DM/그룹)
    ChatRoomResponse addChatRoom(ChatRoomRequest chatRoomRequest, CurrentUserDto currentUser);

    // AI 채팅방 생성
    ChatRoomResponse createAiChatRoom(Long userId);

    void updateChatRoomTitle(Long chatRoomId, String title);

    // 팀 채팅방 생성
    void createTeamChatRoom(Long userId, Team team, List<Member> members);

    // 팀 채팅방 삭제
    void deleteTeamChatRoom(Long userId, Long teamId);

    // 멤버가 참여중인 모든 채팅방 나가기(DM/그룹)
    void leaveAllChatRooms(Long memberId);

    // 채팅방 리스트 조회
    ChatRoomListResponse getChatRooms(Long memberId, ChatRoomType chatRoomType);

    LoopDetailResponse findLoopDetailResponse(Long chatRoomId);
}
