# Modelo de datos: Invitado

Estado tras R6, 14 de noviembre de 2026. Gestión de invitados del anfitrión; la página pública y las respuestas
siguen pendientes de R7 y R8.

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
Las transiciones de envío y apertura no están implementadas en R6.

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
con su origen para copiar el enlace. La ruta se podrá resolver públicamente cuando exista R7.

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

Todas filtran por evento, propietario, estado permitido y versión esperada; edición y borrado comprueban
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

`mvn clean verify`: **245 tests**, sin fallos, errores ni omitidos. R6 incorpora 71 casos: 26 de dominio,
12 de aplicación, 8 de adaptadores y 25 de integración contra Atlas (`gtog_test`).

Incluyen contactos obligatorios e inválidos, normalización, duplicados al crear y editar, estados permitidos,
colisión forzada de tokens en el mismo evento, índice único entre eventos, autorización de los cuatro endpoints,
CSRF, límites de exposición del token, conservación del historial, eventos antiguos y escrituras concurrentes.
