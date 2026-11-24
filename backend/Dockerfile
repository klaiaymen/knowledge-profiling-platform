FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend ./backend
RUN mvn -B -f backend/pom.xml -pl app -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/backend/app/target/*.jar /app/application.jar
COPY docs/sources /app/docs/sources
COPY data/regulation /app/data/regulation
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
