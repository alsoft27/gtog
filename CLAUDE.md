# gtog

Backend de **gtog**, una aplicación de organización de eventos. El anfitrión crea un evento, invita por correo, WhatsApp o Telegram, y cada invitado responde desde un enlace único **sin crear cuenta**.

La especificación funcional está en `docs/mvp-alcance-iteracion-1.md`. **Léela antes de implementar cualquier funcionalidad nueva.** Si algo de este archivo contradice la especificación, gana la especificación y avísame del conflicto.

---

## Stack

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.x (Spring Framework 7) |
| Arquitectura | Hexagonal (puertos y adaptadores) |
| Base de datos | MongoDB Atlas |
| Migraciones | Ninguna por ahora, ver más abajo |
| Tests | JUnit 5 |

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

Este proyecto usa un **repositorio Maven local aislado**. Nunca ejecutes `mvn` sin el flag `-s`, o romperás el aislamiento.

```bash
mvn -s /Users/a.barrios.rodriguez/Desktop/code/alsoft27/maven/settings.xml clean verify
mvn -s /Users/a.barrios.rodriguez/Desktop/code/alsoft27/maven/settings.xml test
mvn -s /Users/a.barrios.rodriguez/Desktop/code/alsoft27/maven/settings.xml spring-boot:run
```

El repositorio `~/.m2/eci-repo` pertenece a otros proyectos. No lo consultes ni escribas en él. El único repositorio de este proyecto es el que indica el `settings.xml` de los comandos anteriores.

La base de datos es **MongoDB Atlas**, no local. Hace falta la variable de entorno `MONGODB_URI` (la cadena de conexión de Atlas, sin nombre de base) en el entorno antes de arrancar la aplicación o de lanzar los tests de integración. Nunca la pongas en un archivo versionado: lleva credenciales.

