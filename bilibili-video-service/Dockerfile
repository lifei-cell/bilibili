FROM amazoncorretto:21-alpine

RUN apk add --no-cache ffmpeg

WORKDIR /app

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

ENV SERVER_PORT=8082
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
