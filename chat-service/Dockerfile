# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy root pom trước để resolve parent POM
COPY pom.xml /build/pom.xml

# Copy all service poms (required by root pom)
COPY auth-service/pom.xml /build/auth-service/pom.xml
COPY chat-service/pom.xml /build/chat-service/pom.xml
COPY chatbot-service/pom.xml /build/chatbot-service/pom.xml
COPY content-service/pom.xml /build/content-service/pom.xml
COPY media-service/pom.xml /build/media-service/pom.xml
COPY notification-service/pom.xml /build/notification-service/pom.xml
COPY recommender-service/pom.xml /build/recommender-service/pom.xml
COPY api-gateway/pom.xml /build/api-gateway/pom.xml

# Download dependencies trước (tận dụng Docker layer cache)
RUN mvn -pl chat-service -am dependency:go-offline -q

# Copy source code
COPY chat-service/src /build/chat-service/src

# Build, bỏ qua tests (chạy test ở CI riêng)
RUN mvn -pl chat-service -am package -DskipTests -q

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# Tạo user non-root để chạy app (security best practice)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy JAR từ build stage
COPY --from=builder /build/chat-service/target/*.jar app.jar

# Đổi owner
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8082

# JVM tuning cho container:
#   -XX:+UseContainerSupport        → đọc CPU/RAM limit từ cgroup (không dùng host)
#   -XX:MaxRAMPercentage=75.0       → dùng tối đa 75% RAM được cấp
#   -Duser.timezone=UTC             → timezone nhất quán với JPA config
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Duser.timezone=UTC", \
  "-jar", "app.jar"]
