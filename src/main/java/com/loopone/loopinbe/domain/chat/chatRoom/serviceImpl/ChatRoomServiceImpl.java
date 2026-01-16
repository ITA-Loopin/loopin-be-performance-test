package com.loopone.loopinbe.domain.chat.chatRoom.serviceImpl;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.converter.MemberConverter;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.req.ChatMessageRequest;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.type.MessageType;
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
import com.loopone.loopinbe.domain.team.team.entity.Team;
import com.loopone.loopinbe.domain.team.team.repository.TeamRepository;
import com.loopone.loopinbe.domain.team.team.repository.TeamMemberRepository;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.loopone.loopinbe.global.constants.Constant.AI_START_MESSAGE;

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
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

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
    public ChatRoomResponse createAiChatRoom(CurrentUserDto currentUser) {
        Member member = memberRepository.findById(currentUser.id())
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
        chatMessageService.sendChatMessage(
                chatRoom.getId(),
                new ChatMessageRequest(
                        AI_START_MESSAGE,
                        UUID.randomUUID(),
                        MessageType.START_CHATROOM
                ),
                currentUser
        );
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
    public void createTeamChatRoom(Long userId, Team team) {
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

    @Override
    @Transactional
    public void leaveTeamChatRoom(Long memberId, Long teamId) {
        chatRoomRepository.findByTeamId(teamId).ifPresent(chatRoom -> {
            chatRoomMemberRepository.deleteByRoomIdAndMemberId(chatRoom.getId(), memberId);
        });
    }

    // 멤버가 참여중인 모든 채팅방 나가기(DM/그룹)
    @Override
    @Transactional
    public void leaveAllChatRooms(Long memberId) {
        List<Long> roomIds = chatRoomRepository.findRoomIdsByMemberId(memberId);
        for (Long roomId : roomIds) {
            Long ownerId = chatRoomRepository.findOwnerId(roomId);
            boolean iAmOwner = ownerId != null && ownerId.equals(memberId);

            // 1) 내 멤버십을 DB에서 먼저 제거 (flush 자동)
            chatRoomMemberRepository.deleteByRoomIdAndMemberId(roomId, memberId);

            // 2) DB 기준으로 남은 인원 확인
            long remain = chatRoomMemberRepository.countByChatRoom_Id(roomId);
            if (iAmOwner && remain == 0) {
                // 3-A) 방 삭제 케이스: 자식부터 확실히 삭제
                chatMessageService.deleteAllChatMessages(roomId);
                chatRoomMemberRepository.deleteAllByRoomId(roomId); // 혹시 남아있으면 제거
                chatRoomRepository.deleteById(roomId);
                continue;
            }
            if (iAmOwner) {
                // 3-B) 방장 위임
                Long nextOwnerId = chatRoomMemberRepository.findFirstMemberId(roomId);
                if (nextOwnerId == null) {
                    chatMessageService.deleteAllChatMessages(roomId);
                    chatRoomRepository.deleteById(roomId);
                    continue;
                }
                chatRoomRepository.updateOwner(roomId, nextOwnerId);
            }
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

    // 팀id로 채팅방 조회
    @Override
    @Transactional(readOnly = true)
    public ChatRoomResponse findChatRoomByTeamId(Long teamId, CurrentUserDto currentUser) {
        if (!teamMemberRepository.existsByTeamIdAndMemberId(teamId, currentUser.id())) {
            throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
        }

        ChatRoom chatRoom = chatRoomRepository.findByTeamId(teamId)
                .orElseThrow(() -> new ServiceException(ReturnCode.CHATROOM_NOT_FOUND));

        return chatRoomConverter.toChatRoomResponse(chatRoom);
    }

    // 초대 수락 시 채팅방에 참여
    @Override
    @Transactional
    public  void participateChatRoom(Long teamId, Long currentUserId) {
        Member member = memberRepository.findById(currentUserId)
                .orElseThrow(() -> new ServiceException(ReturnCode.MEMBER_NOT_FOUND));
        // 1) 팀 채팅방 조회 (없으면 생성까지 해주고 싶으면 아래 create 로직 사용)
        ChatRoom teamRoom = chatRoomRepository
                .findByTeamIdAndIsBotRoomFalse(teamId)
                .orElseThrow(() -> new ServiceException(ReturnCode.CHATROOM_NOT_FOUND));
        // 2) 이미 참여 중이면 idempotent 하게 종료
        boolean alreadyJoined = chatRoomMemberRepository.existsByChatRoomIdAndMemberId(teamRoom.getId(), currentUserId);
        if (alreadyJoined) return;
        // 3) 인원 제한 체크
        long currentCount = chatRoomMemberRepository.countByChatRoomId(teamRoom.getId());
        if (currentCount >= ChatRoom.ROOM_MEMBER_LIMIT) {
            throw new ServiceException(ReturnCode.CHATROOM_LIMIT_EXCEEDED);
        }
        // 4) ChatRoomMember 생성
        ChatRoomMember crm = ChatRoomMember.builder()
                .chatRoom(teamRoom)
                .member(member)
                .build();
        chatRoomMemberRepository.save(crm);
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
