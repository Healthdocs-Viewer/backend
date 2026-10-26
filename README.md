# Healthdocs Viewer — Backend

API Spring Boot del MVP de Healthdocs Viewer. El backend maneja autorización, casos, metadatos de archivos, resultados y eventos de notificación. Los archivos grandes no atraviesan la API: en AWS se cargarán y descargarán directamente desde S3 mediante URLs prefirmadas.

## Requisitos locales

- Java 21
- Maven 3.6.3 o superior
- Docker Desktop con Docker Compose

## Arranque local

1. Copia `.env.example` como `.env` y ajusta los valores locales si es necesario.
2. Ejecuta `docker compose --env-file .env up -d postgres`.
3. Si modificaste las variables `DB_*`, expórtalas en la terminal antes de iniciar Spring Boot. Con los valores por defecto no es necesario hacerlo:
   - PowerShell: `$env:DB_HOST="localhost"; $env:DB_PORT="5432"; $env:DB_NAME="healthdocs"; $env:DB_USER="healthdocs"; $env:DB_PASSWORD="change_me_for_local_development"`
4. Ejecuta `mvn spring-boot:run`.
5. Comprueba `http://localhost:8080/actuator/health` y `http://localhost:8080/api/v1/ping`.

Para ejecutar también el backend dentro de Compose: `docker compose --env-file .env --profile app up -d --build`.
El storage local queda en el volumen `backend-storage` y no se publica como un directorio HTTP anónimo.

Para detener la base de datos, ejecuta `docker compose down`. Para eliminar también los datos locales, ejecuta `docker compose down -v`.

## Pruebas

Las pruebas de integración que activan el perfil `test` usan Testcontainers para iniciar PostgreSQL 16 y ejecutar todas las migraciones Flyway sobre PostgreSQL. Para ejecutarlas, Docker Desktop debe estar iniciado y el motor Docker debe ser accesible desde la terminal:

```powershell
docker version
mvn test
```

Para ejecutar solo las pruebas de arranque y persistencia JPA:

```powershell
mvn "-Dtest=TestProfileStartupTest,JpaCaseRepositoryAdapterIntegrationTest" test
```

La primera ejecución puede tardar mientras Docker descarga las imágenes de PostgreSQL y Testcontainers y Spring inicia el contexto. Si `docker version` no puede conectar con el motor, Maven puede iniciar, pero las pruebas de integración fallarán al crear la base de datos. En Codex, el acceso al motor Docker depende de los permisos del entorno de ejecución y no se configura en este repositorio.

## Estructura inicial

```text
src/main/java/com/healthdocs/backend/
├── adapter/
│   ├── in/web/          # Adaptadores de entrada HTTP
│   └── out/persistence/ # Adaptadores de salida y entidades JPA
├── application/
│   ├── port/in/         # Casos de uso expuestos por la aplicación
│   └── service/         # Implementación de casos de uso
├── domain/model/        # Modelo y reglas de negocio
└── infrastructure/      # Configuración técnica del framework
```

El backend sigue arquitectura hexagonal: los adaptadores HTTP dependen de puertos de entrada de `application`, y la persistencia queda aislada en adaptadores de salida. Las entidades JPA viven en `adapter/out/persistence/entity` y usan los enums del dominio para roles, estados de caso, estados de resultado y tipos de archivo. Los casos de uso no deben depender de Spring MVC, JPA ni PostgreSQL. La migración `V1__initial_schema.sql` crea únicamente las tablas mínimas para usuario, caso, archivo y resultado. Hibernate no crea ni modifica tablas (`ddl-auto=validate`); todos los cambios de esquema deben añadirse como nuevas migraciones Flyway en `src/main/resources/db/migration`.

## Autenticación local de desarrollo

El proveedor se selecciona con `AUTH_PROVIDER`. El valor por defecto seguro es `disabled`; `AUTH_PROVIDER=local` habilita el proveedor local y sus tres cuentas mediante Flyway. El correo y contraseña de las cuentas de investigador/coordinador conservan identificadores históricos; sus roles guardados son `researcher` y `coordinator`:

| Rol | Correo | Contraseña |
|---|---|---|
| `patient` (Paciente) | `paciente@healthdocs.local` | `paciente123` |
| `researcher` (Investigador) | `estudiante@healthdocs.local` | `estudiante123` |
| `coordinator` (Coordinador) | `profesor@healthdocs.local` | `profesor123` |

