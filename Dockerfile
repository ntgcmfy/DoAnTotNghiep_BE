# ---- Build stage: compile the Spring Boot app with JDK 21 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -q clean package -DskipTests

# ---- Run stage: small image with just the JRE + the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/quiz-backend-0.0.1-SNAPSHOT.jar app.jar
ENV JAVA_OPTS="-Xmx400m"
# Render injects the port in $PORT; Spring Boot binds to it.
CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$PORT -jar app.jar"]
