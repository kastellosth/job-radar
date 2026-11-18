# Tests are not run here: the integration tests need Docker themselves. CI runs `./mvnw verify` before a deploy.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
# Dependencies first, so a code change does not download them all again.
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN sh mvnw -B -q dependency:go-offline
COPY src src
RUN sh mvnw -B -q package -DskipTests
RUN java -Djarmode=tools -jar target/job-radar.jar extract --layers --destination /layers

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
# One COPY per layer: a code change does not re-send the unchanged libraries.
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER app
# Render's free plan has 512 MB; let the heap use most of the container's memory, not the JVM default of 25%.
ENV JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
EXPOSE 8080
# Exec form: Java receives the stop signal directly and can shut down cleanly.
ENTRYPOINT ["java", "-jar", "job-radar.jar"]
