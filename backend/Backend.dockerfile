FROM amazoncorretto:22-alpine
VOLUME /tmp
ARG JAR_FILE=build/libs/backend.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]