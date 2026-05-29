# Amazon Demo - Project Overview

## What Is This Project?

**Amazon Demo** is a production-ready e-commerce microservices application built for **learning purposes**. It implements the same architectural patterns used by companies like Amazon, Netflix, and Uber in their real systems.

This is NOT a toy project. Every feature is implemented the way it would be in a real production system.

## Why Was This Built?

To provide developers with a **complete reference implementation** of:
- Java Spring Boot microservices
- Event-driven architecture
- CQRS (Command Query Responsibility Segregation)
- JWT authentication
- Distributed systems patterns

## Technology Choices

| Technology | Why |
|------------|-----|
| Java 17 | LTS version, modern features (records, sealed classes, pattern matching) |
| Spring Boot 3.3 | Industry standard, auto-configuration, vast ecosystem |
| Spring Cloud | Service discovery, config server, circuit breakers |
| PostgreSQL | ACID-compliant relational DB for write operations |
| MongoDB | Flexible document store for read-optimized queries |
| Redis | In-memory cache for sub-millisecond reads |
| Kafka | High-throughput event streaming for order/product events |
| RabbitMQ | Reliable message queuing for payment notifications |
| Docker | Containerization for consistent environments |
| Kubernetes | Container orchestration for scaling |
| React + Vite | Modern, fast frontend with excellent developer experience |
| Android (Java) | Native mobile app with clean architecture |

## System Architecture

```
Client (Browser/Mobile)
        ↓
   API Gateway (8080) ← JWT validation, CORS, Rate limiting, Routing
        ↓
┌────────────────────────────────────────┐
│           Service Mesh                  │
│                                        │
│  Auth Service (8081)                   │
│  User Service (8082)                   │
│  Product Service (8083) ← CQRS        │
│  Inventory Service (8084)              │
│  Order Service (8085) ← Event-driven  │
│  Payment Service (8086) ← Dummy        │
│  Notification Service (8087) ← Events  │
│  Batch Service (8088) ← Scheduled      │
└────────────────────────────────────────┘
        ↓
Infrastructure:
- PostgreSQL (5432) - Relational data
- MongoDB (27017)   - Document data  
- Redis (6379)      - Cache
- Kafka (9092)      - Event streaming
- RabbitMQ (5672)   - Message queuing
- Zipkin (9411)     - Distributed tracing
- LocalStack (4566) - AWS services (S3, SQS)
```

## Ports Quick Reference

| Service | Port | URL |
|---------|------|-----|
| API Gateway | 8080 | http://localhost:8080 |
| Auth Service | 8081 | http://localhost:8081 |
| User Service | 8082 | http://localhost:8082 |
| Product Service | 8083 | http://localhost:8083 |
| Inventory Service | 8084 | http://localhost:8084 |
| Order Service | 8085 | http://localhost:8085 |
| Payment Service | 8086 | http://localhost:8086 |
| Notification Service | 8087 | http://localhost:8087 |
| Batch Service | 8088 | http://localhost:8088 |
| Config Server | 8888 | http://localhost:8888 |
| Eureka Dashboard | 8761 | http://localhost:8761 |
| Kafka UI | 8090 | http://localhost:8090 |
| RabbitMQ UI | 15672 | http://localhost:15672 |
| Zipkin UI | 9411 | http://localhost:9411 |
| Mailhog UI | 8025 | http://localhost:8025 |
| Frontend | 3000 | http://localhost:3000 |
