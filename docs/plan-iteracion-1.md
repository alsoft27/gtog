# gtog — Plan, decisiones y deuda técnica

Estado a 19 de noviembre de 2026. Es el documento para retomar el proyecto: dónde estamos, qué queda, qué se
decidió y por qué, qué está pendiente de decidir y qué deuda hay. Qué hace la aplicación está en
`definicion-funcional.md`; el contrato técnico de cada agregado, en `modelo-evento.md`, `modelo-invitado.md` y
`modelo-usuario.md`. Las instrucciones para el agente están en `AGENTS.md`.

---

## 1. Dónde estamos

El backend del MVP está implementado de R1 a R11. **R12 (verificación de correo y recuperación de
contraseña) está aparcada**, por lo que RF-1.2 y RF-1.3 siguen sin cumplirse y R12 es un bloqueador de
despliegue público. El frontend Angular no se ha empezado.

`mvn clean verify`: 756 tests, 0 fallos, 0 errores, 0 omitidos en la última ejecución completa. Repetirlo desde
la terminal antes de dar por cerrado cualquier cambio. Stack: Java 25, Spring Boot 4.1, MongoDB Atlas,
Maven por defecto, springdoc-openapi, iCal4j 4.2.0, libphonenumber 9.0.43, Mailjet por SMTP.

### Rebanadas cerradas

| Rebanada | Qué quedó |
|---|---|
| Crear evento | `POST /api/events` en `DRAFT`, con validación completa y opciones por defecto |
| R1 | Detalle y listado del anfitrión autenticado |
| R2 | Opciones de respuesta (2 a 5) con identidad estable al editar |
| R3 | Ubicación presencial o acceso en línea, con regla de visibilidad del enlace |
| R4 | Editar, publicar y cancelar: máquina de estados con precondiciones |
| R5 | Registro, login, logout, sesión, CSRF y comprobación de propiedad en la capa de aplicación |
| R6 | Invitados dentro de `event`: alta, edición, borrado y listado, con teléfono E.164 y token de 32 bytes |
| R7 | Página pública del invitado y primera apertura atómica |
| R8 | Plazo editable, respuesta del invitado, confirmación real, opciones protegidas y cascada, calendario `.ics` |
| R9 | Panel de un evento y contadores en el listado |
| Cookie CSRF | Se emite en el primer `GET`; las invitaciones públicas quedan excluidas |
| Errores HTTP | 401 y 403 escritos como `ProblemDetail`, y advice MVC con detalle fijo y `instance` constante |
| R10 | Correo: reservas atómicas, cuota diaria, transporte Mailjet, endpoint de envío y cabecera `Content-Language: es` |
| `canRespond` | Disponibilidad de respuesta calculada con la misma política que el `PUT` |
| R11 | WhatsApp asistido: generar enlaces y confirmar el envío. Telegram aplazado |

### Endpoints

```
POST   /api/auth/register          POST   /api/auth/login          POST   /api/auth/logout

POST   /api/events                 GET    /api/events              GET    /api/events/{id}
PUT    /api/events/{id}            PUT    /api/events/{id}/response-options
PUT    /api/events/{id}/venue      PUT    /api/events/{id}/online-access
PUT    /api/events/{id}/response-deadline
POST   /api/events/{id}/publish    POST   /api/events/{id}/cancel
GET    /api/events/{id}/dashboard

POST   /api/events/{id}/guests     GET    /api/events/{id}/guests
PUT    /api/events/{id}/guests/{guestId}   DELETE /api/events/{id}/guests/{guestId}

POST   /api/events/{id}/invitations/send
POST   /api/events/{id}/invitations/whatsapp-links
POST   /api/events/{id}/invitations/whatsapp-sent

GET    /api/invitations/{token}    PUT    /api/invitations/{token}/response
GET    /api/invitations/{token}/calendar        (los tres públicos, sin sesión ni CSRF)
```

Además: `/actuator/health`, `/v3/api-docs` y `/swagger-ui.html`.

### Recorridos manuales

Archivos `.http` ejecutables con el cliente HTTP de IntelliJ, contra un servidor real.

