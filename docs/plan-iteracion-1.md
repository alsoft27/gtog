# gtog — Plan de la iteración 1

Estado a 19 de noviembre de 2026. R11 cerrada, solo WhatsApp: generación sin escrituras y confirmación idempotente implementadas. Telegram queda fuera de R11. Siguiente: R12.

---

## 1. Dónde estamos

**Cerrado**

| Rebanada | Estado |
|---|---|
| Crear evento en `DRAFT` | `POST /api/events` funcionando de punta a punta, con dominio, puertos, adaptador Mongo, controlador y tests |
| R1 — Consultar y listar eventos | `GET /api/events/{id}` (`EventResponse` completo, `EventNotFoundException` → 404) y `GET /api/events` (proyección `EventSummaryResponse`, sin paginación; desde R5 el anfitrión se obtiene de la sesión, sin parámetro `hostId`) |
| R2 — Opciones de respuesta | `ResponseOption` embebido en `Event`, 2 a 5, defaults ("Asisto"/"No asisto") si no se especifican, `PUT /api/events/{id}/response-options` con identidad por `id` al editar (se conserva si coincide, se rechaza con 422 si no pertenece al evento) |
| R3 — Ubicación y acceso en línea | `Venue` y `OnlineAccess` (el backend no llama a la API de Google), `LinkVisibility`, invariante de modalidad (`IN_PERSON`⇄`venue`, `ONLINE`⇄`onlineAccess`), `PUT /api/events/{id}/venue` y `PUT /api/events/{id}/online-access`, `Event.visibleOnlineAccess(guestHasConfirmed, now)` ya implementado (aplicado desde R7 al endpoint público, con acceso solo en PUBLISHED) |
| R4 — Editar, publicar y cancelar | Máquina de estados: `publish()` solo desde `DRAFT` (precondiciones: venue/onlineAccess según modalidad, ≥2 opciones → `IncompleteEventForPublishException` 422), `cancel(reason, now)` solo desde `PUBLISHED` → `CANCELLED` con `cancelledAt` (irreversible). `edit()` con todos los campos básicos más `allowComment`/`allowResponseChange`/`responseDeadline` (sacados de `replaceResponseOptions`): si la modalidad cambia, el bloque de ubicación correspondiente es obligatorio. `PUT /api/events/{id}`, `POST /api/events/{id}/publish`, `POST /api/events/{id}/cancel`. Cobertura de eventos e invitados: 114 tests de dominio y 78 de integración, todos en verde. |
| R5 — Usuario y seguridad | Registro, login, logout, Spring Security. `POST /api/auth/register` y `POST /api/auth/login`. Ownership check en todos los endpoints de evento: `hostId != event.hostId` → 404. `GET /api/events` lee el host del principal autenticado; `hostId` desaparece como parámetro de query. `/api/invitations/**` público. 174 tests en verde antes de R6, incluida la validación de zona horaria del usuario y su cobertura HTTP. |
| R6 — Invitados | `Guest` dentro de `event`, `guests[]` embebido, alta/edición/borrado/listado autenticados con ownership en aplicación. Normalización E.164 con libphonenumber 9.0.43; tokens de 32 bytes de `SecureRandom`, Base64 URL-safe sin relleno. Escrituras parciales con control e incremento de versión. `invitationPath` solo en alta y listado. D-3 saldada; cascada preparada para R8 sin crear `responses`. 71 tests nuevos. |
| R7 — Página pública del invitado | `GET /api/invitations/{token}`, sin sesión ni CSRF; DTO público construido campo a campo, acceso online filtrado y Clock inyectado. DRAFT y desconocido devuelven el mismo 404; PUBLISHED, CANCELLED y FINISHED devuelven 200. Primera apertura atómica con $elemMatch y version +1; las revisitas OPENED no escriben. Cache-Control exacto `no-store` en éxito y 404. 31 tests nuevos. |
| Preparación de R8 — Plazo editable (commit 0) | `PUT /api/events/{id}/response-deadline`, autenticado y con CSRF. Permite cambiar o quitar el plazo en DRAFT, y en PUBLISHED solo antes del inicio. Escritura parcial atómica con incremento de versión, compatible con aperturas concurrentes. 29 tests nuevos. |
| R8 — Registrar respuesta (commit 1) | `PUT /api/invitations/{token}/response`, público sin sesión ni CSRF. Colección `responses`, insert o upsert atómico según permiso de cambios. GET y PUT incluyen `myResponse` sin email y `canChangeResponse`. 54 tests nuevos. |
| R8 — Confirmación y enlace (commit 2) | D-CONFIRMED-LINK saldada: ON_CONFIRMATION usa la respuesta propia y countsAsAttendance. Opción inexistente o ausencia de respuesta equivale a no confirmado. GET y PUT comparten el filtro. 7 tests nuevos de integración a través del GET. |
| R8 — Opciones publicadas y cascada (commit 3) | D-7 saldada: renombrar/agregar en PUBLISHED, sin eliminar ni cambiar countsAsAttendance de opciones con respuestas. Puerto de ids usados y cascada real de R6: primero retirar invitado y después limpiar respuesta. D-ORPHAN-OPTION documentada sin transacciones. 35 tests nuevos. |
| R8 — Calendario (commit 4) | `GET /api/invitations/{token}/calendar`, público, text/calendar UTF-8, attachment y no-store exacto. iCal4j 4.2.0, fechas locales con VTIMEZONE, UID del evento y URL filtrada como en JSON. CANCELLED → 409; FINISHED → 200; DRAFT y desconocido → mismo 404. 25 tests nuevos. R8 cerrada. |
| R9 — Panel de un evento (commit 1) | `GET /api/events/{id}/dashboard`, autenticado y con propiedad comprobada en aplicación. Dos lecturas unidas en aplicación, sin $lookup; cálculo puro EventAttendanceSummary basado en guests[]. Huérfanos ignorados, optionKnown trivalente y email de respuesta excluido. No escribe; solo su 200 fija no-store exacto. 27 tests nuevos. |
| R9 — Contadores del listado (commit 2) | `EventSummaryResponse` añade totalGuests, answered, pending y attending, sin byOption. Mismo cálculo que el panel, con una única consulta de respuestas para todos los eventos del anfitrión, sin consultas por evento. Lectura sin cambios en documentos, version ni caché. 11 tests nuevos. R9 cerrada; siguiente rebanada: R10. |
| CSRF — Cookie inicial | El primer GET del anfitrión consume DeferredCsrfToken y emite XSRF-TOKEN. Las invitaciones públicas quedan excluidas; se conserva el valor crudo en la cabecera, sin renovación en login y con expiración en logout. 10 tests nuevos. |
| Errores — Seguridad (commit 1) | AccessDeniedHandler y AuthenticationEntryPoint propios escriben directamente 403 y 401 ProblemDetail, sin sendError, con instance constante y no-store exacto. Los rechazos CSRF anónimos ya no acaban en 401. 3 tests nuevos respecto a la corrección de cookie inicial. |
| Errores — MVC (commit 2) | Advice basado en ResponseEntityExceptionHandler de Spring 7.0.8, normalización de cuerpos y estados, conservación de Allow y fallback 500 de menor prioridad. Pruebas HTTP con servidor real: privacidad del cuerpo y del log, errores 400/404/405/406/415/500/503. 18 tests nuevos. D-ERROR-FORMAT saldada. |
| R10 — Reservas de correo (commit 1) | Reservas atómicas del invitado y del anfitrión, límites configurables y compensaciones condicionadas. Reinicio al cambiar el correo y coexistencia con OPENED. 52 tests nuevos; sin envío SMTP ni endpoint todavía. |
| R10 — Transporte de correo (commit 2) | EmailSenderPort, mensaje fijo en español y adaptador SMTP Mailjet con el starter existente. TLS obligatorio, timeouts, activación con configuración y adaptador capturador exclusivo de tests. Indicador de salud desactivado y comprobado con el informe de condiciones de Boot 4.1. 29 tests nuevos; endpoint pendiente. |
| R10 — Endpoint de correo (commit 3) | POST /api/events/{id}/invitations/send: propiedad, CSRF, lote síncrono, resultados individuales y dailyLimit final; no-store exacto. Corte tras tres fallos de proveedor o cuota agotada, compensaciones y SENT/recorded=false explícito. Clasificación SMTP conservadora, sin confundir remitente y destinatario. 44 tests nuevos (10 de dominio, 26 con servidor real y 8 de clasificación SMTP), siempre con correo falso. R10 cerrada; siguiente: R11. |
| Invitación — Disponibilidad de respuesta | InvitationResponse añade canRespond, calculado con ResponseAcceptancePolicy compartida con PUT. Ventana PUBLISHED, plazo inclusive, fin exclusivo y permiso de cambio si ya existe respuesta. canChangeResponse conserva su significado. Sin consultas previas nuevas ni cambios en insert/upsert. 17 tests nuevos: 4 de dominio y 13 de coherencia GET/PUT con Clock fijo. |
| R11 — Generación WhatsApp (commit 1) | POST /api/events/{id}/invitations/whatsapp-links: autenticado, CSRF, propiedad y solo PUBLISHED. READY con url/message o SKIPPED por invitado. Una lectura de respuestas por lote, ninguna escritura ni llamada a reservas/cuotas. Máximo independiente 100, UTF-8 con %20/%0A, recorte de título/lugar a 100 grafemas y URL máxima conservadora de 2000 caracteres. 67 tests nuevos; confirmación pendiente. |
| R11 — Confirmación WhatsApp (commit 2) | POST /api/events/{id}/invitations/whatsapp-sent: cuerpo/lista obligatorios, seguridad y máximo compartidos con generación. Dos actualizaciones con $elemMatch y teléfono en el filtro, incremento de version y Clock inyectado. Completa envío en OPENED sin rebajarlo; preserva primer canal/fecha. Idempotencia, concurrencia, retirada y 409 a mitad de lote probados con servidor real. Archivo manual independiente rest-api-whatsapp.http. R11 cerrada. |
| Infraestructura | Java 25, Spring Boot 4.1, Maven por defecto, MongoDB Atlas, OpenAPI |

