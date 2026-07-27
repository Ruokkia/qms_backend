# ===== 阶段1: Maven 构建 =====
FROM maven:3.8-openjdk-8 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ===== 阶段2: 运行 =====
FROM openjdk:8-jre-slim
WORKDIR /app
COPY --from=builder /build/target/qms-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
