# gtog

Antes de implementar, lee docs/definicion-funcional.md y el modelo del agregado que toques.

Backend de **gtog**, una aplicación de organización de eventos. El anfitrión crea un evento, invita por correo, WhatsApp o Telegram, y cada invitado responde desde un enlace único **sin crear cuenta**.

La especificación funcional está en `docs/definicion-funcional.md`. **Léela antes de implementar cualquier funcionalidad nueva.** Si algo de este archivo contradice la especificación, gana la especificación y avísame del conflicto.

---

## Cómo trabajamos

- Antes de escribir código, presenta un plan y espera mi aprobación.
- Trabajamos por rebanadas verticales: dominio, puerto, adaptador, controlador y test, funcionando de punta a punta antes de pasar a la siguiente.
- Consulta los cinco documentos del proyecto:
    - `docs/definicion-funcional.md`: contexto funcional, alcance y requisitos.
    - `docs/plan-iteracion-1.md`: estado, decisiones, deuda y desarrollo pendiente.
    - `docs/modelo-evento.md`: contrato técnico del agregado de evento.
    - `docs/modelo-invitado.md`: contrato técnico del invitado dentro de event.
    - `docs/modelo-usuario.md`: contrato técnico del agregado de usuario.
- Las decisiones y sus razones están en `docs/plan-iteracion-1.md`, sección Decisiones tomadas. Si propones contradecir alguna, léela primero y explica por qué hay que revisarla. No la cambies sin avisar.
- Si estas instrucciones, los documentos y el código divergen, avísame; no decidas por tu cuenta cuál gana. Las decisiones aprobadas se reflejan en estas instrucciones y en los documentos del proyecto.

---

## Stack

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.x (Spring Framework 7) |
| Arquitectura | Hexagonal (puertos y adaptadores) |
| Base de datos | MongoDB Atlas |
| Migraciones | Ninguna por ahora, ver más abajo |
| Tests | JUnit Jupiter 6 (transitivo de Spring Boot 4) |

Paquete raíz: `com.gtog`. Artefacto: `gtog`.

---

## Idioma

- **En inglés:** nombres de clases, métodos, variables, paquetes, rutas de la API, enums, colecciones y campos de MongoDB, nombres de tests y mensajes de commit.
- **En español:** comentarios en el código, documentación, y la conversación conmigo.
- **Conversación conmigo: siempre en español.** Los resúmenes, explicaciones,
  preguntas y planes van en español, aunque el código y los identificadores estén
  en inglés.

La especificación está escrita en español y el código no. Usa esta tabla de equivalencias para no acabar con `Invitee`, `Attendee` y `Guest` conviviendo en el mismo proyecto:

| Especificación | Código |
|---|---|
| Evento | `Event` |
| Anfitrión | `Host` |
| Invitado | `Guest` |
| Opción de respuesta | `ResponseOption` |
| Respuesta | `GuestResponse` |
| Ubicación | `Venue` |
| Acceso en línea | `OnlineAccess` |
| Estado de invitación | `InvitationStatus` |
| Modalidad | `Modality` |
| Canal de envío | `DeliveryChannel` |
| Muro social | `WallPost` |

---

## Comandos

Se usa Maven por defecto, sin flags de configuración adicionales.

Las salidas de comandos que necesites guardar van a `execution-logs/` en la raíz del proyecto, ignorada por Git; nunca a `/tmp` ni a `$TMPDIR`.

No imprimas ni vuelques variables de entorno: no ejecutes `env`, `printenv` ni `set` para inspeccionarlas. `MONGODB_URI`, `MAILJET_API_KEY` y `MAILJET_SECRET_KEY` llevan credenciales; nunca muestres sus valores ni los guardes en ficheros o logs.

```bash
mvn clean verify
mvn test
mvn spring-boot:run
```

La base de datos es **MongoDB Atlas**, no local. Hace falta la variable de entorno `MONGODB_URI` (la cadena de conexión de Atlas, sin nombre de base) en el entorno antes de arrancar la aplicación o de lanzar los tests de integración. Nunca la pongas en un archivo versionado: lleva credenciales.

