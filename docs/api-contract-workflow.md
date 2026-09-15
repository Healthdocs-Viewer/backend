# Flujo de contrato API y trabajo paralelo

## Fuente única de verdad

`openapi/openapi.yaml` es el contrato canónico entre frontend y backend. Ningún agente debe inventar una ruta, campo, código de estado o estructura de error que no esté descrita en este archivo.

La versión del contrato vive en `info.version`. Mientras esté en `0.x`, un cambio incompatible exige incrementar el número menor y avisar al resto del equipo. Un cambio compatible, como añadir un campo opcional, incrementa el último número cuando se adopte un esquema de tres partes.

## Secuencia para cada historia

1. El coordinador modifica o aprueba el contrato OpenAPI.
2. El agente frontend implementa la pantalla con mocks que respetan el contrato.
3. El agente backend implementa los mismos endpoints y respuestas.
4. El agente de infraestructura publica variables, URLs y recursos requeridos, sin modificar el contrato funcional.
5. El coordinador compara frontend y backend contra el contrato, ejecuta pruebas de integración y aprueba la unión.

## Reglas de colaboración

- Un único agente modifica `openapi/openapi.yaml` en cada momento.
- Backend no modifica archivos de `frontend`; frontend no modifica controladores ni migraciones de `backend`.
- Infraestructura no guarda secretos ni estados Terraform en Git.
- Los cambios incompatibles requieren una propuesta antes de programarse.
- Cada pull request debe indicar el requisito afectado, por ejemplo `RF-04` o `RNF-05`.
- Cada endpoint protegido debe devolver `401`, `403` o `404` conforme al contrato; no exponer detalles de autorización en los mensajes.

## División inicial de agentes

| Agente | Objetivo | Archivos bajo su responsabilidad |
|---|---|---|
| Coordinador | Mantener el contrato, revisar integración y resolver cambios entre equipos. | `openapi/`, documentación de integración y pruebas de contrato. |
| Backend | Implementar migraciones, casos de uso, puertos, adaptadores HTTP y pruebas de integración. | `src/` y `pom.xml` en `backend`. |
| Frontend | Crear pantallas, cliente API tipado y mocks conformes a OpenAPI. | `src/` en `frontend`. |
| Infraestructura | Crear recursos Terraform y variables de despliegue. | Todo `infra`. |

## Instrucciones que puedes dar a los agentes

### Agente backend

> Implementa RF-01 a RF-05 conforme a `openapi/openapi.yaml`. No modifiques el contrato. Crea migraciones y pruebas de integración. Devuelve una lista de endpoints implementados y cualquier discrepancia que encuentres.

### Agente frontend

> Implementa las pantallas de paciente para RF-01 a RF-05 usando `openapi/openapi.yaml` como contrato. Usa mocks estrictamente conformes mientras el backend no esté disponible. No agregues campos ni endpoints no definidos en el contrato.

### Agente de infraestructura

> Prepara el entorno de desarrollo para la arquitectura documentada. Implementa solo recursos requeridos por el incremento actual: PostgreSQL local/AWS, S3 y configuración de API. No crees recursos de producción ni guardes secretos o estado Terraform en Git.

### Agente coordinador

> Revisa que las implementaciones de frontend y backend cumplan `openapi/openapi.yaml`, RF-01 a RF-05 y los criterios de aceptación. Reporta diferencias concretas y no modifiques código de otros agentes salvo para resolver conflictos de integración.

## Contrato de la primera entrega

La primera entrega cubre autenticación de desarrollo, creación de caso, intención y confirmación de carga, lista interna de casos habilitados, descarga, carga de resultado, disponibilidad y consulta del resultado. Las operaciones bajo `/operations` son temporales para demostración; no definen todavía el rol administrativo final.
