package com.loopone.loopinbe.domain.team.team.repository;

import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.team.team.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    // 특정 멤버 객체로 가입된 팀 목록 조회 (조인으로 팀 정보까지 조회)
    List<TeamMember> findAllByMember(Member member);

    // 특정 멤버 ID로 가입된 팀 목록 조회 (ID만으로 빠르게 조회)
    List<TeamMember> findAllByMemberId(Long memberId);

    // 해당 사용자가 팀 멤버인지 확인
    boolean existsByTeamIdAndMemberId(Long teamId, Long memberId);

    Optional<TeamMember> findFirstByTeam_IdAndMember_IdNotOrderByIdAsc(Long teamId, Long memberId);

    //sortOrder 우선 정렬, null이면 createdAt DESC로 정렬
    @Query("""
        SELECT tm FROM TeamMember tm
        WHERE tm.member = :member
        ORDER BY
            CASE WHEN tm.sortOrder IS NULL THEN 1 ELSE 0 END,
            tm.sortOrder ASC,
            tm.createdAt DESC
    """)
    List<TeamMember> findAllByMemberOrderBySortOrder(@Param("member") Member member);

    // (B) 내 탈퇴용
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamMember tm " +
            "where tm.member.id = :memberId and tm.team.id in :teamIds")
    int deleteByMemberAndTeamIds(@Param("memberId") Long memberId,
                                 @Param("teamIds") List<Long> teamIds);

    // (A) 팀 전체 삭제용(명시적으로 지우고 싶으면)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamMember tm where tm.team.id in :teamIds")
    int deleteByTeamIds(@Param("teamIds") List<Long> teamIds);
}
