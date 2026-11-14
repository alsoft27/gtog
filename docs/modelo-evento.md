# Modelo de datos: Evento

Estado del modelo tras R1–R7: creación de eventos, consulta/listado (R1), opciones de respuesta (R2),
ubicación/acceso en línea (R3), editar/publicar/cancelar (R4), seguridad/ownership (R5), invitados (R6), consulta pública (R7) y plazo editable (preparación de R8, commit 0). Todavía no existen respuestas ni muro social.

---

## `Event` (agregado raíz)

Vive en `com.gtog.event.domain.model.Event`. Es una clase plana, sin anotaciones de framework, con dos builders
internos en vez de factory methods con parámetros posicionales (la lista de campos ya ha cambiado de forma en
tres rebanadas seguidas): `Event.builder()...build()` valida todas las reglas de negocio, genera el id y aplica
los valores por defecto, para eventos nuevos; `Event.reconstituteBuilder()...build()` rehidrata un evento ya
persistido sin repetir ninguna validación.

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | `String` | UUID generado por el dominio al crear el evento. No lo asigna Mongo. |
| `hostId` | `String` | Identificador del anfitrión. Desde R5 proviene del usuario autenticado (sesión), nunca del body del request. |
| `title` | `String` | Título del evento. Obligatorio. |
| `description` | `String` | Descripción libre. Opcional. |
| `startsAt` / `endsAt` | `LocalDateTime` | Hora local del evento. `endsAt` debe ser estrictamente posterior a `startsAt` una vez interpretados con `timeZone` (`InvalidEventPeriodException` si no). |
| `timeZone` | `String` | Zona horaria IANA (`region/ciudad`, p. ej. `Europe/Madrid`). Se valida con `ZoneId.of(...)`; si no existe, `InvalidTimeZoneException`. |
| `modality` | `Modality` | `IN_PERSON` o `ONLINE`. Un evento nunca es las dos cosas (modalidad híbrida fuera de alcance). |
| `status` | `EventStatus` | `DRAFT`, `PUBLISHED`, `CANCELLED`. Ver máquina de estados más abajo. `FINISHED` no está implementado (D-FINISHED). |
| `cancelledAt` | `Instant` (nullable) | Instante de cancelación. `null` si el evento no está cancelado. |
| `cancellationReason` | `String` (nullable) | Motivo de cancelación, opcional. |
| `responseOptions` | `List<ResponseOption>` | Ver sección siguiente. Entre 2 y 5, embebidas en el propio evento. |
| `allowComment` | `boolean` | Si el invitado puede añadir un comentario a su respuesta. Default `false`: es un dato personal que el anfitrión debe activar de forma consciente. |
| `allowResponseChange` | `boolean` | Si el invitado puede cambiar su respuesta tras enviarla. Default `true`. |
| `responseDeadline` | `LocalDateTime` (nullable) | Fecha límite para responder, en la hora local del evento. Si viene, no puede ser posterior a `startsAt` (`InvalidResponseDeadlineException`). Sin valor por defecto. |
| `venue` | `Venue` (nullable) | Ver más abajo. Obligatorio si `modality` es `IN_PERSON`, prohibido si es `ONLINE`. |
| `onlineAccess` | `OnlineAccess` (nullable) | Ver más abajo. Obligatorio si `modality` es `ONLINE`, prohibido si es `IN_PERSON`. |
| `guests` | `List<Guest>` | Invitados del mismo agregado, sin respuesta embebida. Lista vacía por defecto. Ver `docs/modelo-invitado.md`. |
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
| `IN_PERSON` requiere `venue` | `MissingVenueException` | 422 |
| `IN_PERSON` prohíbe `onlineAccess` | `UnexpectedOnlineAccessException` | 422 |
| `ONLINE` requiere `onlineAccess` | `MissingOnlineAccessException` | 422 |
| `ONLINE` prohíbe `venue` | `UnexpectedVenueException` | 422 |
| Campos obligatorios de `Venue` (`placeName`, `address`, `latitude`, `longitude`, `placeId`) | `MissingVenueFieldException` | 422 |
| Campos obligatorios de `OnlineAccess` (`platform`, `url`, `linkVisibility`) | `MissingOnlineAccessFieldException` | 422 |
| `url` de `OnlineAccess` debe ser `http`/`https` válida | `InvalidOnlineAccessUrlException` | 422 |
| `hoursBefore` obligatorio si `linkVisibility` es `HOURS_BEFORE` | `MissingHoursBeforeException` | 422 |
| `hoursBefore` debe ser `> 0` si aplica, y no debe enviarse si `linkVisibility` no es `HOURS_BEFORE` | `InvalidHoursBeforeException` | 422 |
| Las opciones solo se pueden reemplazar mientras el evento está en `DRAFT` | `EventNotEditableException` | 409 |
| Reemplazar `venue` o `onlineAccess` exige compatibilidad con la modalidad del evento existente | `ModalityConflictException` | 409 |
| El evento debe existir | `EventNotFoundException` | 404 |

