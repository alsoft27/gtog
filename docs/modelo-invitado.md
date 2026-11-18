# Modelo de datos: Invitado

Estado tras R10 a 18 de noviembre de 2026. Gestión de invitados, consulta pública, respuestas, panel,
calendario y envío por SMTP implementados. Los enlaces asistidos de WhatsApp y Telegram corresponden a R11.

La preparación de R8 (commit 0) añade el plazo editable por el anfitrión mediante
`PUT /api/events/{id}/response-deadline`: DRAFT, o PUBLISHED estrictamente antes del inicio. null lo elimina;
la fecha es local a la zona del evento y puede estar en el pasado, pero no después del inicio. La escritura
parcial incrementa version sin exigir una versión esperada; una primera apertura concurrente no impide el
cambio ni se pierde. La consulta pública sigue reflejando `responseDeadline`. La aplicación del plazo a la
primera respuesta y a sus cambios está implementada desde el commit 1 de R8.

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
| `emailSendCount` | `int` | Reservas de correo consumidas; ausente significa 0 |
| `lastEmailSentAt` | `Instant`, nullable | Instante de la última reserva; ausente significa null |

**La respuesta no vive aquí. `ANSWERED` no es un valor del enum ni se almacena.** El panel de R9 lo deriva
desde los invitados vivos y sus documentos de respuesta. OPENED está implementado desde R7; R10 incorpora
reservas, escritura de SENT y envío SMTP. Los otros canales quedan para R11.

`Guest.create(...)` valida y normaliza contactos, genera el id y aplica el estado inicial.
`editContact(...)` devuelve un invitado con los contactos sustituidos y conserva identidad, token y todo el
historial de envío, salvo emailSendCount/lastEmailSentAt, que se reinician si cambia el correo normalizado.
`reconstitute(...)` rehidrata sin generar nada ni repetir la validación.

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

## Reservas de correo (R10, commit 1)

Implementadas en el commit 1, con transporte SMTP desde el commit 2 y endpoint de envío desde el commit 3. EmailSendPolicy evalúa estado PUBLISHED, correo,
respuesta existente, máximo por invitado e intervalo. Los datos de respuesta siguen fuera de guests[].
La selección de todo el lote y la revalidación de respuesta están en el caso de uso del commit 3.

GuestEmailSendReservationAdapter reserva mediante actualización posicional con $elemMatch de id y correo,
emailSendCount menor que el máximo (o ausente), y lastEmailSentAt nulo/ausente o menor o igual al umbral.
Incrementa contador y version y fija el instante en la misma escritura. El documento anterior proporciona
el comprobante de compensación; no se lee-modifica-guarda. Un rechazo por límite o intervalo no escribe.
Los comprobantes usan precisión de milisegundos, igual que Mongo. Cambiar el correo normalizado reinicia
ambos campos dentro de la edición atómica de R6 y su control de versión; cambiar solo nombre/teléfono no.

Orden aprobado: elegibilidad → reserva del invitado → reserva del anfitrión → SMTP. Un fallo de cuota del
anfitrión compensa al invitado y detiene el lote con DAILY_LIMIT. Si falla SMTP se compensa primero al
anfitrión y después al invitado. Si falla la compensación del anfitrión, no se libera al invitado. No hay
reintentos automáticos. La compensación del invitado exige la versión resultante de su reserva; cualquier
cambio posterior puede impedirla de forma conservadora, sin deshacer una apertura ni una reserva nueva.

El log ERROR EMAIL_SEND_COMPENSATION_FAILED indica stage, eventId, guestId, clase y correlationId, sin
correo, token, mensaje ni causa. Una reserva retenida sigue imponiendo el intervalo; si agotó el máximo,
requiere intervención manual incluso después de pasar el intervalo. No existe recuperación automática.

recordSent solo pasa NOT_SENT a SENT, con canal EMAIL; conserva la primera sentAt y no rebaja OPENED.
SMTP aceptado con fallo al registrar se trata como SENT/recorded=false, sin compensar ni
reenviar, con log ERROR de clase y correlación. El panel puede seguir en NOT_SENT y un reenvío posterior
puede duplicar el correo, acotado por máximo e intervalo.