**Verificación actual:** `mvn clean verify` pasa con **756 tests**, 0 fallos, 0 errores y 0 omitidos. R11, commit 1, añade 67 sobre los 639 anteriores; la corrección posterior de nombres añade 5 (4 de composición y 1 HTTP), y el commit 2 añade 45 (7 de dominio, 4 de aplicación y 34 HTTP). Los tests del correo se mantienen sin modificar y siguen en verde. Ninguna prueba abre WhatsApp ni envía correo o mensajes reales.

**Lo que eso valida:** la arquitectura hexagonal funciona en la práctica, el mapeo entre `Event` y `EventDocument` es asumible, y el circuito completo de desarrollo está operativo. Además, `Event.create(...)`/`reconstitute(...)` cambiaron de firma en las tres rebanadas seguidas, así que desde R3 `Event` se construye con dos builders internos (`Event.builder()` para eventos nuevos, `Event.reconstituteBuilder()` para rehidratar) en vez de factory methods con parámetros posicionales — ver `docs/modelo-evento.md`.

---

## 2. Deuda técnica abierta

Esto no es una lista de pendientes cualquiera: son cosas que ya están mal a propósito y que hay que devolver a su sitio. Escritas para que ninguna se pierda.

| # | Deuda | Consecuencia si se olvida | Cuándo se salda |
|---|---|---|---|
| D-1 | **Spring Security retirado** | Todos los endpoints son públicos | Rebanada 5, ver §4 — **Saldada en R5.** |
| D-2 | **`hostId` viaja en el cuerpo del request** | Cualquiera crea eventos en nombre de otro | Con D-1 — **Saldada en R5.** |
| D-3 | **Índice único de `guests.token`** | Creado de forma idempotente en `MongoIndexInitializer`, con filtro parcial para tokens de tipo string. Permite eventos sin invitados. La unicidad dentro del mismo array se asegura en `Event` y con la escritura condicionada por versión. | **Saldada en R6.** |
| D-5 | **Tests de integración contra Atlas** | Los tests necesitan red y son lentos | Aceptable mientras seas el único desarrollador |
| D-6 | **Sin herramienta de migraciones** | No hay forma versionada de cambiar el esquema | Cuando haya datos reales |
| D-7 | **Opciones de respuesta bloqueadas en eventos publicados** | PUBLISHED permite renombrar y agregar; rechaza eliminar una opción con respuestas o cambiar su countsAsAttendance con dos excepciones de dominio propias (409). La aplicación consulta ids distintos del evento mediante un puerto y los pasa al dominio. Las demás validaciones permanecen. | **Saldada en R8, commit 3.** |
| D-ORPHAN-OPTION | **Carrera entre responder y eliminar una opción** | Un invitado puede responder con X después de consultar los ids usados y antes de guardar la lista que la elimina. No se añaden transacciones. La lectura tolera la opción inexistente: conserva myResponse, pero no confirma asistencia ni muestra ON_CONFIRMATION. | Pendiente de planificación |
| D-LIST-SCALE | **Listado sin paginar con cálculo en memoria** | Desde el commit 2 de R9, el listado calcula contadores sobre todos los eventos del anfitrión y sus respuestas en memoria, con dos lecturas y sin paginación. Se acepta esta limitación; una sola consulta de respuestas evita N+1, pero no limita el volumen cargado. | Antes de necesitar paginación o grandes volúmenes |
| D-CONFIRMED-LINK | **Confirmación de asistencia provisional en R7** | `InvitationViewFactory.guestHasConfirmed(...)` usa la respuesta propia: solo confirma si la opción todavía existe y tiene `countsAsAttendance = true`. Sin respuesta, opción inexistente o "No asisto", false. El filtro se comparte en GET y PUT sin consultas adicionales. | **Saldada en R8, commit 2.** |
| D-EMAIL-NOT-SENT | **Cita al responder pendiente** | **Saldada para invitaciones en R10.** La cita al responder sigue fuera de R10: el email de la respuesta solo se almacena, no se usa para ningún envío; cualquier uso futuro requiere aprobación expresa y doble confirmación de su dueño. | Cita: sin rebanada aprobada |
| D-BOUNCES | **Sin procesamiento de rebotes ni quejas del proveedor** | SENT significa aceptado por SMTP, no entregado. No se procesan rebotes ni quejas; las direcciones inválidas pueden afectar a la reputación del dominio. | Antes del despliegue público |
| D-WHATSAPP-TRUST | **SENT por WhatsApp es declarativo** | El anfitrión afirma haber enviado el mensaje desde su propia cuenta. gtog no puede verificar el envío ni la entrega. Generar enlaces no marca nada; whatsapp-sent registra la declaración desde el commit 2 de R11. | Límite aceptado; mostrarlo en el frontend |
| D-REGISTER-RATE-LIMIT | **Registro y login sin límite de peticiones** | Limitar abuso de creación de cuentas e intentos de autenticación antes del despliegue público. Las cuotas de correo no sustituyen esta protección. | Antes del despliegue público |
| D-RATE-LIMIT | **Endpoint público sin límite de peticiones** | La entropía del token hace inviable adivinarlo, pero no protege frente a abuso o tráfico repetido sobre un enlace conocido. No hay rate limiting en R7. | Pendiente de planificación |
| D-REFERRER | **Política de Referer del frontend** | La página del invitado lleva la credencial en la URL. Para evitar filtrarla al cargar un mapa de Google u otros recursos externos, el frontend debe servir la página con `Referrer-Policy: no-referrer`. La cabecera de la API no sustituye la política del documento del frontend. | Frontend, al integrar la página del invitado |
| D-FINISHED | **`FINISHED` sin implementar** | No existe ningún mecanismo para que un evento pase a `FINISHED`. Ni job programado ni derivación al vuelo. | A definir: requiere decidir si es automático (por fecha) o manual (anfitrión lo marca) |
| D-DELETE-DRAFT | **Borrar un `DRAFT` sin implementar** | Un borrador no se puede cancelar (`cancel()` solo desde `PUBLISHED`). No existe `DELETE /api/events/{id}`. | Rebanada posterior |
| D-SWAGGER-PUBLIC | **OpenAPI y Swagger UI sin autenticación** | Cualquiera puede ver la documentación completa de la API sin autenticarse. Hoy es conveniente para desarrollo; antes de producción hay que decidir si protegerlo o dejarlo público. | Antes del despliegue a producción |
| D-SECURE-COOKIE | **Cookie de sesión sin `Secure` en desarrollo** | La cookie `JSESSIONID` se envía también por HTTP. En producción detrás de HTTPS con `server.ssl.*` o proxy con `server.forward-headers-strategy=native`, Spring Boot activa el flag automáticamente. Verificar antes del primer despliegue. | Antes del despliegue a producción |
| D-ERROR-FORMAT | **Errores de seguridad y MVC sin formato uniforme** | Los handlers de seguridad escriben 401/403 directamente como ProblemDetail. El advice MVC normaliza los errores heredados de ResponseEntityExceptionHandler y el fallback 500, con detalles fijos, instance constante, no-store y sin datos de la petición. El 405 conserva Allow. No se permite el despacho ERROR de forma general. | **Saldada en la corrección de errores, commits 1 y 2.** |
| D-DUP-KEY | **`DuplicateKeyException` mapeada por texto, no por código de error en users** | `UserRepositoryAdapter` sigue inspeccionando `e.getMessage().contains("email")`. En `responses` se evita desde R8: código 11000 y `keyPattern` estructurado distinguen el índice compuesto de cualquier otro duplicado, sin conservar mensaje ni causa. La inserción usa el comando Mongo porque el driver descarta keyPattern en insertOne; el upsert conserva los metadatos del error de comando. Los otros duplicados devuelven 500 saneado, nunca el 409 de respuesta existente. | Pendiente solo en users |

