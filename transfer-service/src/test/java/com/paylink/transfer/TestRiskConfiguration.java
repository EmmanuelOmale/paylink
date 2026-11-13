package com.paylink.transfer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.paylink.transfer.support.Sleeper;

@TestConfiguration
public class TestRiskConfiguration {

    @Bean
    @Primary
    ScriptedRiskClient scriptedRiskClient() {
        return new ScriptedRiskClient();
    }

    @Bean
    @Primary
    Sleeper noOpSleeper() {
        return duration -> {
        };
    }
}
