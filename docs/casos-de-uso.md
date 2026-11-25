# gtog — Casos de uso y métodos del API

Los cinco casos de uso principales y la secuencia de llamadas que los realiza. Es el puente entre
`definicion-funcional.md` (qué hace la aplicación) y los modelos (el contrato técnico de cada agregado). El
contrato exacto de campos está en `/v3/api-docs` y en cada modelo.

Revisión contra controladores, SecurityConfig, dominio y tests del repositorio. La matriz final recoge el
contrato observado para cada método y ruta. Los ejemplos son extractos con valores ficticios, no respuestas completas.

| # | Caso de uso | Actor | Sesión |
|---|---|---|---|
| UC-1 | Alta del anfitrión | Persona sin cuenta | No |
| UC-2 | Creación y convocatoria de un evento | Anfitrión verificado | Sí |
| UC-3 | Respuesta del invitado | Invitado con su enlace | No |
| UC-4 | Consulta del estado del evento | Anfitrión | Sí |
| UC-5 | Alta de invitado por enlace público | Persona con el enlace del evento | No |

---

## Convenciones comunes

- **JSON** bajo `/api`, salvo el calendario público, que devuelve `text/calendar`. Los errores usan
  `application/problem+json`. Las fechas del evento son hora local sin zona más `timeZone`; el servidor
  devuelve también los instantes UTC donde importa.
- **CSRF.** Toda petición `POST`, `PUT` o `DELETE` del anfitrión y de `/api/auth` lleva la cookie `XSRF-TOKEN`
  y su valor en la cabecera `X-XSRF-TOKEN`. La cookie se obtiene, por ejemplo, con `GET /api/auth/me`
  anónimo (401 con la cookie) o `GET /actuator/health`. Los GET de `/api/invitations/**` no la emiten:
  esas rutas no usan CSRF ni sesión; su credencial es el token de la ruta.
- **Sesión.** Cookie `JSESSIONID`, `HttpOnly`. Cambia de identificador al iniciar sesión.
- **Errores.** `application/problem+json` con `status`, `title`, `detail` e `instance`. En invitaciones,
  seguridad y advice MVC, detalle fijo e instancia constante, sin token ni ruta pedida; el saneamiento
  central de invitaciones cubre cualquier handler MVC que devuelva ProblemDetail. Solo los errores de
  dominio del anfitrión admiten detalle variable e instancia derivada de su ruta.
- **Caché.** Invitación pública, lote y panel 200 fijan `no-store` exacto. El alta individual y el listado de
  invitados conservan la cabecera por defecto de Spring Security; no se exige igualdad exacta para ellos.

| Código | Significado |
|---|---|
| 400 | JSON ilegible, o token de verificación o restablecimiento inválido, caducado o ya usado |
| 401 | Sin sesión, o credenciales incorrectas en el login |
| 403 | Falta o no coincide el token CSRF; o `code=EMAIL_NOT_VERIFIED` |
| 404 | Recurso inexistente o ajeno. También un token de invitación desconocido o de un evento en borrador |
| 405, 406, 415 | Método, `Accept` o `Content-Type` no admitidos |
| 409 | Conflicto de estado: no se puede hacer eso en el estado actual, o duplicado |
| 422 | Una regla de negocio rechaza el contenido |

Las respuestas que llevan credenciales (`/me`, el panel, los enlaces de invitación, la vista pública) llevan
`Cache-Control: no-store`.

---

## UC-1 · Alta del anfitrión

**Actor:** persona sin cuenta. **Resultado:** cuenta verificada y sesión iniciada.

### Flujo principal

| Paso | Llamada | Resultado |
|---|---|---|
| 1 | `GET /api/auth/me` anónimo (primera carga) | 401 y cookie `XSRF-TOKEN` |
| 2 | `POST /api/auth/register` `{name, email, rawPassword, timeZone}` | 201, `Location: /api/users/{id}`, usuario con `emailVerified=false`. En segundo plano se envía el correo de verificación |
| 3 | El usuario abre el correo. El enlace es `<base>/verify-email#token=…` | La aplicación lee el token del fragmento |
| 4 | `POST /api/auth/verify-email` `{token}` | 200 sin cuerpo |
| 5 | `POST /api/auth/login` `{email, password}` | 200 con el usuario y cookie de sesión |
| 6 | `GET /api/auth/me` | 200 con `emailVerified=true` |

El registro no inicia sesión por sí solo. Se puede iniciar sesión sin haber verificado el correo, y la
verificación funciona con la sesión que ya existía, sin volver a entrar.

### Alternativos y errores

| Situación | Llamada | Resultado |
|---|---|---|
| Correo ya registrado | register | 409 |
| Nombre vacío, correo mal formado, contraseña de menos de 8 caracteres, zona horaria inválida | register | 422 |
| Sin cookie o cabecera CSRF | cualquier `POST` | 403 |
| Credenciales incorrectas | login | 401 |
| Token inválido, caducado o ya usado | verify-email | 400 genérico |
| Reenviar la verificación | `POST /api/auth/resend-verification` (con sesión) | 204 siempre. Si ya está verificado no envía; límite de 1 cada 5 minutos y 5 cada 24 horas |
| Cerrar sesión | `POST /api/auth/logout` | 204 |

### Recuperar la contraseña

| Paso | Llamada | Resultado |
|---|---|---|
| 1 | `POST /api/auth/forgot-password` `{email}` | 202 con el mismo cuerpo y cabeceras exista o no la cuenta |
| 2 | Enlace del correo: `<base>/reset-password#token=…` | |
| 3 | `POST /api/auth/reset-password` `{token, newPassword}` | 200 sin cuerpo. Contraseña corta: 422 y el token sigue siendo válido. Token inválido: 400 |

Un restablecimiento correcto marca el correo como verificado, invalida las demás sesiones del usuario y los
demás tokens, y **no inicia sesión**: hay que hacer login con la contraseña nueva.

### Reglas

- Sin correo verificado se puede iniciar sesión, listar y consultar, pero no crear eventos (UC-2).
- El tiempo de respuesta de `forgot-password` no depende de que la cuenta exista: todo el trabajo ocurre en
  segundo plano.
- En desarrollo local, `APP_AUTH_AUTOVERIFYEMAIL=true` verifica la cuenta al registrarla y no envía correo.
  La aplicación no arranca con el bypass activo si el host de la URL pública no es exactamente `localhost` o `127.0.0.1`.

