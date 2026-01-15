package com.loopone.loopinbe.domain.team.teamLoop.serviceImpl;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.domain.team.teamLoop.dto.req.TeamLoopChecklistCreateRequest;
import com.loopone.loopinbe.domain.team.teamLoop.dto.req.TeamLoopChecklistUpdateRequest;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.TeamLoopChecklistResponse;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.TeamLoopMemberChecklistResponse;
import com.loopone.loopinbe.domain.team.teamLoop.entity.TeamLoop;
import com.loopone.loopinbe.domain.team.teamLoop.entity.TeamLoopActivity;
import com.loopone.loopinbe.domain.team.teamLoop.entity.TeamLoopChecklist;
import com.loopone.loopinbe.domain.team.teamLoop.entity.TeamLoopMemberCheck;
import com.loopone.loopinbe.domain.team.teamLoop.entity.TeamLoopMemberProgress;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopActivityType;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopStatus;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopType;
import com.loopone.loopinbe.domain.team.teamLoop.repository.TeamLoopActivityRepository;
import com.loopone.loopinbe.domain.team.teamLoop.repository.TeamLoopChecklistRepository;
import com.loopone.loopinbe.domain.team.teamLoop.repository.TeamLoopMemberCheckRepository;
import com.loopone.loopinbe.domain.team.teamLoop.repository.TeamLoopMemberProgressRepository;
import com.loopone.loopinbe.domain.team.teamLoop.repository.TeamLoopRepository;
import com.loopone.loopinbe.domain.team.teamLoop.service.TeamLoopChecklistService;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TeamLoopChecklistServiceImpl implements TeamLoopChecklistService {

    private final TeamLoopRepository teamLoopRepository;
    private final TeamLoopChecklistRepository teamLoopChecklistRepository;
    private final TeamLoopMemberCheckRepository teamLoopMemberCheckRepository;
    private final TeamLoopMemberProgressRepository teamLoopMemberProgressRepository;
    private final MemberRepository memberRepository;
    private final TeamLoopActivityRepository teamLoopActivityRepository;

    @Override
    public TeamLoopChecklistResponse createChecklist(Long loopId, TeamLoopChecklistCreateRequest request,
            CurrentUserDto currentUser) {
        TeamLoop teamLoop = getTeamLoopOrThrow(loopId);
        Member member = getMemberOrThrow(currentUser.id());

        Member checklistOwner;
        List<TeamLoopMemberProgress> targetProgresses;

        if (teamLoop.getType() == TeamLoopType.COMMON) {
            validateLeader(teamLoop, member);
            checklistOwner = null;
            targetProgresses = teamLoop.getMemberProgress();
        } else {
            TeamLoopMemberProgress myProgress = getMyProgressOrThrow(teamLoop, member);
            checklistOwner = member;
            targetProgresses = List.of(myProgress);
        }

        TeamLoopChecklist checklist = teamLoopChecklistRepository.save(TeamLoopChecklist.builder()
                .teamLoop(teamLoop)
                .content(request.content())
                .owner(checklistOwner)
                .build());

        List<TeamLoopMemberCheck> checks = targetProgresses.stream()
                .map(progress -> TeamLoopMemberCheck.builder()
                        .memberProgress(progress)
                        .checklist(checklist)
                        .isChecked(false)
                        .build())
                .collect(Collectors.toList());
        teamLoopMemberCheckRepository.saveAll(checks);

        return TeamLoopChecklistResponse.builder()
                .id(checklist.getId())
                .content(checklist.getContent())
                .isChecked(false)
                .build();
    }

    @Override
    public TeamLoopChecklistResponse updateChecklist(Long checklistId, TeamLoopChecklistUpdateRequest request,
            CurrentUserDto currentUser) {
        TeamLoopChecklist checklist = getChecklistOrThrow(checklistId);
        validatePermission(checklist, currentUser.id());
        checklist.updateContent(request.content());

        return TeamLoopChecklistResponse.builder()
                .id(checklist.getId())
                .content(checklist.getContent())
                .isChecked(false)
                .build();
    }

    @Override
    public void deleteChecklist(Long checklistId, CurrentUserDto currentUser) {
        TeamLoopChecklist checklist = getChecklistOrThrow(checklistId);
        validatePermission(checklist, currentUser.id());
        teamLoopChecklistRepository.delete(checklist);
    }

    @Override
    public TeamLoopChecklistResponse toggleCheck(Long checklistId, CurrentUserDto currentUser) {
        TeamLoopMemberCheck myCheck = getMyCheckOrThrow(currentUser.id(), checklistId);
        myCheck.toggleChecked();

        // 체크리스트 완료 시 활동 로그 기록
        if (myCheck.isChecked()) {
            TeamLoopChecklist checklist = myCheck.getChecklist();
            TeamLoop teamLoop = checklist.getTeamLoop();
            Member member = getMemberOrThrow(currentUser.id());

            // 체크리스트 완료 로그
            TeamLoopActivity activity = TeamLoopActivity.builder()
                    .member(member)
                    .team(teamLoop.getTeam())
                    .teamLoop(teamLoop)
                    .actionType(TeamLoopActivityType.CHECKLIST_COMPLETED)
                    .targetName(checklist.getContent())
                    .build();
            teamLoopActivityRepository.save(activity);

            // 루프 완료 확인 (모든 체크리스트 완료 시)
            if (isLoopCompleted(teamLoop, member)) {
                TeamLoopActivity loopCompletedActivity = TeamLoopActivity.builder()
                        .member(member)
                        .team(teamLoop.getTeam())
                        .teamLoop(teamLoop)
                        .actionType(TeamLoopActivityType.LOOP_COMPLETED)
                        .targetName(teamLoop.getTitle())
                        .build();
                teamLoopActivityRepository.save(loopCompletedActivity);
            }
        }

        return TeamLoopChecklistResponse.builder()
                .id(checklistId)
                .content(myCheck.getChecklist().getContent())
                .isChecked(myCheck.isChecked())
                .build();
    }

    // 체크리스트 현황 조회
    @Override
    @Transactional(readOnly = true)
    public TeamLoopMemberChecklistResponse getChecklistStatus(Long loopId, Long memberId, CurrentUserDto currentUser) {
        TeamLoop teamLoop = getTeamLoopOrThrow(loopId);
        // memberId가 null이면 현재 사용자
        Long targetMemberId = (memberId == null) ? currentUser.id() : memberId;
        Member targetMember = getMemberOrThrow(targetMemberId);
        TeamLoopMemberProgress progress = getMyProgressOrThrow(teamLoop, targetMember);
        List<TeamLoopMemberCheck> checks = teamLoopMemberCheckRepository
                .findByMemberProgressIdOrderByIdAsc(progress.getId());

        // 체크리스트 목록
        List<TeamLoopChecklistResponse> checklistResponses = checks.stream()
                .map(check -> TeamLoopChecklistResponse.builder()
                        .id(check.getChecklist().getId())
                        .content(check.getChecklist().getContent())
                        .isChecked(check.isChecked())
                        .build())
                .toList();

        // 진행률 계산
        int totalChecklistCount = teamLoop.getTeamLoopChecklists().size();
        double progressRate = progress.calculateProgress(totalChecklistCount);

        return TeamLoopMemberChecklistResponse.builder()
                .memberId(targetMember.getId())
                .nickname(targetMember.getNickname())
                .progress(progressRate)
                .checklists(checklistResponses)
                .build();
    }

    // ========== 비즈니스 로직 메서드 ==========
    // 루프 완료 여부 확인
    private boolean isLoopCompleted(TeamLoop teamLoop, Member member) {
        return teamLoop.calculatePersonalStatus(member.getId()) == TeamLoopStatus.COMPLETED;
    }

    // ========== 조회 메서드 ==========
    // 회원 조회
    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceException(ReturnCode.USER_NOT_FOUND));
    }

    // 체크리스트 조회
    private TeamLoopChecklist getChecklistOrThrow(Long checklistId) {
        return teamLoopChecklistRepository.findById(checklistId)
                .orElseThrow(() -> new ServiceException(ReturnCode.NOT_FOUND));
    }

    // 루프 조회
    private TeamLoop getTeamLoopOrThrow(Long loopId) {
        return teamLoopRepository.findById(loopId)
                .orElseThrow(() -> new ServiceException(ReturnCode.NOT_FOUND));
    }

    // TeamLoopMemberProgress 조회
    private TeamLoopMemberProgress getMyProgressOrThrow(TeamLoop teamLoop, Member member) {
        return teamLoopMemberProgressRepository.findByTeamLoopAndMember(teamLoop, member)
                .orElseThrow(() -> new ServiceException(ReturnCode.USER_NOT_IN_TEAM));
    }

    // TeamLoopMemberCheck 조회
    private TeamLoopMemberCheck getMyCheckOrThrow(Long memberId, Long checklistId) {
        return teamLoopMemberCheckRepository.findByMemberIdAndChecklistId(memberId, checklistId)
                .orElseThrow(() -> new ServiceException(ReturnCode.NOT_FOUND));
    }

    // ========== 검증 메서드 ==========
    // 사용자가 팀장인지 검증
    private void validateLeader(TeamLoop teamLoop, Member member) {
        if (!teamLoop.getTeam().getLeader().getId().equals(member.getId())) {
            throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
        }
    }

    // 체크리스트 수정/삭제 권한 검증
    private void validatePermission(TeamLoopChecklist checklist, Long currentUserId) {
        // 체크리스트가 속한 팀 루프 조회
        TeamLoop teamLoop = checklist.getTeamLoop();

        // 공통 루프인 경우 팀장인지 검증
        if (teamLoop.getType() == TeamLoopType.COMMON) {
            if (!teamLoop.getTeam().getLeader().getId().equals(currentUserId)) {
                throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
            }
        }
        // 개인 루프인 경우 주인인지 검증
        else {
            if (checklist.getOwner() == null || !checklist.getOwner().getId().equals(currentUserId)) {
                throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
            }
        }
    }
}