Para el lote aprobado, un destinatario rechazado rompe la racha de errores de proveedor; un salto de
elegibilidad no es un envío. Tres errores de proveedor consecutivos dejan el resto SKIPPED/PROVIDER_UNAVAILABLE.
El mensaje y el transporte están implementados en el commit 2; el corte de lote está implementado en el commit 3.

### Transporte y mensaje (R10, commit 2)

EmailSenderPort recibe InvitationEmail (destinatario, asunto y cuerpo) y devuelve EmailDeliveryResult,
sin objetos de Spring ni excepciones SMTP. Su toString oculta el contenido. InvitationEmailComposer
construye texto plano fijo en español, con nombre del anfitrión, título y fechas locales más la zona,
lugar presencial o solo plataforma online, enlace individual y los textos fijos de respuesta y pie.
Neutraliza caracteres de control en asunto y nombres. Nunca incluye la URL, contraseña ni instrucciones
de la sala, otros invitados ni el email dejado en GuestResponse. No hay plantilla editable.

SmtpEmailSenderAdapter usa un solo destinatario, remitente gtog, sin CC/BCC ni Reply-To. TLS obligatorio
en 587, tiempos de espera de 5/10/10 s y sin reintentos. Traduce errores mediante tipos y datos estructurados
de Jakarta Mail a RECIPIENT_REJECTED, INVALID_RECIPIENT o PROVIDER_UNAVAILABLE; nunca analiza sus mensajes.
Log WARN SMTP_SEND_FAILED con clase y correlationId, sin mensaje, causa, dirección ni enlace.

Solo un rechazo inequívoco del destinatario de ese mensaje evita el corte: RECIPIENT_REJECTED para
RCPT TO con dirección coincidente y código definitivo 550–553, o INVALID_RECIPIENT si se identifica esa
dirección como inválida. Remitente rechazado, autenticación fallida, conexión/timeout, errores de parseo
no atribuibles al destinatario y cualquier 5xx sin clasificar → PROVIDER_UNAVAILABLE y cuentan para el corte.
Un fallo de proveedor domina si la cadena también contiene rechazo del destinatario. No se inspeccionan
mensajes: se usan tipos, direcciones y comandos/códigos estructurados. Angus es de ejecución; su API pública
se consulta sin añadir dependencias. Si no se puede comprobar el rechazo, se clasifica como proveedor.

app.public-base-url define la base del enlace /r/{token}; app.mail.from y app.mail.from-name el remitente.
MAILJET_API_KEY y MAILJET_SECRET_KEY no se guardan en ficheros. Sin credenciales o remitente el transporte
devuelve EMAIL_NOT_CONFIGURED sin conectar y la aplicación arranca. Se trata como fallo de proveedor:
compensa reservas y cuenta para el corte del lote. El renderizador recibe solo la plataforma, no Event
ni OnlineAccess ni la URL de la sala. Los tests MIME serializan y decodifican Unicode y neutralizan CR/LF.
management.health.mail.enabled=false
evita conexiones en las sondas; propiedad comprobada contra el informe de condiciones de Boot 4.1.
El perfil test usa exclusivamente un adaptador falso que captura mensajes; el adaptador SMTP se prueba
con JavaMailSender simulado. El caso de uso y el endpoint de envío están conectados desde el commit 3.

### Lote y endpoint (R10, commit 3)

`POST /api/events/{id}/invitations/send`, autenticado y con CSRF, comprueba propiedad en aplicación:
evento ajeno → 404. Solo PUBLISHED, incluso sin invitados; DRAFT, CANCELLED y FINISHED → 409.
Sin cuerpo o con guestIds ausente/null toma todos en orden de alta. Con lista explícita sigue su orden;
lista vacía, repetidos, ids vacíos o máximo superado → 422, id ajeno al evento → 404. Toda la selección
se valida antes de consultar elegibilidad, reservar o enviar. No hay operaciones parciales ante entrada inválida.

