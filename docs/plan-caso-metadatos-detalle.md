# Plan de implementación: metadatos del caso para el detalle

## Objetivo

Ampliar `medical_case` y el contrato de creación para guardar los datos de la etapa **Información**. Estos campos se conservarán como metadatos del caso y se expondrán para mostrarlos en el detalle. No se usarán para búsquedas, filtros, ordenamiento, asignación, autorización ni procesamiento clínico.

El propietario del caso seguirá determinándose desde la identidad autenticada. `referenceCode` seguirá siendo generado por el backend; no se reemplaza por la referencia adicional del paciente.

## Campos y restricciones propuestas

| Campo API | Columna sugerida | Tipo | Requerido | Restricciones |
|---|---|---|---:|---|
| `studyName` | `study_name` (existente) | texto | Sí | Recortar espacios; no vacío; 1–200 caracteres. Nombre libre tal como aparece en la orden. |
| `additionalReference` | `additional_reference` | texto nullable | No | Recortar; vacío se normaliza a `null`; máximo 100 caracteres. Texto de referencia del paciente, no identificador del sistema. |
| `originInstitution` | `origin_institution` | texto | Sí | Recortar; no vacío; 1–200 caracteres. Nombre de clínica, hospital o centro. |
| `studyDate` | `study_date` | `DATE` | Sí | Fecha ISO `YYYY-MM-DD`; debe ser válida y no futura respecto a la fecha actual de Colombia (`America/Bogota`). |
| `notes` | `notes` | texto nullable | No | Texto libre, recortado; vacío se normaliza a `null`; máximo 500 caracteres. Se muestra únicamente en el detalle del caso. |

`referenceCode` mantiene el formato/semántica actuales y lo genera el servidor. Los campos opcionales no deben persistirse como cadenas vacías. No agregar reglas de normalización de categorías ni interpretación del contenido de `notes`.

## Cambios por capa

1. **Contrato canónico (`openapi/openapi.yaml`)**
   - Ampliar `CreateCaseRequest` con los cinco campos y describir requeridos, longitudes, formatos y semántica.
   - Ampliar la respuesta de creación y los esquemas de detalle/listado que correspondan con los metadatos persistidos. Mantener `additionalReference` y `notes` nullable.
   - Documentar expresamente que estos campos son informativos para presentar el detalle del caso y no participan en las reglas operativas.
   - Incrementar versión menor del OpenAPI por la ampliación incompatible para clientes que creen casos con el contrato anterior.

2. **Persistencia**
   - Crear migración Flyway `V17` que agregue las cuatro columnas nuevas a `medical_case`.
   - En entornos local/dev, el seed local rellena únicamente campos faltantes con valores ficticios. Nunca ejecutar ese backfill en producción.
   - Hacer `origin_institution` y `study_date` inicialmente nullable para que la migración no rompa casos existentes; hacerlos obligatorios a nivel de dominio y request para los casos nuevos. No inventar valores para registros históricos.
   - Añadir constraints de longitud/no vacío cuando sean compatibles con los registros existentes; opcionales deben ser null o no vacíos. Usar tipo SQL `DATE` para `study_date`.
   - Actualizar `MedicalCaseEntity`, sus constructores/getters y el adaptador de persistencia.

3. **Dominio y creación**
   - Añadir los campos al modelo `MedicalCase` y a su fábrica/constructor.
   - Extender `CreateCaseUseCase.Command` para aceptar los metadatos; aplicar recorte, validación requerida y normalización de opcionales.
   - Validar `studyDate` contra la fecha del `Clock` inyectado para que el comportamiento sea determinista.
   - No cambiar propietario, código de caso, estado operativo ni lógica de archivos/consentimiento.

4. **API y lecturas**
   - Extender `PatientCaseController.CreateCaseRequest` con Jakarta Validation y `LocalDate`.
   - Devolver los metadatos en `CaseResponse`.
   - Extender los DTOs de detalle de caso para paciente con los cinco valores, manteniendo control de propiedad del paciente.
   - Si vistas internas reutilizan un DTO común de detalle, incluir los campos allí solo cuando el contrato de esa pantalla los necesite para mostrar el detalle; no añadirlos a tarjetas/listas compactas por defecto.
   - No incorporarlos a búsquedas, sort, filtros, notificaciones ni payloads de resultados.

5. **Documentación y ejemplos**
   - Actualizar contrato API, guía del flujo de creación/retoma y ejemplos de Postman.
   - Anotar compatibilidad de datos existentes: casos previos pueden responder con `originInstitution` y `studyDate` en `null` hasta que se edite/complemente su información, si esa edición se incorpora en alcance.

## Secuencia de trabajo

1. Actualizar y revisar `openapi.yaml` como contrato único antes de cambiar controladores.
2. Agregar migración y mapear columnas nuevas.
3. Extender dominio y caso de uso de creación.
4. Actualizar request/response y lecturas de detalle autorizadas.
5. Alinear ejemplos/Postman y documentación de retomar caso.
6. Verificar validaciones, persistencia, respuesta API, propiedad del caso y compatibilidad de filas antiguas.

## Criterios de aceptación

- Una creación válida persiste y devuelve `studyName`, `originInstitution`, `studyDate`, `additionalReference` y `notes` sin alterar `referenceCode`.
- Falla con `400` si faltan `studyName`, `originInstitution` o `studyDate`; si exceden sus máximos; si los requeridos son blancos; o si la fecha no existe/es futura.
- Los valores opcionales pueden omitirse o ser `null`; se recortan y una cadena vacía se guarda como `null`; se rechazan si superan el máximo.
- El detalle devuelve los valores guardados. Un paciente no puede consultar los datos de otro caso.
- Las filas históricas existentes siguen migrando sin datos fabricados.
- Ninguna operación funcional existente usa referencia adicional, fecha, institución o notas para decidir estado, asignación, procesamiento, búsqueda o permisos.

## Alcance excluido

- Edición posterior de estos campos y reglas para invalidar etapas ya completadas.
- Taxonomía de estudios, catálogo de instituciones o normalización de nombres.
- Búsqueda, filtros, reportes, analítica o procesamiento a partir de estos campos.
- Interpretación clínica de `notes`.

La nota de pantalla “solo texto” queda representada como campo `String` de texto libre con límite de 500 caracteres; no se define un formato especial ni semántica adicional.
