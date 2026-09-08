# gtog — Plan de la iteración 1

Estado a 20 de agosto de 2026.

---

## 1. Dónde estamos

**Cerrado**

| Rebanada | Estado |
|---|---|
| Crear evento en `DRAFT` | `POST /api/events` funcionando de punta a punta, con dominio, puertos, adaptador Mongo, controlador y tests |
| R1 — Consultar y listar eventos | `GET /api/events/{id}` (`EventResponse` completo, `EventNotFoundException` → 404) y `GET /api/events?hostId=` (proyección `EventSummaryResponse`, sin paginación, `hostId` obligatorio y temporal) |
| R2 — Opciones de respuesta | `ResponseOption` embebido en `Event`, 2 a 5, defaults ("Asisto"/"No asisto") si no se especifican, `PUT /api/events/{id}/response-options` con identidad por `id` al editar (se conserva si coincide, se rechaza con 422 si no pertenece al evento) |
| R3 — Ubicación y acceso en línea | `Venue` y `OnlineAccess` (el backend no llama a la API de Google), `LinkVisibility`, invariante de modalidad (`IN_PERSON`⇄`venue`, `ONLINE`⇄`onlineAccess`), `PUT /api/events/{id}/venue` y `PUT /api/events/{id}/online-access`, `Event.visibleOnlineAccess(guestHasConfirmed, now)` ya implementado (solo probado a nivel de dominio, sin invitados todavía) |
| R4 — Editar, publicar y cancelar | Máquina de estados: `publish()` solo desde `DRAFT` (precondiciones: venue/onlineAccess según modalidad, ≥2 opciones → `IncompleteEventForPublishException` 422), `cancel(reason, now)` solo desde `PUBLISHED` → `CANCELLED` con `cancelledAt` (irreversible). `edit()` con todos los campos básicos más `allowComment`/`allowResponseChange`/`responseDeadline` (sacados de `replaceResponseOptions`): si la modalidad cambia, el bloque de ubicación correspondiente es obligatorio. `PUT /api/events/{id}`, `POST /api/events/{id}/publish`, `POST /api/events/{id}/cancel`. 61 tests de dominio, 37 de integración, todos en verde. |
| Infraestructura | Java 25, Spring Boot 4.1, Maven aislado, MongoDB Atlas, OpenAPI |

**Lo que eso valida:** la arquitectura hexagonal funciona en la práctica, el mapeo entre `Event` y `EventDocument` es asumible, y el circuito completo de desarrollo está operativo. Además, `Event.create(...)`/`reconstitute(...)` cambiaron de firma en las tres rebanadas seguidas, así que desde R3 `Event` se construye con dos builders internos (`Event.builder()` para eventos nuevos, `Event.reconstituteBuilder()` para rehidratar) en vez de factory methods con parámetros posicionales — ver `docs/modelo-evento.md`.

---

## 2. Deuda técnica abierta

Esto no es una lista de pendientes cualquiera: son cosas que ya están mal a propósito y que hay que devolver a su sitio. Escritas para que ninguna se pierda.

| # | Deuda | Consecuencia si se olvida | Cuándo se salda |
|---|---|---|---|
| D-1 | **Spring Security retirado** | Todos los endpoints son públicos | Rebanada 5, ver §4 |
| D-2 | **`hostId` viaja en el cuerpo del request** | Cualquiera crea eventos en nombre de otro | Con D-1 |
| D-3 | **Índice único de `guests.token` sin crear** | No es que falte el componente — `MongoIndexInitializer` (en `shared/config`) ya existe y crea el índice de `hostId` en el arranque. Es que `guests.token` no puede indexarse todavía porque `guests[]` no existe como campo de `EventDocument`. Mientras tanto, no hay ninguna consulta que lo necesite. | Rebanada 6, en cuanto exista `guests[]`: ampliar `MongoIndexInitializer` |
| D-5 | **Tests de integración contra Atlas** | Los tests necesitan red y son lentos | Aceptable mientras seas el único desarrollador |
| D-6 | **Sin herramienta de migraciones** | No hay forma versionada de cambiar el esquema | Cuando haya datos reales |
| D-7 | **Opciones de respuesta bloqueadas en eventos publicados** | RF-4.8 permite renombrar y agregar opciones tras publicar, prohibiendo solo eliminar las que tengan respuestas. El código actual es más restrictivo: `Event.replaceResponseOptions(...)` lanza `EventNotEditableException` (409) ante *cualquier* cambio si `status != DRAFT`, sin distinguir renombrar, agregar o eliminar. El dominio no conoce la colección `responses` todavía, así que tampoco puede saber qué ids tienen respuestas. El fix completo tiene dos partes: (1) permitir renombrar y agregar en `PUBLISHED`; (2) bloquear solo el borrado de opciones con respuestas. | Rebanada 8: `replaceResponseOptions(...)` recibirá el conjunto de ids con respuestas (calculado en la capa de aplicación) y pasará a funcionar en `PUBLISHED` para rename/add |
| D-FINISHED | **`FINISHED` sin implementar** | No existe ningún mecanismo para que un evento pase a `FINISHED`. Ni job programado ni derivación al vuelo. | A definir: requiere decidir si es automático (por fecha) o manual (anfitrión lo marca) |
| D-DELETE-DRAFT | **Borrar un `DRAFT` sin implementar** | Un borrador no se puede cancelar (`cancel()` solo desde `PUBLISHED`). No existe `DELETE /api/events/{id}`. | Rebanada posterior |

