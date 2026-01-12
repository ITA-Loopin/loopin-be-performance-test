package com.loopone.loopinbe.domain.loop.loop.dto.req;

import com.loopone.loopinbe.domain.loop.loop.enums.RepeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "루프 그룹 전체의 수정을 위한 요청 DTO")
public record LoopGroupUpdateRequest(
        @Schema(description = "변경할 루프 제목")
        @NotBlank
        String title,

        @Schema(description = "변경할 루프 내용")
        String content,

        @Schema(description = "변경할 반복 규칙 (NONE, WEEKLY, MONTHLY, YEARLY)")
        @NotNull
        RepeatType scheduleType,

        @Schema(description = "scheduleType이 NONE일 때 사용할 특정 날짜")
        LocalDate specificDate,

        @Schema(description = "scheduleType이 WEEKLY일 때 사용할 요일 목록")
        List<DayOfWeek> daysOfWeek,

        @Schema(description = "반복 시작일 (WEEKLY, MONTHLY, YEARLY일 때 사용, 기본값은 당일로 설정)")
        LocalDate startDate,

        @Schema(description = "반복 종료일 (WEEKLY, MONTHLY, YEARLY일 때 사용, null이면 1년 후로 설정)")
        LocalDate endDate,

        @Schema(description = "각 루프에 포함될 체크리스트 내용 목록")
        List<String> checklists,

        @Schema(description = "채팅방 ID (AI 채팅방 등에서 루프 수정 시 사용, null 가능)")
        Long chatRoomId
) {}
