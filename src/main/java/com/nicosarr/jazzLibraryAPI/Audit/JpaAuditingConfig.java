package com.nicosarr.jazzLibraryAPI.Audit;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        // No auth context yet → tag everything as "system".
        // When you add security later, swap this to read from SecurityContextHolder.
        return () -> Optional.of("system");
    }
}