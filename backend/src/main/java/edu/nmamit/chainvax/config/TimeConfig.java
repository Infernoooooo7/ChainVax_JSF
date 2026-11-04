package edu.nmamit.chainvax.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    /** Injectable clock so time-based rules can be unit-tested with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
