# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy Maven wrapper and pom.xml first (for dependency caching)
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B
# Copy source code
COPY src ./src
# Build the JAR (skip tests — we run tests in CI, not during image build)
RUN ./mvnw clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy the JAR from build stage
COPY --from=build /app/target/*.jar app.jar
# Expose port (documentation — doesn't actually open the port)
EXPOSE 8080
# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]