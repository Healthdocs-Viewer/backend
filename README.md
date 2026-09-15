# Healthdocs Viewer — Backend

API Spring Boot del MVP de Healthdocs Viewer. El backend maneja autorización, casos, metadatos de archivos, resultados y eventos de notificación. Los archivos grandes no atraviesan la API: en AWS se cargarán y descargarán directamente desde S3 mediante URLs prefirmadas.

## Requisitos locales

- Java 21
- Maven 3.6.3 o superior
- Docker Desktop con Docker Compose

## Arranque local

1. Copia `.env.example` como `.env` y ajusta los valores locales si es necesario.
2. Ejecuta `docker compose up -d postgres`.
3. Ejecuta `mvn spring-boot:run`.
4. Comprueba `http://localhost:8080/actuator/health` y `http://localhost:8080/api/v1/ping`.

Para detener la base de datos, ejecuta `docker compose down`. Para eliminar también los datos locales, ejecuta `docker compose down -v`.

## Estructura inicial

```text
src/main/java/com/healthdocs/backend/
├── adapter/in/web/      # Adaptadores HTTP
├── application/         # Casos de uso
├── domain/              # Modelo y reglas de negocio
└── infrastructure/      # Persistencia e integraciones
```

La estructura se completará con los primeros casos de uso. La migración `V1__initial_schema.sql` crea únicamente las tablas mínimas para usuario, caso, archivo y resultado.

## Contrato API

El contrato canónico vive en [`openapi/openapi.yaml`](openapi/openapi.yaml). Antes de implementar o consumir un endpoint, revisar la [guía de trabajo paralelo](docs/api-contract-workflow.md).

## Seguridad

- No subir `.env`, secretos ni credenciales AWS al repositorio.
- Usar solo datos de prueba hasta definir las condiciones institucionales para información clínica real.
