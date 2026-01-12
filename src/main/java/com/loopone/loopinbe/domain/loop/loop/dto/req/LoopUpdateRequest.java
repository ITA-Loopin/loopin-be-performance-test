package com.loopone.loopinbe.domain.loop.loop.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "단일 루프의 수정을 위한 요청 DTO")
public record LoopUpdateRequest(
        @Schema(description = "변경할 루프 제목")
        String title,

        @Schema(description = "변경할 루프 내용")
        String content,

        @Schema(description = "변경할 특정 날짜")
        LocalDate specificDate,

        @Schema(description = "각 루프에 포함될 체크리스트 내용 목록")
        List<String> checklists
) {}

