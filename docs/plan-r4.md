# R4 — Editar, publicar y cancelar

## 1. Dominio — excepciones nuevas

| Excepción | HTTP | Cuándo |
|---|---|---|
| `EventNotPublishableException` | 409 | `publish()` desde un estado distinto de `DRAFT` |
| `EventNotCancellableException` | 409 | `cancel()` desde `DRAFT` o `CANCELLED` |
| `IncompleteEventForPublishException` | 422 | Faltan `venue`/`onlineAccess` según modalidad, o < 2 opciones |
| `ModalityChangedWithoutLocationException` | 422 | La modalidad cambia pero no viene el bloque de ubicación |

`EventNotEditableException` ya existe — cubre el 409 del `edit()` en no-DRAFT.

---

## 2. Dominio — métodos nuevos en `Event`

### `publish()`
1. `status != DRAFT` → `EventNotPublishableException`
2. Reutiliza `validateModalityInvariant()` para comprobar `venue`/`onlineAccess`
3. `responseOptions.size() < 2` → `IncompleteEventForPublishException`
4. `status = PUBLISHED`

### `cancel(String reason, Instant now)`
1. `status != PUBLISHED` → `EventNotCancellableException`
   (DRAFT y CANCELLED quedan bloqueados; el borrado de borradores es rebanada posterior — D-DELETE-DRAFT)
2. `status = CANCELLED`, `cancelledAt = now`, `cancellationReason = reason` (nullable)
3. Irreversible — no existe `uncancel`.
4. `now` se pasa como parámetro para que los tests no dependan del reloj del sistema.

### `edit(UpdateEventCommand)`
1. `status != DRAFT` → `EventNotEditableException`
2. Actualiza: `title`, `description`, `startsAt`, `endsAt`, `timeZone`,
   `allowComment`, `allowResponseChange`, `responseDeadline`
3. Si `modality` cambia: el comando debe traer el bloque de la nueva modalidad;
   si no viene → `ModalityChangedWithoutLocationException`. El bloque anterior se descarta.
4. Si `modality` no cambia: si el comando trae `venue`/`onlineAccess`, se aplican
   como actualización del bloque existente. Nunca se ignoran en silencio.

**Campos nuevos en `Event`:** `cancelledAt: Instant`, `cancellationReason: String`.

**Deuda:** `FINISHED` no se implementa. Se anota como D-FINISHED en `docs/plan-iteracion-1.md`.

---

## 3. Limpieza — `ReplaceResponseOptions*`

Eliminar de `ReplaceResponseOptionsRequest` y `ReplaceResponseOptionsCommand`:
- `allowComment`
- `allowResponseChange`
- `responseDeadline`

Pasan a `UpdateEventRequest` / `UpdateEventCommand`.

---

## 4. Puertos (`port/in`) — tres interfaces nuevas

- `PublishEventUseCase` → `void publish(String eventId)`
- `CancelEventUseCase` → `void cancel(String eventId, String reason, Instant now)`
- `UpdateEventUseCase` → `Event update(UpdateEventCommand)`

**`UpdateEventCommand`:** `eventId`, `title`, `description`, `startsAt`, `endsAt`,
`timeZone`, `modality`, `venue` (nullable), `onlineAccess` (nullable),
`allowComment`, `allowResponseChange`, `responseDeadline`.

---

## 5. Aplicación — tres servicios nuevos

- `PublishEventService` — carga → `event.publish()` → guarda
- `CancelEventService` — carga → `event.cancel(reason, Instant.now())` → guarda
- `UpdateEventService` — carga → `event.edit(command)` → guarda

---

## 6. Persistencia

**`EventDocument`:** añadir `cancelledAt`, `cancellationReason`.
Verificar que los campos editables no sean `final`.

**Mapper:** propagar los dos campos nuevos en ambas direcciones.

---

## 7. Web — endpoints y DTOs

| Endpoint | Método | Request | Respuesta OK |
|---|---|---|---|
| `/api/events/{id}` | `PUT` | `UpdateEventRequest` | `200` con `EventResponse` |
| `/api/events/{id}/publish` | `POST` | vacío | `200` con `EventResponse` |
| `/api/events/{id}/cancel` | `POST` | `CancelEventRequest` | `200` con `EventResponse` |

**DTOs nuevos:**
- `UpdateEventRequest` — todos los campos editables (sin `hostId`;
  añade `allowComment`, `allowResponseChange`, `responseDeadline`)
- `CancelEventRequest` — `reason: String` (nullable)

**Modificar:**
- `ReplaceResponseOptionsRequest` — quitar los tres campos
- `EventResponse` — añadir `status`, `cancelledAt`, `cancellationReason` si no están

**`GlobalExceptionHandler`:** registrar handlers para las cuatro excepciones nuevas.

---

## 8. Tests

### Unitarios (dominio)

| Escenario | Resultado |
|---|---|
| `publish()` desde DRAFT con venue + ≥2 opciones | PUBLISHED |
| `publish()` desde DRAFT sin venue (IN_PERSON) | `IncompleteEventForPublishException` |
| `publish()` desde DRAFT sin onlineAccess (ONLINE) | `IncompleteEventForPublishException` |
| `publish()` desde DRAFT con 1 opción | `IncompleteEventForPublishException` |
| `publish()` desde PUBLISHED | `EventNotPublishableException` |
| `publish()` desde CANCELLED | `EventNotPublishableException` |
| `cancel()` desde DRAFT | `EventNotCancellableException` |
| `cancel()` desde PUBLISHED | CANCELLED con `cancelledAt` |
| `cancel()` desde CANCELLED | `EventNotCancellableException` |
| `edit()` desde DRAFT | campos actualizados |
| `edit()` desde PUBLISHED | `EventNotEditableException` |
| `edit()` cambia a ONLINE sin `onlineAccess` | `ModalityChangedWithoutLocationException` |
| `edit()` cambia a IN_PERSON con `venue` | `venue` actualizado, `onlineAccess` descartado |

### Integración (HTTP)

| Endpoint | Escenario | Código |
|---|---|---|
| `POST /publish` | happy path | 200 |
| `POST /publish` | ya PUBLISHED | 409 |
| `POST /publish` | sin venue (IN_PERSON) | 422 |
| `POST /cancel` | desde PUBLISHED | 200 |
| `POST /cancel` | desde DRAFT | 409 |
| `POST /cancel` | ya CANCELLED | 409 |
| `PUT /api/events/{id}` | happy path | 200 |
| `PUT /api/events/{id}` | evento PUBLISHED | 409 |
| `PUT /.../response-options` | evento PUBLISHED | 409 ← deuda pendiente de R2 |

---

## 9. Documentación

- `docs/plan-iteracion-1.md` — marcar R4 completada; añadir D-FINISHED
- `docs/modelo-evento.md` — máquina de estados, campos `cancelledAt`/`cancellationReason`,
  nota sobre FINISHED pendiente

---

## Orden de implementación

1. Dominio — excepciones + métodos + campos nuevos en `Event`
2. Limpieza de `ReplaceResponseOptions*`
3. Puertos + comandos
4. Servicios de aplicación
5. Persistencia — document + mapper
6. Web — DTOs + controlador + exception handlers
7. Tests unitarios + integración
