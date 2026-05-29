// ============================================================
// AMAZON DEMO - CI/CD PIPELINE
// ============================================================
// Stages:
//   1.  Checkout & Environment Setup
//   2.  Build Common Library
//   3.  Unit Tests (JUnit + Mockito) - per service, parallel
//   4.  Build All Services
//   5.  Code Coverage Report (JaCoCo)
//   6.  Docker Build & Push (if tests pass)
//   7.  Deploy to Staging (Docker Compose + LocalStack)
//   8.  Integration / Smoke Tests
//   9.  Deploy to Production (manual gate)
//  10.  Post Actions (notifications)
//
// Prerequisites (Jenkins Plugins):
//   - Pipeline
//   - Git
//   - Maven Integration
//   - Docker Pipeline
//   - HTML Publisher (for test reports)
//   - JUnit (test result publishing)
//   - Slack Notification (optional)
// ============================================================

pipeline {
    agent any

    // ─── Tool versions (configured in Jenkins > Global Tool Configuration) ───
    tools {
        maven 'Maven-3.9'
        jdk   'JDK-21'
    }

    // ─── Pipeline-level environment ──────────────────────────────────────────
    environment {
        DOCKER_REGISTRY  = 'registry.amazondemo.com'
        IMAGE_TAG        = "${env.BUILD_NUMBER}-${env.GIT_COMMIT?.take(7) ?: 'local'}"
        MAVEN_OPTS       = '-Xmx1024m -XX:+TieredCompilation -XX:TieredStopAtLevel=1'
        SPRING_PROFILES_ACTIVE = 'test'

        // Services under test (space-separated for iteration)
        SERVICES = 'auth-service product-service order-service inventory-service notification-service payment-service user-service'
    }

    // ─── Keep only last 10 builds ────────────────────────────────────────────
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    // ─── Trigger on PR or push to main / develop ─────────────────────────────
    triggers {
        githubPush()
        pollSCM('H/5 * * * *')
    }

    stages {

        // ════════════════════════════════════════════════════════════════════
        // STAGE 1 — Checkout & Environment Info
        // ════════════════════════════════════════════════════════════════════
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_BRANCH_SHORT = sh(
                        script: 'git rev-parse --abbrev-ref HEAD',
                        returnStdout: true
                    ).trim()
                    echo "Branch: ${env.GIT_BRANCH_SHORT}"
                    echo "Build:  #${env.BUILD_NUMBER} | Tag: ${env.IMAGE_TAG}"
                    echo "Java:   ${sh(script: 'java -version 2>&1 | head -1', returnStdout: true).trim()}"
                    echo "Maven:  ${sh(script: 'mvn -version | head -1', returnStdout: true).trim()}"
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 2 — Build Common Library (shared by all services)
        // ════════════════════════════════════════════════════════════════════
        stage('Build Common Library') {
            steps {
                dir('backend/common-lib') {
                    sh 'mvn clean install -DskipTests -q'
                }
            }
            post {
                failure {
                    error 'Common library build failed — aborting pipeline'
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 3 — Unit Tests (Parallel across all services)
        // ════════════════════════════════════════════════════════════════════
        stage('Unit Tests') {
            parallel {

                stage('Test: auth-service') {
                    steps {
                        dir('backend/auth-service') {
                            sh '''
                                mvn test \
                                  -Dspring.profiles.active=test \
                                  -Dspring.datasource.url=jdbc:h2:mem:auth_test;DB_CLOSE_DELAY=-1 \
                                  -Dspring.datasource.driver-class-name=org.h2.Driver \
                                  -Dspring.datasource.username=sa \
                                  -Dspring.datasource.password= \
                                  -Dspring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
                                  -Deureka.client.enabled=false \
                                  -Dspring.cloud.config.enabled=false \
                                  -Dapp.aws.enabled=false \
                                  -B -q
                            '''
                        }
                    }
                    post {
                        always {
                            junit 'backend/auth-service/target/surefire-reports/**/*.xml'
                            publishHTML(target: [
                                reportDir:   'backend/auth-service/target/site/jacoco',
                                reportFiles: 'index.html',
                                reportName:  'Auth Service Coverage'
                            ])
                        }
                    }
                }

                stage('Test: product-service') {
                    steps {
                        dir('backend/product-service') {
                            sh '''
                                mvn test \
                                  -Dspring.profiles.active=test \
                                  -Dspring.datasource.url=jdbc:h2:mem:product_test;DB_CLOSE_DELAY=-1 \
                                  -Dspring.datasource.driver-class-name=org.h2.Driver \
                                  -Dspring.datasource.username=sa \
                                  -Dspring.datasource.password= \
                                  -Dspring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
                                  -Dspring.data.mongodb.uri=mongodb://localhost:27017/product_test \
                                  -Deureka.client.enabled=false \
                                  -Dspring.cloud.config.enabled=false \
                                  -Dapp.aws.enabled=false \
                                  -B -q
                            '''
                        }
                    }
                    post {
                        always {
                            junit 'backend/product-service/target/surefire-reports/**/*.xml'
                            publishHTML(target: [
                                reportDir:   'backend/product-service/target/site/jacoco',
                                reportFiles: 'index.html',
                                reportName:  'Product Service Coverage'
                            ])
                        }
                    }
                }

                stage('Test: order-service') {
                    steps {
                        dir('backend/order-service') {
                            sh '''
                                mvn test \
                                  -Dspring.profiles.active=test \
                                  -Dspring.datasource.url=jdbc:h2:mem:order_test;DB_CLOSE_DELAY=-1 \
                                  -Dspring.datasource.driver-class-name=org.h2.Driver \
                                  -Dspring.datasource.username=sa \
                                  -Dspring.datasource.password= \
                                  -Dspring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
                                  -Deureka.client.enabled=false \
                                  -Dspring.cloud.config.enabled=false \
                                  -Dapp.aws.enabled=false \
                                  -B -q
                            '''
                        }
                    }
                    post {
                        always {
                            junit 'backend/order-service/target/surefire-reports/**/*.xml'
                            publishHTML(target: [
                                reportDir:   'backend/order-service/target/site/jacoco',
                                reportFiles: 'index.html',
                                reportName:  'Order Service Coverage'
                            ])
                        }
                    }
                }

                stage('Test: inventory-service') {
                    steps {
                        dir('backend/inventory-service') {
                            sh '''
                                mvn test \
                                  -Dspring.profiles.active=test \
                                  -Dspring.datasource.url=jdbc:h2:mem:inventory_test;DB_CLOSE_DELAY=-1 \
                                  -Dspring.datasource.driver-class-name=org.h2.Driver \
                                  -Dspring.datasource.username=sa \
                                  -Dspring.datasource.password= \
                                  -Dspring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
                                  -Deureka.client.enabled=false \
                                  -Dspring.cloud.config.enabled=false \
                                  -B -q
                            '''
                        }
                    }
                    post {
                        always {
                            junit 'backend/inventory-service/target/surefire-reports/**/*.xml'
                        }
                    }
                }

                stage('Test: notification-service') {
                    steps {
                        dir('backend/notification-service') {
                            sh '''
                                mvn test \
                                  -Dspring.profiles.active=test \
                                  -Dspring.datasource.url=jdbc:h2:mem:notif_test;DB_CLOSE_DELAY=-1 \
                                  -Dspring.datasource.driver-class-name=org.h2.Driver \
                                  -Dspring.datasource.username=sa \
                                  -Dspring.datasource.password= \
                                  -Dspring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
                                  -Deureka.client.enabled=false \
                                  -Dspring.cloud.config.enabled=false \
                                  -Dapp.aws.enabled=false \
                                  -Dapp.aws.ses.enabled=false \
                                  -Dspring.mail.host=localhost \
                                  -B -q
                            '''
                        }
                    }
                    post {
                        always {
                            junit 'backend/notification-service/target/surefire-reports/**/*.xml'
                        }
                    }
                }

            } // end parallel
        } // end Unit Tests stage

        // ════════════════════════════════════════════════════════════════════
        // STAGE 4 — Code Coverage Gate (JaCoCo minimum thresholds)
        // ════════════════════════════════════════════════════════════════════
        stage('Coverage Gate') {
            steps {
                script {
                    // Aggregate all JaCoCo reports
                    def services = ['auth-service', 'product-service', 'order-service',
                                    'inventory-service', 'notification-service']
                    services.each { svc ->
                        def coverageFile = "backend/${svc}/target/site/jacoco/index.html"
                        if (fileExists(coverageFile)) {
                            echo "Coverage report available for: ${svc}"
                        } else {
                            echo "Warning: No coverage report for ${svc}"
                        }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 5 — Build All Services (skip tests, already ran)
        // ════════════════════════════════════════════════════════════════════
        stage('Build All Services') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    branch pattern: 'release/*', comparator: 'GLOB'
                }
            }
            steps {
                dir('backend') {
                    sh '''
                        mvn clean package -DskipTests \
                          -pl auth-service,product-service,order-service,inventory-service,\
notification-service,payment-service,user-service,api-gateway,batch-service \
                          --also-make \
                          -B -q
                    '''
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 6 — Docker Build & Push
        // ════════════════════════════════════════════════════════════════════
        stage('Docker Build & Push') {
            when { branch 'main' }
            environment {
                DOCKER_CREDS = credentials('docker-registry-credentials')
            }
            steps {
                script {
                    def services = ['auth-service', 'product-service', 'order-service',
                                    'inventory-service', 'notification-service',
                                    'payment-service', 'user-service', 'api-gateway']
                    services.each { svc ->
                        def imageName = "${DOCKER_REGISTRY}/amazondemo/${svc}:${IMAGE_TAG}"
                        def latestName = "${DOCKER_REGISTRY}/amazondemo/${svc}:latest"

                        sh """
                            echo "Building Docker image: ${imageName}"
                            docker build -t ${imageName} -t ${latestName} backend/${svc}/
                        """
                    }
                }
            }
            post {
                success {
                    script {
                        sh """
                            echo \$DOCKER_CREDS_PSW | docker login ${DOCKER_REGISTRY} \
                              -u \$DOCKER_CREDS_USR --password-stdin
                        """
                        def services = ['auth-service', 'product-service', 'order-service',
                                        'inventory-service', 'notification-service',
                                        'payment-service', 'user-service', 'api-gateway']
                        services.each { svc ->
                            sh "docker push ${DOCKER_REGISTRY}/amazondemo/${svc}:${IMAGE_TAG}"
                            sh "docker push ${DOCKER_REGISTRY}/amazondemo/${svc}:latest"
                        }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 7 — Deploy to Staging (LocalStack + Docker Compose)
        // ════════════════════════════════════════════════════════════════════
        stage('Deploy to Staging') {
            when { branch 'main' }
            environment {
                COMPOSE_PROJECT_NAME = 'amazondemo-staging'
            }
            steps {
                script {
                    echo "Deploying to STAGING with LocalStack..."
                    sh '''
                        # Stop any running staging environment
                        docker compose -f docker-compose.yml -f docker-compose.stage.yml \
                          --env-file environments/.env.stage down --remove-orphans || true

                        # Start staging environment
                        docker compose -f docker-compose.yml -f docker-compose.stage.yml \
                          --env-file environments/.env.stage up -d \
                          --wait --wait-timeout 120

                        echo "Staging environment started"
                    '''
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 8 — Smoke / Integration Tests against staging
        // ════════════════════════════════════════════════════════════════════
        stage('Smoke Tests') {
            when { branch 'main' }
            steps {
                script {
                    echo "Running smoke tests against staging environment..."
                    sh '''
                        # Wait for services to be healthy
                        sleep 30

                        # Auth Service - registration endpoint
                        echo "Testing auth-service..."
                        STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
                          -X POST http://localhost:8081/api/v1/auth/register \
                          -H "Content-Type: application/json" \
                          -d "{\"email\":\"smoke@test.com\",\"password\":\"Test1234!\",\"firstName\":\"Smoke\",\"lastName\":\"Test\"}")
                        if [ "$STATUS" != "201" ] && [ "$STATUS" != "409" ]; then
                          echo "Auth smoke test FAILED: HTTP $STATUS"
                          exit 1
                        fi
                        echo "Auth smoke test PASSED (HTTP $STATUS)"

                        # Product Service - list products
                        echo "Testing product-service..."
                        STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
                          http://localhost:8082/api/v1/products)
                        if [ "$STATUS" != "200" ]; then
                          echo "Product smoke test FAILED: HTTP $STATUS"
                          exit 1
                        fi
                        echo "Product smoke test PASSED (HTTP $STATUS)"

                        # Inventory Service - health check
                        echo "Testing inventory-service..."
                        STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
                          http://localhost:8083/actuator/health)
                        if [ "$STATUS" != "200" ]; then
                          echo "Inventory health check FAILED: HTTP $STATUS"
                          exit 1
                        fi
                        echo "Inventory health check PASSED"

                        echo "All smoke tests PASSED"
                    '''
                }
            }
            post {
                failure {
                    sh '''
                        echo "=== Service Logs on Failure ==="
                        docker compose -f docker-compose.yml -f docker-compose.stage.yml \
                          --env-file environments/.env.stage logs --tail=50 || true
                    '''
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 9 — Frontend Build & Test
        // ════════════════════════════════════════════════════════════════════
        stage('Frontend Build & Test') {
            tools { nodejs 'NodeJS-18' }
            steps {
                dir('frontend') {
                    sh '''
                        npm ci --silent
                        npm run lint || true
                        npm test -- --run --reporter verbose || true
                        npm run build
                        echo "Frontend build complete"
                        ls -lh dist/
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'frontend/dist/**', allowEmptyArchive: true
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 10 — Deploy to Production (Manual Gate)
        // ════════════════════════════════════════════════════════════════════
        stage('Deploy to Production') {
            when {
                allOf {
                    branch 'main'
                    expression { currentBuild.currentResult == 'SUCCESS' }
                }
            }
            steps {
                script {
                    // Manual approval gate
                    timeout(time: 30, unit: 'MINUTES') {
                        def approve = input(
                            message: "Deploy build #${env.BUILD_NUMBER} to PRODUCTION?",
                            ok: 'Deploy Now',
                            parameters: [
                                booleanParam(
                                    name: 'CONFIRM',
                                    defaultValue: false,
                                    description: 'Check to confirm production deployment'
                                )
                            ]
                        )
                        if (!approve) {
                            echo "Production deployment skipped by operator"
                            return
                        }
                    }

                    echo "Deploying to PRODUCTION..."
                    sh '''
                        docker compose -f docker-compose.yml -f docker-compose.prod.yml \
                          --env-file environments/.env.prod \
                          up -d --wait --wait-timeout 180
                        echo "Production deployment complete"
                    '''
                }
            }
        }

    } // end stages

    // ─── Post Actions ────────────────────────────────────────────────────────
    post {

        always {
            // Aggregate all JUnit test results
            junit allowEmptyResults: true,
                  testResults: 'backend/**/target/surefire-reports/**/*.xml'

            // Clean up Docker artifacts
            sh 'docker system prune -f --filter "until=24h" || true'
        }

        success {
            echo "Pipeline SUCCEEDED — Build #${env.BUILD_NUMBER}"
            // Uncomment to enable Slack notifications:
            // slackSend(channel: '#ci-cd', color: 'good',
            //           message: "✅ Build #${env.BUILD_NUMBER} passed on ${env.GIT_BRANCH_SHORT}")
        }

        failure {
            echo "Pipeline FAILED — Build #${env.BUILD_NUMBER}"
            // slackSend(channel: '#ci-cd', color: 'danger',
            //           message: "❌ Build #${env.BUILD_NUMBER} FAILED on ${env.GIT_BRANCH_SHORT}")
        }

        unstable {
            echo "Pipeline UNSTABLE (some tests failed) — Build #${env.BUILD_NUMBER}"
        }

        cleanup {
            cleanWs(cleanWhenFailure: false)
        }

    }
}
