# Modelo de datos: Evento

Estado del modelo tras R1–R11: creación de eventos, consulta/listado (R1), opciones de respuesta (R2),
ubicación/acceso en línea (R3), editar/publicar/cancelar (R4), seguridad/ownership (R5), invitados (R6),
consulta pública (R7), plazo editable, registro de respuesta, enlace por confirmación, opciones publicadas,
cascada y calendario (R8), panel y contadores en el listado (R9), reservas atómicas y envío de correo (R10),
y generación de enlaces y confirmación manual de WhatsApp (R11), además del enlace público de alta (RF-2.19).
La vista pública incluye `canRespond`,
calculado con la misma política que el PUT de respuesta. Las respuestas viven en `responses`, nunca
embebidas en el evento. El muro social y Telegram siguen pendientes.

---

## `Event` (agregado raíz)

### Tipos temporales — pasos 1 a 4 de la ampliación

`EventType` distingue `FIXED_DATE` y `DATE_POLL`. Omitirlo al crear mantiene `FIXED_DATE`.
Desde el paso 2 se pueden crear, consultar y editar datos generales de borradores `DATE_POLL`.
Desde el paso 4 se pueden publicar sin fecha definitiva, consultar desde los enlaces públicos y enviar
invitaciones por correo o WhatsApp. La disponibilidad se registra por separado de la asistencia.

`EventSchedule` es el contrato temporal del dominio. `FixedDateSchedule` encapsula inicio, fin y zona;
`DatePollSchedule`, las candidatas y la zona, sin inicio ni fin definitivos. Creación y edición comparten
las validaciones del tipo temporal. `Event` conserva sus getters de fechas y conversiones a UTC para los consumidores
existentes; no se introducen fechas ni horas de relleno. La rehidratación conserva su contrato previo,
sin volver a aplicar las validaciones de creación.

Mongo conserva `startsAt`, `endsAt` y `timeZone` en la raíz del documento y añade `eventType` como cadena.
La ausencia del discriminador se interpreta como `FIXED_DATE`, sin migración ni escrituras de relleno al rehidratar.
El siguiente guardado completo añade el discriminador; las actualizaciones parciales conservan los datos
existentes. Un tipo almacenado desconocido no se interpreta como fecha fija: es un error de integridad
de almacenamiento (500 saneado).

`EventResponse`, `EventSummaryResponse`, `InvitationResponse` y la vista previa del enlace público
añaden `eventType`; mantienen todos los campos anteriores y sus políticas de privacidad y caché.
`EventSchedule` nunca se serializa directamente en la frontera web.

### Borradores de consulta de disponibilidad (`DATE_POLL`)

- `CandidateDate` es un record propio: `id`, `LocalDate date` obligatoria y `LocalTime startTime` opcional.
  El dominio genera el id; las candidatas no tienen hora de fin. La zona es la del evento. Se conserva
  su orden y se pueden mezclar fechas con hora y fechas sin ella, o no indicar ninguna hora.
- Entre dos y `app.events.max-candidate-dates` candidatas (10 por defecto). No se repite una fecha,
  aunque cambie la hora. Las listas del dominio son inmutables.
- `responseDeadline` es obligatorio, en la zona del evento. No se compara con un inicio definitivo
  inexistente. La validación se comparte entre alta, edición general y cambio específico del plazo;
  quitarlo devuelve 422 y no escribe.
- `responseOptions` queda vacío: no se generan «Asisto/No asisto». En el alta se rechazan con 422
  las opciones de asistencia no vacías, las fechas definitivas y el bloque de consulta en un `FIXED_DATE`.
  Reemplazar opciones de asistencia de un `DATE_POLL` da 409.
- `PUT /api/events/{id}` permite editar los datos generales del borrador indicando `eventType=DATE_POLL`,
  sin `startsAt` ni `endsAt`, y con plazo. Conserva ids, fechas y horas de las candidatas y la intención
  del enlace público. El tipo no se puede convertir. Las candidatas cambian por su endpoint específico.
- Las vistas del anfitrión incluyen `datePoll: {candidateDates:[{id,date,startTime}],pollRevision,selectedCandidateDateId}` solo para consultas;
  inicio y fin definitivos son null. El listado conserva sus diez campos, con `eventType=DATE_POLL`
  y `startsAt=null`. Los datos generales de modalidad, lugar/acceso y propiedad se validan como antes.

Ejemplo de creación, con sesión y CSRF:

```json
{
  "title": "Cena de compañeros",
  "eventType": "DATE_POLL",
  "timeZone": "Europe/Madrid",
  "modality": "IN_PERSON",
  "venue": {"placeName":"Sala", "address":"Calle Mayor", "latitude":40.4, "longitude":-3.7, "placeId":"place"},
  "responseDeadline": "2027-05-09T18:00:00",
  "datePoll": {"candidateDates":[
    {"date":"2027-05-10", "startTime":"20:00:00"},
    {"date":"2027-05-11"}
  ]}
}
```