Las 4 reglas de modalidad (`Missing`/`Unexpected` × `Venue`/`OnlineAccess`) validan la creación del evento y
producen 422. En cambio, `PUT /api/events/{id}/venue` sobre un evento `ONLINE` y
`PUT /api/events/{id}/online-access` sobre uno `IN_PERSON` lanzan `ModalityConflictException` y producen **409**:
el cuerpo es válido, pero no es compatible con el estado del recurso existente. Los campos inválidos del
bloque siguen produciendo 422. Los demás conflictos de estado son `EventNotEditableException`,
`EventNotPublishableException` y `EventNotCancellableException`.

### Máquina de estados

```
DRAFT ──publish()──► PUBLISHED ──cancel(reason, now)──► CANCELLED
  │                                                          │
  └──────── cancel() lanza EventNotCancellableException ─────┘
            (cancelar solo se permite desde PUBLISHED)
```

| Transición | Método | Condición de entrada | Excepción si falla |
|---|---|---|---|
| `DRAFT → PUBLISHED` | `publish()` | `status == DRAFT` + venue/onlineAccess presente + ≥2 opciones | `EventNotPublishableException` (409) / `IncompleteEventForPublishException` (422) |
| `PUBLISHED → CANCELLED` | `cancel(reason, now)` | `status == PUBLISHED` | `EventNotCancellableException` (409) |

**`FINISHED` no implementado** (D-FINISHED). No existe job programado ni derivación al vuelo. Requiere
decidir si la transición es automática (por fecha) o manual (el anfitrión la activa).

**Borrar un `DRAFT`** tampoco está implementado (D-DELETE-DRAFT). Cancelar un borrador sigue bloqueado por
diseño. Desde R6 puede tener invitados; esa circunstancia no cambia la máquina de estados ni implementa el borrado.

**Todas** heredan de `EventDomainException`, sin excepción: el dominio no sabe de códigos HTTP, solo expresa que
algo viola una regla suya. Es el `@RestControllerAdvice` (`GlobalExceptionHandler`, en `shared`) quien decide el
código: un `@ExceptionHandler` genérico para `EventDomainException` → 422, y uno específico para
`EventNotFoundException` → 404 y manejadores específicos para `EventNotEditableException`,
`EventNotPublishableException`, `EventNotCancellableException` y `ModalityConflictException` → 409, que Spring
resuelve por especificidad de tipo antes que caer en el genérico.

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

La especificación (regla de negocio 4 de `AGENTS.md`) exige que, una vez publicado el evento, una opción de
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

## `Venue` (ubicación)

`record` en `domain.model`. Todos los campos obligatorios salvo `directions`: `placeName`, `address`, `latitude`
(`Double`, no `double` — para poder distinguir "no vino" de la coordenada real `0.0`), `longitude` (`Double`),
`placeId`.

