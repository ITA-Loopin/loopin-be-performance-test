package com.loopone.loopinbe.domain.team.team.repository;

import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.team.team.entity.Team;
import com.loopone.loopinbe.domain.team.team.entity.TeamInvitation;
import com.loopone.loopinbe.domain.team.team.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {

    boolean existsByTeamAndInviteeAndStatus(Team team, Member invitee, InvitationStatus status);

    List<TeamInvitation> findByTeamAndStatus(Team team, InvitationStatus status);

    List<TeamInvitation> findByInviteeAndStatus(Member invitee, InvitationStatus status);
}
