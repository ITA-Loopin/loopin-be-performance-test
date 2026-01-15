package com.loopone.loopinbe.domain.team.team.controller;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUser;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.team.team.dto.req.TeamCreateRequest;
import com.loopone.loopinbe.domain.team.team.dto.req.TeamOrderUpdateRequest;
import com.loopone.loopinbe.domain.team.team.dto.res.MyTeamResponse;
import com.loopone.loopinbe.domain.team.team.dto.res.RecruitingTeamResponse;
import com.loopone.loopinbe.domain.team.team.dto.res.TeamDetailResponse;
import com.loopone.loopinbe.domain.team.team.dto.res.TeamMemberResponse;
import com.loopone.loopinbe.domain.team.team.entity.TeamPage;
import com.loopone.loopinbe.domain.team.team.service.TeamService;
import com.loopone.loopinbe.global.common.response.ApiResponse;
import com.loopone.loopinbe.global.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/rest-api/v1/teams")
@RequiredArgsConstructor
@Tag(name = "Team", description = "팀 API")
public class TeamController {

    private final TeamService teamService;

    @PostMapping("/")
    @Operation(summary = "팀 생성", description = "새로운 팀을 생성하고 팀원을 초대합니다.")
    public ApiResponse<Long> createTeam(
            @Valid @RequestBody TeamCreateRequest request,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser
    ) {
        Long teamId = teamService.createTeam(request, currentUser);
        return ApiResponse.success(teamId);
    }

    @GetMapping("/my")
    @Operation(summary = "내 팀 리스트 조회", description = "내가 참여 중인 팀 리스트를 조회합니다.")
    public ApiResponse<List<MyTeamResponse>> getMyTeams(
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser
    ) {
        List<MyTeamResponse> response = teamService.getMyTeams(currentUser);
        return ApiResponse.success(response);
    }

    @GetMapping("/recruiting")
    @Operation(summary = "모집 중인 팀 리스트 조회", description = "참여 가능한 다른 팀 리스트를 조회합니다.")
    public ApiResponse<List<RecruitingTeamResponse>> getRecruitingTeams(
            @ModelAttribute TeamPage request,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser
    ) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        PageResponse<RecruitingTeamResponse> response = teamService.getRecruitingTeams(pageable, currentUser);
        return ApiResponse.success(response);
    }

    @GetMapping("/{teamId}")
    @Operation(summary = "팀 상세 조회", description = "팀 상세 정보와 해당 날짜 기준의 진행률을 조회합니다.")
    public ApiResponse<TeamDetailResponse> getTeamDetail(
            @PathVariable Long teamId,
            @RequestParam(required = false) LocalDate date,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser
    ) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        TeamDetailResponse response = teamService.getTeamDetails(teamId, targetDate, currentUser);
        return ApiResponse.success(response);
    }

    @GetMapping("/{teamId}/members")
    @Operation(summary = "팀원 리스트 조회", description = "해당 팀에 소속된 팀원 목록을 조회합니다.")
    public ApiResponse<List<TeamMemberResponse>> getTeamMembers(
            @PathVariable Long teamId
    ) {
        List<TeamMemberResponse> response = teamService.getTeamMembers(teamId);
        return ApiResponse.success(response);
    }

    @PutMapping("/order")
    @Operation(summary = "내 팀 목록 순서 변경", description = "드래그로 변경한 팀 목록 순서를 저장합니다.")
    public ApiResponse<Void> updateTeamOrder(
            @Valid @RequestBody TeamOrderUpdateRequest request,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser
    ) {
        teamService.updateTeamOrder(request, currentUser);
        return ApiResponse.success();
    }

    @DeleteMapping("/{teamId}")
    @Operation(summary = "팀 삭제", description = "팀 리더만 삭제 가능합니다. 팀, 팀루프, 팀채팅방이 삭제됩니다.")
    public ApiResponse<Void> deleteTeam(
            @CurrentUser CurrentUserDto currentUser,
            @PathVariable Long teamId
    ) {
        teamService.deleteTeam(teamId, currentUser);
        return ApiResponse.success();
    }
}
