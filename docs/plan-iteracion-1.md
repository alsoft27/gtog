# gtog — Plan de la iteración 1

Estado a 15 de noviembre de 2026. Decisión de correo documentada; sin implementación de R10 todavía.

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
| Infraestructura | Java 25, Spring Boot 4.1, Maven por defecto, MongoDB Atlas, OpenAPI |

**Verificación actual:** `mvn clean verify` pasa con **464 tests**, 0 fallos, 0 errores y 0 omitidos. R8 incorporó 150 casos; R9 añade 38: 27 en el commit 1 (7 de dominio, 6 de aplicación y 14 de integración) y 11 en el commit 2 (2 de aplicación y 9 de integración). El ajuste separado a inyección por constructor no cambia el número de tests.

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
| D-EMAIL-NOT-SENT | **Correo de respuesta sin envío** | El correo opcional se valida y se guarda únicamente en `responses`; no sale en `myResponse` ni se envía confirmación por correo. | R10 |
| D-RATE-LIMIT | **Endpoint público sin límite de peticiones** | La entropía del token hace inviable adivinarlo, pero no protege frente a abuso o tráfico repetido sobre un enlace conocido. No hay rate limiting en R7. | Pendiente de planificación |
| D-REFERRER | **Política de Referer del frontend** | La página del invitado lleva la credencial en la URL. Para evitar filtrarla al cargar un mapa de Google u otros recursos externos, el frontend debe servir la página con `Referrer-Policy: no-referrer`. La cabecera de la API no sustituye la política del documento del frontend. | Frontend, al integrar la página del invitado |
| D-FINISHED | **`FINISHED` sin implementar** | No existe ningún mecanismo para que un evento pase a `FINISHED`. Ni job programado ni derivación al vuelo. | A definir: requiere decidir si es automático (por fecha) o manual (anfitrión lo marca) |
| D-DELETE-DRAFT | **Borrar un `DRAFT` sin implementar** | Un borrador no se puede cancelar (`cancel()` solo desde `PUBLISHED`). No existe `DELETE /api/events/{id}`. | Rebanada posterior |
| D-SWAGGER-PUBLIC | **OpenAPI y Swagger UI sin autenticación** | Cualquiera puede ver la documentación completa de la API sin autenticarse. Hoy es conveniente para desarrollo; antes de producción hay que decidir si protegerlo o dejarlo público. | Antes del despliegue a producción |
| D-SECURE-COOKIE | **Cookie de sesión sin `Secure` en desarrollo** | La cookie `JSESSIONID` se envía también por HTTP. En producción detrás de HTTPS con `server.ssl.*` o proxy con `server.forward-headers-strategy=native`, Spring Boot activa el flag automáticamente. Verificar antes del primer despliegue. | Antes del despliegue a producción |
| D-ERROR-FORMAT | **401 de los filtros de seguridad sin `ProblemDetail`** | El advice devuelve `ProblemDetail`, pero el rechazo por falta de autenticación se produce en los filtros y no garantiza el mismo formato. La unificación queda pendiente; no cambiar los filtros en esta corrección. | Pendiente de planificación |
| D-DUP-KEY | **`DuplicateKeyException` mapeada por texto, no por código de error en users** | `UserRepositoryAdapter` sigue inspeccionando `e.getMessage().contains("email")`. En `responses` se evita desde R8: código 11000 y `keyPattern` estructurado distinguen el índice compuesto de cualquier otro duplicado, sin conservar mensaje ni causa. La inserción usa el comando Mongo porque el driver descarta keyPattern en insertOne; el upsert conserva los metadatos del error de comando. Los otros duplicados devuelven 500 saneado, nunca el 409 de respuesta existente. | Pendiente solo en users |

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
D-EMAIL-NOT-SENT permanece hasta R10. D-ORPHAN-OPTION también queda abierta; D-7 y D-CONFIRMED-LINK están saldadas.

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

**R9 cerrada. Siguiente punto: R10, envío por correo.**

Registrar manualmente respuestas recibidas por otro medio queda fuera del MVP. Si se incorpora después,
GuestResponse ganará un campo source; su ausencia significará invitado, sin migración. No se añade ese campo
ni un endpoint de respuesta manual en R9.

### Bloque D — Envío de invitaciones