- `application.properties` usa `${MONGODB_URI}` con la base `gtog_dev`.
- `src/test/resources/application-test.properties` usa `${MONGODB_URI}` con la base `gtog_test`, distinta de la de desarrollo.
- En Spring Boot 4 el prefijo de propiedad es `spring.mongodb`, no `spring.data.mongodb` (ese era el de la rama 3.x). Las propiedades son `spring.mongodb.uri` y `spring.mongodb.database`.

Desde R12, POST /api/events devuelve 403 con code=EMAIL_NOT_VERIFIED si el correo del anfitrión no está
verificado. Los recorridos .http registran anfitriones @example.com, que no tienen buzón: el primer POST
/api/events de cada recorrido dará 403. Para desarrollo local, añade APP_AUTH_AUTOVERIFYEMAIL=true a la
configuración de ejecución de la aplicación (equivale a app.auth.auto-verify-email=true): verifica la cuenta
al registrarla y no envía correo de verificación. Para la aplicación real y para los tests, déjala sin definir.
No la añadas a la configuración de ejecución de JUnit ni a la plantilla de JUnit del IDE.
La aplicación se niega a arrancar si está activa y app.public-base-url no apunta a localhost o 127.0.0.1.
Para probar la verificación real, arranca sin esa variable y usa rest-api-verify.http, con una dirección real
del entorno privado y el token del fragmento del correo. No ejecutes ese recorrido automáticamente.

---

## Arquitectura

Hexagonal, un hexágono por contexto. Estructura de paquetes:

```
com.gtog
├── event/
│   ├── domain/
│   │   ├── model/              Event, Venue, OnlineAccess, ResponseOption
│   │   └── port/
│   │       ├── in/             interfaces de los casos de uso
│   │       └── out/            EventRepositoryPort, ...
│   ├── application/            implementación de los casos de uso
│   └── infrastructure/
│       ├── in/web/             controladores + DTOs de request y response
│       └── out/persistence/    documentos de Mongo, adaptadores y mappers
├── invitation/                 envío de correo, enlaces profundos de WhatsApp y Telegram
├── user/
└── shared/                     excepciones, ProblemDetail, configuración transversal
```

Los invitados viven dentro del hexágono `event`: no tienen vida independiente del evento y comparten agregado y documento. No crees un hexágono `guest` separado en R6.

Las cinco reglas que hacen que esto sea hexagonal de verdad y no tres carpetas con nombres bonitos:

1. **`domain/` no lleva ni una anotación de framework.** Nada de `@Document`, `@Id`, `@Component`, Jackson ni validación de Jakarta. Java plano. Si te encuentras importando `org.springframework` o `org.bson` dentro de `domain/`, para y replantea.

2. **El modelo de dominio y el documento de Mongo son clases distintas.** `Event` vive en `domain/model`; `EventDocument` vive en `infrastructure/out/persistence`, y un mapper traduce entre ambos. Sí, es código extra. Es justamente el propósito: el día que el modelo de persistencia cambie de forma, el dominio no se mueve.

3. **Las dependencias apuntan solo hacia dentro.** `application` depende de `domain`. `infrastructure` depende de los dos. `domain` no depende de nada.

4. **Los casos de uso son interfaces en `port/in`**, implementadas en `application`. El controlador depende de la interfaz, nunca de la clase concreta.

5. **Los puertos de repositorio son interfaces en `port/out`**, implementadas por adaptadores en `infrastructure/out/persistence`. El puerto habla el lenguaje del dominio y devuelve objetos de dominio.

---

## Modelo de datos

La separación de colecciones es deliberada: **configuración, transacciones, credenciales y contenido tienen patrones de escritura y curvas de crecimiento distintas.**

| Colección | Contenido | Escritura |
|---|---|---|
| `users` | Cuentas de anfitrión | Poco frecuente |
| `events` | El evento como un todo: fechas, lugar o acceso en línea, opciones de respuesta y la lista de invitados embebida | La hace el anfitrión, poco frecuente, un solo escritor |
| `responses` | **Un documento por respuesta de invitado** | La hacen los invitados, concurrente, a ráfagas tras el envío |
| `auth_tokens` | Hash SHA-256 de tokens de autenticación, usuario, propósito y caducidad | Emisión y consumo atómicos; nunca el token en claro |
| `wall_posts` *(iteración 2)* | Contenido social, referenciando el evento | La hacen los invitados, crecimiento sin límite |

### `events`

