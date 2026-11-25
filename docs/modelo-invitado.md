# Modelo de datos: Invitado

Estado a 23 de noviembre de 2026. Incluye alta por enlace público, lote todo o nada y edición/borrado sin versión esperada.
R11 incorpora generación asistida de enlaces WhatsApp, sin escrituras,
y confirmación idempotente del envío declarado por el anfitrión. Telegram está fuera de R11. Se mantienen gestión de invitados,
consulta pública, respuestas, panel, calendario, canRespond y envío SMTP de las rebanadas anteriores.

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
| `source` | `GuestSource` | HOST o PUBLIC_LINK; ausente = HOST, sin migración. Editar contactos no cambia el origen |
| `joinedAt` | `Instant`, nullable | Clock de alta, solo para PUBLIC_LINK |

**La respuesta no vive aquí. `ANSWERED` no es un valor del enum ni se almacena.** El panel de R9 lo deriva
desde los invitados vivos y sus documentos de respuesta. OPENED está implementado desde R7; R10 incorpora
reservas, escritura de SENT y envío SMTP. R11 genera enlaces WhatsApp sin cambiar ese estado y registra
la confirmación manual mediante escrituras parciales. Telegram queda aplazado.

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
| Al menos correo o teléfono no vacío para HOST; opcionales para PUBLIC_LINK | `MissingGuestContactException` | 422 |
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

**El índice no impide repetir un token dentro del mismo documento.** Dominio comprueba los tokens del lote
y la escritura añade una condición de ausencia de todos ellos. Las altas comprueban también la ausencia de
correos/teléfonos normalizados dentro de la propia condición atómica, sin comparar una versión esperada.

Ejemplo ilustrativo de escritura parcial, trasladado de AGENTS.md. El filtro real añade las condiciones
de identidad, propietario y estado que correspondan a la operación; el instante viene del Clock inyectado
y toda modificación incrementa version. Nunca reescribir el evento entero para actualizar un invitado:

```java
// correcto
mongoTemplate.updateFirst(
    query(where("guests.token").is(token)),
    new Update().set("guests.$.invitationStatus", OPENED)
                .set("guests.$.openedAt", clock.instant())
                .inc("version", 1),
    EventDocument.class);

// incorrecto: reescribe el documento entero y pierde escrituras concurrentes
EventDocument event = repository.findByGuestToken(token);
event.getGuests().stream()...setStatus(OPENED);
repository.save(event);
```

`EventGuestRepositoryPort` tiene tres operaciones, implementadas con `MongoTemplate`:

- `addGuest`: `$push` de un invitado.
- `updateGuest`: `$set` posicional de `name`, `email` y `phoneE164`; conserva token, estado y fechas.
- `removeGuest`: `$pull` por identidad del invitado.

Edición y borrado filtran por evento, propietario, estado permitido e identidad, sin comparar la versión
leída. Incrementan `version` en la misma escritura. Editar usa `guests.$[g]` con arrayFilters sobre `g._id`:
el campo id embebido se guarda como _id y Spring no traduce arrayFilters. El filtro niega la existencia de
OTRO invitado con el correo o teléfono nuevos normalizados. Dos ediciones de fichas distintas no se bloquean;
dos hacia el mismo contacto producen un éxito y un 409. Editar la misma ficha desde dos pestañas es de última
escritura. El reinicio de emailSendCount/lastEmailSentAt compara el correo actual en Mongo con el nuevo,
dentro del filtro `reset`, no con una lectura anterior: si no cambia, preserva las reservas concurrentes.
Se comprueba coincidencia y modificación; un fallo se reclasifica reconsultando, nunca se devuelve un éxito
falso. Invitado ausente da 404, estado cerrado o duplicado da 409.

La cascada sigue retirando primero el invitado y después su respuesta. Si una respuesta llega entre la
comprobación inicial y la retirada, también se borra, aunque el anfitrión no hubiera confirmado; la confirmación
solo puede exigirse para una respuesta ya observada. Si la retirada falla, no se ejecuta la limpieza.