SendInvitationsService consulta responses una vez para el lote, por eventId; solo utiliza sus tokens.
Antes de SMTP vuelve a comprobar propiedad, estado, presencia y correo del invitado, y su respuesta mediante
consulta puntual. Si ya respondió, compensa y salta ALREADY_ANSWERED. El email almacenado en GuestResponse
nunca se utiliza como destinatario. Persiste una carrera entre la última lectura y SMTP; no se añaden transacciones.

Respuesta 200 y Cache-Control: no-store exacto. results lleva guestId, status y code (FAILED) o reason
(SKIPPED). SENT lleva recorded: true si el registro se confirmó; false si falló tras aceptación SMTP.
Sin contactos, tokens ni mensajes internos. dailyLimit lleva limit, remaining y resetsAt, leído desde users
después del lote, también tras fallos o saltos. EMAIL_NOT_CONFIGURED es fallo de proveedor: compensa reservas
y cuenta para el corte tras tres; el resto sale SKIPPED/PROVIDER_UNAVAILABLE. Rechazar un destinatario rompe
la racha; los saltos de elegibilidad no son intentos ni la reinician. DAILY_LIMIT corta todos los restantes.

La aceptación SMTP seguida de fallo de registro conserva las reservas y devuelve SENT/recorded=false.
Log ERROR EMAIL_SENT_RECORDING_FAILED con clase y correlación, sin mensaje ni causa. El panel puede seguir
en NOT_SENT; un reenvío posterior podría duplicar el correo, acotado por máximo e intervalo. Ningún reenvío
automático. La compensación fallida se diagnostica con EMAIL_SEND_COMPENSATION_FAILED; al alcanzar el máximo
requiere intervención manual, incluso pasado el intervalo. Las pruebas usan correo falso y servidor real.
rest-api-mail.http es la prueba manual separada para correo real; realRecipient procede del entorno privado.

El test de revalidación simula con un spy una respuesta aparecida después de la lectura del lote y
comprueba ALREADY_ANSWERED, ausencia de correo y compensación. No hay test con dos hilos reales para
esa carrera. Las pruebas concurrentes reales de envío cubren doble petición y cuota del anfitrión.

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
ON_CONFIRMATION usa la respuesta propia: solo confirma si la opción elegida existe y tiene
countsAsAttendance=true. Sin respuesta, con opción inexistente o con "No asisto", no confirma. La lectura
defensiva no lanza por una opción ausente. D-CONFIRMED-LINK saldada en el commit 2 de R8.

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

## Borrado y cascada real (R8, commit 3)

`GuestResponseCleanupPort` declara `hasResponse(eventId, guestToken)` y `deleteResponse(eventId, guestToken)`.
Lo implementa `GuestResponseRepositoryAdapter` sobre `responses`; se elimina `NoGuestResponsesYetAdapter`.
La consulta y el borrado filtran por evento y token, sin afectar respuestas de otros invitados.

`GuestService.delete(...)` comprueba propiedad y estado, y exige `confirmDeleteResponse=true` si ya existe
respuesta; sin confirmación devuelve 409 y no modifica ninguno de los documentos. Retira primero al invitado
mediante el puerto de actualización parcial con versión esperada y solo después borra su respuesta.
Si la retirada falla, no llama a la limpieza y conserva invitado y respuesta. Si falla la limpieza, devuelve
500 saneado: el invitado ya no existe y el token produce 404, aunque la respuesta quede en la colección.
Se prioriza no perder una respuesta mientras su invitado sigue vivo.

La limpieza se intenta también si no había respuesta al consultar: puede haber llegado entre la consulta y
la retirada. No es una transacción: una respuesta que ya estaba en curso puede terminar después de la limpieza
y quedar inalcanzable. No se añade una colección, transacción ni tarea de recuperación automática.

## Opciones con respuestas (R8, commit 3)