- `rest-api.http`: recorrido completo automatizable, sin correo real.
- `rest-api-mail.http` y `rest-api-whatsapp.http`: manuales, con `{{realRecipient}}` y `{{realPhone}}` de
  `http-client.private.env.json`, que está ignorado por Git. No se ejecutan con "Run all": el paso de
  confirmación de WhatsApp debe lanzarse solo después de enviar el mensaje.

---

## 2. Lo que queda

### R12 — Verificación de correo y recuperación de contraseña

Aparcada. Desbloqueada por el proveedor de correo (R10). Decisiones ya tomadas, para no reabrirlas:

- **Puerta:** sin correo verificado no se puede crear un evento (`POST /api/events`). Sí se puede registrar,
  iniciar sesión, listar y consultar. La comprobación lee `emailVerified` de la base de datos en cada
  creación, no de la sesión, para que tras verificar el anfitrión pueda crear en la misma sesión. Rechazo:
  403 con `code=EMAIL_NOT_VERIFIED`.
- **Tokens:** 32 bytes de `SecureRandom` en base64url. Se guarda solo su hash SHA-256 en una colección
  `auth_tokens`, con índice único del hash y un único token activo por (usuario, propósito). Propósitos
  `EMAIL_VERIFICATION` y `PASSWORD_RESET`. TTL de Mongo sobre `expiresAt` más validación de la caducidad en
  código, porque Mongo borra con retraso. Consumo atómico de un solo uso. Caducidad configurable: 24 h la
  verificación, 1 h el restablecimiento.
- **Enlaces** con el token en el fragmento (`#token=...`), nunca en la query: no llega al servidor, a los logs
  de acceso ni al `Referer`. Correos en texto plano, con texto fijo y sin datos escritos por el usuario.
- **Olvidé mi contraseña:** responde siempre 202 con el mismo cuerpo y cabeceras, exista o no la cuenta. El
  envío es asíncrono para que el tiempo de respuesta no delate si existe. Límite por dirección de 1 emisión
  cada 5 minutos y 5 cada 24 horas, aplicado en silencio con la misma respuesta.
- **Reenviar la verificación:** endpoint autenticado, solo a la dirección de la propia cuenta, con el mismo
  límite.
- **Restablecer:** valida la contraseña nueva antes de consumir el token, de modo que una errata no lo gaste.
  Un restablecimiento correcto marca también el correo como verificado, invalida los demás tokens del usuario
  y sus sesiones abiertas. No hay inicio de sesión automático.
- **Token inválido, caducado o usado:** un único 400 genérico con `instance` constante.
- **Escrituras parciales sobre `users`:** ese documento no tiene control de versión y un `save()` pisaría los
  contadores de cuota de correo.
- **`GET /api/auth/me`** y `emailVerified` en las respuestas de registro y login, para que el frontend sepa
  quién es el usuario al recargar.
