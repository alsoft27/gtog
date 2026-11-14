# Modelo de datos: Invitado

Estado tras R7, 14 de noviembre de 2026. Gestión de invitados del anfitrión y consulta pública de la invitación.
Las respuestas de asistencia siguen pendientes de R8.

La preparación de R8 (commit 0) añade el plazo editable por el anfitrión mediante
`PUT /api/events/{id}/response-deadline`: DRAFT, o PUBLISHED estrictamente antes del inicio. null lo elimina;
la fecha es local a la zona del evento y puede estar en el pasado, pero no después del inicio. La escritura
parcial incrementa version sin exigir una versión esperada; una primera apertura concurrente no impide el
cambio ni se pierde. La consulta pública sigue reflejando `responseDeadline`. La aplicación del plazo a la
primera respuesta y a sus cambios se implementará en el commit 1 de R8.

## Agregado y campos

`Guest` pertenece al hexágono `event`, en `com.gtog.event.domain.model`. No tiene vida independiente del evento:
comparte agregado y documento. No existe colección ni hexágono `guest` separado.

| Campo | Tipo | Regla |
|---|---|---|
| `id` | `String` | UUID generado al crear; estable al editar |
| `name` | `String` | Obligatorio, sin espacios exteriores |
| `email` | `String`, nullable | Normalizado a minúsculas con `Locale.ROOT`, sin espacios exteriores |
| `phoneE164` | `String`, nullable | Teléfono normalizado y validado antes de guardar |
| `token` | `String` | Credencial aleatoria del invitado, estable al editar |
| `invitationStatus` | `InvitationStatus` | Inicialmente `NOT_SENT`; enum: `NOT_SENT`, `SENT`, `OPENED` |
| `deliveryChannel` | `DeliveryChannel`, nullable | `EMAIL`, `WHATSAPP`, `TELEGRAM`, `LINK`; inicialmente nulo |
| `sentAt` | `Instant`, nullable | Instante UTC de envío; inicialmente nulo |
| `openedAt` | `Instant`, nullable | Instante UTC de apertura; inicialmente nulo |

**La respuesta no vive aquí. `ANSWERED` no es un valor del enum ni se almacena.** R8 lo derivará de `responses`.
La primera apertura se implementa en R7. El envío y la transición a SENT siguen pendientes de R10–R11.

`Guest.create(...)` valida y normaliza contactos, genera el id y aplica el estado inicial.
`editContact(...)` devuelve un invitado con los contactos sustituidos y conserva identidad, token y todo el
historial de envío. `reconstitute(...)` rehidrata sin generar nada ni repetir la validación.

El agregado `Event` expone `addGuest`, `updateGuest`, `removeGuest`, `getGuest` y una lista inmodificable mediante
`getGuests`. Solo permite mutaciones en `DRAFT` y `PUBLISHED`; se puede consultar en cualquier estado.

## Reglas y errores

| Regla | Excepción | HTTP |
|---|---|---|
| Nombre no nulo ni en blanco | `BlankGuestNameException` | 422 |
| Al menos correo o teléfono no vacío | `MissingGuestContactException` | 422 |
| Correo con el mismo criterio de formato que `User` | `InvalidGuestEmailException` | 422 |
| Teléfono internacional válido, normalizable y sin extensión | `InvalidGuestPhoneException` | 422 |
| Correo único dentro del evento | `DuplicateGuestEmailException` | 409 |
| Teléfono E.164 único dentro del evento | `DuplicateGuestPhoneException` | 409 |
| Token único dentro del evento y entre eventos | `DuplicateGuestTokenException` | 409 |
| No modificar invitados de un evento `FINISHED` o `CANCELLED` | `EventGuestsNotEditableException` | 409 |
| Invitado perteneciente al evento | `GuestNotFoundException` | 404 |
| Versión del evento aún vigente al escribir | `EventConcurrentModificationException` | 409 |
| Confirmación explícita si en el futuro hay respuesta | `GuestResponseDeletionConfirmationRequiredException` | 409 |