El anfitrión puede renombrar o agregar opciones en PUBLISHED. No puede eliminar una opción con respuestas ni
cambiar su countsAsAttendance: ambos producen 409 con excepciones propias. Así no se cambia retroactivamente
la confirmación ni la visibilidad del enlace. El servicio consulta ids distintos del evento mediante
`GuestResponseRepositoryPort.findAnsweredOptionIds(...)` y los pasa a `Event.replaceResponseOptions(...)`.
En DRAFT no aplica esta protección; CANCELLED y FINISHED no permiten cambios. Ver `modelo-evento.md`.

**D-ORPHAN-OPTION:** un invitado puede responder con X mientras el anfitrión la elimina, después de la consulta
de ids con respuestas. No se resuelve con transacciones. La lectura pública conserva la respuesta propia,
pero una opción inexistente no confirma asistencia ni abre el enlace ON_CONFIRMATION.

## Respuesta propia (R8, commit 1)

`GuestResponse`, en `event/domain/model`, contiene `eventId`, `guestToken`, `responseOptionId`, `comment`,
`email`, `answeredAt` y `updatedAt`. Es independiente de `Guest` y se persiste en `responses`, con índices
centrales: único `(eventId, guestToken)` llamado `response_per_guest`, y no único sobre `eventId`.

`PUT /api/invitations/{token}/response` recibe:

```json
{ "responseOptionId": "yes-id", "comment": "Texto opcional", "email": "guest@example.com" }
```

Solo la opción es obligatoria. La operación es pública, sin sesión ni CSRF. Se excluye exclusivamente
`/api/invitations/**`: su credencial es la ruta, no una cookie ambiental. CORS ya permite PUT y Content-Type;
un test verifica el preflight sin ampliar la configuración. Devuelve 200 con `InvitationResponse`, construido
campo a campo y compartido con GET. No registra una apertura ni modifica el documento del evento al responder.

| Regla | Excepción | HTTP |
|---|---|---|
| Token desconocido o DRAFT | `InvitationNotFoundException`, mismo detalle | 404 |
| Solo PUBLISHED, plazo inclusive y fin exclusivo, con Clock inyectado | `ResponseNotAcceptedException` | 409 |
| Opción del evento obligatoria | `InvalidResponseOptionException` | 422 |
| Comentario con contenido solo si allowComment | `ResponseCommentNotAllowedException` | 422 |
| Comentario máximo 500 caracteres | `ResponseCommentTooLongException` | 422 |
| Email opcional con formato válido | `InvalidResponseEmailException` | 422 |
| Ya respondió y allowResponseChange es false | `ResponseChangeNotAllowedException` | 409 |

Comentario null o en blanco se guarda como null, incluso con allowComment=false. El contenido no vacío se
conserva. Email null o en blanco es ausente; el resto se normaliza sin espacios exteriores y en minúsculas.
El email se almacena únicamente en la respuesta. No se muestra al anfitrión ni a otros invitados, y R10 no lo
usa para enviar nada. Cualquier uso futuro requiere aprobación expresa y doble confirmación de la dirección:
el PUT público permite indicar una dirección sin conocimiento de su dueño. D-EMAIL-NOT-SENT sigue abierta para la cita.

Con allowResponseChange=false se ejecuta un insert sin consultar existencia antes: el índice único decide
la carrera. El comando de inserción conserva `code` y `keyPattern` que el driver pierde al construir
MongoWriteException de insertOne. Solo el 11000 de la clave compuesta se transforma en 409. No se conserva
mensaje ni causa originales, que incluyen el token. Otro duplicado produce un 500 saneado, sin falsa
clasificación como respuesta existente. Con allowResponseChange=true se usa findAndModify con upsert:
`$setOnInsert` de answeredAt y `$set` del resto y updatedAt. Una colisión de inserción concurrente sobre la
misma clave reintenta solo la actualización atómica, sin lectura previa.

GET y PUT añaden `myResponse { responseOptionId, comment, answeredAt, updatedAt }`, sin email, ni token ni
identificadores internos. Si no hay respuesta se omite el bloque. `canChangeResponse` requiere respuesta
existente, allowResponseChange y las mismas condiciones de tiempo y estado. El plazo rige toda escritura,
incluida la primera: se acepta exactamente en el plazo, pero nunca en el instante exacto del fin.

