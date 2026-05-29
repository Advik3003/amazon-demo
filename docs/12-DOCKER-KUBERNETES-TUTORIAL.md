# Docker & Kubernetes Tutorial

> **What you'll learn**: How Docker works under the hood, multi-stage builds,
> Docker Compose orchestration, Kubernetes manifests, and interview preparation.

---

## 1. Docker Fundamentals

### What Docker does

```
Without Docker:
  Developer machine: Java 17, PostgreSQL 14, runs fine
  Server: Java 11, PostgreSQL 12, app FAILS
  "Works on my machine" problem

With Docker:
  App + JRE + config = Image (portable, self-contained)
  Same image runs identically everywhere
```

### Key concepts

```
IMAGE      → Blueprint. Immutable. Like a class definition.
             "I need JRE 17 + my app.jar + config"

CONTAINER  → Running instance of an image. Like an object instance.
             Many containers from one image.

LAYER      → Each Dockerfile instruction creates a layer (cached).
             Only changed layers are rebuilt/re-downloaded.

VOLUME     → Persistent storage that survives container restarts.
             Data written to / inside container is lost on stop.

NETWORK    → Virtual network. Containers communicate by service name.
             "postgres" resolves to postgres container's IP.

REGISTRY   → Where images are stored. Docker Hub = public registry.
```

### Dockerfile — Multi-stage Build

```dockerfile
# ===================================
# STAGE 1: Build stage (temporary)
# ===================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
# This image has Maven + JDK — but we don't need it in production

WORKDIR /build

# Copy POM first (better layer caching)
# If pom.xml doesn't change, Maven downloads are cached
COPY pom.xml .
RUN mvn dependency:go-offline -q   # Download all deps (cached layer)

# Now copy source (changes frequently)
COPY src ./src
RUN mvn package -DskipTests -q     # Build the JAR


# ===================================
# STAGE 2: Runtime stage (final image)
# ===================================
FROM eclipse-temurin:17-jre-alpine
# Only JRE needed — much smaller (no Maven, no JDK, no source code)
# Alpine Linux: ~5MB base vs Ubuntu ~180MB

WORKDIR /app

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy ONLY the built JAR from stage 1
COPY --from=builder /build/target/auth-service-1.0.0.jar app.jar

# Document which port the app uses (not actually published — just documentation)
EXPOSE 8081

# Health check — Docker uses this to determine container health
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

# Start the app with tuned JVM settings
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \       # Respect container memory limits
    "-XX:MaxRAMPercentage=75.0", \      # Use max 75% of container RAM for heap
    "-Djava.security.egd=file:/dev/./urandom", \  # Faster random (entropy)
    "-jar", "app.jar"]
```

**Why multi-stage builds?**
```
Single-stage build size:  ~500MB (includes JDK + Maven + source)
Multi-stage build size:   ~180MB (JRE + JAR only)
Smaller image = faster pull, less attack surface, less storage
```

### Simplified Dockerfile (what we use for dev speed)

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/auth-service-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 2. Docker Compose

### What it solves

Starting 10+ services manually:
```bash
docker run -d --name postgres -e POSTGRES_PASSWORD=... -p 5432:5432 postgres
docker run -d --name redis -p 6379:6379 redis
docker run -d --name kafka ...
# ... 10 more commands, managing networks, volumes, order ...
```

With Docker Compose:
```bash
docker compose up -d   # One command to rule them all
```

### Key docker-compose.yml concepts

```yaml
services:

  postgres:
    image: postgres:16-alpine         # Pre-built image from Docker Hub
    container_name: amazon-demo-postgres
    environment:
      POSTGRES_PASSWORD: postgres     # Environment variables
    volumes:
      - postgres_data:/var/lib/postgresql/data   # Named volume (persistent)
      - ./init.sh:/docker-entrypoint-initdb.d/   # Bind mount (init script)
    ports:
      - "5432:5432"   # HOST:CONTAINER — exposes to your machine
    networks:
      - amazon-demo-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  auth-service:
    build:
      context: ./backend/auth-service  # Build from Dockerfile in this directory
      dockerfile: Dockerfile
    depends_on:
      postgres:
        condition: service_healthy   # Wait until postgres is healthy
      redis:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/auth_db
      #                                        ^^^^^^^^
      #                                 Docker service name — resolves to container IP
    ports:
      - "8081:8081"   # Exposed to host (for direct access in dev)
                      # Other containers use internal port via network
    networks:
      - amazon-demo-network

volumes:
  postgres_data:      # Named volume — data persists across container restarts
  redis_data:

networks:
  amazon-demo-network:
    driver: bridge    # Default bridge network — containers can communicate
```

