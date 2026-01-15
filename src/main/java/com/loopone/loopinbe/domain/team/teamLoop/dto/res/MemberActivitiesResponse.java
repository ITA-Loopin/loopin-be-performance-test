package com.loopone.loopinbe.domain.team.teamLoop.dto.res;

import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopActivityType;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopStatus;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Builder
@Schema(description = "팀원 활동 조회 응답 DTO")
public record MemberActivitiesResponse(
        @Schema(description = "팀원별 활동 정보")
        List<MemberActivity> memberActivities,
        @Schema(description = "팀 전체 최근 활동 로그")
        List<TeamActivityLog> recentTeamActivities
) {
    @Builder
    @Schema(description = "개별 팀원 활동 정보")
    public record MemberActivity(
            Long memberId,
            String nickname,

            @Schema(description = "본인 여부")
            boolean isMe,

            @Schema(description = "루프 상태별 개수")
            Map<TeamLoopStatus, Integer> statusStats,

            @Schema(description = "루프 유형별 개수")
            Map<TeamLoopType, Integer> typeStats,

            @Schema(description = "전체 진행률 평균")
            double overallProgress,

            @Schema(description = "가장 최근 활동")
            LastActivity lastActivity
    ) {
        @Builder
        @Schema(description = "최근 활동 정보")
        public record LastActivity(
                TeamLoopActivityType actionType,
                String targetName,
                Instant timestamp
        ) {
        }
    }

    @Builder
    @Schema(description = "팀 전체 활동 로그")
    public record TeamActivityLog(
            Long memberId,
            String nickname,
            TeamLoopActivityType actionType,
            String targetName,
            Instant timestamp
    ) {
    }
}
