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

El proveedor se selecciona con `AUTH_PROVIDER`. El valor por defecto seguro es `disabled`; `AUTH_PROVIDER=local` habilita el proveedor local y sus tres cuentas mediante Flyway:

| Rol | Correo | Contraseña |
|---|---|---|
| paciente | `paciente@healthdocs.local` | `paciente123` |
| estudiante | `estudiante@healthdocs.local` | `estudiante123` |
| profesor | `profesor@healthdocs.local` | `profesor123` |

`POST /api/v1/auth/login` devuelve el JWT local y `GET /api/v1/me` devuelve la identidad, rol y `enabled` de la cuenta autenticada. El secreto debe reemplazarse mediante `JWT_SECRET` fuera del entorno local. En producción se debe usar `AUTH_PROVIDER=disabled` hasta configurar el proveedor real; `AUTH_PROVIDER=local` solo habilita el seed de cuentas de prueba. La interfaz `AuthProvider` y el punto `CognitoAuthProvider` permiten incorporar Cognito sin cambiar controladores ni servicios de aplicación.

**Discrepancia reportada:** la versión actual del contrato OpenAPI (`0.2.0`) no declara `enabled` en `UserProfile`; el backend lo expone de forma aditiva para cerrar el requisito de estado de cuenta. El contrato debe incorporar ese campo en la siguiente actualización coordinada.

Las reglas HTTP base son: `paciente` para `/cases`, `estudiante` o `profesor` para `/internal`, y únicamente `profesor` para `/operations`. Los casos de uso que implementen esas rutas deben validar además propiedad del paciente o asignación explícita del usuario interno antes de consultar archivos, resultados o emitir URLs.

## Contrato API

El contrato canónico vive en [`openapi/openapi.yaml`](openapi/openapi.yaml). Antes de implementar o consumir un endpoint, revisar la [guía de trabajo paralelo](docs/api-contract-workflow.md).

## Seguridad

- No subir `.env`, secretos ni credenciales AWS al repositorio.
- Usar solo datos de prueba hasta definir las condiciones institucionales para información clínica real.

## Flujo interno

Los usuarios `estudiante` y `profesor` asignados pueden consultar `GET /api/v1/internal/cases`, solicitar la descarga de un `INPUT`, crear una intención en `POST /api/v1/internal/cases/{caseId}/result-uploads` y confirmar en `POST /api/v1/files/{fileId}/complete`. La confirmación valida el objeto, evita duplicados y asocia el resultado junto con la auditoría y el cambio de estado en una transacción.
