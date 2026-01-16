package com.loopone.loopinbe.domain.team.team.entity;

import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.team.team.enums.TeamCategory;
import com.loopone.loopinbe.domain.team.team.enums.TeamVisibility;
import com.loopone.loopinbe.global.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class Team extends BaseEntity {

    @Column(nullable = false)
    private String name; // 팀 이름

    @Column(nullable = false)
    private String goal; // 팀 목표

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamCategory category; // 팀 카테고리

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TeamVisibility visibility = TeamVisibility.PRIVATE; // 공개 여부 (기본값 비공개)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id")
    private Member leader; // 팀 리더

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true) // 팀 삭제 시, 소속 정보 삭제
    private List<TeamMember> teamMembers = new ArrayList<>(); // 팀원 목록

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true) // 팀 삭제 시, 초대 정보 삭제
    private List<TeamInvitation> teamInvitations = new ArrayList<>(); // 팀 초대 목록
}