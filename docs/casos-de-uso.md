# gtog — Casos de uso y métodos del API

Los cuatro casos de uso principales y la secuencia de llamadas que los realiza. Es el puente entre
`definicion-funcional.md` (qué hace la aplicación) y los modelos (el contrato técnico de cada agregado). El
contrato exacto de campos está en `/v3/api-docs` y en cada modelo.

Revisión contra controladores, SecurityConfig, dominio y tests del repositorio. Las dos marcas de
verificación iniciales se resolvieron; otras afirmaciones que necesitan decisión se conservan y se enumeran
al final, sin cambiar código ni sustituir en silencio el texto original. La matriz final recoge el contrato
observado para cada método y ruta. Los ejemplos son extractos con valores ficticios, no respuestas completas.

| # | Caso de uso | Actor | Sesión |
|---|---|---|---|
| UC-1 | Alta del anfitrión | Persona sin cuenta | No |
| UC-2 | Creación y convocatoria de un evento | Anfitrión verificado | Sí |
| UC-3 | Respuesta del invitado | Invitado con su enlace | No |
| UC-4 | Consulta del estado del evento | Anfitrión | Sí |

---

## Convenciones comunes

- **JSON** en todo el API, bajo `/api`. Las fechas del evento son hora local sin zona más `timeZone`; el
  servidor devuelve también los instantes UTC donde importa.
- **CSRF.** Toda petición `POST`, `PUT` o `DELETE` del anfitrión y de `/api/auth` lleva la cookie `XSRF-TOKEN`
  y su valor en la cabecera `X-XSRF-TOKEN`. La cookie se obtiene con cualquier `GET` anónimo al servidor
  (por ejemplo la primera carga de la aplicación con `GET /api/auth/me`, que responde 401 con la cookie). Las
  rutas `/api/invitations/**` no usan CSRF ni sesión: su credencial es el token de la ruta.
- **Sesión.** Cookie `JSESSIONID`, `HttpOnly`. Cambia de identificador al iniciar sesión.
- **Errores.** `application/problem+json` con `status`, `title`, `detail` fijo e `instance` constante. Nunca
  contienen tokens ni la ruta pedida.

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
  La aplicación no arranca con el bypass activo si la URL pública no es `localhost`.

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
| 4 | `POST /api/events/{id}/publish` | 200, evento en `PUBLISHED` |
| 5 | Convocar: ver abajo | |

**Cuerpo de la creación:** título, descripción, `startsAt`, `endsAt` y `timeZone`; `modality` (`IN_PERSON` u
`ONLINE`); `venue` si es presencial u `onlineAccess` si es en línea; y, opcionales, `responseOptions`,
`allowComment`, `allowResponseChange` y `responseDeadline`.

**Por defecto:** opciones "Asisto" (cuenta como asistencia) y "No asisto"; `allowComment=false`;
`allowResponseChange=true`.

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

En WhatsApp el anfitrión abre la `url`, pulsa enviar él mismo desde su cuenta y solo después confirma. Sin
`guestIds`, la operación se aplica a todos los invitados del evento.

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
| Cambiar el plazo | `PUT …/response-deadline` | En `DRAFT`, y en `PUBLISHED` solo antes del inicio. Nunca posterior al inicio |
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
  (100 cada 24 horas). El mensaje de WhatsApp sale de la cuenta del anfitrión y no tiene límites.
- Los mensajes nunca contienen la URL de la sala virtual.

---

## UC-3 · Respuesta del invitado

**Actor:** invitado con su enlace. **Sin sesión ni CSRF.** **Resultado:** respuesta registrada.

### Flujo principal

| Paso | Llamada | Resultado |
|---|---|---|
| 1 | El invitado recibe `<base>/r/{token}` por correo, WhatsApp o copiado | La aplicación extrae el token |
| 2 | `GET /api/invitations/{token}` | 200 con la vista pública del evento. **La primera vez marca al invitado como `OPENED`** |
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
| Comentario con comentarios deshabilitados, o de más de 500 caracteres | PUT | 422 |
| Correo opcional mal formado | PUT | 422 |
| JSON ilegible | PUT | 400 |