`POST /api/events` devuelve 201 y Location. Un JSON o formato de fecha/hora ilegible da 400; el bloque
ausente, lista fuera de límites, fecha ausente, fecha duplicada, zona inválida o plazo ausente dan 422.
Las excepciones son `InvalidCandidateDateCountException`, `MissingCandidateDateException`,
`DuplicateCandidateDateException`, `InvalidTimeZoneException`, `MissingDatePollDeadlineException` e
`IncompatibleEventScheduleException`. Los ids inválidos o repetidos al rehidratar se rechazan con
`InvalidCandidateDateIdException`; el mapper traduce la corrupción persistida a 500 saneado.

En Mongo, `datePoll.candidateDates` embebe `{_id,date,startTime?}` con fecha y hora como texto ISO.
Spring Data representa como `_id` el `id` lógico de cada candidata; el JSON del API usa `id`.
No se guarda una medianoche ni una fecha auxiliar para representar sus componentes. `startsAt` y
`endsAt` se omiten en consultas sin fecha definitiva. El máximo configurable se aplica al crear y reemplazar candidatas;
reducirlo no impide leer las consultas existentes. No hay índices ni migraciones nuevos.

### Reemplazo de candidatas y revisión de consulta — paso 3

`PUT /api/events/{id}/candidate-dates` requiere sesión, CSRF y propiedad comprobada en aplicación
(404 si el evento es ajeno). Admite DRAFT y PUBLISHED; CANCELLED/FINISHED y un evento FIXED_DATE dan 409.
Devuelve 200 con EventResponse. El cuerpo representa la lista completa:

```json
{"pollRevision":0,"candidateDates":[
  {"id":"candidate-1","date":"2027-05-14","startTime":"20:00:00"},
  {"id":"candidate-2","date":"2027-05-11","startTime":null}
]}
```

- Los ids existentes se conservan; los desconocidos con revisión actual dan 422 (`UnknownCandidateDateIdException`).
  Sin id se reutiliza una candidata con la misma fecha y hora, o se genera una identidad nueva.
  Se mantienen las reglas de cantidad, fecha obligatoria, ausencia de fin, fechas e ids únicos.
- `pollRevision` es obligatorio y no negativo (422, `InvalidPollRevisionException`). Nace en cero;
  la ausencia del campo en documentos anteriores también significa cero, sin migración al leer.
- Una modificación de fecha u hora, alta o baja incrementa la revisión **una sola vez** y elimina
  `selectedCandidateDateId`. Intercambiar fechas entre ids también cambia su significado y avanza la ronda.
  Las disponibilidades de otra revisión quedan lógicamente invalidadas y no se muestran al invitado.
- Repetir una configuración con o sin ids no incrementa la revisión ni escribe. Reordenar conserva
  revisión y selección, pero guarda el orden y aumenta `version`. Cambiar la revisión del documento
  por otra operación del evento no equivale a cambiar la ronda de disponibilidad.
- Una revisión obsoleta que intenta cambiar la configuración da 409 (`StalePollRevisionException`).
  También si referencia una candidata que ya se eliminó: la pantalla debe recargar, no corregir un id.
  Repetir el mismo cambio ya aplicado con su revisión anterior es idempotente y no regenera ids.
  Una revisión futura siempre da 409.
- `selectedCandidateDateId` es nullable y, si existe, debe referenciar una candidata. Se persiste y
  conserva en cambios generales y reordenaciones; la operación de elegir fecha llegará en el paso 7.
- El reemplazo usa el puerto EventRepositoryPort y un guardado completo con @Version, como las demás
  ediciones de configuración del agregado. Dos editores concurrentes no pueden pisarse: uno gana y
  el otro recibe 409. Una petición idéntica no llama a save(). No toca invitados, tokens, aperturas,
  cuotas ni documentos de responses; no se crea un historial de configuraciones o respuestas.
- Cambiar candidatas no amplía un plazo vencido: la consulta sigue cerrada. El anfitrión puede fijar
  otro plazo obligatorio también en PUBLISHED; no se compara con un inicio inexistente. CANCELLED
  y FINISHED siguen bloqueando el cambio. Desde el paso 4 se publican desde el API sin fecha definitiva.

La revisión y la selección se guardan en datePoll y se conservan al rehidratar o editar otros campos.
Una revisión negativa o una selección desconocida en Mongo es corrupción almacenada: 500 saneado.

### Publicación, vistas públicas y difusión — paso 4

- `publish()` conserva las precondiciones de estado y modalidad. En `FIXED_DATE` siguen siendo
  necesarias las opciones de asistencia; en `DATE_POLL` se publica la configuración validada de
  candidatas y plazo. No genera inicio, fin, instantes UTC definitivos ni opciones «Asisto/No asisto».
- Publicar conserva `pollRevision`, candidatas, invitados y cuotas. Si se pidió enlace público, su token
  nace en la misma escritura que cambia el estado, con @Version y el generador seguro existente.
