# One image: builds the React dashboard, builds the Spring Boot jar with the dashboard inside, runs it.
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 1001 cohortlens
USER cohortlens
COPY --from=build /app/target/cohortlens-0.1.0.jar /app/cohortlens.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/cohortlens.jar"]