**D-4, saldada.** Decía "health indicator de Mongo desactivado", y no era así: estaba activo y en `DOWN`. El indicador por defecto de Actuator recorre **todas** las bases que el `MongoClient` ve vía `listDatabaseNames()` — no solo `local`, también `admin`, `config`, la de la propia app — y ejecuta `hello` en cada una; en Atlas el usuario de la aplicación no tiene permiso sobre `local`, así que el chequeo entero caía con `DOWN` aunque `gtog_dev`/`gtog_test` respondieran sin problema. Comprobado arrancando la app. Sustituido por `MongoDatabaseHealthIndicator` (en `shared/config`), que hace `ping` solo contra la base configurada de la aplicación; el indicador por defecto se desactiva con `management.health.mongodb.enabled=false` en ambos `application*.properties` para que no convivan. Verificado de nuevo: `GET /actuator/health` → `"mongo":{"details":{"database":"gtog_dev","ping":1},"status":"UP"}`.

---

## 3. Las rebanadas que faltan

Cada una es vertical: dominio, puerto, adaptador, controlador y test, funcionando de punta a punta.

### Bloque A — Completar el evento

**R4. Editar, publicar y cancelar** ✓ Completada.

### Bloque B — Identidad del anfitrión

**R5. Usuario y seguridad**
Registro, login y la cadena de Spring Security. Salda D-1 y D-2: el `hostId` sale del DTO y pasa a venir del usuario autenticado. Dos reglas de acceso desde el principio: `/api/events/**` autenticado, `/api/invitations/**` público resuelto por token.

RF-1.2 (verificación de correo) y RF-1.3 (recuperación de contraseña) quedan fuera de esta rebanada: dependen del proveedor de correo, cuya elección sigue pendiente (ver §5). Se implementarán en R12, después de R10.

### Bloque C — El núcleo del producto

**R6. Invitados**
Alta con nombre y correo o teléfono, normalización a E.164, detección de duplicados, y generación del token criptográficamente aleatorio. Salda D-3: aquí es donde el índice único sobre `guests.token` deja de ser opcional.

También en esta rebanada (RF-2.6): editar datos de un invitado (`PUT /api/events/{id}/guests/{guestId}`) y eliminarlo (`DELETE /api/events/{id}/guests/{guestId}`) mientras el evento no esté finalizado. Regla de cascada confirmada: un invitado sin respuesta se elimina directamente; un invitado con respuesta requiere un parámetro de confirmación explícito en la petición (`confirmDeleteResponse=true`), y la capa de aplicación borra en cascada su documento de `responses` antes de eliminar al invitado del evento. Sin el parámetro de confirmación, 409.

RF-4.2 (conjuntos predefinidos de opciones): los presets *Asisto / No asisto*, *Asisto / No asisto / Tal vez* y *Confirmo / Declino / Pendiente* pueden vivir enteramente en el frontend como listas hardcodeadas que el cliente envía al crear el evento. El backend ya acepta cualquier combinación válida. No se necesita endpoint nuevo; confirmar con el equipo de frontend antes de empezar R6.

**R7. Página pública del invitado**
`GET /api/invitations/{token}`. Devuelve el evento tal como lo ve ese invitado. El filtrado del enlace de la reunión ya tiene su regla de dominio lista desde R3 (`Event.visibleOnlineAccess(guestHasConfirmed, now)`, hoy solo probada de forma aislada); este endpoint le pasa el estado real del invitado y necesita su propio DTO de respuesta — distinto de `EventResponse`, que es la vista del anfitrión y expone el enlace sin filtrar (ver comentario en `EventResponse`).

**R8. Registrar respuesta**
`POST /api/invitations/{token}/response`. Crea la colección `responses`, con el índice único sobre `(eventId, guestToken)` y el upsert al cambiar de respuesta. Aquí se implementa que `ANSWERED` se derive y no se almacene. También salda D-7: `replaceResponseOptions(...)` empieza a recibir el conjunto de ids de opciones con respuestas para poder rechazar su eliminación y aceptar rename/add.

