# gtog

Antes de implementar, lee docs/definicion-funcional.md y el modelo del agregado que toques.

Backend de **gtog**, una aplicación de organización de eventos. El anfitrión crea un evento, invita por correo, WhatsApp o Telegram, y cada invitado responde desde un enlace único **sin crear cuenta**.

Si algo de este archivo contradice la especificación, gana la especificación y avísame del conflicto.

---

## Cómo trabajamos

- Antes de escribir código, presenta un plan y espera mi aprobación.
- Trabajamos por rebanadas verticales: dominio, puerto, adaptador, controlador y test, funcionando de punta a punta antes de pasar a la siguiente.
- Consulta los documentos del proyecto:
    - `docs/definicion-funcional.md`: contexto funcional, alcance y requisitos.
    - `docs/plan-iteracion-1.md`: estado, decisiones, deuda y desarrollo pendiente.
    - `docs/casos-de-uso.md`: recorridos del API, ejemplos y verificación de métodos, seguridad y resultados.
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

Usa estas equivalencias entre la especificación en español y el código en inglés:

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

## Lo que no debes hacer

- **No añadas funcionalidad de iteraciones futuras.** Muro social, acompañantes, check-in y modalidad híbrida están fuera de alcance a propósito. Si crees que algo lo necesita, pregunta antes. La consulta de disponibilidad entre fechas está aprobada y se desarrolla por los pasos de docs/plan-iteracion-1.md, §2.
- **No añadas dependencias sin preguntar.** MapStruct, Lombok, ModelMapper y similares son decisiones de arquitectura, no detalles.
- **No pongas anotaciones de framework en `domain/`.** Es la forma más fácil de destruir la arquitectura sin darse cuenta.
- **No generes todas las colecciones y casos de uso de golpe.** Trabajamos por rebanada vertical: dominio, puerto, adaptador, controlador y test, funcionando de punta a punta antes de pasar a la siguiente.
- **No te fíes de los ejemplos de Spring Boot 3.x.** La rama 4.x movió paquetes y configuración. Si algo no compila como dice el ejemplo que estés siguiendo, suele ser por eso.
  Casos concretos ya encontrados: `@AutoConfigureMockMvc` pasó a `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`; el prefijo de Mongo es `spring.mongodb.*` (no `spring.data.mongodb.*`); el indicador de salud Mongo se desactiva con `management.health.mongodb.enabled` (valor `false` para desactivarlo).
- **No adivines la especificación.** Si falta un detalle en `docs/`, pregúntame en lugar de inventar un comportamiento razonable.

---

## Reglas de negocio que no se pueden romper

Estas reglas son el contrato final del producto, no necesariamente lo que el código ya impone hoy: algunas dependen de rebanadas que todavía no existen. Cuando una regla tiene una parte pendiente, lo dice explícitamente — si esta sección afirmara algo que el código no cumple sin avisarlo, dejaría de servir para nada.

1. **El enlace de la reunión en línea se filtra en el backend.** Si el invitado no cumple la regla de `LinkVisibility`, el campo se omite de la respuesta. Nunca lo envíes al cliente confiando en que Angular no lo pinte: viajaría en el JSON y sería legible desde las herramientas del navegador. La regla de dominio (`Event.visibleOnlineAccess(...)`, R3) se aplica en la vista pública de GET, PUT y calendario, exclusivamente para eventos `PUBLISHED`. Desde R8, `ON_CONFIRMATION` exige respuesta propia cuya opción tenga `countsAsAttendance = true`; sin respuesta o con opción inexistente, false. Responder "No asisto" no confirma asistencia. D-CONFIRMED-LINK saldada.

2. **El token es la única credencial del invitado.** Criptográficamente aleatorio, largo, con índice único y revocable. Nunca derivado del identificador ni de datos del invitado.

