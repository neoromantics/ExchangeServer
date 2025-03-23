# 构建 Java 应用（使用 Maven）
FROM maven:3.9.0-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 运行 Spring Boot 应用
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 12345
ENTRYPOINT ["java", "-jar", "app.jar"]