### Reglas

- **Plazo:** se acepta en el instante exacto y se rechaza después. El evento deja de aceptar respuestas en su
  fin exacto. Un plazo pasado cierra las respuestas.
- **Una respuesta por invitado.** Cambiarla es una actualización, no un segundo registro.
- **El correo opcional de la respuesta** se guarda sin uso: no se muestra al anfitrión ni se le escribe.
- El calendario no incluye contactos ni token, y respeta el mismo filtro de la sala que la vista.
- El token no aparece en logs ni en cuerpos de error.

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
| El panel del evento | `GET /api/events/{id}/dashboard` | Totales, contadores por opción en su orden y una fila por invitado |
| Solo los invitados | `GET /api/events/{id}/guests` | Invitados con su contacto, canal, estado y `invitationPath` |

### El panel

- **`totals`:** `guests`, `answered`, `pending` y `attending` (respondidos con una opción que cuenta como
  asistencia).
- **`byOption`:** por opción, `optionId`, `label`, `countsAsAttendance` y `count`, incluidas las que tienen cero.
- **`guests`:** por invitado, `id`, `name`, contacto, `deliveryChannel`, `invitationStatus`, `answered`, la
  respuesta con su etiqueta y comentario, y `optionKnown`.

### Cómo leer los estados

| `invitationStatus` | Significa |
|---|---|
| `NOT_SENT` | Nadie lo ha enviado ni lo ha abierto |
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

## Resumen: método y caso de uso

| Método y ruta | UC |
|---|---|
| `POST /api/auth/register`, `verify-email`, `resend-verification`, `login`, `logout`, `forgot-password`, `reset-password` | 1 |
| `GET /api/auth/me` | 1 |
| `POST /api/events` | 2 |
| `PUT /api/events/{id}` y `…/response-options`, `…/venue`, `…/online-access`, `…/response-deadline` | 2 |
| `POST /api/events/{id}/publish` y `…/cancel` | 2 |
| `POST /api/events/{id}/guests` | 2 |
| `PUT /api/events/{id}/guests/{guestId}`, `DELETE /api/events/{id}/guests/{guestId}` | 2 |
| `GET /api/events/{id}/guests` | 4 |
| `POST /api/events/{id}/guests/batch` | 2 |
| `POST /api/events/{id}/invitations/send`, `whatsapp-links`, `whatsapp-sent` | 2 |
| `GET /api/invitations/{token}`, `PUT …/response`, `GET …/calendar` | 3 |
| `GET /api/events`, `GET /api/events/{id}`, `GET /api/events/{id}/dashboard` | 4 |

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
| GET /api/invitations/{token} | No | No | 200 + InvitationResponse, también CANCELLED/FINISHED; no-store exacto | 404 mismo detalle para DRAFT y token desconocido |
| PUT /api/invitations/{token}/response | No | No | 200 + InvitationResponse actualizada, no-store exacto | 400 cuerpo, 404, 409 estado/plazo/fin/cambio bloqueado, 422 opción/comentario/correo |
| GET /api/invitations/{token}/calendar | No | No | 200 text/calendar, attachment, no-store exacto; también FINISHED | 404 DRAFT/token desconocido, 409 CANCELLED, 500 generación |

### UC-4: consultas del anfitrión

Fuentes: EventController, EventDashboardController, GuestController y los tests de listado y panel.

| Método y ruta completos | Sesión | CSRF | Éxito | Errores |
|---|---|---|---|---|
| GET /api/events | Sí | No | 200 + lista EventSummaryResponse; puede estar vacía | 401 |
| GET /api/events/{id} | Sí | No | 200 + EventResponse | 401, 404 |
| GET /api/events/{id}/dashboard | Sí | No | 200 + EventDashboardResponse, no-store exacto | 401, 404 |
| GET /api/events/{id}/guests | Sí | No | 200 + lista GuestInvitationResponse | 401, 404 |

