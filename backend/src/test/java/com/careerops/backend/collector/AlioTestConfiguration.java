package com.careerops.backend.collector;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class AlioTestConfiguration {

    @Bean
    @Primary
    FixtureAlioJobClient fixtureAlioJobClient() {
        return new FixtureAlioJobClient();
    }

}
