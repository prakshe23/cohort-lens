package com.cohortlens.app.config;

import com.cohortlens.core.AnalyticsService;
import com.cohortlens.core.ImportProcessor;
import com.cohortlens.core.Pseudonymizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework free core classes into Spring. */
@Configuration
public class AppConfig {

    /** Fails at startup if the secret is missing or too short, instead of running with a weak default. */
    @Bean
    public Pseudonymizer pseudonymizer(@Value("${cohortlens.pseudonym-secret}") String secret) {
        return new Pseudonymizer(secret);
    }

    @Bean
    public ImportProcessor importProcessor(Pseudonymizer pseudonymizer) {
        return new ImportProcessor(pseudonymizer);
    }

    @Bean
    public AnalyticsService analyticsService(@Value("${cohortlens.min-cell-size}") int minCell) {
        return new AnalyticsService(minCell);
    }
}