### Ejemplo UC-1

Fuente: `AuthControllerIntegrationTest.registersUserSuccessfully` y `AuthUserResponse`.
Cookie/cabecera CSRF según las convenciones comunes; no hace falta sesión.

```http
POST /api/auth/register
Content-Type: application/json

{"name":"Ana Díaz","email":"ana.host@example.com","rawPassword":"password12","timeZone":"Europe/Madrid"}
```

```http
HTTP/1.1 201 Created
Location: /api/users/host-demo

{"id":"host-demo","name":"Ana Díaz","email":"ana.host@example.com","timeZone":"Europe/Madrid","emailVerified":false}
```

El 200 sin cuerpo de reset-password está comprobado en `PasswordRecoveryController.reset` y en
`PasswordRecoveryIntegrationTest.resetChangesPasswordVerifiesEmailInvalidatesOnlyItsSessionsAndDeletesAllItsTokens`.
La rotación de JSESSIONID y la conservación de XSRF-TOKEN en el login están medidas con servidor real en
`ManualLoginSessionIntegrationTest.aLoginWithAnExistingSessionChangesItsId` y
`loginNeitherIssuesNorRenewsTheExistingCsrfCookie`. El 202 uniforme está probado para cuenta existente,
inexistente, en enfriamiento y con el máximo agotado en
`PasswordRecoveryIntegrationTest.existingUnknownCoolingAndDailyLimitedAccountsHaveIdenticalResponses`:
estado, cuerpo y todas las cabeceras salvo Date. La búsqueda empieza dentro del ejecutor; no es una promesa
de tiempo de pared idéntico bajo cualquier carga. El formato inválido sigue devolviendo 422.

---

## UC-2 · Creación y convocatoria de un evento

**Actor:** anfitrión con sesión y correo verificado. **Resultado:** evento publicado e invitaciones enviadas.

### Flujo principal

| Paso | Llamada | Resultado |
|---|---|---|
| 1 | `POST /api/events` | 201, `Location: /api/events/{id}`, evento en `DRAFT` |
| 2 | Opcional, en `DRAFT`: `PUT /api/events/{id}`, `…/response-options`, `…/venue`, `…/online-access`, `…/response-deadline` | 200 con el evento actualizado |
| 3 | Alta individual: `POST /api/events/{id}/guests` con `{name, email, phone}`; o una sola alta en lote: `POST /api/events/{id}/guests/batch` con `{guests:[{name,email,phone},…]}` | 201 con el invitado (individual) o la lista en el mismo orden (lote), reutilizando el mismo DTO e invitationPath. Location del invitado o de la colección, respectivamente |
| 4 | `POST /api/events/{id}/publish` | 200, evento en `PUBLISHED`; genera el enlace público si se pidió |
| 5 | Convocar: ver abajo | |

**Cuerpo de la creación con fecha definida:** título, descripción, `startsAt`, `endsAt` y `timeZone`; `modality` (`IN_PERSON` u
`ONLINE`); `venue` si es presencial u `onlineAccess` si es en línea; y, opcionales, `responseOptions`,
`allowComment`, `allowResponseChange`, `responseDeadline` y `publicInvitationEnabled` (false por defecto).
Este último expresa intención en DRAFT: el enlace nace al publicar. PUT del evento no lo modifica;
GET/PUT `/api/events/{id}/public-invitation` consultan/cambian el interruptor (ver UC-5).

**Por defecto en fecha definida:** opciones "Asisto" (cuenta como asistencia) y "No asisto"; `allowComment=false`;
`allowResponseChange=true`.

### Evento con fecha por definir (pasos 2 a 4)

La secuencia completa anterior corresponde a `FIXED_DATE`, que sigue siendo el tipo por defecto.
También se puede crear un `DATE_POLL` con sesión y CSRF, consultarlo y editar sus datos generales.
Desde el paso 4 se publica con la misma llamada y devuelve 200 sin generar fechas definitivas ni opciones
de asistencia. Si se pidió enlace público, nace al publicar. Todavía no se recogen disponibilidades.

Fuente: `DatePollDraftIntegrationTest`. Petición abreviada (el bloque venue válido también es obligatorio):

```http
POST /api/events
Content-Type: application/json

{"title":"Cena","eventType":"DATE_POLL","timeZone":"Europe/Madrid","modality":"IN_PERSON",
 "venue":{"placeName":"Sala","address":"Calle Mayor","latitude":40.4,"longitude":-3.7,"placeId":"p"},
 "responseDeadline":"2027-05-09T18:00:00",
 "datePoll":{"candidateDates":[{"date":"2027-05-10","startTime":"20:00:00"},{"date":"2027-05-11"}]}}
```

```http
HTTP/1.1 201 Created
Location: /api/events/event-demo

{"id":"event-demo","eventType":"DATE_POLL","status":"DRAFT","startsAt":null,"endsAt":null,
 "responseOptions":[],"datePoll":{"pollRevision":0,"selectedCandidateDateId":null,"candidateDates":[
 {"id":"candidate-1","date":"2027-05-10","startTime":"20:00:00"},
 {"id":"candidate-2","date":"2027-05-11","startTime":null}]}}
```

Los ids los genera el dominio. Hay entre 2 y `app.events.max-candidate-dates` candidatas (10 por defecto),
sin fechas repetidas y sin hora de fin; el plazo es obligatorio. Las reglas rechazadas dan 422; un formato
ilegible de fecha/hora o tipo desconocido da 400. Las fechas definitivas o las opciones de asistencia en una
consulta también dan 422. No se crea nada al rechazar el alta. Autorización, CSRF y correo verificado se mantienen.

`GET /api/events/{id}` devuelve las candidatas con sus ids; el listado identifica el tipo y lleva
`startsAt=null`. Para editar datos generales, `PUT /api/events/{id}` incluye `eventType=DATE_POLL`,
omite inicio/fin y conserva un plazo. No cambia candidatas ni convierte el tipo; quitar el plazo da 422.
Las opciones de asistencia son propias de `FIXED_DATE`: reemplazarlas en una consulta da 409.

**Reemplazar candidatas (paso 3).** Fuente: `CandidateDatesIntegrationTest`. Con sesión y CSRF,
en DRAFT o PUBLISHED:

```http
PUT /api/events/event-demo/candidate-dates
Content-Type: application/json

{"pollRevision":0,"candidateDates":[
 {"id":"candidate-1","date":"2027-05-14","startTime":"20:00:00"},
 {"id":"candidate-2","date":"2027-05-11","startTime":null}]}
```

