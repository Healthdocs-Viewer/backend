# Usuarios locales de demostración

`db/seed/local/V2__local_demo_users.sql` agrega de forma idempotente los 18 perfiles de investigador de `academic-students.json`. Conserva sus UUIDs y códigos de investigador para que las relaciones de los mocks académicos puedan usar identidades estables. Los correos terminan en `@demo.healthdocs.local` y los subjects usan el prefijo `local:demo:`.

## Roles disponibles

| Rol | Cuenta | Uso en los mocks |
|---|---|---|
| `patient` | Cuenta local existente `paciente@healthdocs.local` (UUID `11111111-1111-1111-1111-111111111111`) | `patient-cases`, `patient-dashboard`, `patient-results`, `notifications` y `notification-preferences`. Los mocks no exponen el ID del usuario autenticado; los conteos y preferencias son respuestas asociadas a esa sesión. |
| `coordinator` | Cuenta local existente `profesor@healthdocs.local` (UUID `33333333-3333-3333-3333-333333333333`) | `coordinator-results`; también representa al actor de coordinación implícito en `academic-students`, `case-student-assignments` y `academic-cases`. |
| `researcher` | 18 cuentas de demostración, enumeradas en `academic-students.json` | `academic-students`, `academic-student-cases`, `case-student-assignments`, `researcher-results` y perfiles de quienes cargan resultados en `coordinator-results`. El investigador de ID `a0000007-0000-4000-8000-000000000007` tiene estado `RESTRICTED`; los otros 17 están `AUTHORIZED`. |

Los nombres, correos, códigos e IDs de los investigadores salen del mock académico. Los conteos de asignaciones son agregados del mock, no atributos de usuario, por lo que esta semilla no los persiste. Las preferencias, notificaciones, casos, archivos y resultados también son datos de otros dominios y no forman parte de esta semilla de usuarios.

## Limitación de consistencia de los mocks

No se pueden representar todas las referencias de identidad de los 11 mocks con un único usuario coherente por investigador. `academic-students.json` identifica, por ejemplo, a Ana Ruiz con el ID `a0000001-0000-4000-8000-000000000001`, mientras `coordinator-results.json` usa `b0000000-0000-4000-8000-000000000001` para la misma persona y código. `academic-cases.json` usa IDs `950e8400-e29b-41d4-a716-446655440102` y similares para uploaders y no incluye perfiles que permitan asignarlos. Crear usuarios duplicados con el mismo código violaría la unicidad case-insensitive de V13; inventar códigos o nombres para esos IDs tampoco está respaldado por el contrato. La semilla adopta los IDs del mock académico como identidades canónicas. Esas referencias de uploader son la limitación pendiente.

Los IDs de caso de `patient-cases` y `patient-dashboard` tampoco son iguales entre mocks. El usuario de paciente puede representarse con la cuenta local existente, pero esos casos no se crean aquí.

## Ejecución y seguridad

La configuración `application-local.yml` ejecuta `V1__local_test_accounts.sql` y luego esta semilla mediante Spring SQL init en cada arranque local. También se puede ejecutar manualmente con `psql` desde `code/backend`:

```powershell
psql "$env:DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/seed/local/V2__local_demo_users.sql
```

No se documentan contraseñas. Los inserts reutilizan un hash de desarrollo local ya presente en la semilla existente. La autenticación local crea el vínculo `auth_identity` bajo demanda al iniciar sesión o emitir un token; no hace falta insertar identidades externas. El `ON CONFLICT` solo actualiza una fila si coinciden su UUID, subject local, correo y rol; nunca reclama otra cuenta por correo. Al repetir la semilla, `RESTRICTED` no vuelve a `AUTHORIZED`. `enabled` se establece únicamente para las filas nuevas y no se usa para expresar autorización.
