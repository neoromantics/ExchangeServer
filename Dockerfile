# ---------------------------
# Stage 1: Build with Maven
# ---------------------------
FROM maven:3.8.7-amazoncorretto-17 AS build

WORKDIR /app

# Copy pom.xml and source files into the container
COPY pom.xml /app
COPY src /app/src

# Build the application
RUN mvn clean package -DskipTests

# -----------------------------------
# Stage 2: Create a minimal runtime image
# -----------------------------------
FROM amazoncorretto:17-alpine3.17

WORKDIR /app

# Copy the JAR from the build stage
COPY --from=build /app/target/ExchangeServer-1.0-SNAPSHOT.jar exchange-server.jar

# Expose the port your application uses (adjust as necessary, e.g., 12345)
EXPOSE 12345

# Run the application
ENTRYPOINT ["java", "-jar", "exchange-server.jar"]
