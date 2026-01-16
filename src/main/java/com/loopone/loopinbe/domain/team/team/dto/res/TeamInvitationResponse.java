package com.loopone.loopinbe.domain.team.team.dto.res;

import com.loopone.loopinbe.domain.team.team.enums.InvitationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Builder
public class TeamInvitationResponse {
    private Long invitationId;
    private Long teamId;
    private String teamName;
    private Long inviterId;
    private String inviterNickname;
    private Long inviteeId;
    private String inviteeNickname;
    private InvitationStatus status;
    private Instant createdAt;
    private LocalDateTime respondedAt;
}