GET y PUT incluyen además `canRespond` (boolean). Es true si el evento está PUBLISHED, no ha vencido el
plazo (inclusive), now es anterior a endsAtInstant y, si existe respuesta, allowResponseChange es true.
Sin respuesta y con ventana abierta puede ser true aunque canChangeResponse sea false. Este último
conserva su significado: `existe respuesta && canRespond`. CANCELLED y FINISHED devuelven ambos false;
DRAFT sigue oculto con 404. Tras una primera respuesta sin cambios permitidos, el PUT devuelve 200 pero
su vista actualizada ya lleva canRespond=false, pues una siguiente escritura quedaría bloqueada.

La política de dominio `ResponseAcceptancePolicy`, obtenida desde Event con el instante del Clock
inyectado, comparte las reglas entre GET y PUT. La ventana se calcula únicamente en Event.acceptsResponses;
GuestResponse.create la exige mediante esa política y el caso de uso toma de ella el permiso de cambios
para elegir insert o upsert. No se añade una consulta de existencia antes del insert: el índice único
sigue imponiendo una respuesta por invitado. Los flags describen disponibilidad; el PUT conserva también
las validaciones de opción, comentario y email.

Tests con Clock fijo comparan GET y PUT con cuerpo válido en 13 escenarios: primera respuesta, respuestas
existentes con/sin cambios, plazo vencido/exacto, fin anterior/exacto/posterior, CANCELLED y FINISHED.
Cuatro tests de dominio cubren la política; las pruebas previas de concurrencia y privacidad se conservan.

Éxitos y errores conservan `Cache-Control: no-store` exacto; los errores usan instancia constante
`/api/invitations`. Los mensajes son constantes, sin eco de token, comentario ni email. JSON ilegible produce
400 saneado sin registrar el mensaje de Jackson. `InvitationViewFactory` comparte la proyección segura de
GET y PUT; su método privado guestHasConfirmed calcula la confirmación con la respuesta ya cargada y la
opción del evento, sin consultas adicionales. Cambiar de una opción que cuenta a una que no cuenta oculta
el enlace en ON_CONFIRMATION; el cambio inverso lo muestra. La confirmación de otro invitado no da acceso.
Fuera de PUBLISHED, el acceso sigue oculto aunque la respuesta propia cuente como asistencia.

## Calendario público (R8, commit 4)

`GET /api/invitations/{token}/calendar` descarga `event.ics`, público sin sesión ni CSRF y sin exigir una
respuesta previa. Devuelve `text/calendar;charset=UTF-8`, `Content-Disposition: attachment; filename="event.ics"`
y `Cache-Control: no-store` exacto. No marca OPENED ni escribe el evento.

| Estado | Resultado |
|---|---|
| Token desconocido / DRAFT | Mismo 404 y detalle que R7, instancia `/api/invitations`, no-store exacto |
| PUBLISHED | 200 con calendario y enlace solo si es visible |
| CANCELLED | 409 con ProblemDetail, instancia constante y no-store exacto; no se descarga un archivo |
| FINISHED | 200; sin enlace online, porque visibleOnlineAccess exige PUBLISHED |

`DownloadInvitationCalendarService` carga la respuesta propia y reutiliza `InvitationViewFactory` para el
filtro del JSON. Solo pasa al puerto de renderizado una proyección `InvitationCalendarData`: id del evento,
título, descripción, inicio/fin locales, zona, venue, URL visible y el instante del Clock. Nunca tokens,
anfitrión, invitados, email, teléfono, comentario de respuesta, contraseña, sala ni instrucciones.
ON_CONFIRMATION muestra el enlace solo con una opción que cuenta; cambiar a otra lo oculta. HOURS_BEFORE
incluye el umbral exacto; ALWAYS puede mostrarlo antes de responder. Opción inexistente no confirma.

