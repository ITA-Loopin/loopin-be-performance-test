package com.loopone.loopinbe.domain.team.teamLoop.dto.res;

import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopImportance;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopStatus;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;

@Builder
@Schema(description = "오늘의 팀 루프 목록 응답 DTO")
public record TeamLoopListResponse(
                Long id,
                String title,
                LocalDate loopDate,

                @Schema(description = "나의 루프 상태 (시작전/진행중/완료됨)") TeamLoopStatus personalStatus,

                @Schema(description = "팀 전체 루프 상태 (시작전/진행중/완료됨)") TeamLoopStatus teamStatus,

                @Schema(description = "루프 유형 (COMMON/INDIVIDUAL)") TeamLoopType type,

                @Schema(description = "루프 중요도") TeamLoopImportance importance,

                @Schema(description = "팀 전체 진행률") double teamProgress,

                @Schema(description = "나의 진행률 (미참여시 0.0)") double personalProgress,

                @Schema(description = "루프의 참여자인지") boolean isParticipating,

                @Schema(description = "반복 주기 문자열 (예: '매주 월금', '매월 1일', '없음')") String repeatCycle) {
}