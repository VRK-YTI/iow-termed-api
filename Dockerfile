FROM openjdk:8-jdk-alpine

ADD build/libs/yti-terminology-api*.jar yti-terminology-api.jar

ENV JAVA_OPTS=""
ENTRYPOINT [ "sh", "-c", "sleep 5 && java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /yti-terminology-api.jar" ]
