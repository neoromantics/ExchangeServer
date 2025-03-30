FROM maven:3.8.7-amazoncorretto-17 as build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Just to confirm the final JAR name
RUN ls -l /app/target

FROM amazoncorretto:17-alpine
WORKDIR /app

# Copy the single jar from target to app.jar
COPY --from=build /app/target/erss-hwk4-tl370-td225-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
