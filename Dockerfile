# Dockerfile para construir desde la raiz del repositorio.
# El codigo esta en backend/. (Si en Render el Root Directory es "backend",
# se usa backend/Dockerfile y este archivo no interviene.)
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY backend/ .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]

