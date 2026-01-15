package com.loopone.loopinbe.domain.team.teamLoop.entity;

import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.team.team.entity.Team;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopActivityType;
import com.loopone.loopinbe.global.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "team_loop_activity", indexes = {
        @Index(name = "idx_team_created", columnList = "team_id, created_at"),
        @Index(name = "idx_member_created", columnList = "member_id, created_at")
})
public class TeamLoopActivity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_loop_id", nullable = false)
    private TeamLoop teamLoop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TeamLoopActivityType actionType;

    @Column(nullable = false)
    private String targetName;
}
