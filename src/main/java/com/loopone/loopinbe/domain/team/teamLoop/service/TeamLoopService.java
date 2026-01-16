package com.loopone.loopinbe.domain.team.teamLoop.service;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.team.teamLoop.dto.req.TeamLoopCreateRequest;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.TeamLoopAllDetailResponse;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.TeamLoopCalendarResponse;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.TeamLoopMyDetailResponse;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.TeamLoopListResponse;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.MemberActivitiesResponse;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopStatus;

import java.time.LocalDate;
import java.util.List;

public interface TeamLoopService {
    List<TeamLoopListResponse> getTeamLoops(Long teamId, LocalDate targetDate, TeamLoopStatus statusFilter,
            CurrentUserDto currentUser);

    Long createTeamLoop(Long teamId, TeamLoopCreateRequest request, CurrentUserDto currentUser);

    TeamLoopMyDetailResponse getTeamLoopMyDetail(Long teamId, Long loopId, CurrentUserDto currentUser);

    TeamLoopAllDetailResponse getTeamLoopAllDetail(Long teamId, Long loopId, CurrentUserDto currentUser);

    TeamLoopCalendarResponse getTeamLoopCalendar(Long teamId, int year, int month, CurrentUserDto currentUser);

    void deleteMyTeamLoops(Long memberId, List<Long> teamsToDelete, List<Long> remainingTeamIds);

    void transferTeamLoopRuleOwner(Long teamId, Long oldLeaderId, Member newLeader);

    MemberActivitiesResponse getMemberActivities(Long teamId, LocalDate targetDate, CurrentUserDto currentUser);

    void completeTeamLoop(Long teamId, Long loopId, CurrentUserDto currentUser);
}