Embebe `responseOptions[]` y `guests[]`. Cada invitado lleva su identidad, datos de contacto, canal de envío y estado de invitación, **pero no su respuesta**.

El documento está acotado: incluso con mil invitados queda muy por debajo del límite de 16 MB. Se lee en casi todas las peticiones y se escribe poco, que es exactamente la forma que le conviene a un documento único.

Índices:
- `guests.token` — único, multiclave. Es la vía de acceso de toda petición pública de invitación.
- `hostId` — la consulta del panel del anfitrión.

### `responses`

Un documento por invitado, creado o reemplazado cuando responde:

```
{ eventId, guestToken, responseOptionId, comment, email, answeredAt, updatedAt }
```

Índices:
- `(eventId, guestToken)` — único. Esto es lo que impone "una respuesta por invitado" a nivel de base de datos, no el código.
- `eventId` — para la agregación del panel.

Que un invitado cambie su respuesta es un **upsert sobre esa clave única**, no un documento nuevo.

### `wall_posts` (iteración 2)

Referencia `eventId`. Nunca embebido en el evento: fotos y comentarios crecen sin límite y acabarían chocando con el tamaño máximo de documento. Los comentarios sí pueden ir embebidos en su publicación; los contadores de reacciones se actualizan con `$inc`, nunca leyendo y reescribiendo.

Desde R12, auth_tokens tiene índice único del hash y de (userId, purpose), más TTL sobre expiresAt. Emitir
sustituye por upsert; consumir usa find-and-delete por hash, propósito y expiresAt > ahora, desde Clock.
La verificación es pública con CSRF; /me y resend-verification requieren sesión. La puerta de creación
consulta HostEmailVerificationPort en cada POST /api/events, nunca el flag de la sesión. Los contadores
authEmailSends/authEmailWindowStartedAt/lastAuthEmailAt son propios: no reutilizar la cuota de invitaciones.

### Consistencia: derivar en lugar de duplicar

Separar las respuestas implica que un invitado al contestar tendría que escribir en dos colecciones: el documento de respuesta y su estado dentro del evento. Hacerlo sin transacción deja una ventana en la que la respuesta existe pero el invitado sigue apareciendo como pendiente.

**No almacenes `ANSWERED` en el evento. Derívalo.** Un invitado ha respondido si y solo si existe un documento de respuesta con su token.

- `NOT_SENT` y `SENT` viven en el evento; los escribe el anfitrión.
- `OPENED` vive en el evento; se escribe una vez al abrir el enlace y es idempotente.
- `ANSWERED` se calcula en lectura consultando `responses`.

El panel del anfitrión lo resuelve con una agregación `$lookup` de `events` a `responses`, o con dos consultas unidas en la capa de aplicación. Cualquiera de las dos vale: elige una y sé consistente.

La ganancia: responder escribe solo en `responses`, sin sincronizar un estado duplicado en `events`.
La excepción es el borrado en cascada de un invitado (R8): se retira de `events` antes de limpiar `responses`,
sin transacción multidocumento. Un fallo de limpieza deja una respuesta inalcanzable, nunca una respuesta
perdida con el invitado vivo.

### Concurrencia

La separación elimina el riesgo principal, pero sobrevive una regla. Al actualizar un invitado concreto dentro del documento del evento —marcar `SENT` u `OPENED`— usa siempre una actualización posicional, nunca leer, modificar y guardar:

```java
// correcto
mongoTemplate.updateFirst(
    query(where("guests.token").is(token)),
    new Update().set("guests.$.invitationStatus", OPENED)
                .set("guests.$.openedAt", Instant.now()),
    EventDocument.class);

// incorrecto: reescribe el documento entero y pierde escrituras concurrentes
EventDocument event = repository.findByGuestToken(token);
event.getGuests().stream()...setStatus(OPENED);
repository.save(event);
```

Las ediciones del anfitrión sobre el cuerpo del evento sí pueden usar `save()` con bloqueo optimista `@Version`, porque solo hay un escritor.

### Otras reglas de persistencia