3. **El envío por WhatsApp y Telegram no es automático.** El backend construye el enlace profundo y lo devuelve; el envío real lo hace el anfitrión desde su cliente y lo confirma a mano. No implementes ni propongas integración con la API de WhatsApp Business.

4. **Las opciones de respuesta las define el anfitrión.** No hay enum fijo de respuestas. Entre dos y cinco opciones, ordenadas, con etiquetas únicas y no vacías y al menos una que cuente como asistencia. En DRAFT pueden cambiarse; en PUBLISHED se pueden renombrar y agregar, pero no eliminar una opción con respuestas ni cambiar su `countsAsAttendance` (409 con excepciones propias). La aplicación consulta los ids con respuestas y los pasa al dominio, que no conoce Mongo. CANCELLED y FINISHED bloquean los cambios. D-7 saldada en R8; la carrera entre responder y eliminar una opción queda en D-ORPHAN-OPTION, sin transacciones. Esta regla corresponde a FIXED_DATE; DATE_POLL usa candidatas con fecha y hora opcional, sin opciones de asistencia.

5. **Un evento es presencial o en línea, nunca ambos.** La modalidad híbrida está fuera de alcance a propósito en esta iteración.

6. **Los teléfonos se guardan en E.164 o no se guardan.**

7. **Una respuesta por invitado, impuesta por el índice único** sobre `(eventId, guestToken)`. Cambiar de respuesta es un upsert, no un segundo documento. No lo resuelvas con comprobaciones en la capa de aplicación.

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

