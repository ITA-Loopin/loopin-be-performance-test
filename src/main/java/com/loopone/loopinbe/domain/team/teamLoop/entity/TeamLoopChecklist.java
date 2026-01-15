package com.loopone.loopinbe.domain.team.teamLoop.entity;

import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.team.team.entity.Team;
import com.loopone.loopinbe.global.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class TeamLoopChecklist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_loop_id")
    private TeamLoop teamLoop;

    // 공통(COMMON) -> null (팀장만 허용), 개인(INDIVIDUAL) -> owner 값인 멤버만 허용
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_member_id", nullable = true)
    private Member owner;

    @Column(nullable = false, length = 200)
    private String content;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<TeamLoopMemberCheck> memberChecks = new ArrayList<>();

    public void updateContent(String content) {
        this.content = content;
    }
}
