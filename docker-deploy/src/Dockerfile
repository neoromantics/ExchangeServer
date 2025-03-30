# Use Maven for the build stage.
FROM maven:3.8.7-amazoncorretto-17 as build
WORKDIR /app

# Copy pom.xml first so that dependency resolution can be cached.
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the full source code.
COPY src ./src

# Build the fat jar using the Shade Plugin.
RUN mvn clean package -DskipTests

# List the target directory to verify the built jar.
RUN ls -l /app/target

# Use a lightweight JRE image for the runtime stage.
FROM amazoncorretto:17-alpine
WORKDIR /app

# Copy the fat jar from the build stage (adjust the pattern as needed).
COPY --from=build /app/target/erss-hwk4-tl370-td225-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