**El backend no llama a la API de Google Maps ni a ninguna otra.** Confía en que el cliente ya ha resuelto el
lugar (buscándolo, geocodificándolo) y manda los datos finales. No se valida que `placeId` exista de verdad ni
que `latitude`/`longitude` sean coherentes con `address` — esa resolución es responsabilidad del cliente.

## `OnlineAccess` (acceso en línea)

`record` en `domain.model`. Obligatorios: `platform`, `url`, `linkVisibility`. Opcionales: `roomId`, `password`,
`instructions`. `url` se valida como URL con esquema `http` o `https` (regla de dominio con `java.net.URI`, no
una anotación Jakarta). `hoursBefore` (`Integer`) solo tiene sentido si `linkVisibility` es `HOURS_BEFORE`: en
ese caso es obligatorio y debe ser mayor que cero; en cualquier otro modo, si se envía, se rechaza — mandar un
dato que no se va a usar indica que el cliente entendió mal la API.

### `LinkVisibility`

```java
LinkVisibility { ON_CONFIRMATION, HOURS_BEFORE, ALWAYS }
```

### Visibilidad del enlace: `Event.visibleOnlineAccess(...)`

```java
public Optional<OnlineAccess> visibleOnlineAccess(boolean guestHasConfirmed, Instant now)
```

Devuelve el `OnlineAccess` si procede mostrarlo, o `Optional.empty()` en caso contrario (evento presencial, o la
regla de `LinkVisibility` todavía no lo permite) — así quien llama no puede olvidarse de comprobar un booleano
antes de leer el enlace. Recibe `now` **como parámetro**, nunca `Instant.now()` internamente, para que el
resultado sea determinista y comprobable en tests.

Desde R7, `GetInvitationService` aplica este método con el instante obtenido de un `Clock` inyectado.
Solo devuelve acceso si `status == PUBLISHED`; DRAFT, CANCELLED y FINISHED siempre producen vacío.
`guestHasConfirmed` se obtiene de un único método privado que devuelve false hasta R8: se necesitará una
respuesta cuya opción cuente como asistencia (D-CONFIRMED-LINK). `InvitationResponse` es un DTO propio,
con metadatos de acceso sin secretos y el bloque de conexión omitido cuando esté oculto. `EventResponse`
sigue siendo la vista privada del anfitrión y conserva el acceso completo sin aplicar este filtrado.

---

## Enums

```java
EventStatus    { DRAFT, PUBLISHED, FINISHED, CANCELLED }   // FINISHED sin implementar (D-FINISHED)
Modality       { IN_PERSON, ONLINE }
LinkVisibility { ON_CONFIRMATION, HOURS_BEFORE, ALWAYS }
```

---

## Persistencia (`events`)

### Plazo editable (preparación de R8)

`Event.changeResponseDeadline(deadline, now)` permite cambiar el plazo en DRAFT y en PUBLISHED solo antes
de `startsAtInstant()`. El inicio exacto y los estados CANCELLED/FINISHED producen
`ResponseDeadlineNotEditableException` (409). El instante procede del Clock inyectado en aplicación.
La misma validación que usan creación y `edit()` exige plazo no posterior a startsAt
(`InvalidResponseDeadlineException`, 422). null elimina el plazo; un plazo pasado es válido.

`ChangeResponseDeadlineService` comprueba propiedad (404 si es ajeno) y delega la escritura en
`EventResponseDeadlineRepositoryPort`. El adaptador usa findAndModify con `$set` de responseDeadline y
`$inc` de version, filtrando por id, hostId y estado DRAFT/PUBLISHED, sin versión esperada ni `save()`.
Un resultado sin coincidencia es conflicto concurrente (409). Las aperturas concurrentes conservan ambos
cambios; una cancelación con versión anterior falla con 409 y no sobrescribe el plazo.

### Documento e índices