- **Actualizaciones de users siempre parciales.** El documento no tiene @Version: leerlo y hacer save() puede pisar los contadores de cuota concurrentes. Para modificar usuarios existentes usa $set/$inc solo de los campos afectados; la inserción del registro inicial es distinta. R12 debe aplicar esta regla al escribir emailVerified o restablecer la contraseña.
- **R10, reservas de correo:** cuotas del invitado mediante $elemMatch e incremento de version; cuota del anfitrión en users con pipeline atómico. HostSendQuotaPort pertenece al dominio de event y su adaptador a infraestructura de user. Compensar primero anfitrión y después invitado, una sola vez y sin reintentos; una compensación fallida conserva el bloqueo y al alcanzar el máximo requiere intervención manual. Diagnóstico: ERROR EMAIL_SEND_COMPENSATION_FAILED, clase y correlación, nunca correo, token ni mensaje original. Reservas, SMTP y endpoint implementados. Si SMTP aceptó pero falla registrar SENT, conserva reservas y devuelve SENT/recorded=false; ERROR EMAIL_SENT_RECORDING_FAILED. Nunca reenvíes automáticamente. Ver modelos de invitado y usuario.
- **Correo:** EmailSenderPort en event, SMTP Mailjet en infraestructura con el starter existente. TLS obligatorio y sin reintentos; management.health.mail.enabled=false, comprobado en Boot 4.1. Sin credenciales/remitente arranca con EMAIL_NOT_CONFIGURED: fallo de proveedor, compensa y cuenta para el corte tras tres; nunca un envío ficticio. En tests solo el capturador de src/test con perfil test o JavaMailSender simulado, nunca correo real. Texto plano fijo, un destinatario, sin CC/BCC ni Reply-To; el renderizador recibe solo plataforma, nunca secretos de sala. Errores SMTP: código y log WARN SMTP_SEND_FAILED con clase/correlación, sin mensaje ni causa. rest-api-mail.http es manual y envía correo real; no lo ejecutes como test automático. Ver docs/modelo-invitado.md.
- **R11 solo WhatsApp; Telegram aplazado.** Generar whatsapp-links es lectura: no escribe ni usa reservas/cuotas de R10. Máximo propio 100; mensaje sin secretos de sala. InvitationMessageFormatter comparte formato con correo; nombres, título y lugar WhatsApp se limpian y recortan a 100 grafemas con BreakIterator (incluidos '...'), nunca por puntos de código. UTF-8 con %20/%0A; URL final hasta 2000 caracteres, cota conservadora no verificada. La respuesta contiene credenciales: no-store y ningún log. whatsapp-sent exige lista explícita y PUBLISHED: dos actualizaciones con $elemMatch y teléfono en el filtro; NOT_SENT pasa a SENT y OPENED sin sentAt completa WHATSAPP/fecha sin rebajar estado. Cada cambio incrementa version; repetición conserva primer canal/fecha. Un cierre a mitad de lote devuelve 409 y puede conservar marcas previas: idempotente, sin rollback. SENT es declarativo. rest-api-whatsapp.http es manual; solo un paso imprime la URL, no lo ejecutes automáticamente. Ver docs/modelo-invitado.md.

- **Índices creados en el arranque**, mediante un componente dedicado en `shared` que use `MongoTemplate.indexOps(...)` y sea idempotente. No los declares con anotaciones `@Indexed`: quedan repartidos por el modelo y no se sabe qué existe en producción sin mirar la base de datos.
- **Sin herramienta de migraciones por ahora.** Mongock no tiene compatibilidad confirmada con Spring Boot 4 y Spring Data MongoDB 5. Mientras el proyecto no tenga datos reales, el coste de no tenerla es cero. Cuando haga falta una migración de datos de verdad, lo evaluamos entonces. No la añadas por tu cuenta.
- Enums persistidos como cadenas, nunca como ordinal.
- Marcas de tiempo en UTC. La zona horaria del evento es un campo aparte.
- Teléfonos en formato **E.164**, normalizados al escribir.
- **Sin integridad referencial nativa.** MongoDB no impone restricciones de clave foránea entre colecciones. Quien elimina un elemento de `events` es responsable de borrar en cascada lo que depende de él en `responses` y, cuando existan, en `wall_posts`. La cascada se orquesta en aplicación: para el invitado, exige confirmación si ya respondió, retíralo primero de `events` y solo después limpia su respuesta. Si falla la retirada, no borres la respuesta. Nunca delegues la cascada al dominio ni confíes en que la base de datos la gestione.
- Si en algún momento crees que una operación necesita una transacción multidocumento, para y dímelo: significa que los límites entre colecciones están mal trazados.

