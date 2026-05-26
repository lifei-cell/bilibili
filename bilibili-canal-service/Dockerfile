FROM amazoncorretto:21-alpine

WORKDIR /app

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

ENV SERVER_PORT=8086
EXPOSE 8086

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
