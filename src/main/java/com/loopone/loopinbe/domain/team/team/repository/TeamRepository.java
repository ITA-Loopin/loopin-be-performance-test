package com.loopone.loopinbe.domain.team.team.repository;

import com.loopone.loopinbe.domain.team.team.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    @Query("select t from Team t where t.leader.id = :leaderId")
    List<Team> findAllByLeaderId(@Param("leaderId") Long leaderId);

    // 내가 속한 팀 제외하고 페이징
    Page<Team> findByIdNotIn(List<Long> excludedIds, Pageable pageable);

    // excludedIds가 비어있을 때는 전체 페이징
    Page<Team> findAll(Pageable pageable);
}