```http
HTTP/1.1 200 OK

{"id":"event-demo","eventType":"DATE_POLL","datePoll":{"pollRevision":1,
 "selectedCandidateDateId":null,"candidateDates":[
 {"id":"candidate-1","date":"2027-05-14","startTime":"20:00:00"},
 {"id":"candidate-2","date":"2027-05-11","startTime":null}]}}
```

Se envía la lista completa y la revisión leída, independiente de la versión del documento. Los ids existentes
se conservan; sin id se reutiliza una fecha/hora coincidente o se genera uno. Cambiar fecha/hora, añadir o
quitar avanza una ronda y borra la selección. Repetir no escribe; reordenar conserva ronda y selección.
Una edición obsoleta que cambia la configuración da 409; repetir el mismo cambio ya aplicado sigue
siendo idempotente, incluso con la revisión anterior. Una revisión futura da 409; ausente/negativa, 422.
Una revisión antigua que referencia una candidata eliminada también da 409; con revisión actual un id
desconocido da 422, igual que ids duplicados, fechas ausentes o una lista fuera de límites. Un tipo fijo o estado cerrado
da 409; otro anfitrión, 404. Cuerpo ilegible, 400, antes de propiedad y validación de negocio.

La revisión invalida lógicamente las disponibilidades anteriores. No se borra ningún invitado, token, apertura
o cuota ni se crea historial. Elegir fecha llega en el paso 7. Si el plazo venció, cambiar candidatas no lo amplía: el anfitrión fija otro plazo obligatorio,
también en consultas publicadas. Dos editores concurrentes no se pisan: uno recibe 409 y recarga antes de reintentar.

**Difusión de la consulta (paso 4).** Los endpoints de correo y WhatsApp son los mismos que en fecha fija:
exigen PUBLISHED y mantienen elegibilidad, cuotas, límites y confirmación de envío. Los mensajes indican
fecha por definir, cada candidata con hora solo si existe, zona y plazo, sin fin de relleno. Incluyen lugar
presencial o solo plataforma online, nunca la URL de sala. No se notifican cambios automáticamente.

La invitación individual y la vista previa `/api/invitations/join/{joinToken}` muestran el bloque público
de candidatas y revisión, sin contactos, anfitrión, invitados ajenos ni datos Mongo. La primera apertura
individual en PUBLISHED fija OPENED de forma atómica; las siguientes no escriben. Ejemplo abreviado:

```http
GET /api/invitations/guest-token-demo

HTTP/1.1 200 OK
Cache-Control: no-store

{"guest":{"name":"Ana"},"eventType":"DATE_POLL","status":"PUBLISHED","timeZone":"Europe/Madrid",
 "datePoll":{"pollRevision":0,"candidateDates":[{"id":"candidate-1","date":"2027-05-10","startTime":"20:00:00"},
 {"id":"candidate-2","date":"2027-05-11","startTime":null}]},"canRespond":true,"canChangeResponse":false}
```

No se incluyen instantes ni inicio/fin definitivos. En consultas se usa el PUT de disponibilidad; el PUT de asistencia
devuelve 409. El calendario devuelve 409 sin fecha definitiva, incluso en FINISHED. DRAFT/desconocido mantienen 404; CANCELLED/FINISHED mantienen GET 200 con su estado,
sin marcar OPENED ni mostrar sala. HOURS_BEFORE queda oculto sin un inicio definitivo; ALWAYS conserva
su regla y ON_CONFIRMATION no se activa por una respuesta de disponibilidad.

El alta por enlace conserva cookie, límites y contactos descartados. Usa el plazo inclusive como ventana,
sin un fin ficticio; tipo, revisión y selección leídos forman parte del filtro atómico. Si cambian antes de
escribir, POST devuelve 409 JOIN_CLOSED sin insertar. Una apertura concurrente sí permite continuar.

### Paso 3: contrato del lote y ejemplo UC-2

No se incluyen invitados dentro de POST /api/events. El lote es todo o nada: si falla una fila, no se
guarda ninguna; un rechazo tampoco cambia version. app.guests.max-batch=100 y app.guests.max-per-event=500,
configurables; el segundo límite también afecta al alta individual. Ambos permiten DRAFT/PUBLISHED y no
exigen emailVerified para gestionar un evento existente: la puerta de R12 solo está en crear el evento.

Orden comprobado: cuerpo ausente/ilegible → 400 del framework, antes del caso de uso; con JSON válido,
propiedad → 404; estado → 409; guests null, ausente, vacío o por encima del máximo → 422; validación completa
por elemento → 422; duplicados contra el evento y capacidad → 409. Un cuerpo ausente no equivale a una
lista null. No se difiere la lectura del JSON.

Los rechazos del lote usan ProblemDetail, detail fijo, instance constante y Cache-Control: no-store exacto.
errors contiene objetos `{index, code}`, con índice de base cero, nunca nombre, correo ni teléfono.
Los códigos son BLANK_NAME, NO_CONTACT, INVALID_EMAIL, INVALID_PHONE y DUPLICATE_IN_BATCH; un contacto
equivalente repetido marca ambos índices. Contra invitados existentes: DUPLICATE_IN_EVENT (409).
El tope devuelve code=CAPACITY_EXCEEDED a nivel de respuesta, sin errors por elemento. Un 422 de validación
precede al 409 por un duplicado contra el evento. Los 400 del framework usan la instancia /api/errors.

Fuente del ejemplo: `GuestBatchControllerIntegrationTest.createsThreeGuestsInOrderWithNormalizedContactsDistinctTokensAndExactHeaders`.
Con sesión y CSRF:

```http
POST /api/events/event-demo/guests/batch
Content-Type: application/json

{"guests":[{"name":"Ana","email":" ANA@EXAMPLE.COM ","phone":null}]}
```

```http
HTTP/1.1 201 Created
Location: /api/events/event-demo/guests
Cache-Control: no-store

[{"id":"guest-ana","name":"Ana","email":"ana@example.com","phoneE164":null,"invitationStatus":"NOT_SENT","deliveryChannel":null,"sentAt":null,"openedAt":null,"invitationPath":"/r/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}]
```

Un correo inválido en la segunda fila produce, como extracto de error:

```json
{"status":422,"detail":"Guest batch rejected","instance":"/api/events/guests/batch","errors":[{"index":1,"code":"INVALID_EMAIL"}]}
```

El alta individual usa el mismo objeto sin la lista envolvente, conserva sus cabeceras anteriores y añade
solo el conflicto de capacidad aprobado. El lote sí fija no-store exacto porque lleva invitationPath.

### Convocar: canal por invitado

| Canal | Llamada | Resultado |
|---|---|---|
| Correo | `POST /api/events/{id}/invitations/send` `{guestIds?}` | 200 con un resultado por invitado (`SENT`, `FAILED` o `SKIPPED`, con su código o motivo) y `dailyLimit {limit, remaining, resetsAt}` |
| WhatsApp, generar | `POST /api/events/{id}/invitations/whatsapp-links` `{guestIds?}` | 200 con, por invitado, `READY` con `url` y `message`, o `SKIPPED` con su motivo. No escribe nada |
| WhatsApp, confirmar | `POST /api/events/{id}/invitations/whatsapp-sent` `{guestIds}` | 200 con `MARKED`, `ALREADY_SENT`, `ALREADY_OPENED`, `NO_PHONE` o `NOT_FOUND` por invitado |
| Cualquier otro medio | copiar el `invitationPath` del invitado | |

En WhatsApp el anfitrión abre la `url`, pulsa enviar él mismo desde su cuenta y solo después confirma.
Omitir `guestIds` aplica a todos solo en correo y generación de enlaces; confirmar WhatsApp exige cuerpo
con una lista explícita no vacía.

### Alternativos y errores

| Situación | Resultado |
|---|---|
| Sin sesión | 401 |
| Correo sin verificar al crear el evento | 403 con `code=EMAIL_NOT_VERIFIED`; no se exige en el alta de invitados |
| Fin no posterior al inicio, zona inválida, título vacío | 422 |
| Modalidad incoherente con `venue` u `onlineAccess` en la creación | 422 |
| Plazo posterior al inicio del evento | 422 |
| Menos de 2 o más de 5 opciones, ninguna que cuente como asistencia, etiquetas vacías o repetidas | 422 |
| Editar un evento que no está en `DRAFT` | 409 |
| `PUT …/venue` en un evento en línea, o `…/online-access` en uno presencial | 409 |
| Publicar un evento ya publicado | 409 |
| Publicar sin ubicación o acceso, o con menos de 2 opciones | 422 |
| Alta individual repetida, o lote con contactos ya existentes en el evento | 409; el lote devuelve errors con DUPLICATE_IN_EVENT por índice |
| Contactos repetidos dentro del lote, incluso con otra grafía | 422 con DUPLICATE_IN_BATCH en ambos índices |
| guests vacío/null/ausente o más de 100 altas (máximo configurable) | 422; sin cuerpo o JSON ilegible, 400 |
| Más de 500 invitados por evento (máximo configurable) | 409 con code=CAPACITY_EXCEEDED; sin errores por elemento |
| Invitado sin correo ni teléfono, teléfono no normalizable | 422 |
| Evento ajeno o inexistente | 404 |
| Enviar o generar enlaces con el evento en `DRAFT`, `CANCELLED` o `FINISHED` | 409 |
| Envío o WhatsApp: lista de ids vacía, repetida o por encima del máximo | 422 |
| Id de invitado que no pertenece al evento | 404 |

### Después de publicar

| Qué | Llamada | Reglas |
|---|---|---|
| Cambiar el plazo | `PUT …/response-deadline` | Fecha fija: DRAFT, o PUBLISHED antes del inicio, nunca posterior a él. Consulta: DRAFT/PUBLISHED con plazo obligatorio |
| Cambiar opciones | `PUT …/response-options` | Se pueden renombrar y añadir. No eliminar ni cambiar si cuenta como asistencia una opción con respuestas (409) |
| Cancelar | `POST …/cancel` `{reason?}` | Solo desde `PUBLISHED`; irreversible. Desde `DRAFT`, 409 |
| Editar invitados | `PUT …/guests/{guestId}` | 200; estado cerrado o contacto duplicado, 409; ausente, 404 |
| Eliminar invitados | `DELETE …/guests/{guestId}?confirmDeleteResponse=true` si ya respondió; sin parámetro equivale a false | 204 sin cuerpo; sin confirmar una respuesta observada, 409. Invitado ausente, 404. Primero se retira al invitado y después se borra su respuesta |

La forma exacta de la confirmación se comprueba en `GuestController.delete`: parámetro de consulta booleano
confirmDeleteResponse, falso por defecto, no un campo JSON. `ResponseOptionsAndCascadeIntegrationTest`
comprueba el 409 sin confirmar y la retirada antes de la limpieza con el parámetro true. Si una respuesta
llega entre la comprobación y la retirada, se elimina igualmente en cascada aunque no se hubiera confirmado.
Editar la misma ficha desde dos pestañas usa última escritura, manteniendo las condiciones de unicidad y cuotas.

### Reglas

- Tras publicar, la ubicación no se puede cambiar y las opciones solo se pueden cambiar con los límites de arriba.
- El correo se envía desde gtog, con límite por invitado (3 envíos, 1 hora entre envíos) y por anfitrión
  (100 por ventana de 24 horas desde el primer envío), y lote máximo 20. El intervalo de 1 hora es
  inclusivo; cambiar realmente el correo reinicia la cuota del invitado. No hay reintentos automáticos.
- Un fallo SMTP compensa las reservas. Tres fallos de proveedor consecutivos cortan el lote; el rechazo
  claro de un destinatario interrumpe esa racha. Si SMTP acepta pero falla registrar SENT, se conservan
  reservas y se devuelve `SENT` con `recorded=false`: el panel puede conservar `NOT_SENT`.
- WhatsApp no usa reservas ni cuota diaria de gtog, pero tiene máximo configurable de 100 invitados por
  petición (generar o confirmar). La URL completa tiene un tope conservador de 2000 caracteres;
  sobrepasarlo produce `SKIPPED` con `MESSAGE_TOO_LONG` para ese invitado.
- Los mensajes nunca contienen la URL de la sala virtual.

---

## UC-3 · Respuesta del invitado

