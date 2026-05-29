package com.amazondemo.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Discovery Server (Eureka)
 * ==========================
 * The service registry for the entire Amazon Demo microservices ecosystem.
 *
 * WHY SERVICE DISCOVERY?
 * - In microservices, services communicate with each other
 * - Instead of hardcoding IP addresses (which change in containers/cloud),
 *   services register by NAME and discover each other dynamically
 *
 * HOW IT WORKS:
 * 1. Every microservice registers itself: "I am product-service at 192.168.1.5:8083"
 * 2. When order-service needs to call product-service, it asks Eureka:
 *    "Where is product-service?" -> Eureka returns the current IP
 * 3. API Gateway uses lb://product-service (load-balanced) to route to it
 *
 * DASHBOARD: http://localhost:8761
 * You can see all registered services and their health status here.
 */
@SpringBootApplication
@EnableEurekaServer  // Turns this app into a Eureka registry server
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
