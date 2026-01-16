package com.loopone.loopinbe.domain.team.team.serviceImpl;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomService;
import com.loopone.loopinbe.domain.notification.dto.NotificationPayload;
import com.loopone.loopinbe.domain.notification.factory.NotificationPayloadFactory;
import com.loopone.loopinbe.domain.team.team.dto.req.TeamInvitationCreateRequest;
import com.loopone.loopinbe.domain.team.team.dto.res.TeamInvitationResponse;
import com.loopone.loopinbe.domain.team.team.entity.Team;
import com.loopone.loopinbe.domain.team.team.entity.TeamInvitation;
import com.loopone.loopinbe.domain.team.team.entity.TeamMember;
import com.loopone.loopinbe.domain.team.team.enums.InvitationStatus;
import com.loopone.loopinbe.domain.team.team.repository.TeamInvitationRepository;
import com.loopone.loopinbe.domain.team.team.repository.TeamMemberRepository;
import com.loopone.loopinbe.domain.team.team.repository.TeamRepository;
import com.loopone.loopinbe.domain.team.team.service.TeamInvitationService;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import com.loopone.loopinbe.global.kafka.event.notification.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.loopone.loopinbe.global.constants.KafkaKey.INVITE_TOPIC;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamInvitationServiceImpl implements TeamInvitationService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final MemberRepository memberRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final ChatRoomService chatRoomService;

    @Override
    @Transactional
    public List<Long> sendInvitation(Long teamId, TeamInvitationCreateRequest request, CurrentUserDto currentUser) {
        Team team = getTeamOrThrow(teamId);
        //validateTeamLeader(team, currentUser.id());

        Member inviter = getMemberOrThrow(currentUser.id());
        List<Member> invitees = getMembersOrThrow(request.getInviteeIds());
        List<Long> invitationIds = new ArrayList<>(invitees.size());

        for (Member invitee : invitees) {
            // 자기 자신 초대 방지
            if (invitee.getId().equals(inviter.getId())) {
                throw new ServiceException(ReturnCode.INVALID_INVITATION);
            }
            validateNotTeamMember(teamId, invitee.getId());
            validateNoPendingInvitation(team, invitee);
            TeamInvitation invitation = TeamInvitation.builder()
                    .team(team)
                    .inviter(inviter)
                    .invitee(invitee)
                    .status(InvitationStatus.PENDING)
                    .build();
            TeamInvitation saved = teamInvitationRepository.save(invitation);

            // 알림 이벤트 발행
            NotificationPayload payload = NotificationPayloadFactory.teamInvite(saved);
            notificationEventPublisher.publishNotification(payload, INVITE_TOPIC);
            invitationIds.add(saved.getId());
        }
        return invitationIds;
    }

    @Override
    @Transactional
    public void cancelInvitation(Long teamId, Long invitationId, CurrentUserDto currentUser) {
        Team team = getTeamOrThrow(teamId);
        validateTeamLeader(team, currentUser.id());

        TeamInvitation invitation = getInvitationOrThrow(invitationId);
        validateInvitationBelongsToTeam(invitation, teamId);
        validateInvitationPending(invitation);

        // 초대 상태를 CANCELLED로 변경
        invitation.setStatus(InvitationStatus.CANCELLED);
        teamInvitationRepository.save(invitation);
    }

    @Override
    @Transactional
    public void acceptInvitation(Long invitationId, CurrentUserDto currentUser) {
        TeamInvitation invitation = getInvitationOrThrow(invitationId);
        validateInvitationRecipient(invitation, currentUser.id());
        validateInvitationPending(invitation);

        // 초대 상태 업데이트
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setRespondedAt(LocalDateTime.now());
        teamInvitationRepository.save(invitation);

        // 팀원 추가
        TeamMember teamMember = TeamMember.builder()
                .team(invitation.getTeam())
                .member(invitation.getInvitee())
                .build();
        teamMemberRepository.save(teamMember);

        // 팀 채팅방 참여
        chatRoomService.participateChatRoom(invitation.getTeam().getId(), currentUser.id());
    }

    @Override
    @Transactional
    public void rejectInvitation(Long invitationId, CurrentUserDto currentUser) {
        TeamInvitation invitation = getInvitationOrThrow(invitationId);
        validateInvitationRecipient(invitation, currentUser.id());
        validateInvitationPending(invitation);

        // 초대 상태 업데이트
        invitation.setStatus(InvitationStatus.REJECTED);
        invitation.setRespondedAt(LocalDateTime.now());
        teamInvitationRepository.save(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamInvitationResponse> getTeamInvitations(Long teamId, CurrentUserDto currentUser) {
        Team team = getTeamOrThrow(teamId);
        validateTeamLeader(team, currentUser.id());

        List<TeamInvitation> invitations = getPendingInvitationsByTeam(team);

        return invitations.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamInvitationResponse> getMyInvitations(CurrentUserDto currentUser) {
        Member member = getMemberOrThrow(currentUser.id());

        List<TeamInvitation> invitations = getPendingInvitationsByInvitee(member);

        return invitations.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ========== 조회 메서드 ==========
    // 팀 조회
    private Team getTeamOrThrow(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ServiceException(ReturnCode.TEAM_NOT_FOUND));
    }

    // 초대 조회
    private TeamInvitation getInvitationOrThrow(Long invitationId) {
        return teamInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ServiceException(ReturnCode.INVITATION_NOT_FOUND));
    }

    // 멤버 조회
    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceException(ReturnCode.MEMBER_NOT_FOUND));
    }

    // 멤버 다건 조회 (없으면 예외)
    private List<Member> getMembersOrThrow(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new ServiceException(ReturnCode.MEMBER_NOT_FOUND); // 또는 별도 ReturnCode
        }
        // 중복 제거 (같은 사람 여러 번 초대 방지)
        List<Long> distinctIds = memberIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Member> members = memberRepository.findAllById(distinctIds);
        // findAllById는 "없는 ID"를 자동으로 걸러서 반환하므로, 누락 검증 필요
        if (members.size() != distinctIds.size()) {
            throw new ServiceException(ReturnCode.MEMBER_NOT_FOUND);
        }
        return members;
    }

    // 팀의 PENDING 상태 초대 목록 조회
    private List<TeamInvitation> getPendingInvitationsByTeam(Team team) {
        return teamInvitationRepository.findByTeamAndStatus(team, InvitationStatus.PENDING);
    }

    // 멤버가 받은 PENDING 상태 초대 목록 조회
    private List<TeamInvitation> getPendingInvitationsByInvitee(Member invitee) {
        return teamInvitationRepository.findByInviteeAndStatus(invitee, InvitationStatus.PENDING);
    }

    // ========== 검증 메서드 ==========
    // 팀 리더 권한 검증
    private void validateTeamLeader(Team team, Long memberId) {
        if (!team.getLeader().getId().equals(memberId)) {
            throw new ServiceException(ReturnCode.UNAUTHORIZED_TEAM_LEADER_ONLY);
        }
    }

    // 초대가 PENDING 상태인지 검증
    private void validateInvitationPending(TeamInvitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ServiceException(ReturnCode.INVITATION_ALREADY_RESPONDED);
        }
    }

    // 초대 수신자 본인인지 검증
    private void validateInvitationRecipient(TeamInvitation invitation, Long memberId) {
        if (!invitation.getInvitee().getId().equals(memberId)) {
            throw new ServiceException(ReturnCode.UNAUTHORIZED_INVITATION_RECIPIENT_ONLY);
        }
    }

    // 초대가 해당 팀의 것인지 검증
    private void validateInvitationBelongsToTeam(TeamInvitation invitation, Long teamId) {
        if (!invitation.getTeam().getId().equals(teamId)) {
            throw new ServiceException(ReturnCode.INVALID_INVITATION);
        }
    }

    // 이미 팀원이 아닌지 검증
    private void validateNotTeamMember(Long teamId, Long memberId) {
        if (teamMemberRepository.existsByTeamIdAndMemberId(teamId, memberId)) {
            throw new ServiceException(ReturnCode.ALREADY_TEAM_MEMBER);
        }
    }

    // 대기 중인 초대가 없는지 검증
    private void validateNoPendingInvitation(Team team, Member invitee) {
        if (teamInvitationRepository.existsByTeamAndInviteeAndStatus(team, invitee, InvitationStatus.PENDING)) {
            throw new ServiceException(ReturnCode.INVITATION_ALREADY_SENT);
        }
    }

    // ========== DTO 변환 메서드 ==========
    // TeamInvitation Entity를 Response DTO로 변환
    private TeamInvitationResponse toResponse(TeamInvitation invitation) {
        return TeamInvitationResponse.builder()
                .invitationId(invitation.getId())
                .teamId(invitation.getTeam().getId())
                .teamName(invitation.getTeam().getName())
                .inviterId(invitation.getInviter().getId())
                .inviterNickname(invitation.getInviter().getNickname())
                .inviteeId(invitation.getInvitee().getId())
                .inviteeNickname(invitation.getInvitee().getNickname())
                .status(invitation.getStatus())
                .createdAt(invitation.getCreatedAt())
                .respondedAt(invitation.getRespondedAt())
                .build();
    }
}
