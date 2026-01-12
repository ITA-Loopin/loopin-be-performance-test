package com.loopone.loopinbe.domain.chat.chatRoom.serviceImpl;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.converter.MemberConverter;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.domain.chat.chatMessage.service.ChatMessageService;
import com.loopone.loopinbe.domain.chat.chatRoom.converter.ChatRoomConverter;
import com.loopone.loopinbe.domain.chat.chatRoom.dto.req.ChatRoomRequest;
import com.loopone.loopinbe.domain.chat.chatRoom.dto.res.ChatRoomListResponse;
import com.loopone.loopinbe.domain.chat.chatRoom.dto.res.ChatRoomResponse;
import com.loopone.loopinbe.domain.chat.chatRoom.entity.ChatRoom;
import com.loopone.loopinbe.domain.chat.chatRoom.entity.ChatRoomMember;
import com.loopone.loopinbe.domain.chat.chatRoom.enums.ChatRoomType;
import com.loopone.loopinbe.domain.chat.chatRoom.repository.ChatRoomMemberRepository;
import com.loopone.loopinbe.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomService;
import com.loopone.loopinbe.domain.loop.loop.dto.res.LoopDetailResponse;
import com.loopone.loopinbe.domain.loop.loop.mapper.LoopMapper;
import com.loopone.loopinbe.domain.loop.loop.repository.LoopRepository;
import com.loopone.loopinbe.domain.team.team.entity.Team;
import com.loopone.loopinbe.domain.team.team.repository.TeamRepository;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomServiceImpl implements ChatRoomService {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageService chatMessageService;
    private final MemberRepository memberRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MemberConverter memberConverter;
    private final ChatRoomConverter chatRoomConverter;
    private final LoopRepository loopRepository;
    private final LoopMapper loopMapper;
    private final TeamRepository teamRepository;

    // 채팅방 생성(DM/그룹)
    @Override
    @Transactional
    public ChatRoomResponse addChatRoom(ChatRoomRequest chatRoomRequest, CurrentUserDto currentUser) {
        // 제한 인원 초과 여부 확인 (본인 제외)
        if (chatRoomRequest.getChatRoomMembers().size() > ChatRoom.ROOM_MEMBER_LIMIT - 1) {
            throw new ServiceException(ReturnCode.CHATROOM_LIMIT_EXCEEDED);
        }

        // 1대1 채팅방 중복 방지 로직
        if (chatRoomRequest.getChatRoomMembers().size() == 1) { // 1대1 채팅인지 확인
            Long otherMemberId = chatRoomRequest.getChatRoomMembers().get(0).getMember().getId();
            // 현재 사용자가 otherMember와 이미 1대1 채팅방이 존재하는지 확인
            boolean exists = chatRoomRepository.existsOneOnOneChatRoom(currentUser.id(), otherMemberId);
            if (exists) {
                throw new ServiceException(ReturnCode.CHATROOM_ALREADY_EXISTS);
            }
        }

        // 새로운 채팅방 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .title(chatRoomRequest.getTitle())
                .member(memberConverter.toMember(currentUser)) // 방장 지정
                .build();

        // 본인을 맨 앞에 추가
        List<ChatRoomMember> chatRoomMembers = new ArrayList<>();
        ChatRoomMember enterChatRoomMyself = ChatRoomMember.builder()
                .member(memberConverter.toMember(currentUser))
                .chatRoom(chatRoom)
                .build();
        chatRoomMembers.add(enterChatRoomMyself);

        // 추가하려는 멤버들 중 중복되지 않는 멤버만 추가
        Set<Long> addedMemberIds = new HashSet<>();
        addedMemberIds.add(currentUser.id()); // 본인 ID 추가
        for (ChatRoomMember chatRoomMember : chatRoomRequest.getChatRoomMembers()) {
            Long memberId = chatRoomMember.getMember().getId();
            if (!addedMemberIds.add(memberId))
                continue; // 중복 제거만 하고, 예외는 던지지 않음
            ChatRoomMember memberInChatRoom = ChatRoomMember.builder()
                    .member(chatRoomMember.getMember())
                    .chatRoom(chatRoom)
                    .build();
            chatRoomMembers.add(memberInChatRoom);
        }
        chatRoom.setChatRoomMembers(chatRoomMembers);
        chatRoomRepository.save(chatRoom);
        return chatRoomConverter.toChatRoomResponse(enterChatRoomMyself);
    }

    // AI 채팅방 생성
    @Override
    @Transactional
    public ChatRoomResponse createAiChatRoom(Long userId) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(ReturnCode.USER_NOT_FOUND));
        // 새로운 채팅방 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .title("새 채팅")
                .member(member)
                .build();
        // 본인을 맨 앞에 추가
        List<ChatRoomMember> chatRoomMembers = new ArrayList<>();
        ChatRoomMember enterChatRoomMyself = ChatRoomMember.builder()
                .member(member)
                .chatRoom(chatRoom)
                .build();
        chatRoomMembers.add(enterChatRoomMyself);
        chatRoom.setChatRoomMembers(chatRoomMembers);
        chatRoomRepository.save(chatRoom);
        memberRepository.save(member);
        return chatRoomConverter.toChatRoomResponse(enterChatRoomMyself);
    }

    @Override
    @Transactional
    public void updateChatRoomTitle(Long chatRoomId, String title) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ServiceException(ReturnCode.CHATROOM_NOT_FOUND));

        if (chatRoom.getTitle().equals("새 채팅")) {
            chatRoom.updateTitle(title);
        }
    }

    @Override
    public void createTeamChatRoom(Long userId, Team team, List<Member> members) {
        ChatRoom chatRoom = ChatRoom.builder()
                .title(team.getName())
                .member(team.getLeader())
                .isBotRoom(false)
                .teamId(team.getId())
                .build();

        List<ChatRoomMember> chatRoomMembers = new ArrayList<>();

        chatRoomMembers.add(ChatRoomMember.builder()
                .member(team.getLeader())
                .chatRoom(chatRoom)
                .build());

        if (members != null && !members.isEmpty()) {
            // 팀원 모두 추가
            for (Member member : members) {
                chatRoomMembers.add(ChatRoomMember.builder()
                        .member(member)
                        .chatRoom(chatRoom)
                        .build());
            }
        }

        chatRoom.setChatRoomMembers(chatRoomMembers);
        chatRoomRepository.save(chatRoom);
    }

    @Override
    @Transactional
    public void deleteTeamChatRoom(Long memberId, Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ServiceException(ReturnCode.TEAM_NOT_FOUND));

        if (!team.getLeader().getId().equals(memberId)) {
            throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
        }

        chatRoomRepository.findByTeamId(teamId).ifPresent(chatRoom -> {
            chatMessageService.deleteAllChatMessages(chatRoom.getId());
            chatRoomRepository.delete(chatRoom);
        });
    }

    // 멤버가 참여중인 모든 채팅방 나가기(DM/그룹)
    @Override
    @Transactional
    public void leaveAllChatRooms(Long memberId) {
        List<ChatRoom> chatRooms = chatRoomRepository.findByMemberId(memberId);
        for (ChatRoom chatRoom : chatRooms) {
            // 현재 멤버가 속한 ChatRoomMember 찾기
            ChatRoomMember chatRoomMember = chatRoom.getChatRoomMembers().stream()
                    .filter(member -> member.getMember().getId().equals(memberId))
                    .findFirst()
                    .orElseThrow(() -> new ServiceException(ReturnCode.USER_NOT_FOUND));
            chatRoom.getChatRoomMembers().remove(chatRoomMember); // ChatRoomMember 제거
            chatRoomMemberRepository.delete(chatRoomMember);

            // 방장인지 확인
            if (chatRoom.getMember().getId().equals(memberId)) {
                if (chatRoom.getChatRoomMembers().size() > 1) { // 2명 이상 남아있으면 다음 방장 지정
                    ChatRoomMember nextOwner = chatRoom.getChatRoomMembers().get(0);
                    // 영속 상태 보장
                    Member persistedMember = memberRepository.findById(nextOwner.getMember().getId())
                            .orElseThrow(() -> new ServiceException(ReturnCode.USER_NOT_FOUND));
                    chatRoom.setMember(persistedMember);
                } else { // 1명 이하면 채팅방 삭제
                    chatMessageService.deleteAllChatMessages(chatRoom.getId());
                    chatRoomRepository.delete(chatRoom);
                    continue;
                }
            } else if (chatRoom.getChatRoomMembers().size() < 2) { // 방장이 아닌데 나갔을 때 1명이 되면 삭제
                chatMessageService.deleteAllChatMessages(chatRoom.getId());
                chatRoomRepository.delete(chatRoom);
                continue;
            }
            chatRoomRepository.save(chatRoom);
        }
    }

    // 채팅방 리스트 조회 (ALL, TEAM, AI)
    @Override
    public ChatRoomListResponse getChatRooms(Long memberId, ChatRoomType chatRoomType) {
        return switch (chatRoomType) {
            case ALL -> getAllChatRooms(memberId);
            case AI -> getAiChatRooms(memberId);
            case TEAM -> getTeamChatRooms(memberId);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public LoopDetailResponse findLoopDetailResponse(Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ServiceException(ReturnCode.CHATROOM_NOT_FOUND));

        return loopMapper.toDetailResponse(chatRoom.getLoop());
    }

    private ChatRoomListResponse getAllChatRooms(Long memberId) {
        List<ChatRoomMember> chatRoomList = chatRoomRepository.findMyChatRooms(memberId);
        return chatRoomConverter.toChatRoomListResponse(chatRoomList);
    }

    private ChatRoomListResponse getAiChatRooms(Long memberId) {
        List<ChatRoomMember> chatRoomList = chatRoomRepository.findAiChatRooms(memberId);
        return chatRoomConverter.toChatRoomListResponse(chatRoomList);
    }

    private ChatRoomListResponse getTeamChatRooms(Long memberId) {
        List<ChatRoomMember> chatRoomList = chatRoomRepository.findTeamChatRooms(memberId);
        return chatRoomConverter.toChatRoomListResponse(chatRoomList);
    }
}
