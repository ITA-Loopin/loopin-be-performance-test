package com.loopone.loopinbe.domain.loop.loop.controller;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUser;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCompletionUpdateRequest;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopGroupUpdateRequest;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopUpdateRequest;
import com.loopone.loopinbe.domain.loop.loop.dto.res.DailyLoopsResponse;
import com.loopone.loopinbe.domain.loop.loop.dto.res.LoopCalendarResponse;
import com.loopone.loopinbe.domain.loop.loop.dto.res.LoopDetailResponse;
import com.loopone.loopinbe.domain.loop.loop.service.LoopService;
import com.loopone.loopinbe.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping(value = "/rest-api/v1/loops")
@RequiredArgsConstructor
@Tag(name = "Loop", description = "루프 API")
public class LoopController {
    private final LoopService loopService;

    // 루프 생성
    @PostMapping("")
    @Operation(summary = "루프 생성", description = "새로운 루프를 생성합니다.")
    public ApiResponse<Long> addLoop(
            @RequestBody @Valid LoopCreateRequest loopCreateRequest,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        Long loopId = loopService.createLoop(loopCreateRequest, currentUser);
        return ApiResponse.success(loopId);
    }

    // 루프 상세 조회
    @GetMapping("/{loopId}")
    @Operation(summary = "루프 상세 조회", description = "해당 루프의 상세 정보를 조회합니다.")
    public ApiResponse<LoopDetailResponse> getDetailLoop(
            @PathVariable Long loopId,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        LoopDetailResponse detailLoop = loopService.getDetailLoop(loopId, currentUser);
        return ApiResponse.success(detailLoop);
    }

    //날짜별 루프 리스트 조회
    @GetMapping("/date/{loopDate}")
    @Operation(summary = "날짜별 루프 리스트 조회", description = "해당 날짜의 루프 리스트를 조회합니다.")
    public ApiResponse<DailyLoopsResponse> getDailyLoops(
            @PathVariable LocalDate loopDate,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        DailyLoopsResponse dailyLoops = loopService.getDailyLoops(loopDate, currentUser);
        return ApiResponse.success(dailyLoops);
    }

/*    //루프 전체 리스트 조회
    @GetMapping("/loops")
    @Operation(summary = "루프 리스트 조회", description = "사용자가 생성한 모든 루프를 조회합니다.")
    public ApiResponse<List<LoopSimpleResponse>> getAllLoop(
            @ModelAttribute LoopPage loopPage,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser
    ){
        Pageable pageable = PageRequest.of(loopPage.getPage(), loopPage.getSize());
        return ApiResponse.success(loopService.getAllLoop(pageable, currentUser));
    }*/

    //루프 완료 처리
    @PatchMapping("/{loopId}/completion")
    @Operation(summary = "루프 완료 처리", description = "해당 루프를 완료(진행도 100%) 처리하거나 미완료(진행도 0%) 처리합니다.")
    public ApiResponse<Void> updateLoopCompletion(
            @PathVariable Long loopId,
            @RequestBody @Valid LoopCompletionUpdateRequest loopCompletionUpdateRequest,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser
    ) {
        loopService.updateLoopCompletion(loopId, loopCompletionUpdateRequest, currentUser);
        return ApiResponse.success();
    }

    //단일 루프 수정
    @PutMapping("/{loopId}")
    @Operation(summary = "단일 루프 수정", description = "해당 루프의 정보를 수정합니다. (그룹에서 제외됨)")
    public ApiResponse<Void> updateLoop(
            @PathVariable Long loopId,
            @RequestBody @Valid LoopUpdateRequest loopUpdateRequest,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        loopService.updateLoop(loopId, loopUpdateRequest, currentUser);
        return ApiResponse.success();
    }

    // 루프 그룹 전체 수정
    @PutMapping("/group/{loopRuleId}")
    @Operation(summary = "루프 그룹 전체 수정", description = "해당 그룹의 루프 전체를 수정합니다.")
    public ApiResponse<Void> updateGroupLoop(
            @Parameter(description = "수정할 루프 그룹의 ID") @PathVariable Long loopRuleId,
            @RequestBody @Valid LoopGroupUpdateRequest loopGroupUpdateRequest,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        loopService.updateLoopGroup(loopRuleId, loopGroupUpdateRequest, currentUser);
        return ApiResponse.success();
    }

    // 루프 삭제
    @DeleteMapping("/{loopId}")
    @Operation(summary = "루프 삭제", description = "해당 루프를 삭제합니다.")
    public ApiResponse<Void> deleteLoop(
            @PathVariable Long loopId,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        loopService.deleteLoop(loopId, currentUser);
        return ApiResponse.success();
    }

    // 루프 그룹 삭제
    @DeleteMapping("/group/{loopId}")
    @Operation(summary = "루프 그룹 전체 삭제", description = "해당 그룹의 루프 전체를 삭제합니다. (선택한 날짜와 그 이후의 루프들만 삭제)")
    public ApiResponse<Void> deleteLoopGroup(
            @Parameter(description = "선택한 루프의 ID") @PathVariable Long loopId,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser) {
        loopService.deleteLoopGroup(loopId, currentUser);
        return ApiResponse.success();
    }

    @GetMapping("/calendar")
    @Operation(summary = "루프 캘린더 조회", description = "특정 연도와 월을 기준으로 전월~익월(총 3개월)의 루프 존재 여부를 반환합니다.")
    public ApiResponse<LoopCalendarResponse> getLoopCalendar(
            @RequestParam int year,
            @RequestParam int month,
            @Parameter(hidden = true) @CurrentUser CurrentUserDto currentUser
    ) {
        // 서비스로 연/월 전달
        LoopCalendarResponse response = loopService.getLoopCalendar(year, month, currentUser);
        return ApiResponse.success(response);
    }
}