**Errores HTTP: decisiones y límite conocido.** Un sendError de Spring MVC o del filtro CSRF provocaba un
despacho a /error, donde un anónimo terminaba en 401. La seguridad escribe ahora ProblemDetail directamente;
MvcExceptionHandler extiende ResponseEntityExceptionHandler y sobrescribe handleExceptionInternal con la
firma comprobada en las fuentes locales de Spring 7.0.8. Construye un cuerpo nuevo, descarta propiedades de
la excepción y fija detalle por estado, instance /api/invitations o /api/errors y Cache-Control: no-store.
HttpMessageNotReadableException conserva su 400 con "Invalid request body" mediante override, sin handler
duplicado. El advice de dominio tiene prioridad alta; el MVC y su fallback Exception → 500 tienen la menor.
El fallback solo registra clase de excepción e identificador generado, nunca mensaje, causa, ruta o stack
trace; devuelve ese identificador en X-Correlation-ID. La cobertura usa servidor embebido real, sin csrf()
de MockMvc, e incluye respuesta 500 forzada con MockitoSpyBean y ausencia de token/ruta en cuerpo y log.

**Límite conocido:** los errores que genera Tomcat antes de llegar a Spring siguen mostrándose como 401 a
un anónimo. Quedan fuera de esta corrección. No se aplica dispatcherTypeMatchers(ERROR).permitAll(): el cuerpo
por defecto de Boot podría incluir path con el token de la invitación. El filtro de emisión de cookie CSRF
y la comparación del valor crudo no se han cambiado.

**Límite conocido de logs:** si se sube a DEBUG el nivel de `org.springframework.web` o se activa
`spring.mvc.log-resolved-exception`, la ruta de invitación con su token puede aparecer en los logs del
framework. Las pruebas de privacidad usan la configuración normal y comprueban la salida capturada
completa con OutputCaptureExtension, sin filtrar por logger, para los errores de las rutas públicas.

**Medición del fallback:** desactivando temporalmente solo el handler de último recurso para Exception,
el test HTTP real de RuntimeException en una ruta pública devolvió **401**. La medición se ejecutó aislada;
el handler y la aserción de 500 se restauraron después. No se versiona la alteración temporal.

**D-4, saldada.** Decía "health indicator de Mongo desactivado", y no era así: estaba activo y en `DOWN`. El indicador por defecto de Actuator recorre **todas** las bases que el `MongoClient` ve vía `listDatabaseNames()` — no solo `local`, también `admin`, `config`, la de la propia app — y ejecuta `hello` en cada una; en Atlas el usuario de la aplicación no tiene permiso sobre `local`, así que el chequeo entero caía con `DOWN` aunque `gtog_dev`/`gtog_test` respondieran sin problema. Comprobado arrancando la app. Sustituido por `MongoDatabaseHealthIndicator` (en `shared/config`), que hace `ping` solo contra la base configurada de la aplicación; el indicador por defecto se desactiva con `management.health.mongodb.enabled=false` en ambos `application*.properties` para que no convivan. Verificado de nuevo: `GET /actuator/health` → `"mongo":{"details":{"database":"gtog_dev","ping":1},"status":"UP"}`.

---

## 3. Las rebanadas que faltan

Cada una es vertical: dominio, puerto, adaptador, controlador y test, funcionando de punta a punta.

### Bloque A — Completar el evento

**R4. Editar, publicar y cancelar** ✓ Completada.

### Bloque B — Identidad del anfitrión

**R5. Usuario y seguridad** ✓ Completada. Ver §1.