Las vistas públicas ya admiten ambos tipos de evento. El recorrido de respuesta y calendario que sigue
corresponde a FIXED_DATE. En DATE_POLL, `PUT /api/invitations/{token}/availability` recibe
`{"pollRevision":0,"candidateDateIds":[]}` (lista vacía significa «ninguna») y devuelve 200
con la invitación y el bloque `myAvailability`. Ronda obsoleta, plazo vencido o cambio
no permitido devuelven 409. Candidatas inválidas devuelven 422. El PUT de asistencia y el
calendario sin fecha definitiva devuelven 409 para la consulta.

**Actor:** invitado con su enlace. **Sin sesión ni CSRF.** **Resultado:** respuesta registrada.

### Flujo principal

| Paso | Llamada | Resultado |
|---|---|---|
| 1 | El invitado recibe `<base>/r/{token}` por correo, WhatsApp o copiado | La aplicación extrae el token |
| 2 | `GET /api/invitations/{token}` | 200 con la vista pública del evento. Solo en `PUBLISHED`, la primera visita pasa `NOT_SENT` o `SENT` a `OPENED`; no cambia un `OPENED` ni su fecha |
| 3 | `PUT /api/invitations/{token}/response` `{responseOptionId, comment?, email?}` | 200 con la vista actualizada, ya con `myResponse` |
| 4 | `GET /api/invitations/{token}/calendar` | 200, `text/calendar`, descarga `.ics` |
| 5 | Cambiar de opinión: el mismo `PUT` | 200 si el anfitrión lo permite y el plazo sigue abierto |

### Qué devuelve la vista pública

- Solo el nombre del invitado. Nunca el anfitrión, contactos, otros invitados, tokens ni qué opciones cuentan
  como asistencia.
- Título, estado, modalidad, fechas locales, zona horaria e instantes UTC, y el lugar si es presencial.
- Opciones de respuesta (id y etiqueta), `allowComment`, `allowResponseChange` y el plazo.
- `canRespond`, calculado en el servidor, que dice si el formulario debe mostrarse; `canChangeResponse`, que
  dice si ya respondió y puede cambiarla; y `myResponse` si ya respondió.
- En un evento en línea, `onlineAccessInfo` (plataforma y regla de visibilidad) siempre; `onlineAccess` (URL,
  sala, contraseña, instrucciones) **solo** si el evento está publicado y se cumple la regla. Con "al
  confirmar", solo tras elegir una opción que cuenta como asistencia: responder "No asisto" no abre la sala.
- En un evento cancelado, `cancelledAt` y `cancellationReason`.

### Alternativos y errores

| Situación | Llamada | Resultado |
|---|---|---|
| Token inventado o evento en borrador | cualquiera | 404 idéntico en los dos casos |
| Evento cancelado o finalizado | GET | 200 con su estado, sin acceso en línea; `canRespond=false` |
| Evento cancelado o finalizado | PUT | 409 |
| Calendario de un evento cancelado | calendar | 409 |
| Plazo vencido o evento terminado | PUT | 409 |
| Ya respondió y el anfitrión no permite cambiar | PUT | 409 |
| Opción que no es del evento | PUT | 422 |
| Comentario con contenido cuando están deshabilitados, o de más de 500 caracteres | PUT | 422 |
| Correo opcional mal formado | PUT | 422 |
| JSON ilegible | PUT | 400 |

### Reglas

- **Plazo:** se acepta en el instante exacto y se rechaza después. El evento deja de aceptar respuestas en su
  fin exacto. Un plazo pasado cierra las respuestas.
- **Una respuesta por invitado.** Cambiarla es una actualización, no un segundo registro.
- **El correo opcional de la respuesta** se guarda sin uso: no se muestra al anfitrión ni se le escribe.
- Un comentario en blanco se trata como ausente y se guarda como null; solo su contenido se rechaza si
  `allowComment=false`.
- El calendario de fecha fija no incluye contactos ni token y respeta el filtro de sala. `FINISHED` devuelve 200 sin
  enlace de sala; `CANCELLED`, 409. Hoy no hay transición automática ni endpoint que produzca FINISHED.
- `CANCELLED` y `FINISHED` no marcan OPENED, aunque el GET de la vista devuelva 200.
- Un dato corrupto al reconstruir el evento desde Mongo devuelve 500 con detalle fijo; no es un 422 del cliente.
- El token no aparece en cuerpos de error ni en los logs con la configuración normal probada. Activar
  `org.springframework.web` en DEBUG o `spring.mvc.log-resolved-exception` puede registrar la ruta con el
  token; no actives esos logs en entornos con credenciales reales.

### Ejemplo UC-3

Fuente: `GuestResponseControllerIntegrationTest` e `InvitationResponse`. Con evento PUBLISHED, opción
yes-id del evento, comentarios y cambios habilitados; sin sesión ni CSRF:

```http
PUT /api/invitations/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/response
Content-Type: application/json

{"responseOptionId":"yes-id","comment":"Allí estaré","email":"ana.respuesta@example.com"}
```

```http
HTTP/1.1 200 OK
Cache-Control: no-store

{"guest":{"name":"Ana"},"title":"Encuentro de verano","status":"PUBLISHED","myResponse":{"responseOptionId":"yes-id","comment":"Allí estaré","answeredAt":"2027-01-01T10:00:00Z","updatedAt":"2027-01-01T10:00:00Z"},"canChangeResponse":true,"canRespond":true}
```

Es un extracto: la respuesta completa incluye fechas, opciones y configuración pública. El correo recibido
no sale en myResponse ni en el resto del cuerpo. FINISHED sigue permitiendo descargar el calendario con
200 text/calendar, sin acceso en línea; CANCELLED da 409. Comprobado en
`InvitationCalendarControllerIntegrationTest.finishedStillDownloadsWithoutAnOnlineMeetingLink` y
`cancelledIs409WithProblemDetailConstantInstanceAndExactNoStore`.

---

## UC-4 · Consulta del estado del evento

**Actor:** anfitrión con sesión. **Resultado:** saber quién ha respondido y qué.

| Qué quiere ver | Llamada | Devuelve |
|---|---|---|
| Todos sus eventos | `GET /api/events` | Lista con `id`, `title`, `startsAt`, `modality`, `status` y los contadores `totalGuests`, `answered`, `pending` y `attending`. Solo los propios |
| Un evento completo | `GET /api/events/{id}` | Configuración, opciones, ubicación o acceso en línea sin filtrar, y datos de cancelación |
| El panel del evento | `GET /api/events/{id}/dashboard` | Totales, una fila por invitado y desglose por opción en fecha fija o por candidata en consultas |
| Solo los invitados | `GET /api/events/{id}/guests` | Invitados con su contacto, canal, estado y `invitationPath` |

