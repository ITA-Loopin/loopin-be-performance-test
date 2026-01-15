package com.loopone.loopinbe.domain.team.teamLoop.repository;

import com.loopone.loopinbe.domain.team.team.entity.Team;
import com.loopone.loopinbe.domain.team.teamLoop.entity.TeamLoop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;


public interface TeamLoopRepository extends JpaRepository<TeamLoop, Long> {

    List<TeamLoop> findAllByTeamId(Long teamId);

    List<TeamLoop> findByTeamAndLoopDate(Team team, LocalDate loopDate);

    @Query("""
        SELECT tl
        FROM TeamLoop tl
        WHERE tl.team.id = :teamId AND tl.loopDate = :date
        ORDER BY
            CASE tl.importance
                WHEN 'HIGH' THEN 1
                WHEN 'MIDDLE' THEN 2
                WHEN 'LOW' THEN 3
            END ASC,
            tl.id DESC
    """)
    List<TeamLoop> findAllByTeamIdAndDate(@Param("teamId") Long teamId, @Param("date") LocalDate date);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamLoop tl where tl.team.id in :teamIds")
    int deleteByTeamIds(@Param("teamIds") List<Long> teamIds);

    @Query("""
        select distinct tl.loopRule.id
          from TeamLoop tl
         where tl.team.id in :teamIds
           and tl.loopRule is not null
    """)
    List<Long> findDistinctLoopRuleIdsByTeamIds(@Param("teamIds") List<Long> teamIds);

    @Query("""
        SELECT DISTINCT tl.loopDate
        FROM TeamLoop tl
        WHERE tl.team.id = :teamId
          AND tl.loopDate BETWEEN :startDate AND :endDate
        ORDER BY tl.loopDate ASC
    """)
    List<LocalDate> findTeamLoopDatesByTeamIdAndDateRange(
            @Param("teamId") Long teamId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