### Bloque C — El núcleo del producto

**R6. Invitados** ✓ Implementada. Ver §1 y `docs/modelo-invitado.md`.

Los cuatro endpoints permiten gestionar contactos en `DRAFT` y `PUBLISHED`; `FINISHED` y `CANCELLED` bloquean
las mutaciones con 409. El listado sigue disponible para el propietario. `POST` devuelve 201 con `Location`;
`PUT`, 200; `DELETE`, 204; `GET`, 200. El anfitrión recibe 404 por un evento ajeno o un invitado inexistente.

RF-2.6: desde R8, commit 3, `GuestResponseCleanupPort` tiene adaptador real sobre `responses` y se elimina
`NoGuestResponsesYetAdapter`. Si ya respondió requiere confirmDeleteResponse=true; sin confirmación, 409.
Primero se retira el invitado y después se limpia su respuesta. Un conflicto de retirada conserva ambos;
un fallo de limpieza deja una respuesta inalcanzable y devuelve 500 saneado. La limpieza se intenta siempre
tras una retirada exitosa, para cubrir una respuesta llegada desde la consulta inicial. Una escritura que
termine aún después puede quedar inalcanzable: no se añade transacción ni recuperación automática.

RF-4.2 (conjuntos predefinidos de opciones): los presets *Asisto / No asisto*, *Asisto / No asisto / Tal vez* y *Confirmo / Declino / Pendiente* pueden vivir enteramente en el frontend como listas hardcodeadas que el cliente envía al crear el evento. El backend ya acepta cualquier combinación válida. No se necesita endpoint nuevo; confirmar con el equipo de frontend al integrar la pantalla de creación.

**R7. Página pública del invitado** ✓ Implementada.

`GET /api/invitations/{token}` es público, sin sesión ni CSRF. `InvitationResponse` se construye campo a campo;
no reutiliza `EventResponse`, ni serializa el dominio ni Mongo. Solo devuelve el nombre del invitado y la
configuración pública del evento. Se excluyen hostId, contactos de cualquiera, tokens, otros invitados y
countsAsAttendance. Devuelve fechas locales más zona e instantes UTC para la conversión del frontend.

Estados: desconocido y `DRAFT` producen el mismo 404, detalle e instancia constante `/api/invitations`, sin
incluir el token; `PUBLISHED`, `CANCELLED` y `FINISHED` producen 200. CANCELLED incluye fecha y motivo.
`Event.visibleOnlineAccess(...)` solo devuelve acceso en `PUBLISHED`: en cualquier otro estado lo oculta.
`onlineAccessInfo` contiene únicamente plataforma, regla y horas; `onlineAccess` se omite completamente cuando
no es visible. Desde el commit 2 de R8, ON_CONFIRMATION se abre únicamente con una respuesta propia cuya
opción cuente como asistencia; D-CONFIRMED-LINK está saldada.

OPENED se implementa aquí, trasladado desde R11. Una visita a PUBLISHED de un invitado NOT_SENT o SENT ejecuta
una actualización posicional con `$elemMatch`, fija OPENED/openedAt y aumenta version una sola vez. Si ya estaba
OPENED, el servicio no intenta escribir. Un update sin coincidencia indica carrera: reconsulta para reflejar
una apertura, cancelación, finalización o eliminación concurrente. DRAFT, CANCELLED y FINISHED no se marcan.
Se utiliza un único instante obtenido del Clock inyectado por petición.

Revisión de versiones: R6 ya comprobaba la versión esperada e incrementaba explícitamente version en alta,
edición y borrado. R7 conserva el incremento por cada cambio real, condicionado por la transición de estado
del mismo elemento del array; no necesita igualdad de versión para disputar una primera apertura. El handler
existente de OptimisticLockingFailureException devuelve 409 con ProblemDetail, probado mediante un cancel del
anfitrión que intenta guardar una versión anterior a una apertura. R11 debe seguir el mismo criterio de
incremento cuando implemente SENT.

El controlador y el handler 404 fijan exactamente `Cache-Control: no-store`. Los tests comparan el valor
completo para no pasar accidentalmente con las cabeceras anti-caché predeterminadas de Spring Security.
No se registran tokens; los tests comprueban también la salida de logs.

**R8. Registrar respuesta** ✓ Implementada, commits 0–4.
`PUT /api/invitations/{token}/response`, recurso singular e idempotente, implementado en el commit 1.
Crea `responses`, con índice único `(eventId, guestToken)` y otro sobre `eventId`, en el inicializador central.
Sin permiso de cambios se inserta directamente: el duplicado del índice compuesto produce 409, sin consultar
existencia antes. Con permiso se usa upsert atómico, `$setOnInsert` de answeredAt y `$set` del resto y updatedAt.
Una colisión de inserción concurrente en ese upsert reintenta solo la actualización atómica de la misma clave.
No se escribe ANSWERED en `events`: se deriva de la existencia de respuesta. No se modifica el evento al responder.

GET y PUT devuelven `InvitationResponse`, con `myResponse` (opción, comentario y fechas, sin email) y
`canChangeResponse`: true solo si existe respuesta, se permiten cambios y se cumplen las reglas de tiempo y estado.
La ampliación posterior añade `canRespond`, true también antes de la primera respuesta si la ventana está
abierta. Event.responseAcceptancePolicy(now) comparte ventana y permiso de cambios con el PUT; no se
duplican las reglas temporales ni se consulta existencia antes del insert. canChangeResponse equivale a
respuesta existente && canRespond. Los 13 casos de coherencia comparan el flag con PUT 200/409 usando
el mismo Clock fijo, incluido plazo exacto, fin exacto, CANCELLED y FINISHED.
Opción ajena, comentario con contenido sin permiso, longitud mayor de 500 y email inválido producen 422;
comentario en blanco equivale a ausente y se guarda como null. Email opcional, validado y almacenado, sin envío
(D-EMAIL-NOT-SENT). Los errores usan instancia constante `/api/invitations` y `Cache-Control: no-store` exacto;
los duplicados se traducen sin mensaje ni causa del driver, que pueden incluir el token. No se registran tokens ni comentarios.

Solo `/api/invitations/**` se excluye de CSRF: la credencial está en la ruta, no en una cookie ambiental.
Login, registro y endpoints del anfitrión conservan CSRF. El preflight CORS ya permitía PUT y Content-Type;
se prueba sin ampliar la configuración.

**Commit 2 implementado: D-CONFIRMED-LINK saldada.** La proyección compartida de GET y PUT calcula la
confirmación con la respuesta ya cargada y la opción del evento. Si no existe respuesta, la opción ya no
existe o no cuenta como asistencia, no confirma. Cambiar entre asistencia y rechazo muestra u oculta el
acceso en ON_CONFIRMATION en la siguiente lectura; el dominio sigue ocultándolo fuera de PUBLISHED.
No se almacena un estado de confirmación nuevo ni se necesita otra consulta.

El acceso al futuro muro social dependerá de estar invitado (token válido), no de responder ni confirmar;
la confirmación solo gobierna la visibilidad del enlace de reunión. Decisión en `gtog-estado-y-decisiones.md`.

