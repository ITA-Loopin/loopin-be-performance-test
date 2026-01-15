package com.loopone.loopinbe.domain.team.teamLoop.repository;

import com.loopone.loopinbe.domain.team.teamLoop.entity.TeamLoopActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamLoopActivityRepository extends JpaRepository<TeamLoopActivity, Long> {

    // 특정 멤버의 가장 최근 활동 조회
    Optional<TeamLoopActivity> findFirstByMemberIdOrderByCreatedAtDesc(Long memberId);

    // 특정 팀의 최근 N개 활동 조회
    @Query("SELECT a FROM TeamLoopActivity a WHERE a.team.id = :teamId ORDER BY a.createdAt DESC")
    List<TeamLoopActivity> findRecentActivitiesByTeamId(@Param("teamId") Long teamId,
            org.springframework.data.domain.Pageable pageable);
}
