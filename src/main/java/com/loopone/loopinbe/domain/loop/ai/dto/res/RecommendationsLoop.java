package com.loopone.loopinbe.domain.loop.ai.dto.res;

import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;

import java.util.List;

public record RecommendationsLoop(
        String title,
        Long loopRuleId,
        List<LoopCreateRequest> recommendations
) {
}
