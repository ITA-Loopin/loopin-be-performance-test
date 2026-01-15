package com.loopone.loopinbe.domain.team.team.dto.res;

import com.loopone.loopinbe.domain.team.team.enums.TeamCategory;
import com.loopone.loopinbe.domain.team.team.enums.TeamVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

@Builder
@Schema(description = "팀 상세 정보 응답 DTO")
public record TeamDetailResponse(
                @Schema(description = "팀 ID") Long teamId,

                @Schema(description = "현재 날짜") LocalDate currentDate,

                @Schema(description = "팀 카테고리") TeamCategory category,

                @Schema(description = "팀 이름") String name,

                @Schema(description = "팀 목표") String goal,

                @Schema(description = "리더 ID") Long leaderId,

                @Schema(description = "팀 생성일자") Instant createdAt,

                @Schema(description = "공개 여부") TeamVisibility visibility,

                @Schema(description = "[팀 루프] 전체 루프 개수") int totalLoopCount,

                @Schema(description = "[팀 루프] 팀 전체 평균 진행률") double teamTotalProgress,

                @Schema(description = "[내 루프] 내가 참여하는 루프 개수") int myLoopCount,

                @Schema(description = "[내 루프] 나의 평균 진행률") double myTotalProgress) {
}