Si se proporcionan ambos contactos, ambos se validan. Los opcionales vacíos se tratan como ausentes. Al editar,
la búsqueda de duplicados excluye al propio invitado. El mismo correo o teléfono se admite en eventos distintos.
Las excepciones heredan de `EventDomainException`; `GlobalExceptionHandler` las traduce a `ProblemDetail`.

## Teléfonos: decisión aprobada

Dependencia: `com.googlecode.libphonenumber:libphonenumber:9.0.43`, encapsulada detrás de `PhoneNormalizerPort`.
`LibPhoneNumberNormalizerAdapter`, en infraestructura, usa el parseo, la validación y el formato E.164 de la
librería. El dominio no importa tipos de esa dependencia ni llama servicios externos.

El cliente envía `phone` con prefijo internacional explícito, construido con su selector de país. Se aceptan
espacios y separadores de presentación: `+34 612 345 678` se guarda como `+34612345678`. No se infiere el país
a partir del anfitrión o de una zona horaria. Los números sin `+` y prefijo internacional, inválidos o con
extensión se rechazan. El dominio comprueba además que el resultado del puerto tenga sintaxis E.164.

Referencia: [proyecto oficial libphonenumber](https://github.com/google/libphonenumber).

## Token y enlace del anfitrión

`GuestTokenGeneratorPort` está implementado por `SecureGuestTokenGeneratorAdapter`: 32 bytes de `SecureRandom`,
codificados con `Base64.getUrlEncoder().withoutPadding()`, producen 43 caracteres. No se usa `Random` ni se
deriva el token de ids, contactos u otros datos personales. El borrado del invitado elimina su credencial del evento;
no se añade en R6 un endpoint de rotación.

**RF-2.16:** el anfitrión necesita recuperar el enlace después de recargar la pantalla. Por eso el alta y el
listado devuelven `GuestInvitationResponse`, con `invitationPath: /r/{token}`. El frontend completa esa ruta
con su origen para copiar el enlace. El frontend usa esa ruta para su página y consulta `GET /api/invitations/{token}`, ya implementado en R7.

No se expone un campo `token` independiente. El `PUT` devuelve `GuestResponse` sin token ni enlace;
`EventResponse` y los listados de eventos tampoco incluyen esos datos. Los errores de colisión no repiten
el token en su mensaje. El DTO web `GuestResponse` es una vista de contactos, no una respuesta de asistencia.

## Persistencia y concurrencia

`GuestDocument` está embebido como `guests[]` en `EventDocument`, sin `@Document` propio. `GuestMapper` traduce
en ambos sentidos; los enums se guardan como cadenas. Los eventos anteriores sin `guests[]` se leen como lista
vacía. `EventMapper` conserva los invitados en las operaciones existentes sobre el evento completo.

`MongoIndexInitializer` crea de forma idempotente `guests.token` ascendente y único. MongoDB lo convierte en
multiclave al indexar el array. El filtro parcial `guests.token: { $type: "string" }` excluye los eventos sin
tokens, para permitir varios eventos vacíos o anteriores a R6. D-3 queda saldada.

**El índice no impide repetir un token dentro del mismo documento.** Esa regla se comprueba en `Event`; la
escritura parcial condicionada por versión impide que dos altas concurrentes, validadas sobre el mismo estado,
introduzcan duplicados. El adaptador añade también una condición de ausencia del token al insertar.

`EventGuestRepositoryPort` tiene tres operaciones, implementadas con `MongoTemplate`:

- `addGuest`: `$push` de un invitado.
- `updateGuest`: `$set` posicional de `name`, `email` y `phoneE164`; conserva token, estado y fechas.
- `removeGuest`: `$pull` por identidad del invitado.

Las tres operaciones de gestión del anfitrión filtran por evento, propietario, estado permitido y versión esperada; edición y borrado comprueban
también la identidad del invitado. Incrementan `version` en la misma escritura. Si el estado leído cambió,
se devuelve 409 y el cliente debe recargar y reintentar. No hay reintentos silenciosos ni `save(event)` como
alternativa. También un `save()` antiguo del evento falla por bloqueo optimista, preservando los invitados.

Referencias: [índices multiclave únicos](https://www.mongodb.com/docs/manual/core/indexes/index-types/index-multikey/),
[índices parciales](https://www.mongodb.com/docs/manual/core/index-partial/) y
[actualizaciones atómicas](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/).

## Aplicación y API

`GuestService` implementa `AddGuestUseCase`, `UpdateGuestUseCase`, `DeleteGuestUseCase` y `ListGuestUseCase`.
Primero carga el evento y comprueba la propiedad en aplicación. Evento ajeno o inexistente produce 404, antes
de procesar datos del invitado. El dominio valida las reglas y el puerto específico persiste la escritura parcial.

| Método | Ruta | Entrada | Resultado |
|---|---|---|---|
| `POST` | `/api/events/{id}/guests` | `GuestRequest(name, email, phone)` | 201 + `Location` + `GuestInvitationResponse` |
| `PUT` | `/api/events/{id}/guests/{guestId}` | Mismos campos; reemplaza contactos | 200 + `GuestResponse` |
| `DELETE` | `/api/events/{id}/guests/{guestId}` | `confirmDeleteResponse=false` por defecto | 204 |
| `GET` | `/api/events/{id}/guests` | — | 200 + lista de `GuestInvitationResponse` |

Todos requieren sesión; las escrituras requieren CSRF. No se aceptan en el contrato de entrada ids, token,
estado, canal ni fechas de envío. `Location` identifica `/api/events/{id}/guests/{guestId}`; no se añade un
`GET` individual en esta rebanada. Las anotaciones OpenAPI permanecen en `infrastructure/in/web`.

## Consulta pública y primera apertura (R7)

`GetInvitationUseCase` y `GetInvitationService` resuelven el token mediante `EventRepositoryPort.findByGuestToken`,
que aprovecha el índice existente. El controlador `InvitationController` no necesita principal autenticado ni
CSRF. La respuesta se construye expresamente desde `InvitationView`, sin serializar el dominio.

| Estado | HTTP | Apertura | Acceso online |
|---|---|---|---|
| Token desconocido | 404 | Ninguna | Ninguno |
| DRAFT | Mismo 404 que desconocido | Ninguna | Ninguno |
| PUBLISHED | 200 | Primera visita desde NOT_SENT o SENT | Según LinkVisibility |
| CANCELLED | 200 con cancelledAt y cancellationReason | Ninguna | Nunca |
| FINISHED | 200 con su estado | Ninguna | Nunca |

La ausencia de transición actual a FINISHED no invalida los enlaces: la lectura pública ya contempla ese estado.
`InvitationNotFoundException` no recibe el token y su mensaje es constante. El handler usa una instancia fija
`/api/invitations`, evitando que ProblemDetail revele la credencial en la URI del error. Desconocido y borrador
producen el mismo cuerpo. Éxito y 404 llevan exactamente `Cache-Control: no-store`.

### Contrato InvitationResponse

- `guest`: exclusivamente `name`, sin id ni contactos.
- `title`, `description`, `status`, `modality`, `startsAt`, `endsAt`, `timeZone`, `startsAtInstant` y `endsAtInstant`.
- `venue` completo si es presencial; opciones de respuesta únicamente con `id` y `label`.
- `allowComment`, `allowResponseChange`, `responseDeadline`; datos de cancelación solo si corresponde.
- `onlineAccessInfo`: `platform`, `linkVisibility` y `hoursBefore`, sin secretos.
- `onlineAccess`: `url`, `roomId`, `password` e `instructions` solo cuando `Event.visibleOnlineAccess(...)` lo permite.
  Si está oculto, se omite el bloque completo y esos valores no aparecen en ningún otro campo.

No se devuelve hostId, lista de invitados, correo o teléfono de nadie, tokens, version, campos del documento
Mongo ni countsAsAttendance. Los DTOs públicos son propios y se construyen campo a campo; no reutilizan EventResponse.

`Clock` se inyecta en aplicación; su implementación normal es `Clock.systemUTC()`. El servicio obtiene un instante
por petición y lo usa tanto para la visibilidad como para openedAt. No llama a Instant.now() directamente.
ON_CONFIRMATION permanece cerrado: un único método privado devuelve false hasta R8. Confirmar exigirá que la
opción elegida tenga countsAsAttendance=true; no basta con haber respondido (D-CONFIRMED-LINK).

### Escritura de OPENED

Si el invitado leído ya es OPENED, el servicio omite la escritura. Para NOT_SENT o SENT en PUBLISHED llama a
`EventGuestRepositoryPort.markGuestOpened`. El adaptador usa findAndModify con retorno del documento actualizado:

- Filtro de evento PUBLISHED y `$elemMatch` que une token y estado NOT_SENT/SENT del mismo invitado.
- `$set` posicional de OPENED y openedAt más `$inc` explícito de version en la misma operación.
- No reescribe el documento completo, no modifica a otros invitados, no toca sentAt y no exige una versión
  leída previamente: la condición sobre el estado decide quién gana la primera apertura.
- Una segunda apertura concurrente no coincide y no modifica fecha ni versión. El servicio reconsulta solo
  ante esa falta de coincidencia, para devolver el estado vigente o 404 si se eliminó la invitación.

R6 ya incrementaba version explícitamente y comprobaba su valor esperado en sus tres escrituras. R7 mantiene
el incremento por cambio real. Un save antiguo del anfitrión queda protegido: OptimisticLockingFailureException
ya tiene handler 409 con ProblemDetail y ahora un test HTTP de cancelación concurrente. Al implementar SENT en
R11 habrá que preservar OPENED, sus fechas y el incremento de versión por modificación real.

El endpoint no registra tokens. No deben habilitarse trazas de URLs ni consultas que contengan estas credenciales.
Quedan anotadas D-RATE-LIMIT y D-REFERRER: sin límite de peticiones en R7, y el frontend debe servir su página
con `Referrer-Policy: no-referrer` para evitar filtrar el token de la URL al cargar recursos externos.

## Borrado y extensión para R8

`GuestResponseCleanupPort` declara `hasResponse(eventId, guestToken)` y `deleteResponse(eventId, guestToken)`.
El servicio rechaza con 409 un borrado con respuesta sin confirmación; con confirmación, invoca el borrado
de la respuesta antes de persistir el borrado del invitado. Si la limpieza falla, no elimina al invitado.

En R6 **no existe la colección `responses`**. `NoGuestResponsesYetAdapter` devuelve siempre `false` al consultar
y falla expresamente si se intenta borrar una respuesta. No consulta ni crea colecciones. Los tests con dobles
comprueban las ramas futuras de confirmación, orden y fallo de cascada.

R8 debe sustituir ese bean por el adaptador real y cubrir las carreras entre responder, borrar y modificar la
versión del evento. En particular, debe definir cómo completar o recuperar el borrado si la limpieza ya se
hizo pero la escritura del evento encuentra un conflicto. R6 prepara el contrato, no implementa una cascada
real sobre una colección todavía inexistente.

## Verificación

`mvn clean verify`: **305 tests**, sin fallos, errores ni omitidos. R6 incorporó 71 casos y R7 incorporó 31:
4 de dominio, 9 de aplicación y 18 de integración contra Atlas (`gtog_test`). La preparación de R8 añade
29: 8 de dominio, 4 de aplicación y 17 de integración, incluida apertura concurrente y cancelación con
versión anterior al cambio del plazo.

R7 cubre privacidad comparando el cuerpo completo, fechas locales/UTC, los tres modos de visibilidad con Clock
fijo y el umbral exacto, estados públicos y borrador, 404 indistinguibles, logs sin tokens, OPENED desde NOT_SENT
y SENT, revisitas sin escritura, carreras entre aperturas, `$elemMatch` sobre el invitado correcto, version +1,
conflicto HTTP 409 al cancelar con una versión antigua, cabecera exacta no-store y acceso sin sesión ni CSRF.
