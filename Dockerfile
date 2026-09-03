FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /build

COPY . .

RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /build/target/it-ticketing-system-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

USER 10001

CMD ["java", "-jar", "app.jar"]