### Docker Compose commands

```bash
# Start all services in background
docker compose up -d

# Start and rebuild images
docker compose up -d --build

# Stop all services (preserve volumes/data)
docker compose down

# Stop and delete everything including volumes (DELETES DATA)
docker compose down -v

# View logs for a service
docker compose logs auth-service --follow

# Execute command inside a running container
docker compose exec postgres psql -U postgres -c "\l"

# Scale a service to 3 instances
docker compose up -d --scale product-service=3

# Check service status
docker compose ps

# Show resource usage
docker stats
```

### Health check dependency chain

```yaml
# Services start in this order because of depends_on:

postgres ──healthcheck→ config-server ──healthcheck→ discovery-server
redis    ──────────────────────────────────────────────────────────────────┐
kafka    ──healthcheck→                                                    │
rabbitmq ──healthcheck→                                                    │
                                                                           ▼
                                          auth-service, product-service, order-service, ...
                                          (all depend on discovery-server being healthy)
```

---

## 3. Kubernetes (K8s) Fundamentals

### Why Kubernetes?

Docker Compose is great for local development but NOT for production:
- No auto-restart if container crashes and VM dies
- No scaling (can't add instances dynamically)
- No rolling deployments (restart = downtime)
- No self-healing

Kubernetes solves all of this.

### Core Kubernetes Objects

```
NODE          → A machine (VM or physical) that runs containers
CLUSTER       → Group of nodes managed together

POD           → Smallest unit. One or more containers running together.
                Like a "wrapper" around containers.
                Gets its own IP. Ephemeral (can die anytime).

DEPLOYMENT    → Manages a set of identical pods (replicas).
                Ensures desired number always running.
                Handles rolling updates.

SERVICE       → Stable endpoint for pods.
                Pods come and go (changing IPs), Service IP stays fixed.
                Load balances across pods.

CONFIGMAP     → Key-value config (non-sensitive: app settings)
SECRET        → Sensitive config (passwords, keys) — base64 encoded

INGRESS       → HTTP routing rules for external traffic
                Like API Gateway but at the K8s level

NAMESPACE     → Virtual cluster within a cluster
                dev-namespace, stage-namespace, prod-namespace
```

### Deployment Manifest

```yaml
# k8s/auth-service/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: amazon-demo
  labels:
    app: auth-service
    version: "1.0.0"
spec:
  replicas: 2                  # Run 2 instances (high availability)
  selector:
    matchLabels:
      app: auth-service        # Manages pods with this label
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1              # Can have 1 extra pod during update
      maxUnavailable: 0        # Zero downtime — don't kill old pod until new one is ready
  template:                    # Template for each Pod
    metadata:
      labels:
        app: auth-service
    spec:
      containers:
        - name: auth-service
          image: amazon-demo/auth-service:1.0.0
          ports:
            - containerPort: 8081
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                secretKeyRef:           # From Kubernetes Secret
                  name: db-secret
                  key: auth-db-url
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: jwt-secret
                  key: secret
          resources:
            requests:
              memory: "256Mi"           # Minimum guaranteed
              cpu: "100m"               # 100 millicores = 0.1 CPU
            limits:
              memory: "512Mi"           # Maximum allowed
              cpu: "500m"               # 0.5 CPU
          livenessProbe:                # Is the container alive?
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:               # Is the container ready to serve traffic?
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 5
```

### Service Manifest

```yaml
# k8s/auth-service/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: amazon-demo
spec:
  selector:
    app: auth-service            # Routes to pods with this label
  ports:
    - port: 8081                 # Service port (what other services call)
      targetPort: 8081           # Pod port
  type: ClusterIP                # Internal only (not exposed outside cluster)
                                 # ClusterIP = internal, NodePort = on every node,
                                 # LoadBalancer = cloud load balancer
```

### ConfigMap and Secrets

```yaml
# ConfigMap — non-sensitive config
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: amazon-demo
data:
  SPRING_PROFILES_ACTIVE: "prod"
  APP_NAME: "Amazon Demo"
  EUREKA_URL: "http://discovery-server:8761/eureka"

---
# Secret — sensitive config (base64 encoded, NOT encrypted by default)
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
  namespace: amazon-demo
type: Opaque
data:
  # base64 encoded: echo -n "postgres" | base64
  DB_PASSWORD: cG9zdGdyZXM=
  JWT_SECRET: c2VjcmV0LWp3dC1rZXk=
```

### Horizontal Pod Autoscaler (HPA)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: product-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: product-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70    # Scale up when CPU > 70%
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

### Ingress — External Traffic Routing

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: amazon-demo-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  ingressClassName: nginx
  rules:
    - host: api.amazondemo.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-gateway     # Routes all to API Gateway
                port:
                  number: 8080
  tls:
    - hosts:
        - api.amazondemo.com
      secretName: tls-secret          # TLS certificate
```

### kubectl Commands

```bash
# Apply manifests
kubectl apply -f k8s/

# Get running pods
kubectl get pods -n amazon-demo

# Describe pod (events, errors)
kubectl describe pod auth-service-abc123 -n amazon-demo

# View logs
kubectl logs auth-service-abc123 -n amazon-demo
kubectl logs -f -l app=auth-service -n amazon-demo  # Follow all auth pods

# Execute command in pod
kubectl exec -it auth-service-abc123 -n amazon-demo -- /bin/sh

# Rolling update (new image)
kubectl set image deployment/auth-service auth-service=amazon-demo/auth-service:1.1.0

# Check rollout status
kubectl rollout status deployment/auth-service

# Rollback if something went wrong
kubectl rollout undo deployment/auth-service

# Scale manually
kubectl scale deployment/product-service --replicas=5

# Port-forward for local access
kubectl port-forward svc/api-gateway 8080:8080 -n amazon-demo
```

---

## 4. Spring Boot + Kubernetes Integration

### Actuator Health Probes

```yaml
# application.yml
management:
  endpoint:
    health:
      probes:
        enabled: true              # Enables /health/liveness and /health/readiness
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

- `/actuator/health/liveness` → **Liveness probe**: Is the app alive? (If not → K8s restarts it)
- `/actuator/health/readiness` → **Readiness probe**: Is the app ready to receive traffic? (If not → K8s removes from service)

### Graceful Shutdown

```yaml
# When K8s sends SIGTERM (scale down, rolling update):
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s   # Wait up to 30s for in-flight requests to finish

server:
  shutdown: graceful                  # Enables graceful shutdown
```

---

## 5. Docker Image Optimization

```dockerfile
# Bad: Single fat layer, no caching
FROM eclipse-temurin:17-jre-alpine
COPY . .
RUN mvn package
ENTRYPOINT ["java", "-jar", "target/app.jar"]

# Good: Layer caching optimized
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Extract Spring Boot layers for better caching
COPY --from=builder /build/target/app.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract  # Splits into layers

# Each Spring Boot layer rarely changes:
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./           # Only this changes with code changes

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
# Re-downloading 150MB deps layer skipped if only app code changed
```

---

## 6. Interview Questions: Docker & Kubernetes

**Q: What is the difference between a Docker image and a container?**
> Image = immutable blueprint (like a class). Container = running instance (like an object).
> Multiple containers can be created from the same image simultaneously.

**Q: What is the difference between CMD and ENTRYPOINT?**
> Both define what runs when the container starts:
> - `ENTRYPOINT`: Fixed command (can't be overridden easily). Our app's main process.
> - `CMD`: Default arguments (can be overridden). Often used with ENTRYPOINT.
> `docker run myimage --debug` → ENTRYPOINT + `--debug` (CMD gets replaced with `--debug`)

**Q: What is the purpose of liveness vs readiness probes in Kubernetes?**
> - **Liveness**: Is the app stuck/deadlocked? If fails → K8s kills and restarts the pod.
> - **Readiness**: Can the app accept traffic? If fails → K8s removes pod from Service endpoints.
>   (App stays running but gets no new requests until ready again.)

**Q: What is a Rolling Update in Kubernetes?**
> Kubernetes gradually replaces old pods with new ones:
> 1. Start 1 new pod (with new version)
> 2. Wait until it's ready (readiness probe passes)
> 3. Stop 1 old pod
> 4. Repeat until all old pods are replaced
> Zero downtime — there's always at least one pod running.

**Q: What is the difference between a Deployment, StatefulSet, and DaemonSet?**
> - **Deployment**: Stateless apps (web servers, microservices). Pods are interchangeable.
> - **StatefulSet**: Stateful apps (databases, Kafka). Pods have stable IDs and storage.
> - **DaemonSet**: Run one pod on every node (log collectors, monitoring agents).

**Q: How do you pass secrets to a container securely?**
> 1. **Kubernetes Secrets**: Stored in etcd (base64, not encrypted by default without KMS)
> 2. **External Secret Manager**: AWS Secrets Manager / HashiCorp Vault → inject at runtime
> 3. **Environment variables from Secret**: `valueFrom.secretKeyRef`
> NEVER hardcode secrets in Dockerfile or docker-compose.yml committed to git.