**R10. Envío por correo**
Plantilla con las variables del evento y el enlace único. **Proveedor decidido: Mailjet por SMTP**, usando
`spring-boot-starter-mail`, ya presente en el POM; sin dependencias nuevas. R10 y R12 quedan desbloqueadas
en cuanto a la elección de proveedor. **Orden inmediato: R9 y después R10.** Esta decisión no implementa
el envío ni salda todavía D-EMAIL-NOT-SENT.

Condiciones aprobadas para R10:

- Credenciales únicamente en las variables de entorno `MAILJET_API_KEY` y `MAILJET_SECRET_KEY`, nunca en
  ficheros. No imprimirlas ni volcarlas a logs, tampoco en los tests.
- `EmailSenderPort` con adaptador SMTP en producción y adaptador falso en tests. Ningún test envía correo real.
- Mensajes en texto plano. Neutralizar los saltos de línea en asunto y nombres para impedir inyección de cabeceras.
- STARTTLS obligatorio en el puerto 587 o SSL implícito en el 465, sin degradación a transporte sin cifrar.
- Dominio y remitente validados en Mailjet, con SPF y DKIM, como requisito previo al envío real.
- `SENT` por correo significa aceptado por el proveedor, no entregado al destinatario.
- Si el envío falla, no se marca `SENT`; se informa del resultado por invitado, también en envíos en bloque.
- La URL base del frontend usada para construir el enlace de invitación va en una propiedad configurable.
- El registro de envío debe preservar OPENED: marcar SENT no puede hacerlo retroceder, igual que en R11.
- Un envío de cita por invitado y dirección, con tope por invitado y contenido fijo sin comentario. El valor
  concreto del tope sigue pendiente; no se implementa ahora.

Comportamientos que aún hay que concretar antes de implementar R10:

- Ejecución síncrona o en segundo plano; límites de lote y contrato de resultados parciales por invitado.
- Política de reintentos y reenvíos, envíos concurrentes y prevención de duplicados; tratamiento de la
  aceptación SMTP seguida de un fallo al persistir SENT. No asumir transacciones entre SMTP y Mongo.
- Estados del evento que admiten envío y política para invitados que ya respondieron o ya recibieron un envío.
- Qué correos de confirmación de respuesta incluye R10 para saldar D-EMAIL-NOT-SENT, cuándo se disparan,
  destinatario y si adjuntan el calendario o enlazan a su descarga.
- Contrato de la plantilla: asunto, variables admitidas y valores por defecto; remitente visible y Reply-To.

RF-2.17 queda aclarado: el reenvío a pendientes incluye a todos los invitados que no han respondido, con
independencia de NOT_SENT, SENT u OPENED. La respuesta se deriva consultando responses para invitados vivos.

RF-2.14 (envío en bloque por correo): el endpoint de envío debe aceptar un listado de tokens además del individual, o exponer un endpoint `POST /api/events/{id}/invitations/send` que tome una lista.

RF-2.15 (plantilla editable): la plantilla vive como campo `messageTemplate` en el documento del evento (campo nulo = texto por defecto generado en el backend). No hay colección separada porque no existen plantillas reutilizables entre eventos en el alcance del MVP. Se edita con el mismo `PUT /api/events/{id}` del bloque básico.

RF-2.16 (copiar enlace individual): R6 devuelve `invitationPath: /r/{token}` exclusivamente en el alta y el listado de invitados. El frontend completa la ruta con su origen para copiar el enlace. No se expone un campo `token` suelto ni se añade a `EventResponse`; el `PUT` tampoco devuelve el enlace.

RF-2.17 (reenvío): seleccionar invitados con `ANSWERED derivado == false`, sea cual sea su invitationStatus;
el endpoint de envío en bloque de correo cubre este caso si acepta una lista de tokens.

**R11. Enlaces de WhatsApp y Telegram**
El backend construye el enlace profundo y lo devuelve; el envío lo hace el anfitrión. R11 implementará `SENT` con actualización posicional atómica e incremento de version. `OPENED` ya está implementado en R7: marcar `SENT` nunca puede sobrescribir un `OPENED` ni hacerlo retroceder.

RF-2.14 (envío en bloque por WhatsApp/Telegram): el endpoint puede devolver una lista de enlaces profundos para que el frontend los abra en secuencia asistida. No hay trabajo adicional de backend salvo exponer el endpoint en forma de lista.

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
