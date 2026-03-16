FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "build/libs/griinserver-0.0.1-SNAPSHOT.jar"]
