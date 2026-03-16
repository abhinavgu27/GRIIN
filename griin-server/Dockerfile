# Stage 1: Build the application using Gradle
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
# Copy only the files needed for the build
COPY . .

# Grant execution permission to the Gradle wrapper
RUN chmod +x ./gradlew

# Run the Gradle build (skip tests to save time on Render)
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Copy the built jar file from the build stage
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]