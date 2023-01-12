FROM eclipse-temurin:17-alpine

COPY build/libs/todo-service-1.0-SNAPSHOT.jar /

RUN apk upgrade --no-cache && \
    apk add --no-cache libgcc libstdc++ ncurses-libs

ENTRYPOINT ["java", "-jar", "/todo-service-1.0-SNAPSHOT.jar"]
