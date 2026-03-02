# Stage 1: Build frontend (Vite) and output to frontend/dist
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend

ARG VITE_GOOGLE_MAPS_API_KEY
ENV VITE_GOOGLE_MAPS_API_KEY=${VITE_GOOGLE_MAPS_API_KEY}
ENV VITE_API_BASE_URL=""

COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build backend (Maven) with frontend static files
FROM maven:3.9.5-eclipse-temurin-17 AS backend-build
WORKDIR /app

COPY backend/ ./
COPY --from=frontend-build /app/frontend/dist/ ./src/main/resources/static/

RUN mvn clean package -DskipTests

# Stage 3: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV PORT=8080
EXPOSE 8080

COPY --from=backend-build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["sh", "-c", "java -jar app.jar"]