### Reglas contrastadas y observaciones pendientes de decisión

Se conservan las frases originales de los flujos para que el usuario decida cómo corregirlas. Esta lista
identifica dónde no equivalen al contrato observado; no cambia código ni amplía su comportamiento.

| Afirmación original | Contraste con el código y estado |
|---|---|
| «JSON en todo el API» | calendar produce text/calendar (InvitationController.calendar). Pendiente de corregir el alcance de la frase |
| «cualquier GET anónimo» emite XSRF-TOKEN | CsrfCookieFilter.shouldNotFilter excluye /api/invitations/**. Los GET de /me y health sí permiten el arranque CSRF. Pendiente de acotar la frase |
| Todos los errores tienen detail fijo, instance constante y no incluyen la ruta | Filtros, advice MVC, invitaciones y lote están saneados. Varios errores de dominio del anfitrión usan exception.getMessage() y no fijan instance (GlobalExceptionHandler.handleEventDomainException, handleEventNotFoundException y handleGuestConflict fuera del lote). No generalizarlo a todo el API sin decidir ese cambio |
| Bypass solo con localhost | LocalEmailVerificationGuard.afterPropertiesSet también admite el host exacto 127.0.0.1. Pendiente de completar la frase |
| «Sin guestIds, la operación se aplica a todos» | Válido para send y whatsapp-links; whatsapp-sent exige cuerpo y lista explícita no vacía. Véanse WhatsAppSentRequest y ConfirmWhatsAppSentService. Pendiente de acotar la frase |
| WhatsApp «no tiene límites» | No usa reservas ni cuota diaria de gtog, pero hay máximo configurable de 100 enlaces/confirmaciones por petición y URL final de hasta 2000 caracteres; exceder la longitud produce SKIPPED/MESSAGE_TOO_LONG. Pendiente de precisar qué límite se quiere expresar |
| Comentario con comentarios deshabilitados → 422 | GuestResponse.create trata el comentario en blanco como ausente/null; solo rechaza contenido. Pendiente de precisar la frase |
| «La primera vez marca OPENED» | GetInvitationService solo escribe para PUBLISHED y NOT_SENT/SENT. CANCELLED y FINISHED pueden devolver 200 sin marcar OPENED. Pendiente de precisar la frase |
| NOT_SENT significa que nadie lo ha enviado | Si SMTP aceptó pero falló registrar SENT, la respuesta del envío es SENT/recorded=false y el panel puede conservar NOT_SENT. Es el límite ya documentado en el plan; el estado no garantiza que nunca haya salido un correo |
| «El token no aparece en logs» | Las pruebas cubren la configuración normal. El plan mantiene el límite conocido de org.springframework.web en DEBUG o spring.mvc.log-resolved-exception, que pueden registrar la ruta pública con el token. Pendiente de acotar la afirmación |

Límites de correo confirmados por application.properties y EmailSendPolicy: por defecto 3 envíos por
invitado, intervalo inclusivo de 1 h, ventana del anfitrión de 24 h desde su primer envío con máximo 100,
y lote máximo 20. Cambiar realmente el correo reinicia la cuota del invitado. Un fallo SMTP compensa;
si SMTP acepta pero falla registrar SENT, conserva reservas y devuelve SENT/recorded=false. No hay
reintentos automáticos. Tres fallos de proveedor consecutivos cortan el lote; un rechazo de destinatario
interrumpe esa racha. Son reglas confirmadas, no una autorización para corregir los textos pendientes.

FINISHED no tiene hoy transición automática ni endpoint que lo produzca; si el estado ya está almacenado,
la consulta pública y el calendario siguen devolviendo 200. La propuesta de derivarlo en la interfaz no
puede verificarse en este repositorio, porque el frontend aún no está implementado.

La mención común a no-store no implica igualdad exacta de cabecera para todos los métodos: el alta y el
listado individual de invitados no la fijan expresamente; Spring Security añade su valor de caché por
defecto. El lote, el panel 200 y la invitación pública sí fijan el valor exacto. No se cambiaron cabeceras.
