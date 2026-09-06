FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S krino && adduser -S krino -G krino
COPY --from=build /workspace/target/krino-backend-0.0.1-SNAPSHOT.jar app.jar
USER krino
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