Compatibilidad temporal: updateGuest/removeGuest conservan sus firmas con expectedVersion para mantener
los tests existentes (salvo las dos expectativas autorizadas), pero ese argumento no condiciona la escritura.
addGuest conserva también su firma con expectedVersion: delega en el mismo motor de addGuests con un elemento
y mantiene el conflicto genérico anterior de una carrera tardía. GuestService conserva su constructor de
cinco puertos por los tests existentes, sin añadir constructores alternativos. Son las únicas entradas de
compatibilidad; no duplican normalización ni escritura. No hay reintentos silenciosos ni save(event)
alternativo. Un save antiguo del documento completo sigue fallando con 409 por bloqueo optimista.

## Alta en lote

GuestBatch es Java sin Spring. Prepara el lote completo con la misma validación de Guest; recopila todos
los errores de nombre/contactos y marca todos los índices con contactos equivalentes. Solo después genera
invitados NOT_SENT con sus identidades y tokens. La preparación no escribe ni modifica el evento.
AddGuestsUseCase pertenece al dominio de event y AddGuestsService comprueba propiedad y estado antes de
validar la lista. No comprueba emailVerified: la puerta de R12 solo está al crear el evento.

app.guests.max-batch=100 limita la petición; app.guests.max-per-event=500 limita el evento y también el alta
individual. Esta última conserva DTO, Location y cabeceras; su única extensión es 409 con
code=CAPACITY_EXCEEDED al alcanzar el tope. No se añade no-store exacto a su respuesta existente.

Mongo ejecuta un único update con id, hostId, DRAFT/PUBLISHED, ausencia de correos, teléfonos y tokens y
$expr: size(ifNull(guests, [])) <= maxPerEvent - tamañoDelLote. Así dos lotes no consumen las mismas plazas.
$push/$each conserva el orden e incrementa version una sola vez. No hay bucle de escrituras ni save del
evento completo. Sin coincidencia se reconsulta para clasificar propietario, estado, duplicados o capacidad.
Un rechazo no altera el BSON ni version. No se añaden invitados dentro de POST /api/events.

### Contrato HTTP

POST /api/events/{id}/guests/batch, autenticado y con CSRF, recibe:

```json
{"guests":[{"name":"Ana","email":"ana@example.com","phone":null}]}
```

Se usa @RequestBody normal. Orden: cuerpo ausente o ilegible → 400 del framework; con JSON válido,
propiedad → 404; estado → 409; lista null, vacía o mayor que max-batch → 422; validación por elemento → 422;
duplicados contra el evento y capacidad → 409. Un cuerpo válido con guests ausente se trata como null y da
422. Un elemento null equivale a nombre y contactos ausentes: BLANK_NAME y NO_CONTACT en su índice.
No exige emailVerified, como el alta individual. Se permite en DRAFT y PUBLISHED; no en CANCELLED/FINISHED.

Se recopilan BLANK_NAME, NO_CONTACT, INVALID_EMAIL, INVALID_PHONE y DUPLICATE_IN_BATCH con index de base
cero. Un contacto normalizado repetido marca todos los índices implicados, no solo los posteriores. Si hay
cualquier error de elemento, no se comparan contactos contra el evento ni se escribe nada. Contra el evento,
se informa DUPLICATE_IN_EVENT por cada índice afectado; la capacidad superada devuelve code=CAPACITY_EXCEEDED
sin errors. Los errores de lote llevan detail fijo, instance constante /api/events/guests/batch y no-store
exacto, sin nombres ni contactos. Los 400 del framework conservan su instance constante /api/errors.

El éxito devuelve 201 con una lista raíz de GuestInvitationResponse, en el orden de la petición, Location
/api/events/{id}/guests y Cache-Control: no-store exacto. El DTO es el mismo del alta individual; cada invitado
tiene id y token propios, y el token solo aparece dentro de invitationPath. No se registra el cuerpo.

