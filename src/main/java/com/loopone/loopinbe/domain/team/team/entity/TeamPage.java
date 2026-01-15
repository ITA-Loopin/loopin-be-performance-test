package com.loopone.loopinbe.domain.team.team.entity;

import lombok.Data;
import lombok.Getter;

@Data
public class TeamPage {
    // 기본 page, size
    private int page = 0;
    private int size = 20;
    @Getter
    private static final int maxPageSize = 20;
}