- La invitación individual y la vista previa del enlace de alta añaden un bloque público
  `datePoll: {candidateDates:[{id,date,startTime}],pollRevision,selectedCandidateDateId?}`. La proyección
  es propia (`DatePollView` / `PublicDatePollResponse`), construida campo a campo, sin documentos Mongo,
  anfitrión, otros invitados ni contactos. Los campos definitivos locales y UTC se omiten mientras no existen.
- La invitación muestra únicamente el nombre del invitado actual y conserva OPENED atómico. No lee
  documentos de asistencia como si fueran disponibilidades. En consultas, el PUT de asistencia
  devuelve 409. El PUT de disponibilidad y los indicadores de la vista usan la política de
  aceptación común y la respuesta de la revisión vigente.
- La sala conserva su filtrado de dominio: ALWAYS puede mostrarse en PUBLISHED; ON_CONFIRMATION no
  interpreta la disponibilidad como confirmación; HOURS_BEFORE permanece oculto
  mientras no exista un inicio definitivo. CANCELLED/FINISHED nunca revelan el acceso. La vista previa
  del enlace de alta solo muestra plataforma, aunque la regla sea ALWAYS.
- El calendario de una consulta sin fecha definitiva devuelve 409 ProblemDetail, instancia constante
  y no-store exacto, también en FINISHED; no invoca el renderizador ni inventa una fecha o duración.
  DRAFT y desconocido siguen dando el mismo 404. El calendario de fecha fija mantiene su contrato.
- El alta pública comparte la política temporal: PUBLISHED y plazo inclusive, sin fin ficticio. Mantiene
  cookie, contactos descartados, límites 25/500, origen PUBLIC_LINK y exclusión de correo. El filtro
  atómico añade tipo, revisión y selección a la configuración leída, además de zona/plazo/fin. Una
  configuración que cambia antes de escribir da 409 JOIN_CLOSED; no se reevalúa el reloj en Mongo.
  Una apertura concurrente no bloquea el alta: no se compara version. Revisión ausente sigue siendo cero.
- Correo y WhatsApp comparten el formateador temporal. Para consultas indican «Fecha por definir»,
  candidatas con hora solo si existe, zona y plazo. No hay línea de fin. Conservan lugar o solo plataforma,
  el enlace individual y los controles existentes de privacidad, cuotas y longitud; no notifican cambios
  automáticamente a los invitados. La comprobación SMTP usa exclusivamente el capturador en tests.

### Campos actuales

Vive en `com.gtog.event.domain.model.Event`. Es una clase plana, sin anotaciones de framework, con dos builders
internos en vez de factory methods con parámetros posicionales (la lista de campos ya ha cambiado de forma en
tres rebanadas seguidas): `Event.builder()...build()` valida todas las reglas de negocio, genera el id y aplica
los valores por defecto, para eventos nuevos; `Event.reconstituteBuilder()...build()` rehidrata un evento ya
persistido sin generar identidad ni aplicar defaults nuevos; valida la integridad del estado almacenado.

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | `String` | UUID generado por el dominio al crear el evento. No lo asigna Mongo. |
| `hostId` | `String` | Identificador del anfitrión. Desde R5 proviene del usuario autenticado (sesión), nunca del body del request. |
| `title` | `String` | Título del evento. Obligatorio. |
| `description` | `String` | Descripción libre. Opcional. |
| `eventType` | `EventType` (derivado de `schedule`) | `FIXED_DATE` o `DATE_POLL`. Ausente en documentos antiguos: `FIXED_DATE`. |
| `schedule` | `EventSchedule` | `FixedDateSchedule` o `DatePollSchedule`. |
| `startsAt` / `endsAt` | `LocalDateTime` (nullable) | En `FIXED_DATE`, inicio y fin locales; el fin debe ser posterior al inicio en UTC. En consultas sin selección, null, sin relleno. |
| `timeZone` | `String` | Zona horaria IANA (`region/ciudad`, p. ej. `Europe/Madrid`). Se valida con `ZoneId.of(...)`; si no existe, `InvalidTimeZoneException`. |
| `modality` | `Modality` | `IN_PERSON` o `ONLINE`. Un evento nunca es las dos cosas (modalidad híbrida fuera de alcance). |
| `status` | `EventStatus` | `DRAFT`, `PUBLISHED`, `CANCELLED`. Ver máquina de estados más abajo. `FINISHED` no está implementado (D-FINISHED). |
| `cancelledAt` | `Instant` (nullable) | Instante de cancelación. `null` si el evento no está cancelado. |
| `cancellationReason` | `String` (nullable) | Motivo de cancelación, opcional. |
| `responseOptions` | `List<ResponseOption>` | En `FIXED_DATE`, entre 2 y 5 embebidas; vacío en `DATE_POLL`. |
| `allowComment` | `boolean` | Si el invitado puede añadir un comentario a su respuesta. Default `false`: es un dato personal que el anfitrión debe activar de forma consciente. |
| `allowResponseChange` | `boolean` | Si el invitado puede cambiar su respuesta tras enviarla. Default `true`. |
| `responseDeadline` | `LocalDateTime` | En `FIXED_DATE` es opcional y no posterior a `startsAt`; obligatorio en `DATE_POLL`. Sin valor por defecto. |
| `venue` | `Venue` (nullable) | Ver más abajo. Obligatorio si `modality` es `IN_PERSON`, prohibido si es `ONLINE`. |
| `onlineAccess` | `OnlineAccess` (nullable) | Ver más abajo. Obligatorio si `modality` es `ONLINE`, prohibido si es `IN_PERSON`. |
| `guests` | `List<Guest>` | Invitados del mismo agregado, sin respuesta embebida. Lista vacía por defecto. Ver `docs/modelo-invitado.md`. |
| `version` | `Long` (nullable) | Bloqueo optimista de Mongo (`@Version`). `null` hasta el primer `save()`. |
| `publicInvitation` | `PublicInvitation` | Intención `enabled` y token nullable del enlace público de alta; ausente equivale a desactivado. |

