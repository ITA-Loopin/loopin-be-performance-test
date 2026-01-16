package com.loopone.loopinbe.global.initData.notProd;

import com.loopone.loopinbe.global.initData.notProd.service.NotProdService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
public class NotProd {
    @Bean
    public ApplicationRunner applicationRunner(NotProdService notProdService) {
        return args -> notProdService.initDummyData();
    }
}
