package com.loopone.loopinbe.global.initData.teamLoop.service;

import com.loopone.loopinbe.domain.account.member.converter.MemberConverter;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.domain.loop.loop.enums.RepeatType;
import com.loopone.loopinbe.domain.team.teamLoop.dto.req.TeamLoopCreateRequest;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopImportance;
import com.loopone.loopinbe.domain.team.teamLoop.enums.TeamLoopType;
import com.loopone.loopinbe.domain.team.teamLoop.service.TeamLoopService;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import com.loopone.loopinbe.global.initData.util.NotProdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotProdTeamLoopService {
    private final TeamLoopService teamLoopService;
    private final MemberConverter memberConverter;
    private final NotProdUtils notProdUtils;

    // 1번 팀에 1번 팀 루프 ("스터디 자료 준비") + 체크리스트 ("자료 조사")
    // 2번 팀에 2번 팀 루프 ("매일 파쿠르 30분") + 체크리스트 ("오후 루틴")
    @Transactional
    public void createTeamLoops(Long team1Id, Long team2Id) {
        Member user1 = notProdUtils.getMemberByEmailOrThrow("user1@example.com");
        Member user2 = notProdUtils.getMemberByEmailOrThrow("user2@example.com");
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusWeeks(1);
        List<DayOfWeek> allDays = Arrays.asList(DayOfWeek.values());
        // 1번 팀 루프: user1이 생성
        teamLoopService.createTeamLoop(
                team1Id,
                new TeamLoopCreateRequest(
                        "스터디 자료 준비",
                        null,
                        RepeatType.WEEKLY,
                        null,          // specificDate (NONE일 때만 사용)
                        allDays,       // daysOfWeek
                        today,         // startDate
                        endDate,          // endDate
                        List.of("자료 조사"),
                        TeamLoopType.COMMON,
                        TeamLoopImportance.MEDIUM,
                        null           // targetMemberIds (INDIVIDUAL일 때만 의미)
                ),
                memberConverter.toCurrentUserDto(user1)
        );
        // 2번 팀 루프: user2가 생성
        teamLoopService.createTeamLoop(
                team2Id,
                new TeamLoopCreateRequest(
                        "어노테이션 공부",
                        null,
                        RepeatType.WEEKLY,
                        null,
                        allDays,
                        today,
                        endDate,
                        List.of("오후 루틴"),
                        TeamLoopType.COMMON,
                        TeamLoopImportance.MEDIUM,
                        null
                ),
                memberConverter.toCurrentUserDto(user2)
        );
        log.info("[NOT_PROD] TeamLoops created. team1Id={}, team2Id={}", team1Id, team2Id);
    }

    // createTestTeams() 구조에 맞춘 대량 TeamLoop 시드
    @Transactional
    public void createTestTeamLoops(List<Long> teamIds) {
        final int MAX_USER = 1000;
        final int GROUP_SIZE = 10;
        final int TEAM_COUNT = MAX_USER / GROUP_SIZE; // 100
        // user1 ~ user1000 로딩
        Map<Integer, Member> members = new HashMap<>(MAX_USER);
        for (int i = 1; i <= MAX_USER; i++) {
            members.put(i, notProdUtils.getMemberByEmailOrThrow("user" + i + "@example.com"));
        }
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusWeeks(1);
        List<DayOfWeek> allDays = Arrays.asList(DayOfWeek.values());
        // 팀 1개당 루프 1개: team1..team100 => Loop1..Loop100
        for (int teamNo = 1; teamNo <= TEAM_COUNT; teamNo++) {
            Long teamId = teamIds.get(teamNo - 1);
            // teamNo=1 -> groupStart=1, teamNo=2 -> 11, ...
            int groupStart = (teamNo - 1) * GROUP_SIZE + 1;
            Member leader = members.get(groupStart);
            teamLoopService.createTeamLoop(
                    teamId,
                    new TeamLoopCreateRequest(
                            "Loop" + teamNo,
                            null,
                            RepeatType.WEEKLY,
                            null,
                            allDays,
                            today,
                            endDate,
                            List.of("Checklist" + teamNo),
                            TeamLoopType.COMMON,
                            TeamLoopImportance.MEDIUM,
                            null
                    ),
                    memberConverter.toCurrentUserDto(leader)
            );
        }
        log.info("[NOT_PROD] createTestTeamLoops done. createdLoops={}", TEAM_COUNT);
    }

}
