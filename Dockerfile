FROM openjdk:8-jdk-alpine@sha256:f362b165b870ef129cbe730f29065ff37399c0aa8bcab3e44b51c302938c9193

ADD build/libs/yti-terminology-api*.jar yti-terminology-api.jar

ENV JAVA_OPTS=""
ENTRYPOINT [ "sh", "-c", "sleep 5 && java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /yti-terminology-api.jar" ]
