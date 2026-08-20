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
| D-7 | **"Renombrar pero no eliminar" sin implementar** | La regla de negocio 4 (`CLAUDE.md`) exige que una opción de respuesta con respuestas asociadas no se pueda eliminar tras publicar. `Event.replaceResponseOptions(...)` hoy permite quitar cualquier opción: el dominio no conoce la colección `responses` todavía, así que no puede saber qué ids tienen respuestas. Documentado en `docs/modelo-evento.md`. | Rebanada 8: `replaceResponseOptions(...)` tendrá que recibir el conjunto de ids con respuestas, calculado en la capa de aplicación |

**D-4, saldada.** Decía "health indicator de Mongo desactivado", y no era así: estaba activo y en `DOWN`. El indicador por defecto de Actuator recorre **todas** las bases que el `MongoClient` ve vía `listDatabaseNames()` — no solo `local`, también `admin`, `config`, la de la propia app — y ejecuta `hello` en cada una; en Atlas el usuario de la aplicación no tiene permiso sobre `local`, así que el chequeo entero caía con `DOWN` aunque `gtog_dev`/`gtog_test` respondieran sin problema. Comprobado arrancando la app. Sustituido por `MongoDatabaseHealthIndicator` (en `shared/config`), que hace `ping` solo contra la base configurada de la aplicación; el indicador por defecto se desactiva con `management.health.mongodb.enabled=false` en ambos `application*.properties` para que no convivan. Verificado de nuevo: `GET /actuator/health` → `"mongo":{"details":{"database":"gtog_dev","ping":1},"status":"UP"}`.

---

## 3. Las rebanadas que faltan

Cada una es vertical: dominio, puerto, adaptador, controlador y test, funcionando de punta a punta.

### Bloque A — Completar el evento

**R4. Editar, publicar y cancelar**
La máquina de estados: `publish()` solo desde `DRAFT`, `cancel()` desde `PUBLISHED`. Es donde se prueba de verdad el `@Version`. También cubre la edición general del evento (título, fechas, descripción) y, en concreto, **qué pasa con `venue`/`onlineAccess` al cambiar de modalidad** — decisión que quedó explícitamente pendiente en R3 (ver §5).

### Bloque B — Identidad del anfitrión

**R5. Usuario y seguridad**
Registro, login y la cadena de Spring Security. Salda D-1 y D-2: el `hostId` sale del DTO y pasa a venir del usuario autenticado. Dos reglas de acceso desde el principio: `/api/events/**` autenticado, `/api/invitations/**` público resuelto por token.

### Bloque C — El núcleo del producto

**R6. Invitados**
Alta con nombre y correo o teléfono, normalización a E.164, detección de duplicados, y generación del token criptográficamente aleatorio. Salda D-3: aquí es donde el índice único sobre `guests.token` deja de ser opcional.

**R7. Página pública del invitado**
`GET /api/invitations/{token}`. Devuelve el evento tal como lo ve ese invitado. El filtrado del enlace de la reunión ya tiene su regla de dominio lista desde R3 (`Event.visibleOnlineAccess(guestHasConfirmed, now)`, hoy solo probada de forma aislada); este endpoint le pasa el estado real del invitado y necesita su propio DTO de respuesta — distinto de `EventResponse`, que es la vista del anfitrión y expone el enlace sin filtrar (ver comentario en `EventResponse`).

**R8. Registrar respuesta**
`POST /api/invitations/{token}/response`. Crea la colección `responses`, con el índice único sobre `(eventId, guestToken)` y el upsert al cambiar de respuesta. Aquí se implementa que `ANSWERED` se derive y no se almacene. También salda D-7: `replaceResponseOptions(...)` empieza a recibir el conjunto de ids de opciones con respuestas para poder rechazar su eliminación.

**R9. Panel del anfitrión**
Contadores por opción de respuesta y listado de invitados con su estado. Es la primera vez que necesitas la agregación `$lookup` entre `events` y `responses`.

### Bloque D — Envío de invitaciones

**R10. Envío por correo**
Plantilla con las variables del evento y el enlace único. Requiere decidir proveedor.

**R11. Enlaces de WhatsApp y Telegram**
El backend construye el enlace profundo y lo devuelve; el envío lo hace el anfitrión. Estados `SENT` y `OPENED` con actualización posicional atómica.

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
| **Proveedor de correo** (SES, SendGrid, SMTP) | R10 | Media |
| **¿Puede el anfitrión registrar a mano la respuesta de un invitado?** | R8, R9 | Baja, pero decide el modelo |
| **Formato del token**: longitud y alfabeto | R6 | Baja, pero irreversible una vez haya enlaces circulando |
| **¿Qué pasa con `venue`/`onlineAccess` al cambiar de modalidad en R4?** | R4 | Media — lo razonable es descartar los datos de la modalidad anterior, pero falta confirmarlo explícitamente antes de implementarlo |

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