- `application.properties` usa `${MONGODB_URI}` con la base `gtog_dev`.
- `src/test/resources/application-test.properties` usa `${MONGODB_URI}` con la base `gtog_test`, distinta de la de desarrollo.
- En Spring Boot 4 el prefijo de propiedad es `spring.mongodb`, no `spring.data.mongodb` (ese era el de la rama 3.x). Las propiedades son `spring.mongodb.uri` y `spring.mongodb.database`.

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
├── guest/
├── invitation/                 envío de correo, enlaces profundos de WhatsApp y Telegram
├── user/
└── shared/                     excepciones, ProblemDetail, configuración transversal
```

Las cinco reglas que hacen que esto sea hexagonal de verdad y no tres carpetas con nombres bonitos:

1. **`domain/` no lleva ni una anotación de framework.** Nada de `@Document`, `@Id`, `@Component`, Jackson ni validación de Jakarta. Java plano. Si te encuentras importando `org.springframework` o `org.bson` dentro de `domain/`, para y replantea.

2. **El modelo de dominio y el documento de Mongo son clases distintas.** `Event` vive en `domain/model`; `EventDocument` vive en `infrastructure/out/persistence`, y un mapper traduce entre ambos. Sí, es código extra. Es justamente el propósito: el día que el modelo de persistencia cambie de forma, el dominio no se mueve.

3. **Las dependencias apuntan solo hacia dentro.** `application` depende de `domain`. `infrastructure` depende de los dos. `domain` no depende de nada.

4. **Los casos de uso son interfaces en `port/in`**, implementadas en `application`. El controlador depende de la interfaz, nunca de la clase concreta.

5. **Los puertos de repositorio son interfaces en `port/out`**, implementadas por adaptadores en `infrastructure/out/persistence`. El puerto habla el lenguaje del dominio y devuelve objetos de dominio.

---

## Modelo de datos

Cuatro colecciones. La separación es deliberada: **configuración, transacciones y contenido tienen patrones de escritura y curvas de crecimiento distintas.**

| Colección | Contenido | Escritura |
|---|---|---|
| `users` | Cuentas de anfitrión | Poco frecuente |
| `events` | El evento como un todo: fechas, lugar o acceso en línea, opciones de respuesta y la lista de invitados embebida | La hace el anfitrión, poco frecuente, un solo escritor |
| `responses` | **Un documento por respuesta de invitado** | La hacen los invitados, concurrente, a ráfagas tras el envío |
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

### Consistencia: derivar en lugar de duplicar

Separar las respuestas implica que un invitado al contestar tendría que escribir en dos colecciones: el documento de respuesta y su estado dentro del evento. Hacerlo sin transacción deja una ventana en la que la respuesta existe pero el invitado sigue apareciendo como pendiente.

**No almacenes `ANSWERED` en el evento. Derívalo.** Un invitado ha respondido si y solo si existe un documento de respuesta con su token.

- `NOT_SENT` y `SENT` viven en el evento; los escribe el anfitrión.
- `OPENED` vive en el evento; se escribe una vez al abrir el enlace y es idempotente.
- `ANSWERED` se calcula en lectura consultando `responses`.

El panel del anfitrión lo resuelve con una agregación `$lookup` de `events` a `responses`, o con dos consultas unidas en la capa de aplicación. Cualquiera de las dos vale: elige una y sé consistente.

La ganancia: **ninguna operación cruza dos colecciones**, así que no hacen falta transacciones multidocumento y MongoDB puede correr en local sin réplica.

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

- **Índices creados en el arranque**, mediante un componente dedicado en `shared` que use `MongoTemplate.indexOps(...)` y sea idempotente. No los declares con anotaciones `@Indexed`: quedan repartidos por el modelo y no se sabe qué existe en producción sin mirar la base de datos.
- **Sin herramienta de migraciones por ahora.** Mongock no tiene compatibilidad confirmada con Spring Boot 4 y Spring Data MongoDB 5. Mientras el proyecto no tenga datos reales, el coste de no tenerla es cero. Cuando haga falta una migración de datos de verdad, lo evaluamos entonces. No la añadas por tu cuenta.
- Enums persistidos como cadenas, nunca como ordinal.
- Marcas de tiempo en UTC. La zona horaria del evento es un campo aparte.
- Teléfonos en formato **E.164**, normalizados al escribir.
- Si en algún momento crees que una operación necesita una transacción multidocumento, para y dímelo: significa que los límites entre colecciones están mal trazados.

---

## Convenciones de código

**`record` para DTOs y objetos de valor.** Sin Lombok en este proyecto.

```java
public record CreateEventRequest(
    @NotBlank String hostId,
    String title,
    @NotNull Instant startsAt,
    @NotNull Modality modality
) {}
```

**Inyección por constructor, siempre.** Nunca `@Autowired` sobre campos.

**Los objetos de dominio no cruzan la frontera web.** Los controladores reciben y devuelven DTOs definidos en `infrastructure/in/web`. No serialices nunca un modelo de dominio ni un documento de Mongo a JSON.

**Sin lógica de negocio en los controladores.** El controlador valida la entrada, llama al caso de uso y mapea el resultado.

**La validación se reparte en dos sitios:** las anotaciones de Jakarta sobre el record de entrada cubren el formato (tipos, rangos, patrones); las reglas de negocio (mínimo dos opciones de respuesta, al menos una que cuente como asistencia, el evento debe estar en `DRAFT` para cambiar las opciones) viven en el dominio.

**Si la obligatoriedad de un campo es en sí una regla de negocio, no la captures con Jakarta.** Nada de `@NotBlank`/`@NotNull` en el DTO para ese campo: se valida en el dominio, con su propia excepción, y sale como 422. Si Jakarta la bloqueara antes, respondería 400 y la regla de negocio no llegaría nunca a ejecutarse — ni a poder probarse a través del controlador. Ejemplos ya en el código: `title` de `CreateEventRequest` (`BlankEventTitleException`), `label` de las opciones de respuesta (`BlankResponseOptionLabelException`), y los campos obligatorios de `Venue` y `OnlineAccess` (`MissingVenueFieldException`, `MissingOnlineAccessFieldException`). Por contraste, `hostId` sigue llevando `@NotBlank`: hoy es un campo suelto sin más significado de negocio que "tiene que venir algo".

---

## Convenciones de API

- Sustantivos en plural y en inglés: `/api/events`, `/api/events/{eventId}/guests`.
- Los endpoints públicos del invitado van bajo `/api/invitations/{token}`, separados del resto para que la regla de seguridad se vea en la propia ruta.
- Códigos: `201` al crear con cabecera `Location`, `204` al borrar, `409` en conflicto de estado, `422` cuando una regla de negocio rechaza la petición.
- Errores con **`ProblemDetail`** (RFC 7807), gestionados por un único `@RestControllerAdvice` en `shared`. No inventes un formato de error propio.

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

1. **El enlace de la reunión en línea se filtra en el backend.** Si el invitado no cumple la regla de `LinkVisibility`, el campo se omite de la respuesta. Nunca lo envíes al cliente confiando en que Angular no lo pinte: viajaría en el JSON y sería legible desde las herramientas del navegador. La regla de dominio ya existe (`Event.visibleOnlineAccess(...)`, R3), pero todavía no la aplica ningún endpoint: el endpoint público del invitado es R7.

2. **El token es la única credencial del invitado.** Criptográficamente aleatorio, largo, con índice único y revocable. Nunca derivado del identificador ni de datos del invitado.

3. **El envío por WhatsApp y Telegram no es automático.** El backend construye el enlace profundo y lo devuelve; el envío real lo hace el anfitrión desde su cliente y lo confirma a mano. No implementes ni propongas integración con la API de WhatsApp Business.

4. **Las opciones de respuesta las define el anfitrión.** No hay enum fijo de respuestas. Entre dos y cinco opciones, ordenadas, cada una marcando si cuenta como asistencia — esta parte ya está implementada e impuesta por el dominio (R2). **Pendiente:** una vez publicado el evento, una opción con respuestas asociadas debería poder renombrarse pero no eliminarse; hoy `replaceResponseOptions(...)` no distingue eso y permite quitar cualquier opción, porque el dominio de `Event` todavía no conoce la colección `responses`. Es la deuda D-7 (`docs/plan-iteracion-1.md`), saldada en R8.

5. **Un evento es presencial o en línea, nunca ambos.** La modalidad híbrida está fuera de alcance a propósito en esta iteración.

6. **Los teléfonos se guardan en E.164 o no se guardan.**

7. **Una respuesta por invitado, impuesta por el índice único** sobre `(eventId, guestToken)`. Cambiar de respuesta es un upsert, no un segundo documento. No lo resuelvas con comprobaciones en la capa de aplicación.

---

## Tests

- Dominio y casos de uso: tests unitarios planos, sin contexto de Spring. Deben ser rápidos, y la arquitectura hexagonal es justamente lo que lo permite: si un test de caso de uso necesita Spring, las dependencias están mal.
- Adaptadores y flujos completos: tests de integración con `@SpringBootTest @ActiveProfiles("test")` contra MongoDB Atlas (base `gtog_test`). La conexión está en `src/test/resources/application-test.properties`. Hace falta `MONGODB_URI` en el entorno antes de lanzar los tests (ver sección Comandos). Nada de Mongo embebido ni Testcontainers.
- Los tests de integración limpian las colecciones que usan antes de cada test (`@BeforeEach`) para no depender del orden de ejecución.
- Nombres descriptivos en inglés: `doesNotAllowDeletingOptionWithAttachedResponses()`.
- Cada regla de la sección anterior necesita al menos un test que la cubra. La de concurrencia necesita un test con escrituras en paralelo.

---

## Git

- Ramas: `feat/nombre-corto`, `fix/nombre-corto`.
- Commits convencionales en inglés: `feat(guest): generate unique token on guest creation`.
- No hagas commit sin que `clean verify` pase.
- Nunca subas `.env`, credenciales ni la clave de Google Maps.

---

## Lo que no debes hacer

- **No añadas funcionalidad de iteraciones futuras.** Muro social, votación de varias fechas, acompañantes, check-in y modalidad híbrida están fuera de alcance a propósito. Si crees que algo lo necesita, pregunta antes.
- **No añadas dependencias sin preguntar.** MapStruct, Lombok, ModelMapper y similares son decisiones de arquitectura, no detalles.
- **No pongas anotaciones de framework en `domain/`.** Es la forma más fácil de destruir la arquitectura sin darse cuenta.
- **No generes todas las colecciones y casos de uso de golpe.** Trabajamos por rebanada vertical: dominio, puerto, adaptador, controlador y test, funcionando de punta a punta antes de pasar a la siguiente.
- **No te fíes de los ejemplos de Spring Boot 3.x.** La rama 4.x movió paquetes y configuración. Si algo no compila como dice el ejemplo que estés siguiendo, suele ser por eso.
- **No adivines la especificación.** Si falta un detalle en `docs/`, pregúntame en lugar de inventar un comportamiento razonable.