**Commit 3 implementado: D-7 y cascada real.** `replaceResponseOptions(edits, answeredOptionIds)` permite
DRAFT y PUBLISHED. En PUBLISHED conserva opciones con respuestas y su countsAsAttendance, permitiendo
renombrar y agregar; las opciones sin respuestas se pueden quitar o cambiar. CANCELLED y FINISHED producen
409. La aplicación obtiene los ids por un puerto; el dominio sigue sin conocer Mongo. La carrera con una
respuesta en curso queda documentada como D-ORPHAN-OPTION, con lectura defensiva y sin transacciones.
La cascada usa los puertos de R6 con el orden aprobado: retirar invitado y después limpiar respuesta real.

**Commit 4 implementado: calendario.** `GET /api/invitations/{token}/calendar` devuelve `event.ics` como
`text/calendar;charset=UTF-8`, con `Content-Disposition: attachment` y `Cache-Control: no-store` exacto.
No exige sesión, CSRF ni respuesta previa. No registra OPENED ni modifica el evento. Desconocido y DRAFT
producen el mismo 404 de R7; CANCELLED produce 409 con ProblemDetail; FINISHED conserva la descarga 200.
Los errores tienen instancia constante `/api/invitations` y no-store exacto incluso con Accept: text/calendar.

`DownloadInvitationCalendarService` obtiene un instante del Clock y reutiliza `InvitationViewFactory` para
aplicar exactamente `Event.visibleOnlineAccess(...)`. El renderizador recibe `InvitationCalendarData`,
sin token, anfitrión, invitados, contactos ni respuesta propia: título, descripción, fechas locales y zona,
venue y solo la URL visible. No recibe contraseña, sala ni instrucciones. UID basado en el id del evento;
DTSTAMP basado en Clock. El archivo incluye VTIMEZONE, escapes de texto, CRLF y plegado a 75 octetos UTF-8
sin cortar caracteres multibyte. Se prueba también el cambio de hora y la lectura posterior del archivo.

**Dependencia fijada:** `org.mnode.ical4j:ical4j:4.2.0`, descargada desde Maven Central con dependency:get.
El árbol real de dependency:tree se mostró antes del commit y quedó en `execution-logs/r8-commit4-dependency-tree.txt`.
Su rama resuelta: slf4j-api 2.0.18, commons-codec 1.21.0, commons-lang3 3.20.0 y threeten-extra 1.8.0,
respetando la gestión de versiones de Spring Boot. iCal4j solo se usa en el adaptador de salida.
`src/main/resources/ical4j.properties` fija `net.fortuna.ical4j.timezone.update.enabled=false`; el Configurator
de iCal4j lo carga al inicializarse, antes de usar el registro de zonas, también fuera del contexto Spring.

**Preparación implementada (commit 0): plazo editable.** `PUT /api/events/{id}/response-deadline` recibe
`{ "responseDeadline": LocalDateTime | null }` y devuelve 200 con `EventResponse`. Requiere sesión, CSRF y
propiedad del evento (404 si es ajeno). La fecha es local a la zona del evento; null quita el plazo y un plazo
pasado se acepta. En DRAFT se puede cambiar sin restricción temporal; en PUBLISHED solo si `now < startsAtInstant`,
con Clock inyectado. En el inicio exacto, después, CANCELLED o FINISHED, devuelve 409. La validación compartida
con `edit()` mantiene 422 si el plazo es posterior a startsAt.

La persistencia usa `$set` del plazo y `$inc` de version, filtrando por id, hostId y estado DRAFT/PUBLISHED,
sin filtrar por versión ni guardar el documento completo. Así una apertura concurrente no bloquea el cambio.
Si no coincide ningún documento, devuelve 409 por cambio concurrente. El handler existente de
`OptimisticLockingFailureException` ya devuelve 409 con ProblemDetail: el test de cancelación con versión
anterior al cambio del plazo comprueba que no se sobrescribe el plazo.

RF-4.7, implementado en el commit 1: el plazo se aplica a toda escritura de respuesta, tanto la primera como
los cambios. Solo PUBLISHED, `responseDeadline == null || now <= responseDeadline` y
`now < endsAtInstant`; los cambios requieren además `allowResponseChange`. El plazo se interpreta en la zona
del evento. En PUBLISHED, fijar un plazo pasado equivale a cerrar las respuestas.

RF descarga de calendario implementada: el frontend solicita el archivo desde la página del invitado,
por ejemplo después de responder (flujo del MVP §6). El email opcional se sigue guardando sin enviar nada:
D-EMAIL-NOT-SENT queda saldada para invitaciones en R10, pero sigue abierta para la cita al responder,
fuera de su alcance. D-ORPHAN-OPTION también queda abierta; D-7 y D-CONFIRMED-LINK están saldadas.

**R9. Panel del anfitrión**
**Commit 1 implementado:** `GET /api/events/{id}/dashboard`. El caso de uso comprueba propiedad (404 si es
ajeno o inexistente), lee el evento y llama una vez a `GuestResponseRepositoryPort.findByEventIds(...)`.
El adaptador hace una consulta `eventId in [...]` con el índice existente; no hay $lookup. El dominio recibe
evento y lista de respuestas en `EventAttendanceSummary.calculate(...)`, una función pura sin Spring.

Los contadores parten de guests[], en orden de alta, y buscan respuestas por evento y token. Una respuesta
sin invitado vivo no cuenta. `totals` contiene guests, answered, pending y attending; `byOption` mantiene el
orden de opciones e incluye ceros. Attending solo cuenta opciones actuales con countsAsAttendance=true.
Una opción desaparecida cuenta como respondida, sin sumar en opciones ni asistencia: optionKnown=false y
label=null. Opción existente con respuesta: true. Sin respuesta: response=null y optionKnown=null.

Las filas contienen id, name, email y phoneE164 del contacto, deliveryChannel, invitationStatus e
invitationPath como GET /guests; añaden answered, response y optionKnown. No exponen token suelto, guestToken
ni el email de la respuesta. El 200 del panel fija exactamente `Cache-Control: no-store`; no se aplica al
listado ni se cambia la caché de errores o el manejador global de EventNotFoundException. Disponible en todos
los estados, sin escrituras ni incrementos de version.

Las pruebas incluyen mezcla de asistencia/rechazo/pendiente, huérfanos sembrados directamente, optionKnown
trivalente, orden, propiedad y documentos sin cambios. El cuerpo completo excluye emails de respuesta y
tokens ajenos; cada token propio aparece exactamente una vez y dentro de invitationPath, sin claves token
ni guestToken.

**Commit 2 implementado — RF-1.5:** `EventSummaryResponse` conserva id, title, startsAt, modality y status y
añade totalGuests, answered, pending y attending (sin byOption), reutilizando el mismo cálculo del dominio.
`EventQueryService` lee los eventos, consulta una sola vez las respuestas para todos sus ids y las agrupa
por eventId en memoria antes del cálculo. Sin consultas por evento; con listado vacío no consulta respuestas.
Los tests comparan los contadores con totals del panel e incluyen huérfanos sembrados directamente, opciones
desconocidas y varios eventos. Un spy del puerto comprueba la consulta única con los ids del anfitrión.
Las lecturas en todos los estados no modifican documentos ni version. El listado conserva las cabeceras de
Spring Security, sin no-store propio; solo el 200 del panel fija ese valor exacto. OpenAPI documenta los cuatro
contadores. D-LIST-SCALE documenta la limitación de volumen en memoria y ausencia de paginación.

