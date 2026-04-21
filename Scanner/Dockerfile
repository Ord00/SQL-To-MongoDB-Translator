FROM gradle:8.5-jdk21-jammy AS build
WORKDIR /app
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY ./Model ./Model
COPY ./Scanner ./Scanner
RUN gradle :Scanner:bootJar -x test --stacktrace --no-daemon

FROM build AS publish
WORKDIR /export
COPY --from=build /app/Scanner/build/libs/Scanner-0.0.1-SNAPSHOT.jar app.jar

FROM eclipse-temurin:21-jre-jammy AS final
VOLUME /tmp

RUN groupadd -r spring && useradd -r -g spring spring
USER spring

WORKDIR /app
COPY --from=publish /export/app.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]