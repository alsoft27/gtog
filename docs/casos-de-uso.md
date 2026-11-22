# gtog — Casos de uso y métodos del API

Los cuatro casos de uso principales y la secuencia de llamadas que los realiza. Es el puente entre
`definicion-funcional.md` (qué hace la aplicación) y los modelos (el contrato técnico de cada agregado). El
contrato exacto de campos está en `/v3/api-docs` y en cada modelo.

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
| 3 | `POST /api/auth/reset-password` `{token, newPassword}` ⚠ | Éxito ⚠. Contraseña corta: 422 y el token sigue siendo válido. Token inválido: 400 |

Un restablecimiento correcto marca el correo como verificado, invalida las demás sesiones del usuario y los
demás tokens, y **no inicia sesión**: hay que hacer login con la contraseña nueva.

### Reglas

- Sin correo verificado se puede iniciar sesión, listar y consultar, pero no crear eventos (UC-2).
- El tiempo de respuesta de `forgot-password` no depende de que la cuenta exista: todo el trabajo ocurre en
  segundo plano.
- En desarrollo local, `APP_AUTH_AUTOVERIFYEMAIL=true` verifica la cuenta al registrarla y no envía correo.
  La aplicación no arranca con el bypass activo si la URL pública no es `localhost`.

---

## UC-2 · Creación y convocatoria de un evento

**Actor:** anfitrión con sesión y correo verificado. **Resultado:** evento publicado e invitaciones enviadas.

### Flujo principal

| Paso | Llamada | Resultado |
|---|---|---|
| 1 | `POST /api/events` | 201, `Location: /api/events/{id}`, evento en `DRAFT` |
| 2 | Opcional, en `DRAFT`: `PUT /api/events/{id}`, `…/response-options`, `…/venue`, `…/online-access`, `…/response-deadline` | 200 con el evento actualizado |
| 3 | `POST /api/events/{id}/guests` por cada invitado | 201 con el invitado y su `invitationPath` (`/r/{token}`) |
| 4 | `POST /api/events/{id}/publish` | 200, evento en `PUBLISHED` |
| 5 | Convocar: ver abajo | |

**Cuerpo de la creación:** título, descripción, `startsAt`, `endsAt` y `timeZone`; `modality` (`IN_PERSON` u
`ONLINE`); `venue` si es presencial u `onlineAccess` si es en línea; y, opcionales, `responseOptions`,
`allowComment`, `allowResponseChange` y `responseDeadline`.

**Por defecto:** opciones "Asisto" (cuenta como asistencia) y "No asisto"; `allowComment=false`;
`allowResponseChange=true`.

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
| Correo sin verificar | 403 con `code=EMAIL_NOT_VERIFIED` |
| Fin no posterior al inicio, zona inválida, título vacío | 422 |
| Modalidad incoherente con `venue` u `onlineAccess` en la creación | 422 |
| Plazo posterior al inicio del evento | 422 |
| Menos de 2 o más de 5 opciones, ninguna que cuente como asistencia, etiquetas vacías o repetidas | 422 |
| Editar un evento que no está en `DRAFT` | 409 |
| `PUT …/venue` en un evento en línea, o `…/online-access` en uno presencial | 409 |
| Publicar un evento ya publicado | 409 |
| Publicar sin ubicación o acceso, o con menos de 2 opciones | 422 |
| Invitado repetido por correo o teléfono | 409 |
| Invitado sin correo ni teléfono, teléfono no normalizable | 422 |
| Evento ajeno o inexistente | 404 |
| Enviar o generar enlaces con el evento en `DRAFT`, `CANCELLED` o `FINISHED` | 409 |
| Lote vacío, con ids repetidos o por encima del máximo | 422 |
| Id de invitado que no pertenece al evento | 404 |

### Después de publicar

| Qué | Llamada | Reglas |
|---|---|---|
| Cambiar el plazo | `PUT …/response-deadline` | En `DRAFT`, y en `PUBLISHED` solo antes del inicio. Nunca posterior al inicio |
| Cambiar opciones | `PUT …/response-options` | Se pueden renombrar y añadir. No eliminar ni cambiar si cuenta como asistencia una opción con respuestas (409) |
| Cancelar | `POST …/cancel` `{reason?}` | Solo desde `PUBLISHED`; irreversible. Desde `DRAFT`, 409 |
| Editar invitados | `PUT` y `DELETE …/guests/{guestId}` | Eliminar uno que ya respondió exige confirmación explícita ⚠ y borra su respuesta |

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

---

## Resumen: método y caso de uso

| Método y ruta | UC |
|---|---|
| `POST /api/auth/register`, `verify-email`, `resend-verification`, `login`, `logout`, `forgot-password`, `reset-password` | 1 |
| `GET /api/auth/me` | 1 |
| `POST /api/events` | 2 |
| `PUT /api/events/{id}` y `…/response-options`, `…/venue`, `…/online-access`, `…/response-deadline` | 2 |
| `POST /api/events/{id}/publish` y `…/cancel` | 2 |
| `POST`, `PUT`, `DELETE`, `GET /api/events/{id}/guests[/{guestId}]` | 2, 4 |
| `POST /api/events/{id}/invitations/send`, `whatsapp-links`, `whatsapp-sent` | 2 |
| `GET /api/invitations/{token}`, `PUT …/response`, `GET …/calendar` | 3 |
| `GET /api/events`, `GET /api/events/{id}`, `GET /api/events/{id}/dashboard` | 4 |

Fuera de estos casos: `/actuator/health`, `/v3/api-docs` y `/swagger-ui.html`.