- **Rutas:** verify-email, forgot-password y reset-password son públicas (POST, con CSRF); resend-verification
  y me exigen sesión. Se añaden a SecurityConfig una a una, porque /api/auth/** no es público en bloque.
- **Bypass local:** `app.auth.auto-verify-email`, falso por defecto, verifica la cuenta al registrarla para
  que los recorridos `.http` sigan funcionando con direcciones `@example.com`. La aplicación se niega a
  arrancar si está activo y `app.public-base-url` no apunta a localhost.
- **Prerrequisito:** el login es manual en `AuthController`, así que ni cambia el identificador de sesión
  (fijación de sesión) ni registra la sesión en un `SessionRegistry`. Hay que comprobarlo y corregirlo antes de
  construir encima, porque la invalidación de sesiones tras restablecer depende de ello.
- Plan en tres commits: sesión del login manual, verificación con la puerta de creación, y recuperación.

### Aplazado

- **Telegram.** Sin endpoint. El enlace `t.me/share/url=` del diseño original está mal formado y la hipótesis
  de que abre un selector de chat y no un contacto concreto sigue sin verificar.
- **Frontend Angular.** No empezado. Notas acumuladas en §8.

---

## 3. Decisiones tomadas

### Producto

**El invitado no crea cuenta; el token es su credencial.** Cualquier fricción de registro baja la tasa de
respuesta. El coste es que la seguridad del enlace es toda la que hay, y por eso el token es aleatorio, largo
y nunca derivado de otros datos.

**WhatsApp es envío asistido, no automático.** Ninguna plataforma permite a terceros enviar mensajes sin una
cuenta de empresa verificada, plantillas aprobadas y coste por mensaje, ni expone la lista de contactos. Lo
que funciona es la agenda del teléfono y el enlace compartible. El mensaje sale de la cuenta del anfitrión:
por eso, a diferencia del correo, no tiene cuota ni reservas, y `SENT` es una declaración suya
(D-WHATSAPP-TRUST).

**El correo sí sale de la infraestructura de gtog**, así que lleva reservas atómicas por invitado (3 envíos,
1 hora entre envíos) y una cuota por anfitrión (100 cada 24 horas), configurables. Texto plano fijo en
español, un destinatario, sin Reply-To y con un pie que identifica al anfitrión. Nunca incluye la URL de la
sala. `SENT` significa aceptado por el proveedor, no entregado.

**Las opciones de respuesta las define el anfitrión.** Convierte un enum fijo en datos. A cambio, el modelo
conserva la identidad de cada opción al renombrarla, porque las respuestas la referencian.

**Un evento es presencial o en línea, nunca ambos.** El híbrido obliga a que la respuesta indique el canal de
asistencia, lo que acopla ubicación y opciones.

**La fecha la fija el anfitrión.** La votación entre fechas es una iteración posterior.

**El plazo de respuesta aplica a toda escritura** y se interpreta en la zona del evento. El anfitrión lo
cambia en DRAFT o en PUBLISHED antes del inicio, y nunca puede ser posterior al inicio, de modo que no se
puede reabrir un evento terminado ni volver a mostrar la sala de uno cerrado. Se descartó permitir cambiarlo
después del evento: la idea era alimentar el muro social, y el muro depende de estar invitado, no de la
asistencia.

**El acceso al muro social depende de estar invitado**, no de haber respondido ni confirmado. La confirmación
solo gobierna la visibilidad del enlace de la sala en `ON_CONFIRMATION` y exige una opción con
`countsAsAttendance = true`.

**El correo que el invitado deja al responder se mantiene, opcional y sin uso.** Nunca se muestra al
anfitrión ni a otros invitados. Cualquier uso futuro exige aprobación expresa y confirmar la dirección antes
de escribirle, porque la operación pública acepta cualquier dirección sin que su dueño lo sepa.

**Una opción con respuestas no puede eliminarse ni cambiar su `countsAsAttendance`** en un evento publicado:
cambiaría retroactivamente quién está confirmado y quién ve el enlace.

**Sin plantilla de mensaje editable** en el MVP. Un motor de plantillas es un cambio aparte.

### Arquitectura y datos

**Hexagonal.** El dominio no lleva anotaciones de framework, y el modelo de dominio y el documento de
persistencia son clases distintas con un mapper en medio. Es la concesión que casi todo el mundo hace para
ahorrarse el mapeo y la que vacía la arquitectura. Un hexágono por contexto: `event` (que incluye a los
invitados, porque no tienen vida independiente del evento y comparten agregado y documento) y `user`.

**La autorización de recursos vive en la capa de aplicación**, no en anotaciones del controlador. Es una
regla de negocio y debe poder probarse sin Spring. Un evento ajeno da 404, no 403.

**MongoDB con colecciones separadas por patrón de escritura**, no por entidad: `users`; `events`, con
opciones e invitados embebidos; `responses`, un documento por respuesta de invitado, que es la escritura
concurrente que justificó separarlas; las escrituras concurrentes sobre `events` (aperturas, reservas de
envío, confirmaciones) son parciales y atómicas. `wall_posts` en el futuro, nunca embebido porque crece sin límite.

**Las respuestas van fuera del evento.** Embebidas, veinte invitados contestando a la vez se pisarían.

**`ANSWERED` no se almacena, se deriva** de la existencia de la respuesta. Responder solo escribe en
`responses`, sin transacciones multidocumento. Los contadores del panel parten de `guests[]` y buscan su
respuesta, nunca cuentan documentos de `responses`: una respuesta huérfana no infla los totales.

**La unicidad se impone con índices, no con código.** Un invitado por token, una respuesta por invitado y
evento, un correo por usuario. Las comprobaciones previas fallan bajo concurrencia.

**Las escrituras sobre un invitado son parciales y atómicas**, con `$elemMatch`, condiciones dentro del filtro
e incremento de `version`, nunca un `save()` del evento completo. Mismo criterio para `users`. Así una
apertura concurrente no bloquea al anfitrión, y un `save()` con una versión anterior falla con 409.

**La cascada del invitado retira primero al invitado y después su respuesta.** Sin transacción: si falla la
limpieza queda una respuesta inalcanzable, nunca una respuesta perdida con su invitado vivo.

**Las fechas se guardan como hora local más zona**, y el instante se calcula al leer. Si cambian las reglas
horarias de un país, un instante almacenado dejaría de corresponder a la hora prevista.

**Sin herramienta de migraciones.** Mongock no tiene compatibilidad confirmada con Spring Boot 4. Los índices
se crean en el arranque con un componente idempotente.

### Seguridad y errores

**Sesión de servidor con cookie `HttpOnly`, no JWT.** El único cliente es Angular. Una cookie `HttpOnly`
elimina la exfiltración por XSS y es más difícil de hacer mal que un JWT en el navegador. Reversible cuando
exista la app móvil.

**CSRF.** Login y registro lo conservan. Solo `/api/invitations/**` se excluye, porque su credencial es el
token de la ruta y no una cookie que el navegador envíe solo. La cookie `XSRF-TOKEN` se emite en el primer
`GET` y se compara el valor crudo con la cabecera. Un rechazo CSRF es 403.

**Rutas públicas:** `/api/invitations/**`, `POST /api/auth/register`, `POST /api/auth/login` y
`/actuator/health`. Swagger y OpenAPI también lo son (D-SWAGGER-PUBLIC). El matcher real de Actuator permite
`/actuator/**`, no solo health: cualquier otro endpoint que se exponga bajo esa ruta tampoco exigirá sesión.
Todo lo demás exige sesión. Las escrituras públicas siguen sujetas a CSRF salvo `/api/invitations/**`.

**El enlace de la sala se filtra en el servidor.** Si viajara en la respuesta confiando en que Angular no lo
pinte, sería legible desde las herramientas del navegador. Solo se devuelve con el evento publicado y
cumpliéndose la regla.

**Los tokens nunca aparecen en logs, errores ni cuerpos ajenos.** Las respuestas con credenciales llevan
`Cache-Control: no-store` exacto, y los tests lo comparan con el valor completo para no pasar con las
cabeceras anti-caché que Spring Security añade por defecto.

**Errores HTTP.** Los handlers de seguridad escriben 401 y 403 como `ProblemDetail` sin `sendError`. Un error
resuelto con `sendError` despacha a `/error`, donde un anónimo termina en 401. El advice MVC extiende
`ResponseEntityExceptionHandler` y fija un detalle por estado, `instance` constante (`/api/invitations` o
`/api/errors`) y `no-store`, sin datos de la petición. Un fallback `Exception → 500` registra solo la clase y
un identificador de correlación. No se aplica `dispatcherTypeMatchers(ERROR).permitAll()`: el cuerpo por
defecto de Boot incluye la ruta pedida, y en las invitaciones esa ruta lleva el token.

### Convenciones

Código, rutas e identificadores en inglés; documentación y comentarios en español. Jakarta valida formato; las
reglas de negocio se validan en el dominio y salen como 422. Sin Lombok. Tests de integración contra una base
de datos real, nunca embebida. Se trabaja por rebanada vertical, directamente sobre `main` hasta tener la
primera versión completa del backend; después se vuelve a ramas `feat/` y `fix/`.

---

## 4. Decisiones pendientes

| Decisión | Bloquea | Notas |
|---|---|---|
| Retomar R12 | Despliegue público | Aparcada; decisiones ya tomadas en §2 |
| Volver a ramas `feat/` y `fix/` | Nada | Se acordó trabajar en `main` hasta cerrar el backend; con el MVP cerrado, decidir si se vuelve a ramas ya, antes de R12 y del frontend |
| RF-3.9: notificar un cambio de ubicación tras publicar | Nada | Sacarlo del alcance, o abrir una excepción para editar la ubicación de un evento publicado |
| Causa del emoji roto en WhatsApp | Nada | Medir con `searchParams.get("text")`; ver §5 |
| Remitente `gtog@gtog.es`: buzón con alguien detrás, o `no-reply@` | Nada | El cuerpo dice que no admite respuestas |
| `FINISHED`: automático por fecha o manual | Nada | D-FINISHED |
| Eliminar un borrador | Nada | D-DELETE-DRAFT |
| Telegram | Nada | Verificar primero su enlace |
| Uso futuro del correo de la respuesta | Nada | Aprobación expresa y doble confirmación |

---

## 5. Observaciones abiertas

**Emoji en WhatsApp.** En una prueba manual, el emoji del título llegó como un carácter de reemplazo mientras
las tildes y el guion largo llegaron bien. El mismo título se vio correcto en el asunto y el cuerpo del
correo. Causa sin determinar: la codificación de la URL de `wa.me` en nuestro código, o cómo WhatsApp
interpreta el parámetro. Medir `new URL(url).searchParams.get("text")` sobre la URL final y compararlo con
`message`. Si el defecto es nuestro, añadir un test sobre la URL final con 🎉, 👨‍👩‍👧‍👦, 🇪🇸 y 👍🏽. La
prueba manual se rehízo sin emoji en el título.

**Límite de longitud de `wa.me`.** La cota de 2000 caracteres sobre la URL final es conservadora y no está
verificada contra WhatsApp. Un emoji ocupa unos 12 caracteres al codificarse, así que un título con muchos
puede disparar `MESSAGE_TOO_LONG` para todo el evento.

---

## 6. Deuda técnica abierta

| # | Deuda | Consecuencia |
|---|---|---|
| D-BOUNCES | No se procesan rebotes ni quejas del proveedor | `SENT` es aceptado, no entregado; direcciones inválidas dañan la reputación del dominio |
| D-REGISTER-RATE-LIMIT | Registro y login sin límite de peticiones | Abuso de creación de cuentas; las cuotas de correo no lo sustituyen |
| D-RATE-LIMIT | Rutas públicas de invitación sin límite | La entropía del token impide adivinarlo, no el tráfico repetido sobre un enlace conocido |
| D-REFERRER | Política de `Referer` del frontend | La página del invitado lleva el token en la URL: debe servirse con `Referrer-Policy: no-referrer` |
| D-SWAGGER-PUBLIC | OpenAPI y Swagger UI sin autenticación | Decidir si protegerlos antes de producción |
| D-SECURE-COOKIE | `JSESSIONID` sin `Secure` en desarrollo | Verificar que Boot lo activa detrás de HTTPS |
| D-WHATSAPP-TRUST | `SENT` por WhatsApp es declarativo | Límite aceptado; mostrarlo en el frontend |
| D-EMAIL-NOT-SENT | La cita al responder no se envía | Sin rebanada aprobada; el correo de la respuesta no se usa |
| D-ORPHAN-OPTION | Un invitado puede responder con una opción mientras el anfitrión la elimina | Lectura defensiva: no confirma asistencia; sin transacciones |
| D-LIST-SCALE | El listado calcula contadores en memoria, sin paginar | Una sola consulta de respuestas evita N+1 pero no limita el volumen |
| D-FINISHED | Ningún mecanismo lleva un evento a `FINISHED` | La regla "no responder tras el fin del evento" cubre el hueco |
| D-DELETE-DRAFT | No se puede eliminar un borrador | Tampoco cancelarlo |
| D-DUP-KEY | `users` detecta el email duplicado por el texto del error de Mongo | Frágil si hay más índices únicos |
| D-5 | Tests de integración contra Atlas | Necesitan red y son más lentos; aceptable con un solo desarrollador |
| D-6 | Sin herramienta de migraciones | Sin cambio de esquema versionado hasta que haya datos reales |
| D-UNVERIFIED-SQUATTING | (R12) Registrarse con el correo de otra persona deja esa dirección ocupada | Purga de cuentas sin verificar |

**Saldadas:** D-1 y D-2 (seguridad y `hostId`, R5), D-3 (índice de `guests.token`, R6), D-4 (indicador de
salud de Mongo), D-7 (opciones en eventos publicados, R8), D-CONFIRMED-LINK (R8) y D-ERROR-FORMAT.

### Límites conocidos, no deuda

- **Responder mientras se envía:** hay una ventana entre la última lectura y el SMTP. Sin transacciones entre
  Mongo y el proveedor, no se puede garantizar exclusión absoluta. Solo se prueba con un spy.
- **SMTP aceptado y fallo al registrar `SENT`:** se informa `SENT` con `recorded=false`, no se reenvía y el
  panel puede mostrar `NOT_SENT`. Un reenvío posterior puede duplicar el correo, acotado por el tope de 3.
- **Compensación fallida al llegar al máximo de 3:** esperar el intervalo no libera el tope. Requiere
  intervención manual; el log `EMAIL_SEND_COMPENSATION_FAILED` la delata. Sin recuperación automática.
- **Errores que genera Tomcat antes de llegar a Spring** siguen viéndose como 401 para un anónimo.
- **Logs en DEBUG:** con `org.springframework.web` en DEBUG o `spring.mvc.log-resolved-exception`, la ruta de
  invitación con su token puede aparecer en los logs del framework.
- **Invalidación de sesiones:** válida solo con una instancia y sesiones en memoria.
- **Lote cancelado a mitad:** la confirmación de WhatsApp devuelve 409 conservando las marcas previas. Es
  aceptable porque la operación es idempotente.

---

## 7. Bloqueadores de despliegue público

- R12: verificación del correo del anfitrión.
- D-REGISTER-RATE-LIMIT y D-RATE-LIMIT: límites de peticiones.
- D-BOUNCES: procesar rebotes y quejas.
- D-SWAGGER-PUBLIC y D-SECURE-COOKIE.
- D-REFERRER del frontend.
- Dominio y remitente de Mailjet validados, con SPF, DKIM y DMARC en `PASS`, comprobados con un envío real.

## 8. Notas para el frontend

- La página del invitado se diseña primero para móvil y se sirve con `Referrer-Policy: no-referrer`.
- `canRespond` y `canChangeResponse` vienen calculados: no reimplementar el plazo, el fin del evento ni el
  estado.
- El invitado no ve nunca qué opciones cuentan como asistencia.
- Mostrar `SENT` como "enviada" o "marcada como enviada", nunca como entregada. En el panel, un invitado puede
  ser `OPENED` con canal `WHATSAPP`.
- WhatsApp: abrir los enlaces de uno en uno tras un gesto del usuario, porque los navegadores bloquean varias
  pestañas a la vez; confirmar tras cada envío; mostrar `message` como alternativa de copia. Si aparece
  `MESSAGE_TOO_LONG`, sugerir acortar el título.
- Dev: usar un proxy de desarrollo para que la API sea del mismo origen, porque Angular no añade la cabecera
  `X-XSRF-TOKEN` a URLs absolutas hacia otro origen.
- El correo opcional de la respuesta debe describir para qué se guarda: "Opcional. Se guarda con tu
  respuesta; el organizador no lo ve."
- Los conjuntos predefinidos de opciones son listas del frontend que el cliente envía al crear el evento.

---

## 9. Qué significa "terminada" una rebanada

1. `mvn clean verify` en verde, ejecutado desde la terminal.
2. Test unitario de dominio por cada regla de negocio nueva.
3. Test de integración del endpoint, con al menos un caso de error. Con servidor real, sin `csrf()` de
   MockMvc, cuando importa el despacho de errores o la cookie.
4. Sin anotaciones de framework en `domain/`.
5. Documentada en OpenAPI, con anotaciones solo en `infrastructure/in/web`.
6. Cada afirmación de seguridad o concurrencia tiene su test y se comprueba que el test falla al quitar
   temporalmente la condición que protege. Los tests de concurrencia usan hilos reales.
7. Evidencia con fichero y línea en el informe de cierre.
8. Documentación actualizada y commit convencional en inglés.