El adaptador `ICal4jInvitationCalendarAdapter`, en `infrastructure/out/calendar`, usa iCal4j 4.2.0. Incluye
VTIMEZONE y DTSTART/DTEND con TZID, UID estable `eventId@gtog` (igual para los invitados del mismo evento)
y DTSTAMP del Clock. LOCATION combina nombre y dirección presencial, o la URL online visible, que también
va en la propiedad URL. La descripción es la del evento, nunca el comentario de respuesta.
iCal4j escapa los textos; el adaptador normaliza saltos y pliega a 75 octetos UTF-8 con CRLF y espacio de
continuación, sin dividir caracteres multibyte (el FoldingWriter de la librería cuenta caracteres).

La dependencia 4.2.0 se descargó desde Maven Central y se mostró dependency:tree antes del commit. Las
actualizaciones remotas de zonas están desactivadas en `src/main/resources/ical4j.properties`, con
`net.fortuna.ical4j.timezone.update.enabled=false`. Configurator lo lee al inicializarse antes de cualquier
uso del registro, sin depender de Spring. Los tests comprueban la configuración, la validez del archivo,
escapes, tildes/emojis, límite por octetos, lectura posterior y correspondencia de instantes al cambiar la hora.

## Verificación

`mvn clean verify`: **426 tests**, sin fallos, errores ni omitidos. R6 incorporó 71 casos y R7 incorporó 31:
4 de dominio, 9 de aplicación y 18 de integración contra Atlas (`gtog_test`). La preparación de R8 añade
29: 8 de dominio, 4 de aplicación y 17 de integración, incluida apertura concurrente y cancelación con
versión anterior al cambio del plazo.

El commit 1 añade 54: 19 de dominio, 4 de aplicación y 31 de integración. Incluye dos PUT simultáneos con
hilos reales (un 200 y un 409 sin cambios; dos 200 con cambios, siempre un documento), límite inclusivo del
plazo y exclusivo del fin, cambios fuera de plazo, errores sin token ni comentario en logs, duplicado real
sin causa, duplicados de otro índice en insert y upsert, preflight CORS y cabeceras exactas.

El commit 2 añade 7 pruebas de integración a través del GET: confirmación afirmativa y negativa, cambios en
ambos sentidos, opción inexistente sin error, aislamiento entre invitados y ocultación aun confirmado en
CANCELLED y FINISHED. Se comprueba el cuerpo completo para detectar URL, contraseña, sala e instrucciones.

El commit 3 añade 35: 16 de dominio, 3 de aplicación y 16 de integración. Incluyen cambios permitidos y
prohibidos en PUBLISHED, reglas estructurales, DRAFT y estados cerrados, propiedad, ids usados por evento,
confirmación de borrado, orden real de cascada y sus dos fallos, aislamiento entre respuestas y limpieza de
una respuesta llegada después de la consulta inicial. Las pruebas anteriores de cascada de R6 se adaptan
al nuevo orden aprobado. También se comprueba que repetir un id no permita cambiar la marca de asistencia
de una opción con respuestas, y que estos conflictos produzcan 409 aunque invaliden el tamaño o la asistencia.

El commit 4 añade 25: 5 de aplicación, 6 del adaptador iCalendar y 14 HTTP. Cubren descarga pública sin CSRF,
privacidad sobre el cuerpo completo, los tres modos de visibilidad comparados con JSON, cambios de asistencia,
opción inexistente, CANCELLED 409 / FINISHED 200 / 404 indistinguibles, cabeceras exactas incluso con Accept:
text/calendar, ausencia de escritura OPENED, Clock, UID, zona/DST, configuración y formato RFC 5545.

R7 cubre privacidad comparando el cuerpo completo, fechas locales/UTC, los tres modos de visibilidad con Clock
fijo y el umbral exacto, estados públicos y borrador, 404 indistinguibles, logs sin tokens, OPENED desde NOT_SENT
y SENT, revisitas sin escritura, carreras entre aperturas, `$elemMatch` sobre el invitado correcto, version +1,
conflicto HTTP 409 al cancelar con una versión antigua, cabecera exacta no-store y acceso sin sesión ni CSRF.