En `FIXED_DATE`, los métodos derivados `startsAtInstant()` / `endsAtInstant()` calculan el instante UTC a partir de la hora local y
`timeZone` **al vuelo**, no lo guardan congelado — así reflejan cualquier cambio futuro en las reglas horarias de
la zona.
En consultas sin selección devuelven null, sin crear instantes auxiliares.

Para `FIXED_DATE`, `acceptsResponses(now)` centraliza las condiciones de tiempo y estado: PUBLISHED,
`responseDeadline == null || now <= responseDeadline` y `now < endsAtInstant()`. El plazo se convierte a
instante con la zona del evento. `requireAcceptingResponses(now)` lanza `ResponseNotAcceptedException` (409)
si falla alguna condición; la aplicación transforma DRAFT en el mismo 404 de un token desconocido antes de
validar la respuesta. El instante procede de Clock inyectado. La regla rige primeras respuestas y cambios;
estos últimos necesitan además `allowResponseChange`, impuesto mediante la política atómica de escritura.
Consultar `canChangeResponse` reutiliza esta regla, sin duplicar comparaciones de fechas en la capa web.
La configuración temporal aporta si existe un fin alcanzado; en `DATE_POLL` no hay fin definitivo y
el plazo obligatorio gobierna la ventana. Los borradores de consulta actuales no aceptan respuestas.

### Reglas de negocio y sus excepciones

| Regla | Excepción | HTTP |
|---|---|---|
| El título es obligatorio | `BlankEventTitleException` | 422 |
| En `FIXED_DATE`, `endsAt` debe ser posterior a `startsAt` | `InvalidEventPeriodException` | 422 |
| `timeZone` debe ser una zona IANA válida | `InvalidTimeZoneException` | 422 |
| En `FIXED_DATE`, `responseDeadline`, si viene, no posterior a `startsAt` | `InvalidResponseDeadlineException` | 422 |
| Entre 2 y 5 opciones de respuesta en `FIXED_DATE` | `InvalidResponseOptionCountException` | 422 |
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
| Reemplazar opciones solo en DRAFT o PUBLISHED | `EventNotEditableException` | 409 |
| En PUBLISHED no se puede eliminar una opción con respuestas | `ResponseOptionDeletionNotAllowedException` | 409 |
| En PUBLISHED no se puede cambiar countsAsAttendance de una opción con respuestas | `ResponseOptionAttendanceChangeNotAllowedException` | 409 |
| Reemplazar `venue` o `onlineAccess` exige compatibilidad con la modalidad del evento existente | `ModalityConflictException` | 409 |
| El evento debe existir | `EventNotFoundException` | 404 |

Las 4 reglas de modalidad (`Missing`/`Unexpected` × `Venue`/`OnlineAccess`) validan la creación del evento y
producen 422. En cambio, `PUT /api/events/{id}/venue` sobre un evento `ONLINE` y
`PUT /api/events/{id}/online-access` sobre uno `IN_PERSON` lanzan `ModalityConflictException` y producen **409**:
el cuerpo es válido, pero no es compatible con el estado del recurso existente. Los campos inválidos del
bloque siguen produciendo 422. Los demás conflictos de estado son `EventNotEditableException`,
`EventNotPublishableException` y `EventNotCancellableException`.

Ejemplos ya en el código: `title` de `CreateEventRequest` (`BlankEventTitleException`), `label` de las opciones de respuesta (`BlankResponseOptionLabelException`), y los campos obligatorios de `Venue` y `OnlineAccess` (`MissingVenueFieldException`, `MissingOnlineAccessFieldException`). Por contraste, `timeZone` del evento lleva `@NotBlank` para exigir su presencia; el dominio comprueba que sea una zona válida con `ZoneId.of(...)`. `hostId` ya no forma parte de `CreateEventRequest` desde R5.

La traducción de reglas del cliente no se aplica a datos corruptos persistidos: EventMapper envuelve las
excepciones de dominio de la rehidratación en StoredDataIntegrityException, que devuelve 500 con detalle
fijo. El saneamiento público de ProblemDetail se describe en el plan, §3 Seguridad y errores.

