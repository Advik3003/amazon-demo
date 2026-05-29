package com.amazondemo.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Config Server Application
 * ==========================
 * The Config Server acts as a centralized configuration store for all microservices.
 *
 * WHY CENTRALIZED CONFIG?
 * - Without it, each service has its own application.yml - hard to manage 10+ services
 * - Change a DB password in ONE place, all services pick it up automatically
 * - Environment-specific configs (dev, stage, prod) in one repository
 * - Supports runtime config refresh without restarting services (@RefreshScope)
 *
 * HOW IT WORKS:
 * 1. Config Server reads config files from a Git repo (or local folder)
 * 2. Other services call Config Server on startup: GET /application/default
 * 3. Services get their configs and start up
 *
 * CONFIG FILE NAMING CONVENTION:
 * - application.yml          -> Common config for all services
 * - auth-service.yml         -> Config specific to auth-service
 * - auth-service-dev.yml     -> Config for auth-service in dev environment
 * - auth-service-prod.yml    -> Config for auth-service in prod environment
 */
@SpringBootApplication
@EnableConfigServer        // Activates Config Server functionality
@EnableDiscoveryClient     // Registers with Eureka for service discovery
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
