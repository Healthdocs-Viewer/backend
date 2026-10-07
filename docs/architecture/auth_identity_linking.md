# Vinculación de identidades de autenticación

## Regla

`app_user.id` es el identificador estable de negocio. Una identidad externa se vincula usando el UUID de esa cuenta y los claims verificados `provider`, `iss` y `sub`. El correo no se usa para buscar, crear ni fusionar cuentas.

## Cuentas existentes

1. Resolver la cuenta objetivo desde el inventario aprobado de usuarios y obtener su `app_user.id`.
2. Verificar el control de la identidad nueva con el proveedor y verificar por separado la titularidad de la cuenta existente, o completar una revisión administrativa documentada.
3. Invocar `AuthIdentityLinkingService.linkVerifiedIdentity` con el UUID interno y los claims obtenidos del token ya verificado.
4. Confirmar que la identidad resuelve al mismo `app_user.id` y que sus casos y asignaciones siguen ligados a ese UUID.

No se expone un endpoint de vinculación en la API. Los roles de paciente, investigador y coordinador no autorizan a vincular identidades. El servicio es interno hasta que exista un flujo de prueba de ambas cuentas y una política administrativa de aprobación.

## Importación controlada a Supabase

El backend incluye un importador de una sola ejecución bajo el perfil Spring `auth-migration`. Primero se crean o invitan las cuentas desde Supabase Auth por un administrador; no se copian `password_hash` locales ni se crea una cuenta por coincidencia de correo. Cada titular inicia sesión en Supabase y entrega un access token vigente para verificar el control de esa identidad. El operador compara ese titular con el inventario anterior de forma independiente y registra su identificador y una referencia de aprobación.

La entrada es un archivo UTF-8 temporal con este encabezado exacto:

```csv
app_user_id,access_token,verified_by,verification_reference
00000000-0000-0000-0000-000000000001,<access-token-vigente>,operador-01,HD-AUTH-2026-001
```

`app_user_id` debe provenir del inventario aprobado de Healthdocs; no se aceptan correos en el archivo. El `access_token` se valida con el `JwtDecoder` configurado para Supabase (firma/JWKS, `iss`, `aud` y expiración). El importador valida todas las filas antes de escribir, rechaza tokens repetidos y no muestra tokens ni subjects en sus logs. Por fila guarda `linked_by` y `verification_reference` en `auth_identity` para auditoría. Una identidad ya vinculada al mismo usuario es idempotente; si pertenece a otro usuario, se rechaza.

### Ejecución

1. Aplique las migraciones Flyway, incluida `V10__audit_auth_identity_links.sql`, en la base de datos objetivo.
2. Prepare la lista aprobada de IDs internos y complete el proceso de invitación/alta de usuarios en Supabase. Los tres usuarios semilla locales son cuentas de desarrollo y no se deben importar como usuarios reales.
3. Obtenga un access token vigente por usuario después de verificar su titularidad. Guarde el archivo fuera del repositorio con permisos restringidos y elimínelo al terminar; es una credencial bearer.
4. Configure `AUTH_PROVIDER=supabase`, `AUTH_ISSUER_URL`, `AUTH_JWKS_URL`, `AUTH_AUDIENCE`, las variables de conexión de la base de datos y `AUTH_MIGRATION_FILE`.
5. Haga primero una simulación con `AUTH_MIGRATION_APPLY=false` (valor predeterminado) y `SPRING_PROFILES_ACTIVE=auth-migration`. Ejecute la aplicación sin servidor HTTP: `mvn "-Dspring-boot.run.arguments=--spring.main.web-application-type=none" spring-boot:run`.
6. Si la simulación termina correctamente y la revisión administrativa está aprobada, repita con tokens aún vigentes y `AUTH_MIGRATION_APPLY=true`. La ejecución termina al acabar porque el perfil de importación es no web.
7. Confirme que cada `sub` resuelve al `app_user.id` esperado y que casos/asignaciones siguen asociados a ese UUID. Borre el archivo de tokens y mantenga `AUTH_PROVIDER=local` en desarrollo; active Supabase en el entorno integrado solo después de completar el paso 4 del frontend y esta vinculación.

El importador no invita usuarios ni autentica por ellos. El token demuestra control actual de la identidad Supabase; `verified_by` y `verification_reference` registran la revisión separada de que esa identidad corresponde a la cuenta interna indicada. No se deben reutilizar contraseñas ni hashes locales.

## Cuentas nuevas y conflictos

`POST /api/v1/auth/register` verifica el bearer token y aprovisiona una cuenta nueva con rol `patient`. El correo se normaliza a minúsculas para cuentas nuevas; las búsquedas de conflicto ignoran mayúsculas/minúsculas.

- Si ya existe `provider + iss + sub`, el registro devuelve el `app_user` vinculado y conserva su correo y rol actuales. Repetir la solicitud es idempotente.
- Si la identidad es nueva pero el correo ya pertenece a otro `app_user`, responde `409 Conflict`. No vincula ni fusiona las cuentas, aunque coincidan sus correos.
- Solicitudes concurrentes para la misma identidad se serializan y devuelven el usuario creado por la primera. Identidades distintas con el mismo correo también se serializan por correo; solo una puede registrarse.
- Un token válido pero sin identidad vinculada no resuelve usuario en endpoints protegidos y debe recibir 401. `POST /api/v1/auth/register` es la excepción: verifica el token antes de crear la cuenta y su vínculo.
- Nunca se fusionan registros porque coincida el correo.
