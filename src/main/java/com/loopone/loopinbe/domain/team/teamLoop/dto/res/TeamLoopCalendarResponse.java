package com.loopone.loopinbe.domain.team.teamLoop.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
@Schema(description = "팀 루프 캘린더 조회 응답 DTO")
public record TeamLoopCalendarResponse(
        @Schema(description = "팀 이름")
        String teamName,
        @Schema(description = "조회한 날짜 리스트")
        List<CalendarDay> days
) {
    @Builder
    public record CalendarDay(
            @Schema(description = "날짜")
            LocalDate date,
            @Schema(description = "팀 루프 존재 여부")
            boolean hasTeamLoop
    ) {
    }
}