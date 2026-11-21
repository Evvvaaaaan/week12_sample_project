# Render 배포용 Dockerfile (Java 17, Spring Boot)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# Render가 PORT 환경 변수를 주입하며, application.yml의 server.port=${PORT:8080}이 이를 사용한다.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
