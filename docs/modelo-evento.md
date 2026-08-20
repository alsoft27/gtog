# Modelo de datos: Evento

Estado del modelo tras las rebanadas implementadas hasta ahora: creación de eventos, consulta/listado (R1) y
opciones de respuesta (R2). Todavía no existen invitados, respuestas ni muro social — ese es el motivo de que
`Event` no tenga aún una lista de invitados embebida, aunque la especificación (`docs/mvp-alcance-iteracion-1.md`)
la contemple para más adelante.

---

## `Event` (agregado raíz)

Vive en `com.gtog.event.domain.model.Event`. Es una clase plana, sin anotaciones de framework, con dos formas de
construcción: `Event.create(...)` (valida todas las reglas de negocio, para eventos nuevos) y
`Event.reconstitute(...)` (rehidrata un evento ya persistido sin repetir la validación).

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | `String` | UUID generado por el dominio al crear el evento. No lo asigna Mongo. |
| `hostId` | `String` | Identificador del anfitrión. Hoy es un campo suelto en la petición; no hay todavía usuario autenticado (RF-1.1 sin implementar). |
| `title` | `String` | Título del evento. Obligatorio. |
| `description` | `String` | Descripción libre. Opcional. |
| `startsAt` / `endsAt` | `LocalDateTime` | Hora local del evento. `endsAt` debe ser estrictamente posterior a `startsAt` una vez interpretados con `timeZone` (`InvalidEventPeriodException` si no). |
| `timeZone` | `String` | Zona horaria IANA (`region/ciudad`, p. ej. `Europe/Madrid`). Se valida con `ZoneId.of(...)`; si no existe, `InvalidTimeZoneException`. |
| `modality` | `Modality` | `IN_PERSON` o `ONLINE`. Un evento nunca es las dos cosas (modalidad híbrida fuera de alcance). |
| `status` | `EventStatus` | `DRAFT`, `PUBLISHED`, `FINISHED`, `CANCELLED`. Hoy todo evento se crea en `DRAFT` y no existe todavía ningún caso de uso que lo transicione a los demás estados. |
| `responseOptions` | `List<ResponseOption>` | Ver sección siguiente. Entre 2 y 5, embebidas en el propio evento. |
| `allowComment` | `boolean` | Si el invitado puede añadir un comentario a su respuesta. Default `false`: es un dato personal que el anfitrión debe activar de forma consciente. |
| `allowResponseChange` | `boolean` | Si el invitado puede cambiar su respuesta tras enviarla. Default `true`. |
| `responseDeadline` | `LocalDateTime` (nullable) | Fecha límite para responder, en la hora local del evento. Si viene, no puede ser posterior a `startsAt` (`InvalidResponseDeadlineException`). Sin valor por defecto. |
| `version` | `Long` (nullable) | Bloqueo optimista de Mongo (`@Version`). `null` hasta el primer `save()`. |

Métodos derivados: `startsAtInstant()` / `endsAtInstant()` calculan el instante UTC a partir de la hora local y
`timeZone` **al vuelo**, no lo guardan congelado — así reflejan cualquier cambio futuro en las reglas horarias de
la zona.

### Reglas de negocio y sus excepciones

| Regla | Excepción | HTTP |
|---|---|---|
| El título es obligatorio | `BlankEventTitleException` | 422 |
| `endsAt` debe ser posterior a `startsAt` | `InvalidEventPeriodException` | 422 |
| `timeZone` debe ser una zona IANA válida | `InvalidTimeZoneException` | 422 |
| `responseDeadline`, si viene, no posterior a `startsAt` | `InvalidResponseDeadlineException` | 422 |
| Entre 2 y 5 opciones de respuesta | `InvalidResponseOptionCountException` | 422 |
| Al menos una opción cuenta como asistencia | `NoAttendanceResponseOptionException` | 422 |
| Etiquetas de opción no vacías | `BlankResponseOptionLabelException` | 422 |
| Etiquetas de opción sin duplicados en el mismo evento | `DuplicateResponseOptionLabelException` | 422 |
| Al editar, un id de opción que no pertenece al evento se rechaza (no se crea como nueva) | `UnknownResponseOptionIdException` | 422 |
| Las opciones solo se pueden reemplazar mientras el evento está en `DRAFT` | `EventNotEditableException` | 409 |
| El evento debe existir | `EventNotFoundException` | 404 |

**Todas** heredan de `EventDomainException`, sin excepción: el dominio no sabe de códigos HTTP, solo expresa que
algo viola una regla suya. Es el `@RestControllerAdvice` (`GlobalExceptionHandler`, en `shared`) quien decide el
código: un `@ExceptionHandler` genérico para `EventDomainException` → 422, y uno específico para
`EventNotFoundException` → 404 y otro para `EventNotEditableException` → 409, que Spring resuelve por
especificidad de tipo antes que caer en el genérico.

---

## `ResponseOption` (opción de respuesta)

Vive en `com.gtog.event.domain.model.ResponseOption`, como `record` embebido en la lista de `Event`. **No existe
un campo `order`**: el orden es la posición en la lista, tal cual se envía o se guarda.

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | `String` | UUID generado por el dominio (`ResponseOption.create(...)`), nunca por el cliente ni por Mongo. |
| `label` | `String` | Texto que ve el invitado. No puede estar vacío ni ser `null` — se valida en el propio constructor del record. |
| `countsAsAttendance` | `boolean` | Si marcar esta opción cuenta como asistencia confirmada. |

