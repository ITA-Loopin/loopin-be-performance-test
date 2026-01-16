package com.loopone.loopinbe.domain.team.team.service;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.team.team.dto.req.TeamInvitationCreateRequest;
import com.loopone.loopinbe.domain.team.team.dto.res.TeamInvitationResponse;

import java.util.List;

public interface TeamInvitationService {
    List<Long> sendInvitation(Long teamId, TeamInvitationCreateRequest request, CurrentUserDto currentUser);

    void cancelInvitation(Long teamId, Long invitationId, CurrentUserDto currentUser);

    void acceptInvitation(Long invitationId, CurrentUserDto currentUser);

    void rejectInvitation(Long invitationId, CurrentUserDto currentUser);

    List<TeamInvitationResponse> getTeamInvitations(Long teamId, CurrentUserDto currentUser);

    List<TeamInvitationResponse> getMyInvitations(CurrentUserDto currentUser);
}
