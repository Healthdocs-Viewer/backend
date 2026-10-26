# Healthdocs Viewer — Backend

API del MVP de Healthdocs Viewer para gestionar el recorrido de un caso: creación, consentimiento, archivos de entrada, asignación a investigadores, resultados y seguimiento. Está desarrollada con Java 21, Spring Boot y PostgreSQL. Este documento describe **funciones presentes en el repositorio**, no un despliegue productivo.

## Avance del producto

| Área | Funcionalidad implementada |
|---|---|
| Acceso y permisos | Autenticación local para demostraciones; validación de tokens de Supabase mediante JWKS; roles de paciente, investigador, coordinador y administrador. Las consultas sensibles comprueban propiedad del caso o asignación. |
| Casos de pacientes | Creación con clave de idempotencia y referencia generada por el servidor; metadatos del estudio; consentimiento; carga y consulta de archivos; envío del caso; listado, detalle y tablero del paciente. |
| Trabajo académico | Directorio y autorización de investigadores; asignación de casos; vistas de coordinación e investigación; carga, revisión, publicación, corrección e historial de resultados. |
| Cambios de archivos | Solicitudes del paciente para reemplazar archivos de entrada, con borrador, envío, aprobación o rechazo y limpieza programada de archivos abandonados. |
| Seguimiento | Actividad del caso, auditoría de accesos y cambios sensibles, bandeja de notificaciones del paciente y preferencias de correo. |
| Administración | Archivo de casos y recepción y decisión de solicitudes de eliminación de casos o archivos. **La eliminación física de datos todavía no está implementada.** |

La API y sus respuestas están documentadas en el [contrato OpenAPI 0.9.0](openapi/openapi.yaml). El esquema de base de datos se versiona con migraciones Flyway; hay pruebas unitarias y de integración con PostgreSQL mediante Testcontainers.

### Alcance pendiente para operación real

- **Archivos:** la implementación actual usa almacenamiento local y enlaces temporales servidos por el backend. La integración de almacenamiento en S3 y sus URL prefirmadas sigue pendiente.
- **Notificaciones externas:** la bandeja dentro de la aplicación y el registro persistente de entregas están implementados. Se puede habilitar la publicación de eventos a SQS; el envío final de correos requiere un consumidor o servicio externo.
- **Retención:** existen solicitudes y decisiones auditables, pero aún no hay ejecución automática de borrado físico ni una política de retención productiva.
- **Puesta en producción:** falta configurar y validar infraestructura, credenciales, almacenamiento, observabilidad e integración completa con el frontend en el entorno de destino. Las cuentas y los datos de demostración son solo para desarrollo.

## Recorrido disponible para una demostración

1. Un paciente crea un caso con los datos del estudio. Una clave `Idempotency-Key` evita duplicados cuando se reintenta la solicitud.
2. Acepta los términos, carga uno o varios archivos y envía el caso. Los enlaces de carga tienen vencimiento; la confirmación comprueba que el archivo exista.
3. Coordinación consulta la cola de casos, gestiona asignaciones y autoriza investigadores.
4. Un investigador asignado consulta los archivos y carga un resultado. Coordinación puede solicitar una corrección o publicarlo.
5. El paciente consulta el resultado, la actividad y las notificaciones desde sus vistas. Las decisiones y accesos relevantes dejan registros de auditoría.

El recorrido se puede probar localmente con cuentas y datos ficticios. No se deben cargar datos clínicos reales en el entorno de demostración.

## Arquitectura y contrato

- **API:** Spring Boot 3, con endpoints bajo `/api/v1` y autorización por rol y recurso.
- **Datos:** PostgreSQL 16, JPA y migraciones Flyway. Hibernate valida el esquema; no crea tablas automáticamente.
- **Diseño:** dominio, casos de uso y adaptadores HTTP, de persistencia, almacenamiento y notificaciones.
- **Archivos:** el adaptador local guarda objetos fuera de la base de datos; PostgreSQL conserva sus metadatos y relaciones con los casos.
- **Contrato compartido:** [`openapi/openapi.yaml`](openapi/openapi.yaml) es la referencia para integrar frontend y backend. La [guía de cambios del contrato](docs/api-contract-workflow.md) explica cómo coordinar actualizaciones.

La autenticación local se limita a desarrollo. El proveedor Supabase valida firma, emisor, audiencia y expiración del token, y resuelve la identidad contra usuarios vinculados en la base de datos; no otorga roles a partir de un token sin ese vínculo. El [procedimiento de vinculación de identidades](docs/architecture/auth_identity_linking.md) documenta la migración aprobada de cuentas existentes. El adaptador de Cognito es todavía un marcador sin implementación funcional.

## Ejecutar en local

**Requisitos:** Java 21, Maven y Docker Desktop con Docker Compose.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose --env-file .env up -d postgres
mvn "-Dspring-boot.run.profiles=local" spring-boot:run
```

Ejecuta los comandos desde la carpeta `code/backend`. Con los valores de `.env.example`, PostgreSQL y la API quedan disponibles en `localhost:5432` y `localhost:8080`. Comprueba [`/actuator/health`](http://localhost:8080/actuator/health) y [`/api/v1/ping`](http://localhost:8080/api/v1/ping). Si cambias `DB_*` en `.env`, asegúrate de que esos valores estén disponibles para el proceso de Maven.

Para iniciar también la API en Docker:

```powershell
docker compose --env-file .env --profile app up -d --build
```

El perfil local incorpora cuentas y usuarios ficticios; consulta [usuarios de demostración](docs/local_demo_users.md). Las credenciales de prueba viven en la configuración local y deben reemplazarse fuera de ese entorno. No publiques `.env`, tokens ni credenciales de AWS.

## Pruebas

Las pruebas de integración levantan PostgreSQL 16 mediante Testcontainers y aplican las migraciones Flyway. Requieren acceso al motor de Docker.

```powershell
docker version
mvn test
```

Para una comprobación acotada del arranque y la persistencia:

```powershell
mvn "-Dtest=TestProfileStartupTest,JpaCaseRepositoryAdapterIntegrationTest" test
```

## Integración de notificaciones

SQS está desactivado por defecto (`HEALTHDOCS_SQS_ENABLED=false`). Al habilitarlo, el backend publica eventos de disponibilidad de resultados en la cola indicada por `HEALTHDOCS_SQS_QUEUE_NAME`, usando las credenciales normales del AWS SDK. El evento no contiene archivos, datos clínicos, URL temporales ni tokens. Publicar el evento en SQS no equivale a entregar un correo al paciente.
