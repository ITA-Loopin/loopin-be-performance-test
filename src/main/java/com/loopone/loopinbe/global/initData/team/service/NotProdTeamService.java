package com.loopone.loopinbe.global.initData.team.service;

import com.loopone.loopinbe.domain.account.member.converter.MemberConverter;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.domain.team.team.dto.req.TeamCreateRequest;
import com.loopone.loopinbe.domain.team.team.enums.TeamCategory;
import com.loopone.loopinbe.domain.team.team.service.TeamService;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import com.loopone.loopinbe.global.initData.util.NotProdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotProdTeamService {
    private final TeamService teamService;
    private final MemberConverter memberConverter;
    private final NotProdUtils notProdUtils;

    // 1번 유저가 1번 팀 생성 + 2번 유저 초대
    // 2번 유저가 2번 팀 생성 + 1번 유저 초대
    @Transactional
    public SeedTeamsResult createTeams() {
        Member user1 = notProdUtils.getMemberByEmailOrThrow("user1@example.com");
        Member user2 = notProdUtils.getMemberByEmailOrThrow("user2@example.com");
        Member user3 = notProdUtils.getMemberByEmailOrThrow("user3@example.com");
        // 에펙 마스터 팀: user1(리더) -> user2 초대
        Long team1Id = teamService.createTeam(
                new TeamCreateRequest(
                        TeamCategory.ROUTINE,
                        "에펙 마스터",
                        "3개월 동안 에펙 초보 탈출하기",
                        List.of(user2.getNickname()) // invitedNicknames
                ),
                memberConverter.toCurrentUserDto(user1)
        );
        // 스프링 정복하기 팀: user2(리더) -> user1 초대
        Long team2Id = teamService.createTeam(
                new TeamCreateRequest(
                        TeamCategory.ROUTINE,
                        "스프링 정복하기",
                        "3개월 동안 스프링 개발해보기",
                        List.of(user1.getNickname())
                ),
                memberConverter.toCurrentUserDto(user2)
        );
        // 정처기 도전 팀: user3(리더) -> user1 초대
        Long team3Id = teamService.createTeam(
                new TeamCreateRequest(
                        TeamCategory.ROUTINE,
                        "정처기 도전",
                        "정처기 합격하기",
                        List.of(user2.getNickname())
                ),
                memberConverter.toCurrentUserDto(user3)
        );
        log.info("[NOT_PROD] Teams created. team1Id={}, team2Id={}, team3Id={}", team1Id, team2Id, team3Id);
        return new SeedTeamsResult(team1Id, team2Id, team3Id);
    }

    @Transactional
    public SeedTestTeamsResult createTestTeams() {
        final int MAX_USER = 1000;
        final int GROUP_SIZE = 10;
        final int TEAM_COUNT = MAX_USER / GROUP_SIZE; // 100
        // 1) user1 ~ user1000 미리 로딩
        Map<Integer, Member> members = new HashMap<>(MAX_USER);
        for (int i = 1; i <= MAX_USER; i++) {
            members.put(i, notProdUtils.getMemberByEmailOrThrow("user" + i + "@example.com"));
        }
        // 2) team 100개 생성 (groupStart: 1, 11, 21, ... 991)
        List<Long> teamIds = new ArrayList<>(TEAM_COUNT);
        int teamNo = 1;
        for (int groupStart = 1; groupStart <= MAX_USER; groupStart += GROUP_SIZE) {
            Member leader = members.get(groupStart);
            int finalGroupStart = groupStart;
            // 그룹 멤버(10명) 중 리더 제외 9명 초대
            List<String> invitedNicknames = IntStream.range(groupStart, groupStart + GROUP_SIZE)
                    .filter(n -> n != finalGroupStart)
                    .mapToObj(n -> members.get(n).getNickname())
                    .toList();
            Long teamId = teamService.createTeam(
                    new TeamCreateRequest(
                            TeamCategory.ROUTINE,
                            "team" + teamNo,
                            "goal" + teamNo,
                            invitedNicknames
                    ),
                    memberConverter.toCurrentUserDto(leader)
            );
            teamIds.add(teamId);
            teamNo++;
        }
        log.info("[NOT_PROD] createTestTeams done. createdTeams={}", teamIds.size());
        return new SeedTestTeamsResult(teamIds);
    }

    public record SeedTeamsResult(Long team1Id, Long team2Id, Long team3Id) {}
    public record SeedTestTeamsResult(List<Long> teamIds) {}
}
