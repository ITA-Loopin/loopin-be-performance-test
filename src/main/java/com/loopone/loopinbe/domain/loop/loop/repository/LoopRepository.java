package com.loopone.loopinbe.domain.loop.loop.repository;

import com.loopone.loopinbe.domain.loop.loop.entity.Loop;
import com.loopone.loopinbe.domain.loop.loop.entity.LoopRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoopRepository extends JpaRepository<Loop, Long> {
    // ========== 멤버 관련 조회 ==========
    //특정 멤버의 모든 루프 목록을 조회
    @Query("""
        SELECT m
        FROM Loop m
        WHERE m.member.id = :memberId
        ORDER BY m.loopDate DESC, m.createdAt DESC
    """)
    Page<Loop> findByMemberIdWithOrder(@Param("memberId") Long memberId, Pageable pageable);

    //특정 멤버의 특정 날짜에 해당하는 모든 루프 목록을 조회
    List<Loop> findByMemberIdAndLoopDate(Long memberId, LocalDate loopDate);

    // ========== LoopRule 관련 조회 ==========
    //LoopRule 객체로 첫 번째 루프 찾기
    Optional<Loop> findFirstByLoopRule(LoopRule loopRule);

    // loopRuleId로 오늘 포함 가장 가까운 미래의 루프 찾기 (AI 업데이트 프롬프트용)
    @Query("""
        SELECT DISTINCT l
        FROM Loop l
        LEFT JOIN FETCH l.loopChecklists
        LEFT JOIN FETCH l.loopRule lr
        WHERE l.loopRule.id = :loopRuleId
          AND l.loopDate >= :date
        ORDER BY l.loopDate ASC
        LIMIT 1
    """)
    Optional<Loop> findFirstByLoopRuleIdAndLoopDateGreaterThanEqualOrderByLoopDateAsc(@Param("loopRuleId") Long loopRuleId, @Param("date") LocalDate date);

    //loopRule에 속한 루프 전체를 리스트로 조회 (오늘 포함 미래만 조회)
    @Query("""
        SELECT l
        FROM Loop l
        WHERE l.loopRule = :loopRule AND l.loopDate >= :date
    """)
    List<Loop> findAllByLoopRuleAndLoopDateAfter(@Param("loopRule") LoopRule loopRule, @Param("date") LocalDate date);

    //loopRule에 속한 과거의 루프 전체를 리스트로 조회
    @Query("""
        SELECT l
        FROM Loop l
        WHERE l.loopRule = :loopRule AND l.loopDate < :date
    """)
    List<Loop> findAllByLoopRuleAndLoopDateBefore(@Param("loopRule") LoopRule loopRule, @Param("date") LocalDate date);

    // loopReport에서 조회
    @EntityGraph(attributePaths = {"loopChecklists", "loopRule", "loopRule.daysOfWeek"})
    @Query("""
    select distinct l
    from Loop l
    where l.member.id = :memberId
      and l.loopRule is not null
      and l.loopDate between :start and :end
    """)
    List<Loop> findRepeatLoopsByMemberAndDateBetween(
            @Param("memberId") Long memberId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    // loopReport 시나리오 검증 테스트
    Optional<Loop> findFirstByMember_IdAndTitleAndLoopDate(Long memberId, String title, LocalDate loopDate);

    // 멤버가 만든 모든 루프 조회
    List<Loop> findAllByMemberId(Long memberId);

    // 특정 기간 내에 내 루프가 존재하는 날짜만 조회
    @Query("""
        SELECT DISTINCT l.loopDate FROM Loop l
        WHERE l.member.id = :memberId
        AND l.loopDate BETWEEN :startDate AND :endDate
    """)
    List<LocalDate> findLoopDatesByMemberIdAndDateRange(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