**R9, R10 y R11 cerradas. R11 solo WhatsApp; Telegram queda fuera. Siguiente: R12.**

Registrar manualmente respuestas recibidas por otro medio queda fuera del MVP. Si se incorpora después,
GuestResponse ganará un campo source; su ausencia significará invitado, sin migración. No se añade ese campo
ni un endpoint de respuesta manual en R9.

### Bloque D — Envío de invitaciones

**R10. Envío por correo — cerrada, tres commits**

**Commit 1 implementado:** reservas atómicas y sus compensaciones, sin SMTP ni endpoint de envío todavía.
Guest incorpora emailSendCount y lastEmailSentAt; User incorpora emailSendsInWindow y windowStartedAt.
Los documentos antiguos se leen con 0/null, sin migración. El cambio de correo normalizado reinicia los
campos del invitado dentro de la escritura parcial de R6; una edición equivalente los conserva.

GuestEmailSendReservationPort vive en event/domain/port/out. Su adaptador usa $elemMatch, $inc del contador
y de version y $set de lastEmailSentAt. El límite y el intervalo se imponen en la escritura, sin save(event).
El intervalo permite el instante exacto. El comprobante conserva valores anteriores y la versión resultante.
La compensación del invitado compara identidad, correo, contador, instante y versión: es conservadora,
también ante una apertura o modificación de otro invitado. Si pierde la coincidencia, no borra cambios nuevos.

HostSendQuotaPort se define en el dominio de event y lo implementa HostSendQuotaAdapter en infraestructura
de user, que conoce users. No hay dependencia de event hacia user. La reserva del anfitrión usa un único
findAndModify con pipeline condicionado: inicia o reinicia la ventana a partir de 24 horas inclusive,
o incrementa solo por debajo del máximo. La compensación comprueba la ventana y evita contadores negativos.
read calcula remaining desde el documento, y resetsAt=windowStartedAt+24h o null si no hay ventana vigente.

Orden: elegibilidad → reserva del invitado → reserva del anfitrión → envío. DAILY_LIMIT compensa al invitado
y detiene el lote. Ante fallo del envío se compensa primero al anfitrión y después al invitado, sin reintentos.
Una compensación fallida conserva el bloqueo del invitado. El log ERROR EMAIL_SEND_COMPENSATION_FAILED incluye
stage, eventId, guestId, clase y correlación, nunca correo, token, mensaje ni causa. Si el contador retenido
alcanza el máximo, requiere intervención manual: esperar el intervalo no lo libera. No hay recuperación automática.
Una reserva de anfitrión con resultado incierto también conserva la del invitado y registra el diagnóstico.

Defaults: app.invitations.max-sends-per-guest=3, min-interval=PT1H,
max-sends-per-host-per-day=100 y max-batch=20. Clock se inyecta; los comprobantes usan la precisión de Mongo.
La confirmación parcial de SENT no rebaja OPENED y conserva la primera fecha. En este commit se prueba
una única autorización de envío con dos lotes concurrentes y cuota 1, usando un contador simulado.
El mensaje capturado y la prueba HTTP completa se incorporan con los commits 2 y 3.

Auditoría del commit 1: usuario BSON sin campos de cuota, compensación seguida de guardado antiguo → 409,
y comprobación de version en SENT. Mutaciones temporales aisladas: quitar el tope del invitado hace fallar
el test concurrente con el máximo alcanzado; quitar la cuota del anfitrión hace fallar el de dos lotes con
cuota 1. El test concurrente original del invitado sigue pasando sin tope porque el intervalo también
protege esa carrera. Ambas condiciones se restauraron antes del build y de enmendar el commit.

**Commit 2 implementado:** EmailSenderPort y SMTP Mailjet con el starter existente, sin dependencias nuevas.
Un único destinatario por mensaje, sin CC/BCC ni Reply-To; remitente visible gtog. Texto plano fijo en español:
anfitrión, título, fecha/hora en la zona del evento, lugar presencial o solo plataforma online, enlace de
invitación y los dos textos fijos aprobados. Nunca incluir secretos de sala ni contactos de otros invitados.
Asunto y nombres sin caracteres de control. Credenciales exclusivamente MAILJET_API_KEY/MAILJET_SECRET_KEY;
TLS obligatorio en 587 y timeouts 5/10/10 s. Arranque permitido sin credenciales, SMTP desactivado sin configuración,
adaptador falso en tests y ninguna prueba con correo real. El informe de condiciones de Boot 4.1 confirma
`@ConditionalOnEnabledHealthIndicator management.health.mail.enabled is false`; la propiedad queda fijada
a false para que la sonda no abra conexiones. Una prueba con true confirma el registro del indicador,
sin ejecutarlo. Errores traducidos a RECIPIENT_REJECTED, PROVIDER_UNAVAILABLE o INVALID_RECIPIENT;
log WARN SMTP_SEND_FAILED con clase y correlación, nunca mensaje ni causa.