### El panel

- **`totals`:** `guests`, `answered`, `pending` y `attending` (respondidos con una opción que cuenta como
  asistencia).
- **`byOption`:** por opción, `optionId`, `label`, `countsAsAttendance` y `count`, incluidas las que tienen cero.
- **`guests`:** por invitado, `id`, `name`, contacto, `deliveryChannel`, `invitationStatus`, `answered`, la
  respuesta con su etiqueta y comentario, `optionKnown` y `source` (HOST/PUBLIC_LINK).
- **`publicInvitation`:** enabled, joined, limit y open; los invitados por enlace cuentan igual en totals.

### Cómo leer los estados

| `invitationStatus` | Significa |
|---|---|
| `NOT_SENT` | No hay envío ni apertura registrados; un SMTP aceptado con `recorded=false` puede conservar este estado |
| `SENT` | Correo: aceptado por el proveedor, no entregado. WhatsApp: el anfitrión declara haberlo enviado |
| `OPENED` | El invitado abrió su enlace. Puede constar además un canal de envío |

`answered` es independiente: se deriva de que exista la respuesta. Un invitado puede estar `OPENED` sin
responder. `optionKnown` vale `true` si la respuesta usa una opción vigente, `false` si esa opción ya no
existe en el evento, y `null` si no ha respondido.

### Alternativos y errores

| Situación | Resultado |
|---|---|
| Sin sesión | 401 |
| Evento ajeno o inexistente | 404 |

### Reglas

- El panel nunca expone el correo que el invitado dejó al responder.
- Los contadores parten de los invitados del evento: una respuesta huérfana no cuenta.
- En `DATE_POLL`, el panel muestra `availability.pollRevision`, las personas por
  candidata en `byCandidate`, los pendientes en `pendingGuests` y quienes marcaron
  ninguna en `unavailableGuests`. Una persona puede aparecer en varias candidatas.
  El listado usa respondidos y pendientes de la misma revisión. `attending` vale cero.
- Para reenviar a quien no ha respondido, el anfitrión toma las filas con `answered=false` y las pasa como
  `guestIds` al envío de UC-2.
- `FINISHED` no ocurre nunca. Para mostrar un evento como terminado, la interfaz lo deriva de `endsAt`.

### Ejemplo UC-4

Fuente: `EventDashboardControllerIntegrationTest.returnsAllTotalsOrderedOptionsAndOrderedGuestRows` y
`EventDashboardResponse`. Con sesión; un GET no requiere cabecera CSRF:

```http
GET /api/events/event-demo/dashboard
Cookie: JSESSIONID=sesion-ficticia
```

```http
HTTP/1.1 200 OK
Cache-Control: no-store

{"totals":{"guests":3,"answered":2,"pending":1,"attending":1},"byOption":[{"optionId":"yes-id","label":"Asisto","countsAsAttendance":true,"count":1},{"optionId":"no-id","label":"No asisto","countsAsAttendance":false,"count":1}]}
```

Extracto sin guests: la respuesta completa añade las filas con los campos descritos arriba, sin el correo
que se dejó en la respuesta. El no-store exacto se aplica al 200 del panel; no se añadió al listado ni a sus errores.

---

## UC-5 · Alta de invitado por enlace público

**Actor:** persona con `/j/{joinToken}`, sin cuenta ni sesión. **Resultado:** invitado nuevo PUBLIC_LINK y
su enlace individual, sin aprobación del anfitrión. Es otra credencial: nunca se registra.

| Paso | Llamada | Resultado |
|---|---|---|
| 1, anfitrión | POST /api/events con publicInvitationEnabled=true, o PUT /api/events/{id}/public-invitation `{enabled:true}` | En DRAFT expresa intención, `{enabled:true,path:null}`; publicar genera el token |
| 2, anfitrión | GET /api/events/{id}/public-invitation | 200 `{enabled,path}`; path `/j/<token>` solo si existe; no-store exacto |
| 3, invitado | GET /api/invitations/join/{joinToken} | 200 con vista previa, canJoin y alreadyJoined; no escribe |
| 4, invitado | POST /api/invitations/join/{joinToken} `{name,email?,phone?}` | 201 `{invitationPath}` y cookie HttpOnly; no-store exacto |
| 5, navegador propio | GET join con esa cookie | alreadyJoined=true y su invitationPath; redirigir a `/r/{token}` y continuar UC-3 |

El interruptor exige propiedad y sesión (404 si es ajeno), además CSRF en PUT. DRAFT solo cambia intención;
PUBLISHED permite activar idempotentemente, desactivar y reactivar con un token distinto. PUT en CANCELLED
o FINISHED da 409; GET sigue dando 200 y muestra el enlace conservado, inerte. EventResponse solo incluye
publicInvitationEnabled, nunca el token. Revocar no borra invitados ni sus enlaces individuales.

La vista previa no expone anfitrión, invitados ni contactos: título, descripción, estado, modalidad,
fechas locales/UTC, zona, venue presencial o solo plataforma online. CANCELLED/FINISHED dan 200 con
canJoin=false; POST devuelve JOIN_CLOSED. DRAFT, desactivado, revocado e inventado comparten el mismo 404.

Nombre recortado, 1–100 caracteres sin controles; contactos opcionales, pero válidos si vienen. Si uno ya
existe en cualquier invitado, se descartan ambos en silencio: nunca se revela el enlace de otra persona.
La nueva respuesta es indistinguible de un alta sin conflicto. Máximo público 25 y total 500, configurables,
comprobados atómicamente. La admisión reutiliza la política de primera respuesta, con Clock.

**Orden de errores del POST** (JSON normal con @RequestBody):

| Orden | Comprobación | Resultado |
|---|---|---|
| 1 | Cuerpo ilegible o ausente | 400 del framework |
| 2 | Enlace desconocido, desactivado, revocado o DRAFT | 404 indistinguible |
| 3 | Cookie de un PUBLIC_LINK vivo de este evento | 409, code=ALREADY_JOINED, incluso si cerró |
| 4 | Evento no admite una primera respuesta | 409, code=JOIN_CLOSED, antes de validar campos |
| 5 | Nombre/contactos inválidos | 422, errors `[{field,code}]`, antes de comprobar plazas |
| 6 | Plazas públicas agotadas | 409, code=PUBLIC_LINK_FULL |
| 7 | Capacidad total agotada | 409, code=CAPACITY_EXCEEDED |

