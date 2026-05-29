package com.amazondemo.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing Configuration
 * Enables automatic population of @CreatedDate and @LastModifiedDate fields.
 * Any entity with @EntityListeners(AuditingEntityListener.class) will have
 * these fields auto-filled on save.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
