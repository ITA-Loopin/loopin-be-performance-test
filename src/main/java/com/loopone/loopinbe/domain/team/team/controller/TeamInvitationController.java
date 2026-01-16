package com.loopone.loopinbe.domain.team.team.controller;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUser;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.team.team.dto.req.TeamInvitationCreateRequest;
import com.loopone.loopinbe.domain.team.team.dto.res.TeamInvitationResponse;
import com.loopone.loopinbe.domain.team.team.service.TeamInvitationService;
import com.loopone.loopinbe.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rest-api/v1/teams")
@RequiredArgsConstructor
@Tag(name = "Team Invitation", description = "팀 초대 API")
public class TeamInvitationController {

    private final TeamInvitationService teamInvitationService;

    @PostMapping("/{teamId}/invitations")
    @Operation(summary = "팀 초대 전송", description = "팀 리더가 멤버를 팀에 초대합니다.")
    public ApiResponse<List<Long>> sendInvitation(
            @PathVariable Long teamId,
            @Valid @RequestBody TeamInvitationCreateRequest request,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        List<Long> invitationIds = teamInvitationService.sendInvitation(teamId, request, currentUser);
        return ApiResponse.success(invitationIds);
    }

    @DeleteMapping("/{teamId}/invitations/{invitationId}")
    @Operation(summary = "팀 초대 취소", description = "팀 리더가 보낸 초대를 취소합니다.")
    public ApiResponse<Void> cancelInvitation(
            @PathVariable Long teamId,
            @PathVariable Long invitationId,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        teamInvitationService.cancelInvitation(teamId, invitationId, currentUser);
        return ApiResponse.success();
    }

    @PostMapping("/invitations/{invitationId}/accept")
    @Operation(summary = "팀 초대 수락", description = "멤버가 받은 초대를 수락합니다.")
    public ApiResponse<Void> acceptInvitation(
            @PathVariable Long invitationId,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        teamInvitationService.acceptInvitation(invitationId, currentUser);
        return ApiResponse.success();
    }

    @PostMapping("/invitations/{invitationId}/reject")
    @Operation(summary = "팀 초대 거절", description = "멤버가 받은 초대를 거절합니다.")
    public ApiResponse<Void> rejectInvitation(
            @PathVariable Long invitationId,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        teamInvitationService.rejectInvitation(invitationId, currentUser);
        return ApiResponse.success();
    }

    @GetMapping("/{teamId}/invitations")
    @Operation(summary = "팀 초대 목록 조회", description = "팀의 대기 중인 초대 목록을 조회합니다. (리더 전용)")
    public ApiResponse<List<TeamInvitationResponse>> getTeamInvitations(
            @PathVariable Long teamId,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        List<TeamInvitationResponse> response = teamInvitationService.getTeamInvitations(teamId, currentUser);
        return ApiResponse.success(response);
    }

    @GetMapping("/my/invitations")
    @Operation(summary = "내가 받은 초대 조회", description = "현재 사용자가 받은 대기 중인 초대 목록을 조회합니다.")
    public ApiResponse<List<TeamInvitationResponse>> getMyInvitations(
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        List<TeamInvitationResponse> response = teamInvitationService.getMyInvitations(currentUser);
        return ApiResponse.success(response);
    }
}