RF-4.7 (límite de cambio de respuesta): los campos `allowResponseChange` y `responseDeadline` ya existen en el dominio desde R4 pero ningún endpoint los comprueba. En esta rebanada, el endpoint de respuesta debe verificar ambas condiciones antes de aceptar una respuesta nueva sobre una existente, y devolver 409 si el plazo venció o el anfitrión no lo permite.

RF descarga de calendario: `GET /api/invitations/{token}/calendar` devuelve un archivo `.ics` con los datos del evento. Se puede devolver como `text/calendar` directamente. Añadir aquí: el frontend solicita la descarga desde la pantalla de confirmación (flujo de respuesta, paso 4 del MVP §6).

**R9. Panel del anfitrión**
Contadores por opción de respuesta y listado de invitados con su estado. Es la primera vez que necesitas la agregación `$lookup` entre `events` y `responses`.

RF-1.5 (contadores en el listado): `EventSummaryResponse` hoy solo tiene `id`, `title`, `startsAt`, `modality` y `status`. Aquí se le añaden los contadores de respuesta (`totalGuests`, `answered`, `pending`, por opción de respuesta), calculados con la misma agregación del panel. El listado de eventos `GET /api/events?hostId=` empieza a tener sentido para el anfitrión solo cuando muestra estos datos.

### Bloque D — Envío de invitaciones

**R10. Envío por correo**
Plantilla con las variables del evento y el enlace único. Requiere decidir proveedor (ver §5 — urgencia alta, bloquea esta rebanada).

RF-2.14 (envío en bloque por correo): el endpoint de envío debe aceptar un listado de tokens además del individual, o exponer un endpoint `POST /api/events/{id}/invitations/send` que tome una lista.

RF-2.15 (plantilla editable): la plantilla vive como campo `messageTemplate` en el documento del evento (campo nulo = texto por defecto generado en el backend). No hay colección separada porque no existen plantillas reutilizables entre eventos en el alcance del MVP. Se edita con el mismo `PUT /api/events/{id}` del bloque básico.

RF-2.16 (copiar enlace individual): puramente frontend; no requiere endpoint nuevo — el enlace es `<base-url>/r/{token}` y el token ya viene en `EventResponse`.

RF-2.17 (reenvío): el reenvío a quien no ha respondido es un filtro sobre la lista de invitados con `invitationStatus != OPENED && ANSWERED derivado == false`; el endpoint de envío en bloque de correo cubre este caso si acepta una lista de tokens.

**R11. Enlaces de WhatsApp y Telegram**
El backend construye el enlace profundo y lo devuelve; el envío lo hace el anfitrión. Estados `SENT` y `OPENED` con actualización posicional atómica.

RF-2.14 (envío en bloque por WhatsApp/Telegram): el endpoint puede devolver una lista de enlaces profundos para que el frontend los abra en secuencia asistida. No hay trabajo adicional de backend salvo exponer el endpoint en forma de lista.

### Bloque E — Identidad completa

**R12. Verificación de correo y recuperación de contraseña**
RF-1.2 y RF-1.3. Dependen del proveedor de correo decidido en R10. Implementar después de R10, no antes. Endpoints: `POST /api/auth/verify-email` (confirma el token del correo de verificación), `POST /api/auth/resend-verification`, `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`.

---

## 4. La decisión que hay que tomar ahora

**¿Cuándo vuelve Spring Security?**

Lo puse en R5, entre el bloque del evento y el del invitado, y no al final. La razón: a partir de R6 todo lo que construyas depende del modelo de autorización. La página del invitado necesita que `/api/invitations/**` sea público mientras el resto no lo es, y el panel necesita saber quién es el anfitrión que consulta. Si construyes eso sin seguridad y la añades después, tocas todos los controladores otra vez.

La alternativa es dejarla para el final y aceptar la reescritura. Es defendible si prefieres validar antes el flujo completo del producto, que es lo que de verdad hace distinta a gtog.

**Mi recomendación es R5 donde está.** Retrasarla más significa que el `hostId` de texto se propaga a cuatro o cinco endpoints antes de desaparecer.

---

## 5. Decisiones que siguen pendientes

| Decisión | Bloquea | Urgencia |
|---|---|---|
| **Proveedor de correo** (SES, SendGrid, SMTP) | R5 (RF-1.2, RF-1.3 excluidos hasta decidir), R10 (RF-2.10), R12 (RF-1.2, RF-1.3) | **Alta** |
| **¿Puede el anfitrión registrar a mano la respuesta de un invitado?** | R8, R9 | Baja, pero decide el modelo |
| **Formato del token**: longitud y alfabeto | R6 | Baja, pero irreversible una vez haya enlaces circulando |
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