InvitationEmailComposer vive en aplicación y forma el enlace con app.public-base-url (PUBLIC_BASE_URL,
por defecto http://localhost:4200). app.mail.from toma MAIL_FROM y app.mail.from-name vale gtog.
Credenciales vacías por defecto para permitir arranque local; sin usuario, contraseña o remitente se usa
UnavailableEmailSenderAdapter, que devuelve EMAIL_NOT_CONFIGURED sin conectar. Es fallo de proveedor:
el commit 3 debe compensar las reservas y contarlo para el corte. El perfil test siempre
usa CapturingEmailSenderAdapter, incluso con credenciales presentes. JavaMailSender se simula en los
tests del adaptador SMTP. Ningún test llama al proveedor ni a la sonda de correo. El transporte no se
invoca desde ningún endpoint hasta el commit 3. La configuración y los mensajes están cubiertos por 29 tests.
El renderizador del cuerpo recibe solo plataforma, nunca Event, OnlineAccess ni la URL de la sala.
El test MIME serializa y decodifica asunto con ñ, tildes y emoji, y comprueba CR/LF neutralizados.
El contexto completo de arranque sin credenciales usa dobles de Mongo y no abre ninguna conexión.

**Commit 3 implementado:** POST /api/events/{id}/invitations/send, autenticado, CSRF, propiedad comprobada en aplicación.
Solo PUBLISHED: DRAFT/CANCELLED/FINISHED → 409; el enlace público de DRAFT conserva su 404.
guestIds opcional: ausencia selecciona todos en orden de alta; lista vacía o repetidos → 422,
id ajeno al evento → 404, antes de reservas o mensajes; superar max-batch → 422. Se exige verificar
que las tres entradas inválidas no envían mensajes ni consumen cuota ni alteran contadores del invitado.

Respuesta 200 con results por invitado y dailyLimit {limit, remaining, resetsAt}, leído tras el lote desde users;
no-store exacto. Elegibilidad con una sola consulta de respuestas para todo el lote y revalidación puntual antes
de SMTP. SKIPPED: NO_EMAIL, ALREADY_ANSWERED, THROTTLED, LIMIT_REACHED, DAILY_LIMIT y PROVIDER_UNAVAILABLE.
RF-2.17 incluye a NOT_SENT, SENT y OPENED sin respuesta; quien ya respondió se salta. Persiste la ventana de
carrera entre la última consulta y SMTP, sin transacciones entre Mongo y proveedor.

Ejecución síncrona, sin reintentos automáticos. Tres fallos consecutivos de proveedor cortan el lote y dejan
los restantes SKIPPED/PROVIDER_UNAVAILABLE. Un rechazo de destinatario rompe esa racha; un salto de elegibilidad
no es un intento de envío. DAILY_LIMIT detiene el lote y marca todos los restantes con ese motivo.

SENT significa aceptado por SMTP, no entregado. Si se acepta pero falla registrar SENT, conservar reservas,
no reenviar y devolver SENT con recorded=false; log ERROR con clase y correlación. El panel puede seguir
mostrando NOT_SENT; un reenvío posterior puede duplicar el correo, acotado por intervalo y máximo de 3.
El archivo manual rest-api-mail.http queda separado del recorrido y usa realRecipient del entorno privado.

SendInvitationsService valida el lote completo antes de reservar: lista vacía/repetida o exceso → 422;
id ajeno → 404. Ausencia de cuerpo o guestIds selecciona todos. Selección explícita en orden de petición;
implícita en orden de alta. Lee las respuestas de todo el evento una sola vez y revalida por invitado antes
de SMTP, junto al estado, existencia y correo actuales. Un cambio incompatible compensa y rechaza la petición:
409 si cambió el estado o correo, 404 si desapareció el evento o invitado.
La carrera entre esa última lectura y SMTP permanece como límite, sin transacciones ni reintentos.
EMAIL_NOT_CONFIGURED produce FAILED, compensa ambas reservas y cuenta como fallo de proveedor para el corte;
los invitados restantes del lote reciben SKIPPED/PROVIDER_UNAVAILABLE. Un destinatario rechazado rompe la racha.
La respuesta no incluye contactos, tokens ni mensajes del proveedor. Las pruebas HTTP usan sesión y CSRF reales,
un servidor embebido y CapturingEmailSenderAdapter; incluyen dos peticiones y dos lotes concurrentes.
El archivo manual no se ha ejecutado: enviaría correo real. http-client.private.env.json está ignorado por Git.

Auditoría del commit 3: el test de dos peticiones simultáneas falla al omitir temporalmente la reserva del
invitado conservando la del anfitrión: esperaba 1 mensaje y capturó 2. Mutación restaurada, no versionada;
el test pasa con la reserva real. El rechazo claro del propio destinatario no cuenta para el corte;
remitente rechazado, autenticación, conexión/timeout y cualquier 5xx no clasificado sí cuentan como
PROVIDER_UNAVAILABLE. No se clasifica un SendFailedException genérico como destinatario rechazado.
Un fallo de proveedor prevalece sobre rechazos de destinatario en la cadena. Sin leer mensajes SMTP:
se usan los campos estructurados; Angus, de ejecución, se consulta por su API pública sin nueva dependencia.
La prueba de respuesta aparecida antes de SMTP usa un spy y verifica salto y compensación; no existe un
test de esa carrera con dos hilos reales. No se añade ni se presenta la simulación como concurrencia real.

**RF-2.15 queda fuera del MVP:** plantilla editable con variables, anotada como seguimiento. No se añade
messageTemplate. RF-2.16 conserva invitationPath /r/{token}; la URL base del frontend será configurable.
El correo de GuestResponse sigue opcional y almacenado, nunca mostrado al anfitrión ni usado en R10.
La cita al responder queda pendiente: cualquier uso futuro exige aprobación expresa y confirmar la dirección
mediante doble confirmación antes de escribirle. No se debilita el test de privacidad del panel de R9.

**R11. WhatsApp asistido — cerrada, dos commits y una corrección de nombres; Telegram fuera de alcance**

**Commit 1 implementado:** POST /api/events/{id}/invitations/whatsapp-links. Autenticado y con CSRF;
propiedad comprobada en aplicación (404 si es ajeno). Solo PUBLISHED; DRAFT, CANCELLED y FINISHED → 409.
El GET público de la invitación de un DRAFT sigue devolviendo 404: son operaciones distintas.
Cuerpo opcional {guestIds:[...]}; ausencia de cuerpo/lista/null selecciona todos en orden de alta.
Lista vacía, ids repetidos o máximo superado → 422; id ajeno al evento → 404 antes de procesar nada.
Selección explícita en su orden. app.invitations.max-whatsapp-links-per-request=100, independiente del correo.

GenerateWhatsAppLinksService consulta responses una vez para todo el lote por el puerto existente.
Salta NO_PHONE, ALREADY_ANSWERED, INVALID_PHONE o MESSAGE_TOO_LONG sin abortar a los restantes. Comprueba
el formato E.164 almacenado, sin renormalizar ni reparar Mongo. Devuelve {results:[{guestId,status,reason?,url?,message?}]}:
READY lleva url y texto plano; SKIPPED solo motivo. Cache-Control: no-store exacto; no registrar las credenciales.
No hay puertos de escritura, reservas de invitado, cuotas de anfitrión ni reutilización del motor de R10.

Texto fijo en español: saludo, anfitrión, título, inicio/fin con zona, lugar presencial o solo plataforma
online y enlace <app.public-base-url>/r/{token}. Nunca secretos de sala ni los dos pies del correo.
InvitationMessageFormatter comparte fechas, zona, lugar y limpieza de controles con InvitationEmailComposer;
sus tests se conservan sin modificar. InvitationLinkFactory comparte la construcción y validación de la base.
Título y lugar compuesto (nombre — dirección) se recortan a 100 clústeres de grafemas, incluidos los tres
puntos de "...", mediante BreakIterator de Java 25. Se conservan enteros familia ZWJ, bandera y tono de piel.
URL https://wa.me/<E164 sin +>?text=<texto>, codificada en UTF-8 con espacios %20, saltos %0A y signo + literal %2B.
La cota conservadora se comprueba sobre la URL final codificada: 2000 se admite y 2001 se salta.
No se ha verificado que sea el límite real de WhatsApp. Los tests usan URLDecoder estándar y comparan
carácter a carácter el mensaje decodificado, incluidos & # % + comillas, tildes, ñ y emoji; el token aparece una vez.
Las pruebas HTTP comparan íntegros events/responses/users y version antes/después; no cambia ningún estado.

Auditoría del commit 1: la petición HTTP completa captura stdout/stderr con OutputCaptureExtension y
comprueba expresamente ausencia de token, URL completa y message en los logs. El caso online siembra
dos invitados y genera solo para uno; el cuerpo completo no contiene datos del otro ni secretos de sala.
La codificación incluye el ejemplo con C++, &, #, %, comillas y 'cena'. El límite de 2000 incluye todo el
prefijo https://wa.me/<número>?text= y el mensaje codificado, no solo el parámetro text.

**Commit 2 implementado:** POST /api/events/{id}/invitations/whatsapp-sent, cuerpo obligatorio con guestIds
explícitos y el mismo máximo. Misma seguridad, propiedad y publicación. Confirmar no exige generar primero.
Primera actualización con $elemMatch por id, NOT_SENT y presencia de phoneE164: fija SENT, WHATSAPP y sentAt
del Clock, e incrementa version. Segunda actualización condicionada: OPENED y sentAt nulo, con teléfono;
completa WHATSAPP/sentAt sin cambiar OPENED e incrementa version. Ambas devuelven MARKED.
Un OPENED con envío registrado devuelve ALREADY_OPENED sin escribir; un SENT conserva canal/fecha del primer
envío y devuelve ALREADY_SENT. Sin teléfono → NO_PHONE, impuesto en los filtros, sin lectura previa para decidirlo.
Sin coincidencia se reconsulta; un invitado retirado entre validación y escritura → NOT_FOUND sin abortar el lote.
Si el evento deja de estar PUBLISHED a mitad del lote se devuelve 409 aunque haya marcas previas: se acepta
porque la operación es idempotente, sin transacciones ni rollback del lote.

200 con {results:[{guestId,status}]} y Cache-Control exactamente no-store. Cuerpo ausente → 400;
lista ausente/null/vacía/repetida o máximo superado → 422; id ajeno → 404 antes de escribir.
Se prueban ambos órdenes de apertura/confirmación, repetición sin cambiar sentAt ni version, NO_PHONE con
documento completo intacto (teléfono ausente, null, vacío y blanco), dos confirmaciones con hilos reales,
carrera con primera apertura, guardado de versión anterior → 409 y panel R9 con SENT/WHATSAPP.
La retirada entre validación y escritura devuelve NOT_FOUND y continúa; cancelar después de la primera marca
da 409 y conserva esa marca. Confirmar no lee respuestas ni toca reservas/cuotas de correo.
Mutación temporal ejecutada y no versionada: sin NOT_SENT en el primer filtro, el test de OPENED falló
con expected OPENED / actual SENT (1 fallo, 0 errores). Condición restaurada antes de la verificación final.

rest-api-whatsapp.http es manual e independiente del recorrido automático. Crea anfitrión, evento e invitado
con {{realPhone}} del entorno http-client.private.env.json, publica, genera y confirma. Solo un paso imprime
la URL; el usuario abre y envía desde WhatsApp antes de confirmar. No se ha ejecutado automáticamente.
El fichero privado sigue ignorado por Git y no se crea ni se versiona.

**Frontend futuro:** abrir enlaces de uno en uno tras un gesto del usuario; varios popups pueden bloquearse.
Confirmar después de cada envío y mostrar message como alternativa de copia si WhatsApp no abre.
MESSAGE_TOO_LONG puede afectar a todo el evento por un título con emojis: cada emoji sencillo de cuatro
bytes suele ocupar unos 12 caracteres al codificarse; los compuestos consumen más. Sugerir acortar el título.
Los nombres de invitado y anfitrión también pasan por la limpieza y recorte a 100 grafemas del título.
La prueba de Cache-Control compara el valor exacto no-store, sin aceptar el valor largo de Spring Security.
D-WHATSAPP-TRUST: SENT es una declaración del anfitrión, nunca una verificación del proveedor.

**Telegram aplazado:** no hay endpoint ni implementación en R11. El formato actual del MVP está mal formado;
la hipótesis de que el enlace de compartir abre un selector de chat en vez de un contacto concreto queda
sin verificar. No se propone ni implementa una corrección de Telegram en esta rebanada.

### Bloque E — Identidad completa

**R12. Verificación de correo y recuperación de contraseña**
RF-1.2 y RF-1.3. **Proveedor resuelto: Mailjet por SMTP; R12 desbloqueada por esa decisión**, sin dependencias
nuevas. Implementar después de R10 para reutilizar el transporte y el puerto de correo, no antes.
Endpoints: `POST /api/auth/verify-email` (confirma el token del correo de verificación),
`POST /api/auth/resend-verification`, `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`.

---

## 4. Decisión resuelta — Spring Security en R5

**Spring Security ya se incorporó en R5**, antes de comenzar R6. El anfitrión se identifica por su sesión;
`hostId` ya no se recibe en el cuerpo de creación ni como parámetro del listado. Los casos de uso comprueban
la propiedad del evento y devuelven 404 al acceder a uno ajeno. `/api/invitations/**` es público y R7 ya implementa su consulta por token.

Se mantiene el orden decidido: construir invitados, página pública y panel sobre el modelo de autorización
ya implementado evita rehacer los controladores al añadir seguridad más adelante.

---

## 5. Decisiones que siguen pendientes

| Decisión | Bloquea | Urgencia |
|---|---|---|
| ~~**Registrar a mano una respuesta recibida por otro medio**~~ | ~~R8, R9~~ | **Resuelta:** fuera del MVP; futura ampliación con source, ausencia = invitado, sin migración. |
| ~~**Formato del token**: longitud y alfabeto~~ | ~~R6~~ | **Resuelta e implementada en R6:** 32 bytes de `SecureRandom`, Base64 URL-safe sin relleno, 43 caracteres; independiente de ids y datos personales. |
| ~~**¿Qué pasa con `venue`/`onlineAccess` al cambiar de modalidad en R4?**~~ | ~~R4~~ | **Resuelta en R4:** si la modalidad cambia, el cliente debe aportar el bloque de la nueva modalidad; el de la anterior se descarta. Si no lo aporta, `ModalityChangedWithoutLocationException` (422). |

**Resuelta durante R1–R3:** la clave de Google Maps con facturación activa, que aparecía aquí bloqueando R3. Ya no bloquea nada en este repositorio: el backend no llama a la API de Google (ver `Venue` en `docs/modelo-evento.md`), confía en los datos que el cliente ya ha resuelto. Si hace falta una clave, es para el autocompletado en el frontend, fuera de este repo.

---

## 6. Qué significa "terminada" una rebanada

Para no discutirlo cada vez:

1. `clean verify` en verde.
2. Test unitario de dominio para cada regla de negocio nueva.
3. Test de integración del endpoint, incluyendo al menos un caso de error.
4. Sin anotaciones de framework en `domain/`.
5. Documentada en OpenAPI, con anotaciones solo en `infrastructure/in/web`.
6. Commit con mensaje convencional.

## Bloqueadores de despliegue público

- D-BOUNCES: procesar rebotes y quejas del proveedor para proteger la reputación del dominio; SENT no garantiza entrega.
- R12: verificación del correo del anfitrión.
- D-REGISTER-RATE-LIMIT: límite de peticiones en registro y login.
- D-RATE-LIMIT: protección contra abuso de las rutas públicas de invitación.
- D-SWAGGER-PUBLIC: decisión y configuración de exposición de OpenAPI/Swagger.
- D-SECURE-COOKIE: cookies seguras bajo HTTPS.
- D-REFERRER del frontend: Referrer-Policy: no-referrer en la página con token.
- Dominio y remitente de Mailjet verificados, con SPF, DKIM y DMARC en PASS.