No se repiten datos personales ni credenciales en errores o logs. Instance `/api/invitations`, detalle fijo,
no-store exacto y sin Set-Cookie en todos los errores; las extensiones se validan mediante lista blanca.
Un Accept incompatible da 406 antes del alta, sin escribir. También se aplican 405, 415 y 500 saneados.

Cookie solo en el 201: nombre `gtog_join_<16 hex SHA-256(joinToken)>`, valor guestToken, HttpOnly,
Path=/api/invitations/join, Max-Age 30 días, SameSite=Lax por defecto. Secure se deduce de la URL pública
(https=true) y es configurable, igual que SameSite y duración. Solo reconoce PUBLIC_LINK de ESTE evento;
cookie ajena, HOST, borrada o con otro nombre se ignora. Es fricción: otro navegador o quitar la cookie
permite otra alta. Frontend con withCredentials y política SameSite/Secure adecuada al despliegue.

PUBLIC_LINK no recibe correo de gtog: UNVERIFIED_CONTACT sin reservas ni cuota, también tras editarlo.
WhatsApp sí genera enlace si hay teléfono; sin él, NO_PHONE. Cuenta en el panel igual que HOST y conserva
la cascada confirmada si se elimina tras responder. La conversión a invitado normal queda pendiente.
Los nombres son texto de un anónimo: nunca HTML; neutralizar prefijos =, +, - o @ en futuras exportaciones CSV.

### Ejemplo UC-5

```http
POST /api/invitations/join/{joinToken}
Content-Type: application/json

{"name":"Elena"}
```

```http
HTTP/1.1 201 Created
Cache-Control: no-store

{"invitationPath":"/r/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"}
```

Se emite además la cookie descrita, sin sesión ni CSRF. El recorrido manual completo está en
`rest-api-join.http`, con jar real para el navegador A y otro cliente sin jar como navegador B.

## Resumen: método y caso de uso

| Método y ruta | UC |
|---|---|
| `POST /api/auth/register`, `verify-email`, `resend-verification`, `login`, `logout`, `forgot-password`, `reset-password` | 1 |
| `GET /api/auth/me` | 1 |
| `POST /api/events` | 2 |
| `PUT /api/events/{id}` y `…/response-options`, `…/venue`, `…/online-access`, `…/response-deadline` | 2 |
| `PUT /api/events/{id}/candidate-dates` | 2 (consulta de fechas) |
| `POST /api/events/{id}/publish` y `…/cancel` | 2 |
| `POST /api/events/{id}/guests` | 2 |
| `PUT /api/events/{id}/guests/{guestId}`, `DELETE /api/events/{id}/guests/{guestId}` | 2 |
| `GET /api/events/{id}/guests` | 4 |
| `POST /api/events/{id}/guests/batch` | 2 |
| `POST /api/events/{id}/invitations/send`, `whatsapp-links`, `whatsapp-sent` | 2 |
| `GET /api/invitations/{token}`, `PUT …/response`, `PUT …/availability`, `GET …/calendar` | 3 |
| `GET /api/events`, `GET /api/events/{id}`, `GET /api/events/{id}/dashboard` | 4 |
| `GET` y `PUT /api/events/{id}/public-invitation` | 5 (anfitrión) |
| `GET` y `POST /api/invitations/join/{joinToken}` | 5 (público) |

Fuera de estos casos: `/actuator/health`, `/v3/api-docs` y `/swagger-ui.html`.

---

## Verificación por método y ruta

Sesión indica si hace falta estar autenticado, no si el navegador puede enviar una cookie. CSRF significa
cookie más cabecera crudas. En las rutas públicas del invitado la credencial es el token, no una sesión.
Se indican los errores funcionales y de entrada. Además pueden aparecer 405/406/415 por método o
negociación no admitidos y 500 por fallos internos. Los errores del contenedor anteriores a Spring tienen
el límite conocido descrito en el plan. El 400 de un cuerpo ilegible se resuelve antes del caso de uso,
no se transforma en 404 porque el evento sea ajeno.

### UC-1: cuenta

Fuentes: AuthController, EmailVerificationController, PasswordRecoveryController, SecurityConfig,
GlobalExceptionHandler y los tests de autenticación, recuperación y sesión con servidor real.

| Método y ruta completos | Sesión | CSRF | Éxito | Errores |
|---|---|---|---|---|
| POST /api/auth/register | No | Sí | 201 + Location + AuthUserResponse | 400, 403, 409 duplicado, 422 |
| POST /api/auth/login | No | Sí | 200 + AuthUserResponse + sesión | 400, 401 credenciales, 403 |
| POST /api/auth/verify-email | No | Sí | 200 sin cuerpo | 400 genérico de token, 403 |
| POST /api/auth/resend-verification | Sí | Sí | 204 sin cuerpo uniforme | 401, 403 |
| GET /api/auth/me | Sí | No | 200 + AuthUserResponse vigente | 401; el anónimo recibe XSRF-TOKEN |
| POST /api/auth/forgot-password | No | Sí | 202 sin cuerpo uniforme | 400 cuerpo, 403, 422 formato de correo |
| POST /api/auth/reset-password | No | Sí | 200 sin cuerpo | 400 token, 403, 422 contraseña; 500 si falla hash/escritura tras consumir |
| POST /api/auth/logout | No lo exige LogoutFilter; uso normal con sesión | Sí | 204 sin cuerpo | 403 CSRF |

Logout se procesa en el filtro de Spring Security antes de la autorización. El LogoutFilter 7.1.0 del
classpath invoca los handlers aunque Authentication sea null; SecurityConfig fija el éxito en 204 y no
hay un controlador de logout. La tabla no presupone un 401 para un cierre anónimo con CSRF válido.
Este detalle se verificó por lectura de la clase local y de la configuración, sin añadir ni ejecutar una
nueva prueba de logout anónimo.

### UC-2: evento, invitados y convocatoria

Fuentes: EventController, GuestController, GuestBatchController, InvitationSendingController,
WhatsAppInvitationController, casos de uso, dominio de Event y GlobalExceptionHandler. Los fallos SMTP
se informan como resultados FAILED/SKIPPED dentro del 200, no cambiando el estado HTTP.