---

## Convenciones de código

**`record` para DTOs y objetos de valor.** Sin Lombok en este proyecto.

```java
public record CreateEventRequest(
    String title,
    String description,
    @NotNull LocalDateTime startsAt,
    @NotNull LocalDateTime endsAt,
    @NotBlank String timeZone,
    @NotNull Modality modality,
    List<@Valid CreateResponseOptionRequest> responseOptions,
    Boolean allowComment,
    Boolean allowResponseChange,
    LocalDateTime responseDeadline,
    VenueRequest venue,
    OnlineAccessRequest onlineAccess
) {}
```

`hostId` se obtiene del principal autenticado, nunca del cuerpo. Las fechas del evento son horas locales (`LocalDateTime`) más `timeZone`; el instante se calcula al consultar. Los instantes de auditoría siguen en UTC.

**Inyección por constructor, siempre.** Nunca `@Autowired` sobre campos.

El calendario usa iCal4j 4.2.0 únicamente en `infrastructure/out/calendar`, detrás de un puerto. Su configuración
vive en `src/main/resources/ical4j.properties`: `net.fortuna.ical4j.timezone.update.enabled=false` se carga antes
del primer uso de la librería, sin actualizaciones remotas de zonas. El renderizador solo recibe datos públicos
y la URL ya filtrada; UID basado en el evento, nunca en el token; DTSTAMP obtenido del Clock inyectado.

**Los objetos de dominio no cruzan la frontera web.** Los controladores reciben y devuelven DTOs definidos en `infrastructure/in/web`. No serialices nunca un modelo de dominio ni un documento de Mongo a JSON.

**Sin lógica de negocio en los controladores.** El controlador valida la entrada, llama al caso de uso y mapea el resultado.

**La validación se reparte en dos sitios:** las anotaciones de Jakarta sobre el record de entrada cubren el formato (tipos, rangos, patrones); las reglas de negocio (mínimo dos opciones de respuesta, al menos una que cuente como asistencia, restricciones de opciones en `PUBLISHED`) viven en el dominio.

**Si la obligatoriedad de un campo es en sí una regla de negocio, no la captures con Jakarta.** Nada de `@NotBlank`/`@NotNull` en el DTO para ese campo: se valida en el dominio, con su propia excepción, y sale como 422. Si Jakarta la bloqueara antes, respondería 400 y la regla de negocio no llegaría nunca a ejecutarse — ni a poder probarse a través del controlador. Ejemplos ya en el código: `title` de `CreateEventRequest` (`BlankEventTitleException`), `label` de las opciones de respuesta (`BlankResponseOptionLabelException`), y los campos obligatorios de `Venue` y `OnlineAccess` (`MissingVenueFieldException`, `MissingOnlineAccessFieldException`). Por contraste, `timeZone` del evento lleva `@NotBlank` para exigir su presencia; el dominio comprueba que sea una zona válida con `ZoneId.of(...)`. `hostId` ya no forma parte de `CreateEventRequest` desde R5.

---

## Convenciones de API

- Sustantivos en plural y en inglés: `/api/events`, `/api/events/{eventId}/guests`.
- Los endpoints públicos del invitado van bajo `/api/invitations/{token}`, separados del resto para que la regla de seguridad se vea en la propia ruta.
- Códigos: `201` al crear con cabecera `Location`, `204` al borrar, `409` en conflicto de estado, `422` cuando una regla de negocio rechaza la petición.
- Errores con **`ProblemDetail`** (RFC 7807), gestionados en `shared`: advice de dominio con prioridad alta y advice MVC con menor prioridad para errores de framework y fallback. Los filtros de seguridad escriben el mismo formato directamente. No inventes un formato de error propio.

**CSRF en login y registro: deliberado.** Tanto `POST /api/auth/login` como `POST /api/auth/register` requieren un token CSRF válido, enviado en `X-XSRF-TOKEN`. No los excluyas de la protección: excluir el login permitiría que un atacante autenticase a la víctima en la cuenta del atacante (login-CSRF). Ver `docs/plan-iteracion-1.md`, sección Decisiones tomadas, para la justificación.

