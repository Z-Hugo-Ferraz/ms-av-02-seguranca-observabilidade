FROM eclipse-temurin:21.0.8_9-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN chmod +x mvnw && ./mvnw -B -DskipTests package && cp target/av2-0.0.1-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21.0.8_9-jre
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
