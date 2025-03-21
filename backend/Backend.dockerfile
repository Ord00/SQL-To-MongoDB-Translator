FROM amazoncorretto:22-alpine
VOLUME /tmp
ARG JAR_FILE=build/libs/SQL-To-MongoDB-Translator.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]