Si al crear un evento no se especifican opciones, el dominio aplica dos por defecto: **"Asisto"** (cuenta) y
**"No asisto"** (no cuenta).

### Identidad al editar (`PUT /api/events/{id}/response-options`)

El reemplazo de la lista distingue opción nueva de opción existente por la presencia de `id` en la petición:

- **Sin `id`** → opción nueva; el dominio le genera un id.
- **Con `id` que coincide con una opción actual del evento** → se conserva el id, se actualizan `label` y
  `countsAsAttendance` (permite renombrar sin perder la referencia desde `responses`, pensado para cuando exista
  esa colección).
- **Con `id` que no coincide con ninguna opción actual** → error, `UnknownResponseOptionIdException` (422). No se
  trata como opción nueva.

### Pendiente: "se puede renombrar pero no eliminar" una opción con respuestas

La especificación (regla de negocio 4 de `CLAUDE.md`) exige que, una vez publicado el evento, una opción de
respuesta con respuestas asociadas se pueda renombrar pero no eliminar de la lista. **Todavía no está
implementada**: `Event.replaceResponseOptions(...)` no distingue hoy entre "opción sin respuestas, se puede
quitar libremente" y "opción con respuestas, no se puede quitar" — permite quitar cualquier opción que no venga
en la lista de reemplazo, sin más.

El motivo es que el dominio de `Event` no conoce la colección `responses` (todavía no existe: es la rebanada
R8 del plan). Sin esa información, `Event` no puede saber qué ids de sus propias opciones tienen respuestas.

Cuando se implemente `responses`, `replaceResponseOptions(...)` tendrá que recibir además el conjunto de ids de
opciones con al menos una respuesta (calculado en la capa de aplicación, consultando `responses`, y pasado como
parámetro — el dominio sigue sin conocer la colección, solo el resultado de la pregunta). Con ese conjunto,
`Event` podrá rechazar con una nueva excepción de dominio (422) cualquier reemplazo que omita un id perteneciente
a ese conjunto.

---

## Enums

```java
EventStatus { DRAFT, PUBLISHED, FINISHED, CANCELLED }   // hoy solo se usa DRAFT; no hay transición implementada
Modality    { IN_PERSON, ONLINE }
```

---

## Persistencia (`events`)

`EventDocument` (en `infrastructure/out/persistence`) espeja `Event` campo a campo, con `ResponseOptionDocument`
como clase embebida (sin `@Document` propio, sin colección aparte). `EventMapper` traduce en ambos sentidos;
`modality` y `status` se guardan como `String` (nombre del enum), nunca como ordinal.

Índice creado en el arranque (`MongoIndexInitializer`, en `shared/config`, idempotente vía
`MongoTemplate.indexOps(...).createIndex(...)`):

- `hostId` — ascendente, para el listado del panel del anfitrión.

Pendiente para cuando exista la colección `guests` embebida en `events`: el índice único y multiclave sobre
`guests.token` que describe `docs/mvp-alcance-iteracion-1.md`.

---

## Puertos y casos de uso

| Caso de uso (`port/in`) | Servicio (`application`) | Qué hace |
|---|---|---|
| `CreateEventUseCase` | `CreateEventService` | Crea el evento en `DRAFT` con sus opciones (o los defaults) y lo persiste. |
| `GetEventByIdUseCase` | `EventQueryService` | Devuelve el evento completo o lanza `EventNotFoundException`. |
| `ListEventsByHostUseCase` | `EventQueryService` | Lista los eventos de un `hostId`, sin paginación. |
| `ReplaceResponseOptionsUseCase` | `ReplaceResponseOptionsService` | Busca el evento, delega la sustitución de opciones en el propio `Event` y lo guarda. |

`EventRepositoryPort` (`port/out`): `save`, `findById`, `findByHostId`. Implementado por `EventRepositoryAdapter`
sobre `EventMongoRepository` (Spring Data).

---

## API HTTP expuesta hoy

| Método | Ruta | Body / parámetros | Respuesta |
|---|---|---|---|
| `POST` | `/api/events` | `CreateEventRequest` | `201` + `Location`, `400`, `422` |
| `GET` | `/api/events/{id}` | — | `200` (`EventResponse` completo), `404` |
| `GET` | `/api/events?hostId=` | `hostId` obligatorio (temporal, hasta que exista el usuario autenticado) | `200` (lista de `EventSummaryResponse`: `id`, `title`, `startsAt`, `modality`, `status`), `400` si falta `hostId` |
| `PUT` | `/api/events/{id}/response-options` | `ReplaceResponseOptionsRequest` (lista completa + `allowComment` + `allowResponseChange` + `responseDeadline`) | `200` (`EventResponse` completo), `404`, `409`, `422` |

Documentado también en OpenAPI (`/v3/api-docs`, `/swagger-ui.html`), ver sección "Documentación de API" en
`CLAUDE.md`.

---

## Fuera de alcance por ahora

- Invitados (`Guest`), estado de invitación, envío por email/WhatsApp/Telegram.
- Respuestas de invitado (`GuestResponse`, colección `responses`).
- Transición de `Event` fuera de `DRAFT` (publicar, finalizar, cancelar) — no hay caso de uso todavía, por eso el
  409 de "opciones no editables" solo se prueba a nivel de dominio y no de extremo a extremo.
- Muro social, ubicación/acceso en línea, acompañantes, check-in: iteración 2 o posteriores.