El login manual rota el id de sesión y la registra en SessionRegistry con la estrategia compartida, pero
no emite ni renueva XSRF-TOKEN. No añadas CsrfAuthenticationStrategy a esa composición. AuthenticatedUser
se compara por userId. SessionInvalidationPort expira las sesiones del usuario; el filtro invalida HttpSession
en el siguiente acceso y escribe 401 ProblemDetail con instance constante y no-store exacto. Solo una instancia.

Desde R8, **solo `/api/invitations/**` está excluido de CSRF**: su credencial es el token de la ruta, no una cookie; no hay credencial ambiental que un tercero pueda aprovechar. No amplíes la exclusión a `/api/**`. Los errores de invitación usan instancia constante `/api/invitations` y `Cache-Control: no-store`; nunca conserves mensajes ni causas de errores Mongo que incluyan tokens.

El email opcional de GuestResponse se mantiene almacenado, sin mostrarlo al anfitrión ni a otros invitados y sin usarlo para envíos en R10. No debilites el test de privacidad del panel de R9. Cualquier uso futuro exige aprobación expresa y doble confirmación de la dirección antes de escribirle: el PUT público acepta direcciones sin conocimiento de su dueño. RF-2.15 (plantilla editable) queda fuera del MVP; el correo de invitación será texto fijo en español.

**Enums**

```java
EventStatus       { DRAFT, PUBLISHED, FINISHED, CANCELLED }
InvitationStatus  { NOT_SENT, SENT, OPENED }   // ANSWERED se deriva, no se almacena
Modality          { IN_PERSON, ONLINE }
DeliveryChannel   { EMAIL, WHATSAPP, TELEGRAM, LINK }
LinkVisibility    { ON_CONFIRMATION, HOURS_BEFORE, ALWAYS }
```

---

## Documentación de API

La API se documenta con **springdoc-openapi** (`springdoc-openapi-starter-webmvc-ui`, versión `3.1.0`, la rama compatible con Spring Boot 4). Con la aplicación arrancada:

- Especificación OpenAPI en JSON: `/v3/api-docs`.
- Swagger UI: `/swagger-ui.html`.

El `@Bean OpenAPI` con el título, versión y descripción de la API vive en `shared/config/OpenApiConfig`.

**Las anotaciones de Swagger van únicamente en `infrastructure/in/web`**, sobre controladores y DTOs de request y response (`@Operation`, `@ApiResponse`, `@Schema`, etc.). Ni una sola en `domain/`: es la misma regla que prohíbe anotaciones de framework ahí (ver sección Arquitectura), y por la misma razón — el dominio no sabe que existe una API HTTP por encima.

---

## Reglas de negocio que no se pueden romper

Estas reglas son el contrato final del producto, no necesariamente lo que el código ya impone hoy: algunas dependen de rebanadas que todavía no existen. Cuando una regla tiene una parte pendiente, lo dice explícitamente — si esta sección afirmara algo que el código no cumple sin avisarlo, dejaría de servir para nada.

1. **El enlace de la reunión en línea se filtra en el backend.** Si el invitado no cumple la regla de `LinkVisibility`, el campo se omite de la respuesta. Nunca lo envíes al cliente confiando en que Angular no lo pinte: viajaría en el JSON y sería legible desde las herramientas del navegador. La regla de dominio (`Event.visibleOnlineAccess(...)`, R3) se aplica en la vista pública de GET, PUT y calendario, exclusivamente para eventos `PUBLISHED`. Desde R8, `ON_CONFIRMATION` exige respuesta propia cuya opción tenga `countsAsAttendance = true`; sin respuesta o con opción inexistente, false. Responder "No asisto" no confirma asistencia. D-CONFIRMED-LINK saldada.

2. **El token es la única credencial del invitado.** Criptográficamente aleatorio, largo, con índice único y revocable. Nunca derivado del identificador ni de datos del invitado.

3. **El envío por WhatsApp y Telegram no es automático.** El backend construye el enlace profundo y lo devuelve; el envío real lo hace el anfitrión desde su cliente y lo confirma a mano. No implementes ni propongas integración con la API de WhatsApp Business.

