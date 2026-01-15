package com.loopone.loopinbe.domain.team.team.serviceImpl;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomService;
import com.loopone.loopinbe.domain.loop.loop.entity.LoopPage;
import com.loopone.loopinbe.domain.team.team.dto.req.TeamCreateRequest;
import com.loopone.loopinbe.domain.team.team.dto.req.TeamOrderUpdateRequest;
import com.loopone.loopinbe.domain.team.team.dto.res.MyTeamResponse;
import com.loopone.loopinbe.domain.team.team.dto.res.RecruitingTeamResponse;
import com.loopone.loopinbe.domain.team.team.dto.res.TeamDetailResponse;
import com.loopone.loopinbe.domain.team.team.dto.res.TeamMemberResponse;
import com.loopone.loopinbe.domain.team.team.entity.Team;
import com.loopone.loopinbe.domain.team.team.entity.TeamMember;
import com.loopone.loopinbe.domain.team.team.entity.TeamPage;
import com.loopone.loopinbe.domain.team.team.mapper.TeamMapper;
import com.loopone.loopinbe.domain.team.team.repository.TeamMemberRepository;
import com.loopone.loopinbe.domain.team.team.repository.TeamRepository;
import com.loopone.loopinbe.domain.team.team.service.TeamService;
import com.loopone.loopinbe.domain.team.teamLoop.entity.TeamLoop;
import com.loopone.loopinbe.domain.team.teamLoop.entity.TeamLoopMemberCheck;
import com.loopone.loopinbe.domain.team.teamLoop.entity.TeamLoopMemberProgress;
import com.loopone.loopinbe.domain.team.teamLoop.repository.TeamLoopChecklistRepository;
import com.loopone.loopinbe.domain.team.teamLoop.repository.TeamLoopMemberCheckRepository;
import com.loopone.loopinbe.domain.team.teamLoop.repository.TeamLoopMemberProgressRepository;
import com.loopone.loopinbe.domain.team.teamLoop.repository.TeamLoopRepository;
import com.loopone.loopinbe.domain.team.teamLoop.service.TeamLoopService;
import com.loopone.loopinbe.global.common.response.PageResponse;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MemberRepository memberRepository;
    private final TeamMapper teamMapper;
    private final TeamLoopRepository teamLoopRepository;
    private final TeamLoopService teamLoopService;
    private final ChatRoomService chatRoomService;

    @Override
    @Transactional
    public Long createTeam(TeamCreateRequest request, CurrentUserDto currentUser) {
        Member leader = getMemberOrThrow(currentUser.id());

        // 팀 엔티티 생성 및 저장
        Team team = saveTeam(request, leader);

        // 팀장을 팀원으로 등록
        saveLeaderAsMember(team, leader);

        // 초대된 멤버들 등록
        List<Member> members = inviteMembers(team, request.invitedNicknames());

        chatRoomService.createTeamChatRoom(currentUser.id(), team, members);

        return team.getId();
    }

    @Override
    public List<MyTeamResponse> getMyTeams(CurrentUserDto currentUser) {
        Member member = getMemberOrThrow(currentUser.id());

        // 내가 속한 팀들과의 연결 정보 조회 (sortOrder 우선, null이면 createdAt DESC)
        List<TeamMember> myTeamMembers = teamMemberRepository.findAllByMemberOrderBySortOrder(member);

        // DTO 변환
        return myTeamMembers.stream()
                .map(teamMapper::toMyTeamResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<RecruitingTeamResponse> getRecruitingTeams(Pageable pageable, CurrentUserDto currentUser) {
        checkPageSize(pageable.getPageSize());
        //모든 팀 조회
        List<Long> myTeamIds = getMyTeamIds(currentUser.id());
        Page<Team> page = myTeamIds.isEmpty()
                ? teamRepository.findAll(pageable)
                : teamRepository.findByIdNotIn(myTeamIds, pageable);

        return PageResponse.of(page.map(teamMapper::toRecruitingTeamResponse)); // 또는 PageResponse.from(page.map(...))
    }

    @Override
    public TeamDetailResponse getTeamDetails(Long teamId, LocalDate targetDate, CurrentUserDto currentUser) {
        Team team = getTeamOrThrow(teamId);

        // 해당 날짜의 팀 전체 루프 조회
        List<TeamLoop> todayLoops = teamLoopRepository.findByTeamAndLoopDate(team, targetDate);

        // 팀 루프 통계 계산
        int totalLoopCount = todayLoops.size();
        double teamTotalProgress = todayLoops.isEmpty() ? 0.0
                : todayLoops.stream()
                        .mapToDouble(TeamLoop::calculateTeamProgress)
                        .average().orElse(0.0);

        // 내 루프 통계 계산
        Long myId = currentUser.id();
        List<TeamLoop> myTeamLoops = todayLoops.stream()
                .filter(loop -> loop.isParticipating(myId))
                .toList();
        int myTeamLoopCount = myTeamLoops.size();
        double myTotalProgress = myTeamLoops.isEmpty() ? 0.0
                : myTeamLoops.stream()
                        .mapToDouble(loop -> loop.calculatePersonalProgress(myId))
                        .average().orElse(0.0);

        return TeamDetailResponse.builder()
                .teamId(team.getId())
                .currentDate(targetDate)
                .name(team.getName())
                .goal(team.getGoal())
                .category(team.getCategory())
                .leaderId(team.getLeader().getId())
                .createdAt(team.getCreatedAt())
                .visibility(team.getVisibility())
                .totalLoopCount(totalLoopCount)
                .teamTotalProgress(teamTotalProgress)
                .myLoopCount(myTeamLoopCount)
                .myTotalProgress(myTotalProgress)
                .build();
    }

    @Override
    public List<TeamMemberResponse> getTeamMembers(Long teamId) {
        Team team = getTeamOrThrow(teamId);

        return team.getTeamMembers().stream()
                .map(tm -> TeamMemberResponse.builder()
                        .memberId(tm.getMember().getId())
                        .nickname(tm.getMember().getNickname())
                        .profileImage(tm.getMember().getProfileImageUrl())
                        .build())
                .collect(Collectors.toList());
    }

    // 팀 순서 변경
    @Override
    @Transactional
    public void updateTeamOrder(TeamOrderUpdateRequest request, CurrentUserDto currentUser) {
        Member member = getMemberOrThrow(currentUser.id());

        Long movingTeamId = request.teamId();
        Integer newPosition = request.newPosition();

        // 내가 속한 팀들과의 연결 정보 조회 (정렬된 순서로)
        List<TeamMember> myTeamMembers = teamMemberRepository.findAllByMemberOrderBySortOrder(member);
        if (myTeamMembers.isEmpty()) {
            throw new ServiceException(ReturnCode.USER_NOT_IN_TEAM);
        }

        // 이동할 팀 찾기
        TeamMember movingTeamMember = myTeamMembers.stream()
                .filter(tm -> tm.getTeam().getId().equals(movingTeamId))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ReturnCode.USER_NOT_IN_TEAM));

        // 현재 위치 찾기
        int currentPosition = myTeamMembers.indexOf(movingTeamMember);
        // 위치가 같으면 아무 작업 안 함
        if (currentPosition == newPosition) {
            return;
        }

        // 리스트에서 제거 후 새 위치에 삽입
        myTeamMembers.remove(currentPosition);
        myTeamMembers.add(newPosition, movingTeamMember);
        // 모든 팀의 sortOrder 재설정 (sortOrder가 null이었던 항목들도 값 할당하기 위해)
        for (int i = 0; i < myTeamMembers.size(); i++) {
            myTeamMembers.get(i).setSortOrder(i);
        }

        teamMemberRepository.saveAll(myTeamMembers);
    }

    // 사용자가 참여중인 모든 팀 나가기/관련 엔티티 삭제
    @Override
    @Transactional
    public void deleteMyTeams(Member member) {
        Long memberId = member.getId();
        // 1) 내가 가입된 팀
        List<TeamMember> myMemberships = teamMemberRepository.findAllByMember(member);
        List<Long> myTeamIds = myMemberships.stream()
                .map(tm -> tm.getTeam().getId())
                .distinct()
                .toList();
        // 2) 내가 리더인 팀 (멤버십에 없어도 반드시 처리해야 함)
        List<Team> myLeaderTeams = teamRepository.findAllByLeaderId(memberId);
        List<Long> leaderTeamIds = myLeaderTeams.stream()
                .map(Team::getId)
                .distinct()
                .toList();
        // 3) 내가 연관된 모든 팀 = (가입한 팀 ∪ 리더인 팀)
        List<Long> allRelatedTeamIds = new ArrayList<>();
        allRelatedTeamIds.addAll(myTeamIds);
        allRelatedTeamIds.addAll(leaderTeamIds);
        allRelatedTeamIds = allRelatedTeamIds.stream().distinct().toList();
        if (allRelatedTeamIds.isEmpty())
            return;
        // 4) 리더 팀 처리: 위임 or 팀 삭제
        List<Long> teamsToDelete = new ArrayList<>();
        for (Team team : myLeaderTeams) {
            // 리더 팀이면 무조건 처리(기존의 myTeamIds.contains(...) 조건 제거)
            teamMemberRepository.findFirstByTeam_IdAndMember_IdNotOrderByIdAsc(team.getId(), memberId)
                    .map(TeamMember::getMember)
                    .ifPresentOrElse(
                            nextLeader -> {
                                team.setLeader(nextLeader);
                                teamRepository.save(team); // 명시적으로 저장
                                teamLoopService.transferTeamLoopRuleOwner(team.getId(), memberId, nextLeader);
                            },
                            () -> teamsToDelete.add(team.getId()));
        }
        // 5) 팀은 남고 나는 나가기만 하는 팀들
        List<Long> remainingTeamIds = allRelatedTeamIds.stream()
                .filter(id -> !teamsToDelete.contains(id))
                .toList();
        // 6) 루프 관련 삭제
        teamLoopService.deleteMyTeamLoops(memberId, teamsToDelete, remainingTeamIds);
        // 7) (A) 팀 전체 삭제
        if (!teamsToDelete.isEmpty()) {
            teamMemberRepository.deleteByTeamIds(teamsToDelete);
            teamRepository.deleteAllByIdInBatch(teamsToDelete);
        }
        // 8) (B) 팀은 남음: 내 TeamMember만 삭제(탈퇴)
        if (!remainingTeamIds.isEmpty()) {
            teamMemberRepository.deleteByMemberAndTeamIds(memberId, remainingTeamIds);
        }
    }

    @Override
    @Transactional
    public void deleteTeam(Long teamId, CurrentUserDto currentUser) {
        Team team = getTeamOrThrow(teamId);

        if (!team.getLeader().getId().equals(currentUser.id())) {
            throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
        }

        chatRoomService.deleteTeamChatRoom(currentUser.id(), teamId);

        List<TeamLoop> loops = teamLoopRepository.findAllByTeamId(teamId);
        teamLoopRepository.deleteAll(loops);

        // 팀 삭제
        teamRepository.delete(team);
    }

    // ========== 비즈니스 로직 메서드 ==========
    // 팀 저장
    private Team saveTeam(TeamCreateRequest request, Member leader) {
        Team team = Team.builder()
                .name(request.name())
                .goal(request.goal())
                .category(request.category())
                .leader(leader)
                .build();
        return teamRepository.save(team);
    }

    // 팀장 멤버 등록
    private void saveLeaderAsMember(Team team, Member leader) {
        TeamMember leaderMember = TeamMember.builder()
                .team(team)
                .member(leader)
                .build();
        teamMemberRepository.save(leaderMember);
    }

    // 멤버 초대 및 등록
    private List<Member> inviteMembers(Team team, List<String> invitedNicknames) {
        if (invitedNicknames == null || invitedNicknames.isEmpty()) {
            return null;
        }

        // 닉네임 리스트로 멤버 한 번에 조회
        List<Member> invitedMembers = memberRepository.findAllByNicknameIn(invitedNicknames);

        // TeamMember 리스트 생성
        List<TeamMember> teamMembers = invitedMembers.stream()
                .map(member -> TeamMember.builder()
                        .team(team)
                        .member(member)
                        .build())
                .collect(Collectors.toList());

        teamMemberRepository.saveAll(teamMembers);

        return invitedMembers;
    }

    // ========== 조회 메서드 ==========
    // 회원 조회
    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceException(ReturnCode.USER_NOT_FOUND));
    }

    // 내가 속한 팀 ID 목록 조회
    private List<Long> getMyTeamIds(Long memberId) {
        return teamMemberRepository.findAllByMemberId(memberId).stream()
                .map(tm -> tm.getTeam().getId())
                .toList();
    }

    // 팀 조회
    private Team getTeamOrThrow(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ServiceException(ReturnCode.TEAM_NOT_FOUND));
    }

    // 요청 페이지 수 제한
    private void checkPageSize(int pageSize) {
        int maxPageSize = TeamPage.getMaxPageSize();
        if (pageSize > maxPageSize) {
            throw new ServiceException(ReturnCode.PAGE_REQUEST_FAIL);
        }
    }

    // ========== 검증 메서드 ==========
    // 팀원 검증
    private void validateTeamMember(Long teamId, Long memberId) {
        if (!teamMemberRepository.existsByTeamIdAndMemberId(teamId, memberId)) {
            throw new ServiceException(ReturnCode.USER_NOT_IN_TEAM);
        }
    }
}