### Máquina de estados

El flujo siguiente está disponible para ambos tipos. La publicación de consultas no exige fechas definitivas;
sus disponibilidades se guardan con la revisión de la consulta.

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

Las excepciones de reglas de negocio del evento heredan de `EventDomainException`: el dominio no sabe de códigos HTTP, solo expresa que
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

Si al crear un evento `FIXED_DATE` no se especifican opciones, el dominio aplica dos por defecto: **"Asisto"** (cuenta) y
**"No asisto"** (no cuenta).

### Identidad al editar (`PUT /api/events/{id}/response-options`)

El reemplazo de la lista distingue opción nueva de opción existente por la presencia de `id` en la petición:

- **Sin `id`** → opción nueva; el dominio le genera un id.
- **Con `id` que coincide con una opción actual del evento** → se conserva el id, se actualizan `label` y
  `countsAsAttendance`, respetando la protección de opciones con respuestas en PUBLISHED. Permite renombrar
  sin perder la referencia desde `responses`.
- **Con `id` que no coincide con ninguna opción actual** → error, `UnknownResponseOptionIdException` (422). No se
  trata como opción nueva.

### Opciones con respuestas en PUBLISHED (R8, commit 3)

`Event.replaceResponseOptions(edits, answeredOptionIds)` recibe los ids con respuestas, calculados por
`ReplaceResponseOptionsService` mediante `GuestResponseRepositoryPort.findAnsweredOptionIds(eventId)`.
La consulta devuelve ids distintos únicamente del evento solicitado. El dominio no conoce la colección.

En DRAFT no hay restricciones por respuestas; en PUBLISHED se puede renombrar y agregar. No se puede quitar
una opción con respuestas (`ResponseOptionDeletionNotAllowedException`, 409), ni cambiar su countsAsAttendance
(`ResponseOptionAttendanceChangeNotAllowedException`, 409): hacerlo alteraría retroactivamente quién está
confirmado y quién recibe el enlace. Las opciones sin respuestas sí pueden eliminarse o cambiar esa marca.
CANCELLED y FINISHED producen 409. Siguen vigentes todas las validaciones de 2–5 opciones, asistencia y etiquetas.
La lista del agregado solo cambia si pasa toda la validación; la persistencia conserva el bloqueo optimista.

**D-ORPHAN-OPTION:** un invitado puede responder con X entre la consulta de ids usados y el guardado de una
lista que elimina X. No se añaden transacciones. La lectura pública tolera esa referencia: la respuesta se
conserva, pero una opción inexistente no confirma asistencia ni abre ON_CONFIRMATION.

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

Desde R7, `GetInvitationService` aplica este método con el instante obtenido de un `Clock` inyectado;
desde el commit 1 de R8, `InvitationViewFactory` comparte esa proyección con el PUT de respuesta.
Solo devuelve acceso si `status == PUBLISHED`; DRAFT, CANCELLED y FINISHED siempre producen vacío.
Desde el commit 2 de R8, `InvitationViewFactory.guestHasConfirmed(...)` usa la respuesta propia ya cargada:
solo devuelve true si su opción todavía existe en el evento y tiene `countsAsAttendance = true`.
Sin respuesta, con opción inexistente o con una opción que no cuenta, devuelve false sin lanzar.
GET y PUT comparten el cálculo; cambiar de opción muestra u oculta el enlace en ON_CONFIRMATION sin guardar
un estado adicional ni hacer otra consulta. D-CONFIRMED-LINK saldada. `InvitationResponse` es un DTO propio,
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
de `startsAtInstant()` para fecha fija. En consultas admite DRAFT y PUBLISHED, con plazo
no nulo y sin comparar con un inicio inexistente. El inicio exacto en fecha fija y los estados CANCELLED/FINISHED producen
`ResponseDeadlineNotEditableException` (409). El instante procede del Clock inyectado en aplicación.
La misma validación que usan creación y `edit()` exige plazo no posterior a startsAt
(`InvalidResponseDeadlineException`, 422) en fecha fija. null elimina el plazo solo en `FIXED_DATE`;
en `DATE_POLL` da `MissingDatePollDeadlineException` (422). Un plazo pasado es válido.

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

Las escrituras sobre un invitado no guardan el evento entero ni comparan la versión esperada: el alta
individual y el lote usan `$push` con `$each`, la edición usa `$set` parcial con arrayFilters y el borrado
usa `$pull`. Filtran por id, propietario, estado permitido y las condiciones propias de cada operación,
e incrementan `version` en la misma escritura. Un conflicto se traduce a 409. Esto protege las reglas
validadas en el agregado y evita que un `save()` anterior borre una escritura parcial.
La comparación de versión esperada se mantiene solo en las escrituras que reescriben el documento completo
del evento: editar evento, opciones, candidatas, publicar y cancelar. Una apertura concurrente puede provocar un 409
seguro en esas operaciones; el cliente recarga y reintenta.
El índice impone unicidad entre eventos; la regla de dominio y la escritura condicionada evitan duplicados
dentro del mismo `guests[]`. Ver `docs/modelo-invitado.md`.

