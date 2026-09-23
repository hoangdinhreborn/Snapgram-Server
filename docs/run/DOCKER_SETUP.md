# Snapgram Docker Setup Guide

## Overview

Snapgram là một microservices project với 7 services (auth, chat, chatbot, content, media, notification, recommender, api-gateway) + infrastructure (Kafka, Redis, PostgreSQL, MinIO, Mailpit).

Tất cả được quản lý bởi một **docker-compose.yml** duy nhất trong folder `infra/`.

## Prerequisites

- Docker Desktop (hoặc Docker + Docker Compose)
- Java 21+ (nếu build locally)
- Maven 3.9+

## Project Structure

```
snapgram/
├── infra/
│   ├── docker-compose.yml          # Main compose file (quản lý tất cả)
│   └── create-topics.sh            # Kafka topic creation script
├── auth-service/
│   ├── Dockerfile                  # Multi-stage build
│   └── src/
├── chat-service/
│   ├── Dockerfile
│   └── src/
├── chatbot-service/
│   ├── Dockerfile
│   └── src/
├── content-service/
│   ├── Dockerfile
│   └── src/
├── media-service/
│   ├── Dockerfile
│   └── src/
├── notification-service/
│   ├── Dockerfile
│   └── src/
├── recommender-service/
│   ├── Dockerfile
│   └── src/
├── api-gateway/
│   ├── Dockerfile
│   └── src/
├── pom.xml                         # Root Maven (multi-module)
├── .env                            # Environment variables
└── docs/
    └── DOCKER_SETUP.md             # This file
```

## Services & Ports

### Infrastructure
- **Kafka**: 9092
- **Redis**: 6379
- **PostgreSQL (Auth)**: 5432
- **PostgreSQL (Chat)**: 5433
- **PostgreSQL (Bot)**: 5434
- **PostgreSQL (Content)**: 5435
- **PostgreSQL (Recommender)**: 5436
- **PostgreSQL (Media)**: 5437
- **PostgreSQL (Notification)**: 5438
- **MinIO**: 9000 (API), 9001 (Console)
- **Mailpit**: 1025 (SMTP), 8025 (Web)

### Application Services
- **API Gateway**: 8080
- **Auth Service**: 8081
- **Chat Service**: 8082
- **Chatbot Service**: 8083
- **Content Service**: 8084
- **Media Service**: 8085
- **Notification Service**: 8086
- **Recommender Service**: 8087

## Quick Start

### 1. Start All Services

```bash
cd infra
docker compose up -d --build
```

Lần đầu sẽ mất khoảng 5-10 phút (build + pull images).

### 2. Check Status

```bash
docker compose ps
```

Tất cả services phải có status `Up` và các infrastructure service phải `healthy`.

### 3. View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f auth-service

# Last 50 lines
docker compose logs --tail=50 chat-service
```

### 4. Stop All

```bash
docker compose down
```

## Development Workflow

### When You Change Code

1. **Edit code** trong service folder (e.g., `auth-service/src/main/java/...`)

2. **Rebuild & restart** service:
   ```bash
   docker compose up -d --build auth-service
   ```

3. **Or rebuild all services**:
   ```bash
   docker compose up -d --build
   ```

4. **View logs** to verify it started:
   ```bash
   docker compose logs -f auth-service
   ```

### Quick Restart (No Code Changes)

```bash
docker compose restart auth-service
```

## Environment Variables

File `.env` tại root folder chứa:
- Database credentials
- Kafka settings
- Redis config
- JWT secrets
- SMTP config

Mỗi service load `.env` file qua `env_file:` trong compose, và có thể override trong `environment:` section.

## Dockerfile Structure

Tất cả services dùng **multi-stage build**:

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
# - Copy all pom.xml từ tất cả services (multi-module)
# - Run dependency:go-offline (layer cache)
# - Copy source code
# - Build JAR (skip tests)

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
# - Create non-root user
# - Copy JAR từ builder
# - Run as appuser
# - JVM tuning (container support, RAM percentage, timezone)
```

**Benefits:**
- Final image chỉ chứa JRE + JAR (rất nhỏ)
- Layer cache tối ưu (dependencies reuse)
- Security best practice (non-root user)

## Compose File Structure

`infra/docker-compose.yml` có 3 phần:

### 1. Infrastructure Services
- Kafka, Redis, PostgreSQL (7 instances), MinIO, Mailpit
- Tất cả có `healthcheck` để confirm ready

### 2. Application Services
- 7 microservices
- Mỗi cái:
  - `build` từ root folder với Dockerfile riêng
  - Load `.env` file
  - Override specific environment variables
  - Depend on infrastructure services (với `condition: service_healthy`)
  - Có healthcheck (actuator endpoint)

### 3. Volumes
- Persistent data cho Kafka, Redis, PostgreSQL, MinIO

## Troubleshooting

### Service Won't Start

1. **Check logs**:
   ```bash
   docker compose logs auth-service
   ```

2. **Check dependencies**:
   ```bash
   docker compose ps
   ```
   - Tất cả dependency services phải `healthy` trước

3. **Common issues**:
   - Port conflict (service đang chạy ở port khác)
   - Database connection error (kiểm tra `DB_URL` env var)
   - Out of memory (Docker Desktop RAM limit)

### Port Already in Use

```bash
# Find what's using port 8081
netstat -ano | findstr :8081

# Or restart Docker
```

### Database Connection Failed

```bash
# Check PostgreSQL is healthy
docker compose ps postgres-auth

# Check environment variables in container
docker exec snapgram-auth-service env | grep DB_URL
```

### Rebuild From Scratch

```bash
# Stop & remove everything
docker compose down -v

# Start fresh (thêm flag -v xóa luôn volumes)
docker compose up -d --build
```

## Useful Commands

```bash
# Change to infra folder
cd infra

# Start services
docker compose up -d
docker compose up -d --build          # Rebuild
docker compose up -d service-name     # Single service
docker compose up -d --build service-name

# Stop services
docker compose stop                   # Graceful stop
docker compose down                   # Stop & remove containers
docker compose down -v                # Remove containers + volumes

# View status
docker compose ps                     # All services
docker compose ps --filter status=running

# Logs
docker compose logs -f                # All services (follow)
docker compose logs --tail=50 service-name
docker compose logs -f service-name --since 5m

# Restart
docker compose restart                # All
docker compose restart service-name

# Execute command in container
docker compose exec service-name bash
docker compose exec service-name /bin/sh

# Remove unused images/volumes
docker system prune
docker system prune -a --volumes
```

## Notes

- **Development Only**: Setup này dành cho development, không dùng trong production
- **Timezone**: Tất cả services chạy UTC (`-Duser.timezone=UTC`)
- **Non-root User**: Services chạy dưới user `appuser` (security)
- **Healthchecks**: Tất cả services có healthcheck, đợi cho healthy trước khi start service khác
- **Multi-module Maven**: Root `pom.xml` là parent, các service pom.xml là child - Docker copy tất cả để resolve dependencies

## References

- Docker Compose docs: https://docs.docker.com/compose/
- Spring Boot in Docker: https://spring.io/guides/gs/spring-boot-docker/
- Multi-stage builds: https://docs.docker.com/build/building/multi-stage/
