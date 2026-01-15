package com.loopone.loopinbe.domain.team.teamLoop.controller;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUser;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.team.teamLoop.dto.req.TeamLoopCreateRequest;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.TeamLoopAllDetailResponse;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.TeamLoopCalendarResponse;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.TeamLoopMyDetailResponse;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.TeamLoopListResponse;
import com.loopone.loopinbe.domain.team.teamLoop.dto.res.MemberActivitiesResponse;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopStatus;
import com.loopone.loopinbe.domain.team.teamLoop.service.TeamLoopService;
import com.loopone.loopinbe.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/rest-api/v1/teams")
@RequiredArgsConstructor
@Tag(name = "Team", description = "팀 루프 API")
public class TeamLoopController {

    private final TeamLoopService teamLoopService;

    @GetMapping("/{teamId}/loops")
    @Operation(summary = "팀 루프 리스트 조회", description = "특정 날짜의 팀 루프 리스트를 조회합니다. status 파라미터로 상태 필터링 가능 (파라메터 없으면 오늘 기준, 전체 상태)")
    public ApiResponse<List<TeamLoopListResponse>> getTeamLoops(
            @PathVariable Long teamId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) @Parameter(description = "루프 상태 필터 (NOT_STARTED/IN_PROGRESS/COMPLETED)") TeamLoopStatus status,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        List<TeamLoopListResponse> response = teamLoopService.getTeamLoops(teamId, targetDate, status, currentUser);
        return ApiResponse.success(response);
    }

    @PostMapping("/{teamId}/loops")
    @Operation(summary = "팀 루프 생성", description = "팀 루프를 생성하고 팀원들에게 할당합니다.")
    public ApiResponse<Long> createTeamLoop(
            @PathVariable Long teamId,
            @RequestBody @Valid TeamLoopCreateRequest request,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        Long loopRuleId = teamLoopService.createTeamLoop(teamId, request, currentUser);
        return ApiResponse.success(loopRuleId);
    }

    @GetMapping("/{teamId}/loops/{loopId}/my")
    @Operation(summary = "팀 루프 상세 조회 (내 루프)", description = "내가 참여 중인 팀 루프의 나의 상세 정보를 조회합니다.")
    public ApiResponse<TeamLoopMyDetailResponse> getTeamLoopMyDetail(
            @PathVariable Long teamId,
            @PathVariable Long loopId,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        TeamLoopMyDetailResponse response = teamLoopService.getTeamLoopMyDetail(teamId, loopId, currentUser);
        return ApiResponse.success(response);
    }

    @GetMapping("/{teamId}/loops/{loopId}/all")
    @Operation(summary = "팀 루프 상세 조회 (팀 루프)", description = "팀 루프의 전체 진행 상황과 팀원별 진행 현황을 조회합니다.")
    public ApiResponse<TeamLoopAllDetailResponse> getTeamLoopDetail(
            @PathVariable Long teamId,
            @PathVariable Long loopId,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        TeamLoopAllDetailResponse response = teamLoopService.getTeamLoopAllDetail(teamId, loopId, currentUser);
        return ApiResponse.success(response);
    }

    @GetMapping("/{teamId}/loops/calendar")
    @Operation(summary = "팀 루프 캘린더 조회", description = "특정 연도와 월을 기준으로 전월~익월(총 3개월)의 팀 루프 존재 여부를 반환합니다.")
    public ApiResponse<TeamLoopCalendarResponse> getTeamLoopCalendar(
            @PathVariable Long teamId,
            @RequestParam int year,
            @RequestParam int month,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        TeamLoopCalendarResponse response = teamLoopService.getTeamLoopCalendar(teamId, year, month, currentUser);
        return ApiResponse.success(response);
    }

    @GetMapping("/{teamId}/member-activities")
    @Operation(summary = "팀 활동 조회", description = "특정 날짜의 팀원별 활동 및 팀 전체 최근 활동 로그를 반환합니다. (파라미터 없으면 오늘 기준)")
    public ApiResponse<MemberActivitiesResponse> getMemberActivities(
            @PathVariable Long teamId,
            @RequestParam(required = false) LocalDate date,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        MemberActivitiesResponse response = teamLoopService.getMemberActivities(teamId, targetDate, currentUser);
        return ApiResponse.success(response);
    }
}
