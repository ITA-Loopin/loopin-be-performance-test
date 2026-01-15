package com.loopone.loopinbe.domain.team.teamLoop.entity;

import com.loopone.loopinbe.global.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
//각 멤버에 대한 팀 루프 체크리스트의 체크 현황
public class TeamLoopMemberCheck extends BaseEntity {
    // 누구의 진행 기록인지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_progress_id")
    private TeamLoopMemberProgress memberProgress;

    // 어떤 체크리스트 항목에 대한 체크인지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_loop_checklist_id")
    private TeamLoopChecklist checklist;

    @Column(nullable = false)
    @Builder.Default
    private boolean isChecked = false;

    public void toggleChecked() {
        this.isChecked = !this.isChecked;
    }
}