---

## Puertos y casos de uso

| Caso de uso (`port/in`) | Servicio (`application`) | Qué hace |
|---|---|---|
| `CreateEventUseCase` | `CreateEventService` | Crea el evento en `DRAFT` con sus opciones (o los defaults) y lo persiste. |
| `GetEventByIdUseCase` | `EventQueryService` | Devuelve el evento completo o lanza `EventNotFoundException`. |
| `GetEventDashboardUseCase` | `GetEventDashboardService` | Comprueba propiedad, lee respuestas por un puerto en una consulta y calcula EventAttendanceSummary sin escrituras. |
| `ListEventsByHostUseCase` | `EventQueryService` | Lista proyecciones EventSummary (evento y totales), sin paginación. Lee una sola vez las respuestas de todos los eventos del anfitrión y reutiliza el cálculo del panel. |
| `ReplaceResponseOptionsUseCase` | `ReplaceResponseOptionsService` | Comprueba propiedad; en PUBLISHED consulta los ids con respuestas mediante un puerto, los pasa al dominio junto con la lista y guarda con bloqueo optimista. No cambia allowComment/allowResponseChange/responseDeadline. |
| `ReplaceVenueUseCase` | `EventLocationService` | Busca el evento, delega en `event.replaceVenue(...)` y lo guarda. |
| `ReplaceOnlineAccessUseCase` | `EventLocationService` | Busca el evento, delega en `event.replaceOnlineAccess(...)` y lo guarda. |
| `UpdateEventUseCase` | `UpdateEventService` | Busca el evento, delega en `event.edit(EventEdit)` y lo guarda. `allowComment`/`allowResponseChange`/`responseDeadline` se editan aquí, no en `replaceResponseOptions`. |
| `ChangeResponseDeadlineUseCase` | `ChangeResponseDeadlineService` | Comprueba propiedad, valida plazo y estado con Clock, y actualiza solo el plazo mediante el puerto de escritura parcial. |
| `RecordGuestResponseUseCase` | `RecordGuestResponseService` | Resuelve la invitación por token, valida la respuesta y la inserta o actualiza atómicamente en `responses`, sin guardar el evento. Devuelve la vista pública con la respuesta propia. |
| `DownloadInvitationCalendarUseCase` | `DownloadInvitationCalendarService` | Resuelve la invitación, oculta DRAFT, rechaza CANCELLED, filtra la URL como en JSON y delega la generación en un puerto; sin escrituras de evento. |
| `PublishEventUseCase` | `PublishEventService` | Busca el evento, llama a `event.publish()` y lo guarda. |
| `AddGuestUseCase` / `UpdateGuestUseCase` | `GuestService` | Valida propiedad y reglas del agregado; persiste únicamente el alta o los contactos editados. |
| `DeleteGuestUseCase` | `GuestService` | Valida estado, propiedad y confirmación si ya respondió; retira al invitado con escritura parcial y después limpia su respuesta real. |
| `ListGuestUseCase` | `GuestService` | Devuelve los invitados al propietario, también en eventos cerrados. |
| `CancelEventUseCase` | `CancelEventService` | Busca el evento, llama a `event.cancel(reason, Instant.now())` y lo guarda. |

`EventRepositoryPort` (`port/out`): `save`, `findById`, `findByHostId`, `findByGuestToken`. Implementado por `EventRepositoryAdapter`
sobre `EventMongoRepository` (Spring Data).

---

## API HTTP expuesta hoy

### DTO de creación (referencia)

```java
@ValidFixedDateFields
public record CreateEventRequest(
    String title,
    String description,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    @NotBlank String timeZone,
    @NotNull Modality modality,
    List<@Valid CreateResponseOptionRequest> responseOptions,
    Boolean allowComment,
    Boolean allowResponseChange,
    LocalDateTime responseDeadline,
    VenueRequest venue,
    OnlineAccessRequest onlineAccess,
    Boolean publicInvitationEnabled,
    EventType eventType,
    DatePollRequest datePoll
) implements TemporalEventRequest {}
```

`ValidFixedDateFields` conserva el 400 anterior si faltan inicio o fin en la petición de fecha fija;
las fechas no se exigen en consultas. Las reglas de candidatas y plazo se validan en el dominio (422).

### Rutas

