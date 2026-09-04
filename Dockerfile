# Dockerfile - Uniday (Spring Boot + Java 17)
# Imagen multi-stage: compila con Gradle wrapper y ejecuta con JRE 17.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Primero el wrapper y config de Gradle para cachear dependencias.
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --console=plain || true

COPY src src
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]