package com.loopone.loopinbe.global.initData.notProd.service;

import com.loopone.loopinbe.global.initData.loop.service.NotProdLoopService;
import com.loopone.loopinbe.global.initData.member.service.NotProdMemberService;
import com.loopone.loopinbe.global.initData.team.service.NotProdTeamService;
import com.loopone.loopinbe.global.initData.teamLoop.service.NotProdTeamLoopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotProdService {
    private final NotProdMemberService notProdMemberService;
    private final NotProdLoopService notProdLoopService;
    private final NotProdTeamService notProdTeamService;
    private final NotProdTeamLoopService notProdTeamLoopService;
    private final List<String> memberEmails = new ArrayList<>();

    // 1) 가데이터 생성 메서드
    public void initDummyDataTransactional() {
        notProdMemberService.createMembers(memberEmails);
//        notProdLoopService.createWeekLoops();
//        notProdLoopService.completeScenario_1_1();
//        notProdLoopService.completeScenario_1_2();
        notProdLoopService.createMonthLoops(1, 1);
//        notProdLoopService.completeScenario_2_1();
//        notProdLoopService.completeScenario_2_2();
        NotProdTeamService.SeedTeamsResult seedTeamsResult= notProdTeamService.createTeams();
        notProdTeamLoopService.createTeamLoops(seedTeamsResult.team1Id(), seedTeamsResult.team2Id());
    }

    // 성능 테스트용 가데이터 생성 메서드
    public void initTestDataTransactional() {
        notProdMemberService.createTestMembers(memberEmails);
        notProdLoopService.createMonthLoops(1, 1000);
        NotProdTeamService.SeedTestTeamsResult seedTestTeamsResult = notProdTeamService.createTestTeams();
        notProdTeamLoopService.createTestTeamLoops(seedTestTeamsResult.teamIds());
    }

    // 2) 가데이터 정보 출력
    public void initDummyData() {
        long start = System.currentTimeMillis();
//        initDummyDataTransactional();   // 일반 가데이터
        initTestDataTransactional();  // 성능 테스트용 가데이터
        long end = System.currentTimeMillis();
        long executionTimeMillis = end - start;
        NotProdPrintService.printTestAccounts(
                memberEmails,
                executionTimeMillis
        );
    }
}