| Método | Ruta | Body / parámetros | Respuesta |
|---|---|---|---|
| `POST` | `/api/events` | `CreateEventRequest` | `201` + `Location`, `400`, `422` |
| `GET` | `/api/events/{id}` | — | `200` (`EventResponse` completo), `404` |
| `GET` | `/api/events/{id}/dashboard` | — (sesión del propietario) | `200` (`EventDashboardResponse`) con no-store exacto; `401`, `404` |
| `GET` | `/api/events` | — (el host se lee del principal autenticado) | `200` (lista de `EventSummaryResponse`: `id`, `title`, `startsAt`, `modality`, `status`, `totalGuests`, `answered`, `pending`, `attending`, `eventType`), `401` si no hay sesión |
| `PUT` | `/api/events/{id}` | `UpdateEventRequest` (título, fechas, zona horaria, modalidad, venue/onlineAccess opcionales, `allowComment`, `allowResponseChange`, `responseDeadline`) | `200` (`EventResponse` completo), `404`, `409` (no `DRAFT`), `422` |
| `PUT` | `/api/events/{id}/response-deadline` | `{ "responseDeadline": LocalDateTime o null }`, sesión y CSRF | `200` (`EventResponse`), `401`, `403`, `404` (inexistente o ajeno), `409` (estado, inicio alcanzado o carrera), `422` (plazo posterior al inicio) |
| `PUT` | `/api/events/{id}/candidate-dates` | `{pollRevision,candidateDates:[{id?,date,startTime?}]}`, sesión y CSRF | `200` (`EventResponse`), `400`, `401`, `403`, `404`, `409` (tipo/estado/revisión/versión), `422` (reglas de candidatas/revisión) |
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

El calendario público `GET /api/invitations/{token}/calendar` devuelve 200 con archivo `.ics`, UTF-8, attachment
y no-store exacto, sin sesión ni CSRF. DRAFT y desconocido comparten el 404 de R7; CANCELLED da 409 con
ProblemDetail e instancia constante; FINISHED de fecha fija permite 200. Una consulta sin fecha definitiva
da 409 también en FINISHED, sin renderizar. No registra OPENED ni aumenta version.
La proyección `InvitationCalendarData` contiene id del evento para UID, título, descripción, fechas locales,
zona, venue, URL ya filtrada y el instante del Clock para DTSTAMP. `InvitationCalendarRendererPort` mantiene
iCal4j fuera del dominio y la aplicación. No se envían invitados, contactos, token, respuesta propia,
contraseña, sala ni instrucciones al renderizador. Ver `modelo-invitado.md` para formato y configuración.

Documentado también en OpenAPI (`/v3/api-docs`, `/swagger-ui.html`), ver sección "Documentación de API" en
`AGENTS.md`.

---

## Panel del anfitrión y contadores del listado

### Eventos de fecha fija (R9)

`EventAttendanceSummary.calculate(event, responses)` es una función pura del dominio: construye un índice
de respuestas del evento por token y recorre guests[] en orden de alta. Los documentos huérfanos no aportan
contadores ni filas. El resultado inmutable contiene totals, byOption y guests; no modifica Event ni version.

- Totals: guests, answered, pending=guests-answered y attending. Solo cuentan asistencia las respuestas cuya
  opción actual tiene countsAsAttendance=true.
- ByOption: optionId, label, countsAsAttendance y count, en el orden de responseOptions, incluidos los ceros.
- Filas: contacto del Guest y respuesta seleccionada sin email. Una opción desconocida cuenta como answered,
  sin asistencia ni contador por opción; conserva responseOptionId, comentario y fechas con label=null.
- OptionKnown es Boolean: true si hay respuesta y opción actual; false si hay respuesta y opción ausente;
  null si no hay respuesta, en cuyo caso response también es null.

`GuestResponseRepositoryPort.findByEventIds(Set<String>)` permite leer respuestas de varios eventos mediante
una sola consulta Mongo `eventId in [...]`, usando el índice de eventId existente; un conjunto vacío devuelve
lista vacía sin consultar Mongo. El panel usa ese puerto con el id de su evento después de comprobar propiedad.
Son dos lecturas unidas en aplicación, sin $lookup.

Desde el commit 2, `EventQueryService.listEventsByHost(...)` lee los eventos del anfitrión, reúne sus ids y
llama una sola vez a ese mismo puerto. Agrupa las respuestas por eventId en memoria y pasa cada grupo a
`EventAttendanceSummary.calculate(...)`, igual que el panel. Devuelve `EventSummary`, un record de dominio
con el evento y sus Totals. No se consulta responses si no hay eventos ni se hace una consulta por evento.
`EventSummaryResponse` mapea campo a campo id, title, startsAt, modality, status, eventType y totalGuests, answered,
pending y attending; no expone byOption, invitados, respuestas ni contactos. Los documentos huérfanos y las
opciones desconocidas siguen exactamente las mismas reglas que el panel. Ambas lecturas conservan los
documentos y version. El listado no añade una política de caché propia. La ausencia de paginación y el
cálculo en memoria sobre todos los eventos y sus respuestas se documentan como D-LIST-SCALE.

`EventDashboardResponse` se construye campo a campo en web. Sus filas exponen id, name, email y phoneE164 del
contacto, source, deliveryChannel, invitationStatus e invitationPath como GET /guests, más answered, response y
optionKnown. No hay claves token o guestToken; el token aparece solo dentro de invitationPath. El email
guardado en GuestResponse nunca se incorpora al DTO. La respuesta seleccionada lleva responseOptionId,
label, comment, answeredAt y updatedAt. No se serializa el dominio ni el documento Mongo.

### Consultas de fechas (paso 6)