Bypass local: `APP_AUTH_AUTOVERIFYEMAIL=true` equivale a `app.auth.auto-verify-email=true` (false por defecto); verifica al registrar y omite el correo. Solo con `app.public-base-url` en localhost o 127.0.0.1; nunca en producción ni en JUnit o su plantilla del IDE. Sin él, los recorridos @example.com reciben 403/EMAIL_NOT_VERIFIED al crear. `rest-api-verify.http` exige arrancar sin la variable y no se ejecuta automáticamente. Ver [modelo-usuario.md](docs/modelo-usuario.md#verificación-de-correo-r12-commit-2).

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

2. **El modelo de dominio y el documento de Mongo son clases distintas.** `Event` vive en `domain/model`; `EventDocument` vive en `infrastructure/out/persistence`, y un mapper traduce entre ambos.

3. **Las dependencias apuntan solo hacia dentro.** `application` depende de `domain`. `infrastructure` depende de los dos. `domain` no depende de nada.

4. **Los casos de uso son interfaces en `port/in`**, implementadas en `application`. El controlador depende de la interfaz, nunca de la clase concreta.

5. **Los puertos de repositorio son interfaces en `port/out`**, implementadas por adaptadores en `infrastructure/out/persistence`. El puerto habla el lenguaje del dominio y devuelve objetos de dominio.

---

## Modelo de datos

Los campos, documentos e índices se detallan en [modelo-evento.md](docs/modelo-evento.md#persistencia-events),
[modelo-invitado.md](docs/modelo-invitado.md#persistencia-y-concurrencia) y
[modelo-usuario.md](docs/modelo-usuario.md#persistencia-users).

- `events` embebe `responseOptions[]` y `guests[]`; la respuesta va en `responses`, nunca en el invitado.
- Una respuesta por `(eventId, guestToken)`, con índice único; cambiarla es un upsert, no otro documento.
- `wall_posts` (iteración 2) referencia `eventId`: nunca embebido en el evento. Los comentarios pueden embeberse en la publicación; los contadores usan `$inc`, nunca leer y reescribir.

R12: `auth_tokens` guarda solo hash SHA-256; índices únicos hash/(userId,purpose) y TTL expiresAt. Emitir por upsert y consumir por find-and-delete con propósito y expiresAt > Clock. Cuota auth propia, nunca la de invitaciones. verify-email es público con CSRF; /me y resend-verification exigen sesión. La puerta POST /api/events lee HostEmailVerificationPort, nunca la sesión. Forgot-password: formato antes de encolar; búsqueda, reserva, emisión y envío dentro del ejecutor acotado; 202 uniforme, sin reintentos. Reset: Password.validateRaw antes de consumir; después hash, escritura parcial de passwordHash/emailVerified, borrado de otros tokens e invalidación de sesiones, sin login automático; un fallo posterior gasta el token. Conserva CSRF y no-store exacto; nunca abras /api/auth/** en bloque. Ver [modelo-usuario.md](docs/modelo-usuario.md).

### Consistencia: derivar en lugar de duplicar

La justificación de separar respuestas está en [modelo-evento.md](docs/modelo-evento.md#persistencia-events).

**No almacenes `ANSWERED` en el evento. Derívalo.** Un invitado ha respondido si y solo si existe un documento de respuesta con su token.

- `NOT_SENT` y `SENT` viven en el evento; los escribe el anfitrión.
- `OPENED` vive en el evento; se escribe una vez al abrir el enlace y es idempotente.
- `ANSWERED` se calcula en lectura consultando `responses`.

El panel usa dos consultas unidas en aplicación, sin `$lookup`, por la decisión de R9; ver [plan](docs/plan-iteracion-1.md#3-decisiones-tomadas).

La excepción es el borrado en cascada de un invitado (R8): se retira de `events` antes de limpiar `responses`,
sin transacción multidocumento. Un fallo de limpieza deja una respuesta inalcanzable, nunca una respuesta
perdida con el invitado vivo.

### Concurrencia

Al actualizar un invitado concreto usa siempre escrituras posicionales parciales atómicas, nunca leer-modificar-guardar el evento completo. Incrementa `version`; no compares la versión esperada. Ver [modelo-invitado.md](docs/modelo-invitado.md#persistencia-y-concurrencia).

Las ediciones del anfitrión sobre el cuerpo del evento (incluidas las candidatas) sí pueden usar `save()` con bloqueo optimista `@Version`. Ante un 409 concurrente, recarga y reintenta.

En DATE_POLL, `pollRevision` identifica la ronda, no la versión del documento. Cambiar candidatas invalida y quita selección; repetir o reordenar conserva la ronda. Ver [modelo-evento.md](docs/modelo-evento.md#reemplazo-de-candidatas-y-revisión-de-consulta--paso-3).

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

El ejemplo y el contrato de creación están en [modelo-evento.md](docs/modelo-evento.md#api-http-expuesta-hoy) y en `CreateEventRequest`.

`hostId` se obtiene del principal autenticado, nunca del cuerpo. Las fechas de FIXED_DATE son horas locales (`LocalDateTime`) más `timeZone`; el instante se calcula al consultar. Los instantes de auditoría siguen en UTC.

**Inyección por constructor, siempre.** Nunca `@Autowired` sobre campos.

El calendario usa iCal4j 4.2.0 únicamente en `infrastructure/out/calendar`, detrás de un puerto. Su configuración
vive en `src/main/resources/ical4j.properties`: `net.fortuna.ical4j.timezone.update.enabled=false` se carga antes
del primer uso de la librería, sin actualizaciones remotas de zonas. El renderizador solo recibe datos públicos
y la URL ya filtrada; UID basado en el evento, nunca en el token; DTSTAMP obtenido del Clock inyectado.

**Los objetos de dominio no cruzan la frontera web.** Los controladores reciben y devuelven DTOs definidos en `infrastructure/in/web`. No serialices nunca un modelo de dominio ni un documento de Mongo a JSON.

**Sin lógica de negocio en los controladores.** El controlador valida la entrada, llama al caso de uso y mapea el resultado.

**La validación se reparte en dos sitios:** las anotaciones de Jakarta sobre el record de entrada cubren el formato (tipos, rangos, patrones); las reglas de negocio (mínimo dos opciones de respuesta, al menos una que cuente como asistencia, restricciones de opciones en `PUBLISHED`) viven en el dominio.

**Si la obligatoriedad de un campo es en sí una regla de negocio, no la captures con Jakarta.** Nada de `@NotBlank`/`@NotNull` en el DTO para ese campo: se valida en el dominio, con su propia excepción, y sale como 422. Si Jakarta la bloqueara antes, respondería 400 y la regla de negocio no llegaría nunca a ejecutarse — ni a poder probarse a través del controlador. `timeZone` del evento lleva `@NotBlank`; el dominio valida `ZoneId.of(...)`. Los ejemplos concretos de título, etiquetas, Venue, OnlineAccess y timeZone están en [modelo-evento.md](docs/modelo-evento.md#reglas-de-negocio-y-sus-excepciones).

---

## Convenciones de API

- Sustantivos en plural y en inglés: `/api/events`, `/api/events/{eventId}/guests`.
- Los endpoints públicos del invitado van bajo `/api/invitations/{token}`, separados del resto para que la regla de seguridad se vea en la propia ruta.
- Códigos: `201` al crear con cabecera `Location`, `204` al borrar, `409` en conflicto de estado, `422` cuando una regla de negocio rechaza la petición.
- Errores con **`ProblemDetail`** (RFC 7807), gestionados en `shared`: advice de dominio con prioridad alta y advice MVC con menor prioridad para errores de framework y fallback. Los filtros de seguridad escriben el mismo formato directamente. No inventes un formato de error propio.
- En `/api/invitations/**`, instancia constante `/api/invitations`, detalle fijo y `no-store` exacto: `InvitationProblemDetailAdvice` sanea todo ProblemDetail después de que Spring rellene instance; solo admite `code` y `errors` con valores de lista blanca, nunca datos de la petición. Seguridad y advice MVC también usan instancia constante y detalle fijo. Solo los errores de dominio del anfitrión admiten detalle variable e instancia derivada de la ruta; nunca contactos de otras personas.
- El enlace público del evento es una credencial como el token del invitado; nunca lo registres. Nace al publicar, no en borrador. Ver [modelo-evento.md](docs/modelo-evento.md#enlace-público-de-alta).
- Nunca devuelvas el enlace de un invitado existente al dar de alta por enlace público. Los invitados `PUBLIC_LINK` no reciben correo de gtog, tampoco después de editar su contacto. Sus nombres son texto no confiable: nunca HTML y neutralizar prefijos `=`, `+`, `-`, `@` en futuras exportaciones CSV. Ver [modelo-invitado.md](docs/modelo-invitado.md#alta-por-enlace-público).

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

OpenAPI (springdoc 3.1.0) y Swagger: `/v3/api-docs` y `/swagger-ui.html`; configuración `shared/config/OpenApiConfig`. Ver [plan](docs/plan-iteracion-1.md).

**Las anotaciones de Swagger van únicamente en `infrastructure/in/web`**, sobre controladores y DTOs de request y response (`@Operation`, `@ApiResponse`, `@Schema`, etc.). Ni una sola en `domain/`.

---

## Tests

- Ejecuta Maven siempre en primer plano y de una en una, nunca con & ni dos a la vez (comparten gtog_test y se borran los datos). No hace falta comprobar procesos antes de cada ejecución. Solo si una ejecución se interrumpió, antes de la siguiente comprueba que no queda ningún Maven o surefire de este repositorio, mostrando solo pid, tiempo y nombre del ejecutable.

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
- Un commit que solo modifica Markdown y no cambia src, pom.xml ni configuración no requiere repetir la suite si la última sigue en verde sobre el mismo código; compruébalo con git diff --stat y git status --short. Si aparece cualquier fichero que no sea Markdown, ejecuta la suite.
- Sin push salvo petición expresa del usuario.
- Nunca subas `.env`, credenciales ni la clave de Google Maps.

---
