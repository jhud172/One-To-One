# ---- Build stage ----
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean bootJar

# ---- Run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/one-to-one.jar app.jar

# Render deployments should use production profile by default.
ENV SPRING_PROFILES_ACTIVE=render

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