El mismo GET de panel devuelve `availability` para `DATE_POLL` con `pollRevision`,
`byCandidate: [{candidateDateId,date,startTime,count,guests}]`, `pendingGuests` y
`unavailableGuests`. Las referencias a invitados son solo `{id,name}`. Las candidatas
conservan el orden del anfitrión, incluidas las que tienen contador cero. Las personas
conservan el orden de alta y pueden figurar en varias candidatas. Una selección vacía
cuenta como respondida y se muestra en `unavailableGuests`. Las respuestas huérfanas o
de otra revisión se ignoran. La tabla general de invitados reutiliza `answered`;
`response` y `optionKnown` quedan en null y `byOption` queda vacío.

El listado conserva sus campos. Para consultas, `answered` y `pending` reflejan la
ronda actual y `attending` vale cero. La asistencia de fecha fija conserva su cálculo
y su panel se serializa sin el bloque `availability`. Si el anfitrión tiene ambos tipos,
el listado lee respuestas con hasta dos consultas agrupadas sobre `responses`, una por
tipo mediante `eventId in [...]`. El panel solo lee el tipo de su evento. Se calcula a
partir de `guests[]` y se filtra por revisión en dominio. Son operaciones de lectura.

El panel está disponible en cualquier estado para el propietario. Solo su 200 fija exactamente
Cache-Control: no-store; ni el listado ni los errores se cambian. EventNotFoundException conserva su manejador
global. Los tests comprueban cuerpo completo, unicidad del token en invitationPath, ausencia de tokens ajenos
y emails de respuesta, huérfanos y documentos sin cambios tras la lectura.

## Enlace público de alta

`publicInvitation { enabled, token }` pertenece al evento. `token != null` implica `enabled=true` y
`status != DRAFT`; el dominio rechaza estados imposibles. En DRAFT, `enabled=true` expresa solo la intención:
nunca hay token. En PUBLISHED, enabled=true si y solo si hay token. Se conserva al cancelar o finalizar;
queda inerte, pero permite consultar la vista pública con estado y `canJoin=false`.

POST /api/events admite `publicInvitationEnabled`, false por defecto. Publicar genera, si se pidió, un token
de 32 bytes de SecureRandom en base64url sin relleno, mediante un puerto sustituible en tests, en el mismo
save con versión esperada que cambia a PUBLISHED. PUT /api/events/{id} nunca modifica la intención.
El mapper conserva intención y token en todas las operaciones. Ausente = false/null; la forma anterior
`{token}` sin enabled se interpreta como enabled=(token != null), sin migración.

| Método | Ruta | Contrato |
|---|---|---|
| GET | `/api/events/{id}/public-invitation` | Sesión y propiedad; 200 `{enabled,path}`, también CANCELLED/FINISHED con enlace conservado; 401, 404 |
| PUT | `/api/events/{id}/public-invitation` | Sesión, CSRF y propiedad; cuerpo `{enabled:boolean}`; 200 `{enabled,path}`; 400, 401, 403, 404, 409 |

Ambos 200 fijan no-store exacto. path es `/j/<token>` solo si hay token, null en borrador o desactivado.
EventResponse expone solo `publicInvitationEnabled`, nunca el token. El GET no escribe y está permitido
en cualquier estado; el PUT solo en DRAFT/PUBLISHED. En borrador cambia la intención; en publicado,
activar es idempotente y devuelve el mismo enlace si ya existe, desactivar destruye el token y reactivar
genera otro distinto. Cada cambio es parcial, condicionado por estado e incrementa version; una escritura
de intención DRAFT que llega después de publicar da 409. Un save obsoleto no puede resucitar un enlace revocado.

MongoIndexInitializer crea `public_invitation_token`, único y parcial sobre `publicInvitation.token` de
tipo string, permitiendo múltiples eventos sin token. Los E11000, también en publicación, se traducen sin
mensaje ni causa original a error de almacenamiento con detalle fijo: ningún token se propaga a errores o logs.

El panel añade `publicInvitation {enabled,joined,limit,open}`: joined cuenta invitados vivos PUBLIC_LINK,
limit es app.guests.public-link-max (25), y open reutiliza ResponseAcceptancePolicy y los topes de 25/500.
Cancelado, finalizado, plazo vencido o fin alcanzado implican open=false. Las filas y GET /guests incluyen
source; los contadores siguen contando a todos los invitados, con independencia del origen.
Ver [modelo-invitado.md](modelo-invitado.md#alta-por-enlace-público) para admisión y cookie.

## Fuera de alcance por ahora

- Telegram sigue pendiente, fuera de R11. `SENT` por correo está implementado desde R10 y por confirmación manual de WhatsApp desde R11; OPENED desde R7. WhatsApp completa canal/fecha en OPENED sin rebajarlo, mediante escrituras posicionales con incremento de version.
- `FINISHED`: no implementado (D-FINISHED).
- Borrar un borrador: no implementado (D-DELETE-DRAFT).
- Muro social, acompañantes, check-in: iteración 2 o posteriores.
