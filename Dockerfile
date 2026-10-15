# ============================================================================
# DOCKERFILE MULTI-STAGE OPTIMIZADO (ADR-002: Monolito Modular)
# Java 21 / Spring Boot 3 & 4 / Maven
# ============================================================================

# ETAPA 1: Construcción (Build Stage)
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copiar archivos de dependencias y wrapper de Maven
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Descargar dependencias en caché de capa Docker
RUN ./mvnw dependency:go-offline -B || true

# Copiar código fuente y compilar artefacto ejecutable sin correr tests en el build de imagen
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Extraer el jar generado
RUN mkdir -p target/extracted && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted || \
    cp target/*.jar target/app.jar

# ETAPA 2: Runtime Ligero (Production Stage)
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Crear usuario y grupo de seguridad no-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

# Copiar el binario compilado desde la etapa de construcción
COPY --from=builder /workspace/target/*.jar app.jar

# Configuración de variables de entorno por defecto
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/api/v1/cache/metrics || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