| Método y ruta completos | Sesión | CSRF | Éxito | Errores |
|---|---|---|---|---|
| POST /api/events | Sí | Sí | 201 + Location + EventResponse DRAFT | 400, 401, 403 CSRF o EMAIL_NOT_VERIFIED, 422 |
| PUT /api/events/{id} | Sí | Sí | 200 + EventResponse | 400, 401, 403, 404, 409 estado/versión, 422 |
| PUT /api/events/{id}/response-options | Sí | Sí | 200 + EventResponse | 400, 401, 403, 404, 409 estado/opción usada/versión, 422 |
| PUT /api/events/{id}/venue | Sí | Sí | 200 + EventResponse | 400, 401, 403, 404, 409 estado/modalidad/versión, 422 |
| PUT /api/events/{id}/online-access | Sí | Sí | 200 + EventResponse | 400, 401, 403, 404, 409 estado/modalidad/versión, 422 |
| PUT /api/events/{id}/response-deadline | Sí | Sí | 200 + EventResponse | 400, 401, 403, 404, 409 estado/inicio/carrera, 422 plazo posterior al inicio |
| PUT /api/events/{id}/candidate-dates | Sí | Sí | 200 + EventResponse | 400, 401, 403, 404, 409 tipo/estado/revisión/versión, 422 revisión o candidatas inválidas |
| POST /api/events/{id}/publish | Sí | Sí | 200 + EventResponse PUBLISHED | 401, 403, 404, 409 estado/versión, 422 publicación incompleta |
| POST /api/events/{id}/cancel | Sí | Sí | 200 + EventResponse CANCELLED | 400 body opcional ilegible, 401, 403, 404, 409 estado/versión |
| POST /api/events/{id}/guests | Sí | Sí | 201 + Location individual + GuestInvitationResponse | 400, 401, 403 CSRF, 404, 409 duplicado/estado/capacidad, 422 |
| POST /api/events/{id}/guests/batch | Sí | Sí | 201 + Location de colección + lista GuestInvitationResponse, no-store exacto | 400 framework, 401, 403 CSRF, 404, 409 estado/duplicado/capacidad, 422 lista/elementos |
| PUT /api/events/{id}/guests/{guestId} | Sí | Sí | 200 + GuestResponse, sin enlace | 400, 401, 403, 404, 409 estado/duplicado, 422 |
| DELETE /api/events/{id}/guests/{guestId} | Sí | Sí | 204 sin cuerpo | 400 confirmDeleteResponse no booleano, 401, 403, 404, 409 estado/falta confirmación |
| POST /api/events/{id}/invitations/send | Sí | Sí | 200 + results y dailyLimit, no-store exacto | 400 body opcional ilegible, 401, 403, 404, 409 estado, 422 selección |
| POST /api/events/{id}/invitations/whatsapp-links | Sí | Sí | 200 + results READY/SKIPPED, no-store exacto | 400 body opcional ilegible, 401, 403, 404, 409 estado, 422 selección |
| POST /api/events/{id}/invitations/whatsapp-sent | Sí | Sí | 200 + results, no-store exacto | 400 body ausente/ilegible, 401, 403, 404 selección, 409 estado incluso a mitad del lote, 422 selección |

### UC-3: invitación pública

Fuentes: InvitationController, GetInvitationService, RecordGuestResponseService,
DownloadInvitationCalendarService, ResponseAcceptancePolicy y sus tests de integración.

| Método y ruta completos | Sesión | CSRF | Éxito | Errores |
|---|---|---|---|---|
| GET /api/invitations/{token} | No | No | 200 + InvitationResponse, también CANCELLED/FINISHED; no-store exacto | 404 mismo detalle para DRAFT y token desconocido; 500 datos almacenados inválidos |
| PUT /api/invitations/{token}/response | No | No | Fecha fija: 200 + InvitationResponse actualizada, no-store exacto | 400 cuerpo, 404, 409 tipo/estado/plazo/fin/cambio bloqueado, 422 opción/comentario/correo, 500 datos almacenados inválidos |
| PUT /api/invitations/{token}/availability | No | No | Consulta: 200 + InvitationResponse con myAvailability, no-store exacto | 400 cuerpo, 404, 409 tipo/estado/plazo/revisión/cambio bloqueado, 422 revisión o candidatas inválidas, 500 almacenamiento |
| GET /api/invitations/{token}/calendar | No | No | Fecha fija: 200 text/calendar, attachment, no-store exacto; también FINISHED | 404 DRAFT/token desconocido, 409 CANCELLED o DATE_POLL sin fecha definitiva, 500 generación o datos almacenados inválidos |

### UC-4: consultas del anfitrión

Fuentes: EventController, EventDashboardController, GuestController y los tests de listado y panel.

| Método y ruta completos | Sesión | CSRF | Éxito | Errores |
|---|---|---|---|---|
| GET /api/events | Sí | No | 200 + lista EventSummaryResponse; puede estar vacía | 401 |
| GET /api/events/{id} | Sí | No | 200 + EventResponse | 401, 404 |
| GET /api/events/{id}/dashboard | Sí | No | 200 + EventDashboardResponse, no-store exacto | 401, 404 |
| GET /api/events/{id}/guests | Sí | No | 200 + lista GuestInvitationResponse | 401, 404 |

### UC-5: interruptor y alta pública

Fuentes: PublicInvitationController, PublicJoinController, PublicInvitationService,
PreviewPublicInvitationService, PublicInvitationRepositoryAdapter y sus tests con servidor real.

| Método y ruta completos | Sesión | CSRF | Éxito | Errores |
|---|---|---|---|---|
| GET /api/events/{id}/public-invitation | Sí | No | 200 `{enabled,path}`, todos los estados, no-store exacto | 401, 404 |
| PUT /api/events/{id}/public-invitation | Sí | Sí | 200 `{enabled,path}`, DRAFT/PUBLISHED, no-store exacto | 400, 401 con CSRF válido sin sesión, 403 CSRF, 404, 409 estado/carrera |
| GET /api/invitations/join/{joinToken} | No | No | 200 vista previa; CANCELLED/FINISHED con canJoin=false; no-store exacto | 404; 405, 406, 500 saneados |
| POST /api/invitations/join/{joinToken} | No | No | 201 `{invitationPath}` y cookie, no-store exacto | 400, 404, 409 ALREADY_JOINED/JOIN_CLOSED/PUBLIC_LINK_FULL/CAPACITY_EXCEEDED, 422 errors por campo; 405, 406, 415, 500 saneados |