`POST /api/v1/auth/login` devuelve el JWT local y `GET /api/v1/me` devuelve la identidad, rol y `enabled` de la cuenta autenticada. El secreto debe reemplazarse mediante `JWT_SECRET` fuera del entorno local. `AUTH_PROVIDER=local` solo habilita el seed de cuentas de prueba y solo funciona en perfiles `local`/`dev`.

Para validar access tokens de Supabase, configure `AUTH_PROVIDER=supabase`, `AUTH_ISSUER_URL`, `AUTH_JWKS_URL` y `AUTH_AUDIENCE` (por defecto `authenticated`). El backend verifica la firma mediante JWKS, limita los algoritmos a ES256/RS256 y comprueba issuer, audience y expiración. Luego resuelve `(provider=supabase, issuer, subject)` en `auth_identity`; no provisiona ni vincula cuentas automáticamente y toma el rol y `enabled` desde `app_user`. `POST /api/v1/auth/login` responde 410 cuando se selecciona Supabase porque las credenciales deben autenticarse desde el servicio de frontend. La interfaz `AuthProvider` conserva el proveedor local y deja separado el adaptador externo.

Para vincular cuentas existentes de forma aprobada use el importador no web del perfil `auth-migration`, documentado en [`docs/architecture/auth_identity_linking.md`](docs/architecture/auth_identity_linking.md). La simulación es el valor predeterminado; el importador valida access tokens vigentes y registra quién aprobó cada vínculo.

Supabase debe usar una llave de firma asimétrica para publicar las llaves en JWKS. El proveedor local continúa usando `JWT_SECRET` para emitir y validar tokens HMAC de desarrollo; no se debe reutilizar ese secreto para tokens externos.

**Discrepancia reportada:** la versión actual del contrato OpenAPI (`0.2.0`) no declara `enabled` en `UserProfile`; el backend lo expone de forma aditiva para cerrar el requisito de estado de cuenta. El contrato debe incorporar ese campo en la siguiente actualización coordinada.

Las reglas HTTP base son: `patient` para `/cases`, `researcher` o `coordinator` para `/internal`, y únicamente `coordinator` para `/operations`. El rol `admin` está disponible para gestión de roles; cualquier otro privilegio administrativo debe definirse de forma explícita. Los casos de uso que implementen esas rutas deben validar además propiedad del paciente o asignación explícita del usuario interno antes de consultar archivos, resultados o emitir URLs.

## Contrato API

El contrato canónico vive en [`openapi/openapi.yaml`](openapi/openapi.yaml). Antes de implementar o consumir un endpoint, revisar la [guía de trabajo paralelo](docs/api-contract-workflow.md).

## Seguridad

- No subir `.env`, secretos ni credenciales AWS al repositorio.
- Usar solo datos de prueba hasta definir las condiciones institucionales para información clínica real.

## Flujo interno

Los usuarios `researcher` y `coordinator` asignados pueden consultar `GET /api/v1/internal/cases`, solicitar la descarga de un `INPUT`, crear una intención en `POST /api/v1/internal/cases/{caseId}/result-uploads` y confirmar en `POST /api/v1/files/{fileId}/complete`. La confirmación valida el objeto, evita duplicados y asocia el resultado junto con la auditoría y el cambio de estado en una transacción.

## Notificaciones SQS de resultados

El adaptador SQS está desactivado por defecto. Las credenciales se resuelven
mediante la cadena estándar del AWS SDK (variables, perfil local o rol de
ejecución); nunca se configuran en el código.

| Variable | Default | Descripción |
|---|---|---|
| `HEALTHDOCS_SQS_ENABLED` | `false` | Activa el adaptador SQS |
| `AWS_REGION` | `us-east-1` | Región AWS |
| `HEALTHDOCS_SQS_QUEUE_NAME` | `result-available-dev` | Nombre de la cola |
| `HEALTHDOCS_SQS_ENDPOINT` | vacío | Endpoint opcional de LocalStack/pruebas |
| `HEALTHDOCS_SQS_TIMEOUT` | `5s` | Timeout por llamada |
| `HEALTHDOCS_SQS_MAX_RETRIES` | `3` | Reintentos de errores temporales |
| `HEALTHDOCS_SQS_RETRY_DELAY` | `250ms` | Espera entre reintentos |

El mensaje JSON contiene `eventVersion`, `eventType`, `eventId`, `resultId`,
`caseId`, `patientId`, `patientEmail`, `availableAt`, `eventTimestamp` y
`publishedBy`. No incluye datos clínicos, URLs, tokens ni credenciales. La cola
estándar no ofrece deduplicación nativa: `eventId` se conserva para que el
consumidor del paso 5 pueda deduplicar.