`EventDocument` (en `infrastructure/out/persistence`) espeja `Event` campo a campo, con `ResponseOptionDocument`,
`VenueDocument`, `OnlineAccessDocument` y `GuestDocument` como documentos embebidos (sin `@Document` propio, sin colección aparte).
`EventMapper` traduce en ambos sentidos; `modality`, `status` y `linkVisibility` se guardan como `String` (nombre
del enum), nunca como ordinal.

Índices creados en el arranque (`MongoIndexInitializer`, en `shared/config`, idempotente vía
`MongoTemplate.indexOps(...).createIndex(...)`):

- `hostId` — ascendente, para el listado del panel del anfitrión.

- `guests.token` — único y multiclave, con filtro parcial `guests.token: { $type: "string" }`, para que los
  eventos sin invitados queden fuera del índice. D-3 saldada.

`GuestMapper` traduce los invitados en ambos sentidos. `invitationStatus` y `deliveryChannel` se guardan como
cadenas; los documentos anteriores sin `guests[]` se leen con lista vacía. Los `save()` existentes de evento
preservan todos sus invitados mediante el mapper.

Las operaciones de R6 nunca guardan el evento entero: `EventGuestRepositoryPort` se implementa con `$push`,
`$set` posicional sobre los campos editables y `$pull`, siempre filtrando por id, propietario, estado permitido
y versión esperada, e incrementando `version` en la misma operación. Un conflicto se traduce a 409. Esto
protege las reglas validadas en el agregado y evita que un `save()` anterior borre una escritura parcial.
El índice impone unicidad entre eventos; la regla de dominio y la escritura condicionada evitan duplicados
dentro del mismo `guests[]`. Ver `docs/modelo-invitado.md`.

---

## Puertos y casos de uso

| Caso de uso (`port/in`) | Servicio (`application`) | Qué hace |
|---|---|---|
| `CreateEventUseCase` | `CreateEventService` | Crea el evento en `DRAFT` con sus opciones (o los defaults) y lo persiste. |
| `GetEventByIdUseCase` | `EventQueryService` | Devuelve el evento completo o lanza `EventNotFoundException`. |
| `ListEventsByHostUseCase` | `EventQueryService` | Lista los eventos de un `hostId`, sin paginación. |
| `ReplaceResponseOptionsUseCase` | `ReplaceResponseOptionsService` | Busca el evento, delega la sustitución de opciones (solo la lista, sin `allowComment`/`allowResponseChange`/`responseDeadline`) en el propio `Event` y lo guarda. |
| `ReplaceVenueUseCase` | `EventLocationService` | Busca el evento, delega en `event.replaceVenue(...)` y lo guarda. |
| `ReplaceOnlineAccessUseCase` | `EventLocationService` | Busca el evento, delega en `event.replaceOnlineAccess(...)` y lo guarda. |
| `UpdateEventUseCase` | `UpdateEventService` | Busca el evento, delega en `event.edit(EventEdit)` y lo guarda. `allowComment`/`allowResponseChange`/`responseDeadline` se editan aquí, no en `replaceResponseOptions`. |
| `ChangeResponseDeadlineUseCase` | `ChangeResponseDeadlineService` | Comprueba propiedad, valida plazo y estado con Clock, y actualiza solo el plazo mediante el puerto de escritura parcial. |
| `PublishEventUseCase` | `PublishEventService` | Busca el evento, llama a `event.publish()` y lo guarda. |
| `AddGuestUseCase` / `UpdateGuestUseCase` | `GuestService` | Valida propiedad y reglas del agregado; persiste únicamente el alta o los contactos editados. |
| `DeleteGuestUseCase` | `GuestService` | Valida estado y propiedad; prepara la futura confirmación y cascada, y elimina el invitado con escritura parcial. |
| `ListGuestUseCase` | `GuestService` | Devuelve los invitados al propietario, también en eventos cerrados. |
| `CancelEventUseCase` | `CancelEventService` | Busca el evento, llama a `event.cancel(reason, Instant.now())` y lo guarda. |

