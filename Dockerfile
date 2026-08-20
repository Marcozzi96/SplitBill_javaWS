# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
# Cache delle dipendenze: il layer viene invalidato solo se cambia il pom
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime (JRE: immagine più leggera del JDK)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/rest-api-server-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
# Limite heap JVM impostabile da docker-compose via JAVA_TOOL_OPTIONS
CMD ["java", "-jar", "app.jar"]