Tests con servidor real y cookies crudas verifican orden, normalización, credenciales, errores y privacidad,
prevalencia del 400 del framework, estados/propiedad/CSRF y los bordes 497+3 y 498+3. El alta individual conserva
sus códigos, DTO y cabeceras, salvo el nuevo conflicto de capacidad, y genera el mismo documento que un lote
de uno salvo identidad y token. Las futuras altas desde CSV o agenda usarán este contrato.

Referencias: [índices multiclave únicos](https://www.mongodb.com/docs/manual/core/indexes/index-types/index-multikey/),
[índices parciales](https://www.mongodb.com/docs/manual/core/index-partial/) y
[actualizaciones atómicas](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/).

## WhatsApp asistido (R11)

Guest sigue embebido en events, sin campos nuevos ni estado de generación persistido.
`POST /api/events/{id}/invitations/whatsapp-links` requiere sesión, CSRF y propiedad en aplicación.
Solo PUBLISHED; los demás estados → 409. El enlace público de un DRAFT conserva su 404.
Sin cuerpo, guestIds ausente o null, selecciona todos en orden de alta. La lista explícita conserva su orden.
Antes de procesar se valida: vacío/repetidos/máximo → 422, invitado ajeno → 404. El máximo propio es
app.invitations.max-whatsapp-links-per-request=100; no se usa el límite del correo.

GenerateWhatsAppLinksService lee las respuestas del evento una sola vez y usa sus tokens para excluir
a quienes ya respondieron, sin utilizar el email de la respuesta. NO_PHONE precede a ALREADY_ANSWERED y
después INVALID_PHONE; la comprobación del E.164 almacenado usa el mismo patrón del dominio de Guest,
sin renormalizar ni lanzar excepción por datos antiguos inválidos. Un invitado saltado no aborta el lote.

200 con {results:[{guestId,status,reason?,url?,message?}]}, no-store exacto. READY lleva enlace y texto;
SKIPPED lleva NO_PHONE, ALREADY_ANSWERED, INVALID_PHONE o MESSAGE_TOO_LONG, sin url/message.
Los dígitos de wa.me son phoneE164 sin '+'. El texto se codifica en UTF-8 con %20, %0A y %2B para '+' literal;
la decodificación estándar reproduce exactamente message. El token solo se incorpora al enlace /r/{token}
del mensaje. No se registran estos datos; los resultados de dominio ocultan su contenido en toString.

InvitationMessageFormatter comparte con el correo fechas locales, zona, lugar y neutralización de controles,
sin modificar los tests del correo. El texto WhatsApp incluye saludo, anfitrión, título, inicio/fin y lugar
o plataforma; no incluye URL de la sala, contraseña, instrucciones ni los pies propios del correo.
Nombres del invitado/anfitrión, título y lugar compuesto se limpian y recortan a 100 grafemas totales con '...' mediante BreakIterator de Java 25;
no se parten familia ZWJ, banderas ni modificadores de piel. La URL final codificada admite hasta 2000
caracteres inclusive; por encima se salta. Es una cota conservadora, no un límite real verificado de WhatsApp.

Generar no escribe ningún documento ni modifica version, invitationStatus, canal, fechas o cuotas.
No usa el motor de reservas de correo. Los tests HTTP comparan documentos completos de events/responses/users
antes y después, incluidos datos previos de envío/apertura; un spy verifica una lectura de respuestas.
El frontend deberá abrir de uno en uno tras un gesto del usuario y ofrecer message para copia alternativa.

**Confirmación implementada (commit 2):** POST /api/events/{id}/invitations/whatsapp-sent requiere sesión,
CSRF, propiedad y PUBLISHED, con cuerpo obligatorio {guestIds:[...]}; no exige generar enlaces antes.
La lista es obligatoria, única, no vacía y con el mismo máximo configurable 100. Lista ausente/vacía/repetida
o máximo superado → 422; cuerpo ausente → 400; id ajeno → 404 antes de cualquier escritura.
Devuelve 200 {results:[{guestId,status}]} con no-store exacto.

GuestWhatsAppConfirmationAdapter intenta dos actualizaciones posicionales atómicas con $elemMatch, siempre
filtrando id del evento, hostId, PUBLISHED, id del invitado y phoneE164 con contenido no blanco:
- NOT_SENT: fija SENT, deliveryChannel WHATSAPP y sentAt obtenido del Clock inyectado; incrementa version.
- OPENED con sentAt nulo/ausente: completa WHATSAPP y sentAt, incrementa version y conserva OPENED/openedAt.

Ambas devuelven MARKED. Sin coincidencia, aplicación reconsulta: SENT → ALREADY_SENT, OPENED con fecha previa
→ ALREADY_OPENED, sin teléfono → NO_PHONE, retirado durante el lote → NOT_FOUND y continúa. Los resultados
de idempotencia no escriben, conservan canal/fecha del primer envío (también si fue EMAIL) y no incrementan version.
No se consultan respuestas ni se usan reservas/cuotas de correo al confirmar; no se envía ningún mensaje.
La primera apertura y la confirmación conservan OPENED, canal y fecha en ambos órdenes y en paralelo.
Una versión anterior guardada por el anfitrión falla con 409, sin sobrescribir la confirmación.
Si el evento deja de estar PUBLISHED a mitad del lote, se devuelve 409 aunque ya haya marcas: se acepta
porque confirmar es idempotente; no hay transacción ni rollback del lote.
D-WHATSAPP-TRUST: SENT es declarativo; el envío real pertenece a la cuenta del anfitrión y gtog no puede verificarlo.
rest-api-whatsapp.http es una prueba manual independiente: lee realPhone del entorno privado, genera,
imprime solo la URL en un paso y confirma después de que el usuario envíe desde WhatsApp. No se ejecuta automáticamente.
El frontend abrirá de uno en uno por gesto, confirmará cada envío y ofrecerá copiar message. MESSAGE_TOO_LONG
puede afectar a todo el evento con un título de emojis; debe sugerir acortarlo.
Telegram queda fuera de R11; su formato de enlace y la hipótesis del selector de chat están sin verificar.

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
Para DATE_POLL lista las candidatas, las horas que existen, la zona y el plazo; no crea inicio o fin.
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
compensa reservas y cuenta para el corte del lote. El formateador recibe solo plataforma, nunca OnlineAccess;
el renderizador recibe texto público ya preparado, sin la URL de la sala. Los tests MIME serializan y decodifican Unicode y neutralizan CR/LF.
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

Todo ProblemDetail de `/api/invitations/**` pasa por InvitationProblemDetailAdvice después de que Spring
rellene instance: constante /api/invitations, detalle fijo, sin extensiones con datos de la petición y
no-store exacto. Las excepciones de dominio durante la rehidratación desde Mongo son errores 500 de
integridad almacenada, no errores 422 del invitado; no se registra el token ni los datos corruptos.

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

### Disponibilidad de invitado en una consulta

`PUT /api/invitations/{token}/availability` usa la credencial de la invitación, sin sesión ni CSRF.
Cuerpo: `{"pollRevision":0,"candidateDateIds":["candidate-1","candidate-2"]}`. La lista es
obligatoria, puede ser vacía para indicar «ninguna», y solo contiene ids distintos de candidatas
vigentes. Devuelve 200 con la invitación y `myAvailability` (revisión, candidatas,
`answeredAt` y `updatedAt`). Un invitado sin respuesta vigente no recibe ese bloque.

Se reutiliza la política de respuestas: PUBLISHED y plazo inclusivo, con Clock inyectado.
La primera respuesta de cada ronda siempre puede entrar en plazo. Cambiarla exige
`allowResponseChange`. Tipo incorrecto, ronda distinta, plazo vencido, estado cerrado o
cambio prohibido dan 409. Desconocido y DRAFT dan el mismo 404. Entrada de negocio inválida
da 422 y JSON ilegible, 400. Éxitos y errores mantienen no-store y los errores se sanean.

El bloque `availability` comparte la colección `responses` y el índice único
`(eventId, guestToken)`. Un upsert condicionado permite reemplazar una revisión menor,
o actualizar la misma si se permiten cambios. La primera fecha de respuesta se conserva
dentro de una revisión. Las lecturas públicas, envíos y confirmación de borrado usan la
revisión vigente. El borrado en cascada limpia el documento aunque sea de otra ronda.
La lectura de la ronda y la escritura viven en colecciones distintas: si la ronda cambia
entre ambas, el PUT devuelve 409 tras releer y cualquier escritura de la ronda anterior
queda invisible, sin poder sobrescribir una respuesta de la nueva.

### Votos de la consulta al cierre

El GET de invitación personal de un `DATE_POLL` omite `pollResults` mientras el plazo
siga abierto, incluido el instante exacto del vencimiento. Después, devuelve
`pollResults: {pollRevision, votes:[{name, answered, candidateDateIds}]}`. Las filas
siguen el orden de los invitados actuales. Una lista vacía con `answered=true` significa
«ninguna candidata». Con `answered=false` significa que no hay voto vigente.
Los votos huérfanos o de otra revisión quedan fuera. La vista previa del enlace público
y el PUT de disponibilidad no incluyen el bloque. El resultado lleva no-store exacto y
no incluye contactos, identificadores de invitados, tokens ni marcas de tiempo de sus votos.

### Contrato InvitationResponse

- `guest`: exclusivamente `name`, sin id ni contactos.
- `title`, `description`, `status`, `modality`, `startsAt`, `endsAt`, `timeZone`, `startsAtInstant` y `endsAtInstant`.
- `eventType`: `FIXED_DATE` también para eventos antiguos sin discriminador, o `DATE_POLL`.
- `datePoll`, solo en consultas: candidatas con id, fecha y hora opcional, revisión y selección
  si existe. Se omiten las fechas definitivas y sus instantes mientras no existan. En consultas se
  omite `myResponse`; `myAvailability` aparece solo si existe disponibilidad en la revisión vigente.
  El PUT de asistencia sigue devolviendo 409 para consultas.
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
ya tiene handler 409 con ProblemDetail y un test HTTP de cancelación concurrente. SENT en R11 preserva
OPENED y sus fechas e incrementa version únicamente cuando registra datos nuevos.

El endpoint no registra tokens. No deben habilitarse trazas de URLs ni consultas que contengan estas credenciales.
Quedan anotadas D-RATE-LIMIT y D-REFERRER: sin límite de peticiones en R7, y el frontend debe servir su página
con `Referrer-Policy: no-referrer` para evitar filtrar el token de la URL al cargar recursos externos.

## Borrado y cascada real (R8, commit 3)

`GuestResponseCleanupPort` declara `hasResponse(eventId, guestToken)` y `deleteResponse(eventId, guestToken)`.
Lo implementa `GuestResponseRepositoryAdapter` sobre `responses`; se elimina `NoGuestResponsesYetAdapter`.
La consulta y el borrado filtran por evento y token, sin afectar respuestas de otros invitados.

`GuestService.delete(...)` comprueba propiedad y estado, y exige `confirmDeleteResponse=true` si ya existe
respuesta; sin confirmación devuelve 409 y no modifica ninguno de los documentos. Retira primero al invitado
mediante el puerto de actualización parcial, sin comparar la versión esperada e incrementando `version`,
y solo después borra su respuesta.
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

En fecha fija, GET y PUT incluyen además `canRespond` (boolean). Es true si el evento está PUBLISHED, no ha vencido el
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

Las pruebas con Clock fijo comparan GET y PUT con cuerpo válido: primera respuesta, respuestas
existentes con/sin cambios, plazo vencido/exacto, fin anterior/exacto/posterior, CANCELLED y FINISHED.
Los tests de dominio cubren la política; las pruebas de concurrencia y privacidad se conservan.

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

La tabla de 200 con archivo corresponde a FIXED_DATE. Una consulta DATE_POLL sin fecha definitiva
devuelve 409, también en FINISHED, sin invocar iCal4j. Mantiene no-store exacto, instance constante y
ausencia del token en el cuerpo. El paso 7 añade la consulta de votos al cierre.

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

## Alta por enlace público

El anfitrión solicita el enlace en borrador y este se genera al publicar; el interruptor y su invariante se
detallan en [modelo-evento.md](modelo-evento.md#enlace-público-de-alta). El enlace `/j/{joinToken}` es una
credencial independiente del enlace individual `/r/{guestToken}`; nunca se registran tokens, nombres o contactos.
Revocarlo no elimina a los invitados que ya entraron ni invalida sus enlaces individuales.

El alta es inmediata, sin aprobación. Guest.joinPublicInvitation comparte la normalización del dominio con
el alta HOST, pero permite omitir ambos contactos. Nombre recortado, entre 1 y 100 caracteres, sin controles;
si vienen, correo válido normalizado y teléfono E.164. El nuevo invitado recibe id y token propios,
source=PUBLIC_LINK, joinedAt del Clock y NOT_SENT; no cambia deliveryChannel.

Si cualquier contacto normalizado ya pertenece a cualquier invitado del evento, se descartan AMBOS en
silencio antes de escribir. Nunca se devuelve el invitationPath de un invitado existente. Con conflicto
conocido y sin él se hace el mismo número de operaciones Mongo: lectura del evento y una escritura.
La ausencia de contactos en el filtro sigue siendo la autoridad: ante una carrera se reintenta una sola
vez sin ambos contactos. La unicidad también se aplica al alta HOST, al lote y a la edición.

Una sola escritura parcial condiciona token del enlace, PUBLISHED, ausencia de conflicto, tamaño total
menor que app.guests.max-per-event=500 y número de source=PUBLIC_LINK menor que app.guests.public-link-max=25;
usa arrays ausentes como vacíos, añade el invitado e incrementa version. No compara versión esperada.
Al fallar, reconsulta y clasifica: enlace ausente/DRAFT 404, cierre JOIN_CLOSED, PUBLIC_LINK_FULL o
CAPACITY_EXCEEDED. La ventana usa ResponseAcceptancePolicy con Clock en la lectura previa; el filtro
comprueba que plazo, fin y zona no cambiaron, pero no reevalúa el reloj. Si se cruza el cierre durante
el alta, el invitado puede quedar creado y no poder responder después (409).

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/invitations/join/{joinToken}` | 200 con vista previa propia y no-store; 404 indistinguible para enlace desconocido, revocado, desactivado o DRAFT |
| POST | `/api/invitations/join/{joinToken}` | `{name,email?,phone?}` → 201 `{invitationPath}` y cookie; 400, 404, 409 o 422 con errors `{field,code}` |

Ambos son públicos sin sesión ni CSRF. El GET no escribe: título, descripción, estado, modalidad, horas
locales, zona e instantes UTC, venue presencial y solo plataforma en línea; nunca identidad del anfitrión,
contactos ni secretos de sala. Incluye canJoin, alreadyJoined y, solo con cookie válida propia, invitationPath.
CANCELLED/FINISHED conservan 200 con canJoin=false y POST 409 JOIN_CLOSED. Revocado e inventado comparten 404.
En DATE_POLL la vista previa incluye el bloque público de candidatas y revisión, sin fechas definitivas.
La ventana depende del plazo obligatorio, inclusive. La actualización del alta comprueba también tipo,
revisión y selección leídos; si cambian devuelve 409 JOIN_CLOSED sin insertar. No compara version ni
repite el reloj en Mongo, y mantiene los mismos límites, cookie y descarte silencioso de contactos.

Precedencia del POST: cuerpo ilegible/ausente (400 del framework) → enlace (404) → cookie válida
(409 ALREADY_JOINED, incluso si el evento cerró) → ventana de admisión (409 JOIN_CLOSED) → validación
(422, todos los errores de campo) → tope público (409 PUBLIC_LINK_FULL) → total (409 CAPACITY_EXCEEDED).
Los errores no repiten los datos; instancia constante y detalle fijo, extensiones code/errors con valores
de lista blanca, no-store exacto y ningún Set-Cookie. La negociación JSON rechaza un Accept incompatible
antes del alta (406); el saneamiento también elimina cookies heredadas de cualquier error posterior.

Cookie solo en el 201: HttpOnly, `gtog_join_<primeros 16 hex SHA-256(joinToken)>`, valor guestToken,
Path=/api/invitations/join, app.join.cookie-max-age=P30D y app.join.cookie-same-site=Lax por defecto.
app.join.cookie-secure se deduce de app.public-base-url: true si empieza por https, false en otro caso;
puede sobrescribirse. Solo reconoce un invitado vivo PUBLIC_LINK del MISMO evento: una cookie HOST,
ajena, borrada o con otro nombre se ignora. El GET válido recupera solo el enlace propio; un POST repetido
da ALREADY_JOINED. La cookie es fricción, no una barrera: omitirla permite otra alta hasta agotar el tope.

PUBLIC_LINK nunca recibe correo de gtog: SKIPPED/UNVERIFIED_CONTACT antes de reservar o consumir cuota,
también si el anfitrión edita después su contacto. La futura conversión a invitado normal exige decidir
la verificación del contacto. WhatsApp sí permite READY con teléfono y NO_PHONE sin él. Respuesta, panel,
primera apertura y cascada confirmada funcionan igual que para HOST. Los nombres vienen de un anónimo:
el frontend los pinta como texto, nunca HTML; futuras exportaciones CSV neutralizarán prefijos =, +, - y @.

## Verificación

El total de tests y el estado de su última ejecución se registran únicamente en `docs/plan-iteracion-1.md`.
La cobertura se organiza por comportamiento:

- **R6:** alta, edición y borrado; nombre y contactos válidos, normalización E.164, duplicados, unicidad
  del token, propiedad, estados editables y escrituras parciales bajo concurrencia.
- **R7:** vista pública y privacidad del cuerpo completo y los logs; fechas locales/UTC, visibilidad online
  con Clock fijo, primera apertura idempotente y concurrente, bloqueo optimista y cabeceras exactas.
- **R8:** plazo y fin del evento, coherencia de `canRespond` con el PUT, respuestas concurrentes, validación
  y privacidad, enlace por asistencia, opciones publicadas, cascada de borrado y calendario público seguro.
- **R9:** contadores y filas desde invitados vivos, respuestas huérfanas y opciones desconocidas, correo
  de respuesta oculto y tokens solo en invitationPath, lectura por lote y ausencia de escrituras en panel/listado.
- **R10:** reservas y cuotas atómicas, límites temporales, compensaciones, corte del lote, privacidad y
  composición MIME del correo; transporte falso en tests, sin envíos reales, y fallo al registrar SENT.
- **R11:** grafemas y codificación de la URL, límites, generación sin escrituras, privacidad y lectura por
  lote; confirmación idempotente, teléfono requerido, preservación de OPENED/canal/fecha, concurrencia,
  bloqueo optimista y cierre del evento a mitad del lote.
- **Enlace público:** intención sin token en DRAFT, publicación/revocación y conservación al cerrar;
  topes y contactos bajo concurrencia, descarte silencioso, cookie propia/ajena y precedencia de errores,
  privacidad de cuerpo y logs, WhatsApp permitido y correo excluido incluso después de editar.