`EventRepositoryPort` (`port/out`): `save`, `findById`, `findByHostId`. Implementado por `EventRepositoryAdapter`
sobre `EventMongoRepository` (Spring Data).

---

## API HTTP expuesta hoy

| Método | Ruta | Body / parámetros | Respuesta |
|---|---|---|---|
| `POST` | `/api/events` | `CreateEventRequest` | `201` + `Location`, `400`, `422` |
| `GET` | `/api/events/{id}` | — | `200` (`EventResponse` completo), `404` |
| `GET` | `/api/events` | — (el host se lee del principal autenticado) | `200` (lista de `EventSummaryResponse`: `id`, `title`, `startsAt`, `modality`, `status`), `401` si no hay sesión |
| `PUT` | `/api/events/{id}` | `UpdateEventRequest` (título, fechas, zona horaria, modalidad, venue/onlineAccess opcionales, `allowComment`, `allowResponseChange`, `responseDeadline`) | `200` (`EventResponse` completo), `404`, `409` (no `DRAFT`), `422` |
| `PUT` | `/api/events/{id}/response-deadline` | `{ "responseDeadline": LocalDateTime o null }`, sesión y CSRF | `200` (`EventResponse`), `401`, `403`, `404` (inexistente o ajeno), `409` (estado, inicio alcanzado o carrera), `422` (plazo posterior al inicio) |
| `POST` | `/api/events/{id}/publish` | — | `200` (`EventResponse` completo), `404`, `409` (no `DRAFT`), `422` (falta venue/onlineAccess o < 2 opciones) |
| `POST` | `/api/events/{id}/cancel` | `CancelEventRequest` (`reason` opcional) | `200` (`EventResponse` completo con `cancelledAt`), `404`, `409` (no `PUBLISHED`) |
| `PUT` | `/api/events/{id}/response-options` | `ReplaceResponseOptionsRequest` (solo la lista de opciones) | `200` (`EventResponse` completo), `404`, `409`, `422` |
| `PUT` | `/api/events/{id}/venue` | `VenueRequest` | `200` (`EventResponse` completo), `404`, `409` (no `DRAFT` o el evento es `ONLINE`), `422` (falta campo obligatorio) |
| `PUT` | `/api/events/{id}/online-access` | `OnlineAccessRequest` | `200` (`EventResponse` completo), `404`, `409` (no `DRAFT` o el evento es `IN_PERSON`), `422` (falta campo obligatorio, url inválida o `hoursBefore` incoherente) |

Los cuatro endpoints de invitados también son autenticados; las escrituras requieren CSRF:

| Método | Ruta | Respuesta |
|---|---|---|
| `POST` | `/api/events/{id}/guests` | `201` + `Location` + `GuestInvitationResponse`, `404`, `409`, `422` |
| `PUT` | `/api/events/{id}/guests/{guestId}` | `200` + `GuestResponse`, `404`, `409`, `422` |
| `DELETE` | `/api/events/{id}/guests/{guestId}` | `204`, `404`, `409`; parámetro `confirmDeleteResponse=false` |
| `GET` | `/api/events/{id}/guests` | `200` + lista de `GuestInvitationResponse`, `404` |

`EventResponse` continúa sin invitados ni tokens. El enlace individual solo se expone como `invitationPath`
en el alta y el listado de invitados, para RF-2.16. El DTO web `GuestResponse` describe contactos editados;
no es una respuesta de asistencia ni un documento de `responses`.

Documentado también en OpenAPI (`/v3/api-docs`, `/swagger-ui.html`), ver sección "Documentación de API" en
`AGENTS.md`.

---

## Fuera de alcance por ahora

- Transiciones a `SENT` y `OPENED`, envío por email/WhatsApp/Telegram.
- Respuestas de asistencia (futura colección `responses`).
- `FINISHED`: no implementado (D-FINISHED).
- Borrar un borrador: no implementado (D-DELETE-DRAFT).
- Muro social, acompañantes, check-in: iteración 2 o posteriores.
