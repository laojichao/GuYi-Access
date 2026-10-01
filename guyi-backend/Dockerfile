# ---- build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Resolve dependencies first so the (slow) download layer is cached across code changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

# ---- runtime stage ----------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app
# Never run the application as root
RUN groupadd --system app && useradd --system --gid app --home /app app
# target/*.jar matches only the repackaged executable jar (the original is *.jar.original)
COPY --from=build /build/target/*.jar /app/app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
# All configuration (DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, API_TOKEN, ...) comes from the
# environment - see docs/deployment.md for the full list.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