4. **Las opciones de respuesta las define el anfitrión.** No hay enum fijo de respuestas. Entre dos y cinco opciones, ordenadas, con etiquetas únicas y no vacías y al menos una que cuente como asistencia. En DRAFT pueden cambiarse; en PUBLISHED se pueden renombrar y agregar, pero no eliminar una opción con respuestas ni cambiar su `countsAsAttendance` (409 con excepciones propias). La aplicación consulta los ids con respuestas y los pasa al dominio, que no conoce Mongo. CANCELLED y FINISHED bloquean los cambios. D-7 saldada en R8; la carrera entre responder y eliminar una opción queda en D-ORPHAN-OPTION, sin transacciones.

5. **Un evento es presencial o en línea, nunca ambos.** La modalidad híbrida está fuera de alcance a propósito en esta iteración.

6. **Los teléfonos se guardan en E.164 o no se guardan.**

7. **Una respuesta por invitado, impuesta por el índice único** sobre `(eventId, guestToken)`. Cambiar de respuesta es un upsert, no un segundo documento. No lo resuelvas con comprobaciones en la capa de aplicación.

---

## Tests

- Dominio y casos de uso: tests unitarios planos, sin contexto de Spring. Deben ser rápidos, y la arquitectura hexagonal es justamente lo que lo permite: si un test de caso de uso necesita Spring, las dependencias están mal.
- Adaptadores y flujos completos: tests de integración con `@SpringBootTest @ActiveProfiles("test")` contra MongoDB Atlas (base `gtog_test`). La conexión está en `src/test/resources/application-test.properties`. Hace falta `MONGODB_URI` en el entorno antes de lanzar los tests (ver sección Comandos). Nada de Mongo embebido.
- También en los tests de integración, inyecta los beans por constructor en campos `final`, con `@TestConstructor(autowireMode = ALL)`, sin `@Autowired` en campos. `@MockitoBean` y `@MockitoSpyBean` se mantienen para declarar los dobles del contexto.
- Los tests fijan explícitamente el bypass con propiedades de SpringBootTest o con @AuthBypassDisabled. Nunca configures APP_AUTH_AUTOVERIFYEMAIL en JUnit; el ejecutor de correo de autenticación es síncrono y el transporte es falso en tests.
- Los tests de integración limpian las colecciones que usan antes de cada test (`@BeforeEach`) para no depender del orden de ejecución.
- Nombres descriptivos en inglés: `doesNotAllowDeletingOptionWithAttachedResponses()`.
- Cada regla de la sección anterior necesita al menos un test que la cubra. La de concurrencia necesita un test con escrituras en paralelo.

---

## Git

- Se trabaja directamente sobre `main` hasta el primer despliegue, por decisión del usuario. En ese momento se reconsiderará el uso de ramas `feat/nombre-corto` y `fix/nombre-corto`.
- Commits convencionales en inglés: `feat(guest): generate unique token on guest creation`.
- No hagas commit sin que `mvn clean verify` pase en verde antes de cada uno.
- Sin push salvo petición expresa del usuario.
- Nunca subas `.env`, credenciales ni la clave de Google Maps.

---

## Lo que no debes hacer

- **No añadas funcionalidad de iteraciones futuras.** Muro social, votación de varias fechas, acompañantes, check-in y modalidad híbrida están fuera de alcance a propósito. Si crees que algo lo necesita, pregunta antes.
- **No añadas dependencias sin preguntar.** MapStruct, Lombok, ModelMapper y similares son decisiones de arquitectura, no detalles.
- **No pongas anotaciones de framework en `domain/`.** Es la forma más fácil de destruir la arquitectura sin darse cuenta.
- **No generes todas las colecciones y casos de uso de golpe.** Trabajamos por rebanada vertical: dominio, puerto, adaptador, controlador y test, funcionando de punta a punta antes de pasar a la siguiente.
- **No te fíes de los ejemplos de Spring Boot 3.x.** La rama 4.x movió paquetes y configuración. Si algo no compila como dice el ejemplo que estés siguiendo, suele ser por eso.
  Casos concretos ya encontrados: `@AutoConfigureMockMvc` pasó a `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`; el prefijo de Mongo es `spring.mongodb.*` (no `spring.data.mongodb.*`); el indicador de salud Mongo se desactiva con `management.health.mongodb.enabled` (valor `false` para desactivarlo).
- **No adivines la especificación.** Si falta un detalle en `docs/`, pregúntame en lugar de inventar un comportamiento razonable.
