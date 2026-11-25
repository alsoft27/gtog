# gtog — Plan, decisiones y deuda técnica

Estado a 25 de noviembre de 2026. Es el documento para retomar el proyecto: dónde estamos, qué queda, qué se
decidió y por qué, qué está pendiente de decidir y qué deuda hay. Qué hace la aplicación está en
`definicion-funcional.md`; el contrato técnico de cada agregado, en `modelo-evento.md`, `modelo-invitado.md` y
`modelo-usuario.md`. Las instrucciones para el agente están en `AGENTS.md`.

---

## 1. Dónde estamos

El backend del MVP está implementado de R1 a R12. **R12 cerrada:** sesión manual, verificación y puerta
de creación, y recuperación de contraseña; RF-1.2 y RF-1.3 cumplidas. Siguen pendientes los bloqueadores
de despliegue público de §7 y la deuda abierta de §6, incluida D-UNVERIFIED-SQUATTING.
El frontend Angular no se ha empezado.
Los recorridos y ejemplos del API están en [casos-de-uso.md](casos-de-uso.md), con la verificación de rutas, seguridad y resultados.

`mvn clean verify`: 1.250 tests, 0 fallos, 0 errores y 0 omitidos en el paso 5. Añade 12 casos
(3 de dominio y 9 con servidor real). El paso 4 añadió 35 casos: 10 de dominio, 5 de compositores
y 20 con servidor real. Adapta solo las dos expectativas temporales de publicación del paso 2;
los tests de mensajes de fecha fija permanecen intactos. El paso 3 añadió 56 casos sin modificar tests anteriores. El paso 2 añadió 58 casos
y adaptó el constructor del unitario de creación, conservando sus aserciones. En el paso 1 el listado
pasó de nueve a diez campos para incluir `eventType`.
La verificación anterior de `a85d4e6` sufrió errores DNS de Atlas al arrancar tres contextos;
tras recuperarse la red, aquella suite pasó sin cambios de código ni tests entre ambas ejecuciones.
Las ejecuciones negativas de mutación se conservan solo como evidencia, no como resultado de la suite final.
El endpoint del lote está verificado con servidor real, privacidad y regresión individual. Las mutaciones
de duplicados y capacidad hicieron fallar sus tests concurrentes y quedaron restauradas, sin versionarlas.
Repetirlo desde la terminal antes de cerrar cambios que no sean solo Markdown; la excepción documental
exige la misma base de código en verde, según AGENTS.md. Stack: Java 25, Spring Boot 4.1, MongoDB Atlas,
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
| R12 — Sesiones (commit 1) | Login manual con rotación de id y registro compartido, sin renovar XSRF-TOKEN. SessionInvalidationPort, igualdad del principal por userId y expiración con 401 ProblemDetail directo, instance constante y no-store exacto |
| R12 — Verificación (commit 2) | Tokens solo como hash, consumo atómico, correo asíncrono y cuota propia; verify-email, resend-verification y /me. Puerta de creación desde la base de datos y bypass solo local con guarda |
| R12 — Recuperación (commit 3) | forgot-password con 202 uniforme y flujo completo en el ejecutor; reset-password valida antes de consumir, hashea después, verifica correo y revoca tokens y sesiones con escrituras parciales |
| Invitados — concurrencia (commit A) | Edición con arrayFilters y unicidad explícita, y borrado parcial, sin comparar versión esperada; preservan aperturas y cuotas. Tests autorizados de concurrencia y reserva obsoleta actualizados al nuevo comportamiento |
| Invitados — lote (commits 1 y 2) | POST /api/events/{id}/guests/batch, todo o nada y errores por posición; dominio compartido con el individual y una escritura atómica, con unicidad y capacidad en el filtro. RF-2.18 implementado; RF-2.4 sigue en frontend |
| Invitados — enlace público | RF-2.19: intención en borrador, token al publicar, interruptor revocable, alta inmediata con contacto opcional y cookie de fricción. Topes atómicos 25/500, descarte silencioso de contactos, origen en panel y exclusión del correo; WhatsApp permitido |
| Tipos temporales — paso 1 | `EventSchedule` y `FixedDateSchedule`; `eventType=FIXED_DATE` en persistencia y vistas, también para documentos antiguos sin discriminador. Base de compatibilidad de la ampliación, en `024d09a` |
| Borradores DATE_POLL — paso 2 | Alta, detalle, listado y edición de datos generales; candidatas con id, fecha y hora opcional, sin fin, y plazo obligatorio. Máximo configurable 10; sin opciones de asistencia ni fechas de relleno |
| Candidatas DATE_POLL — paso 3 | PUT /api/events/{id}/candidate-dates con revisión propia, selección que se limpia al cambiar y reintentos idempotentes. Reordenar no invalida. Concurrencia con @Version y conservación de invitados/cuotas. |
| Publicación DATE_POLL — paso 4 | Publicación sin fecha definitiva, proyección pública de candidatas, OPENED, alta por enlace protegida por revisión/selección y mensajes de correo/WhatsApp compartidos. Calendario sin fecha: 409. |
| Disponibilidad DATE_POLL — paso 5 | PUT público de disponibilidad múltiple, lista vacía explícita, última respuesta en responses y escritura condicionada por revisión. Lecturas públicas, envíos y confirmación de borrado usan solo la ronda vigente |

### Endpoints

```
POST   /api/auth/register          POST   /api/auth/login          POST   /api/auth/logout
POST   /api/auth/verify-email      POST   /api/auth/resend-verification
POST   /api/auth/forgot-password   POST   /api/auth/reset-password
GET    /api/auth/me

POST   /api/events                 GET    /api/events              GET    /api/events/{id}
PUT    /api/events/{id}            PUT    /api/events/{id}/response-options
PUT    /api/events/{id}/venue      PUT    /api/events/{id}/online-access
PUT    /api/events/{id}/response-deadline
PUT    /api/events/{id}/candidate-dates
POST   /api/events/{id}/publish    POST   /api/events/{id}/cancel
GET    /api/events/{id}/dashboard
GET    /api/events/{id}/public-invitation   PUT /api/events/{id}/public-invitation

POST   /api/events/{id}/guests     GET    /api/events/{id}/guests
POST   /api/events/{id}/guests/batch
PUT    /api/events/{id}/guests/{guestId}   DELETE /api/events/{id}/guests/{guestId}

POST   /api/events/{id}/invitations/send
POST   /api/events/{id}/invitations/whatsapp-links
POST   /api/events/{id}/invitations/whatsapp-sent

GET    /api/invitations/{token}    PUT    /api/invitations/{token}/response
GET    /api/invitations/join/{joinToken}   POST /api/invitations/join/{joinToken}
PUT    /api/invitations/{token}/availability
GET    /api/invitations/{token}/calendar        (rutas de invitación públicas, sin sesión ni CSRF)
```

Además: `/actuator/health`, `/v3/api-docs` y `/swagger-ui.html`.

### Recorridos manuales

Archivos `.http` ejecutables con el cliente HTTP de IntelliJ, contra un servidor real.

- `rest-api.http`: recorrido completo automatizable, sin correo real.
- `rest-api-join.http`: alta pública con jar real del navegador A y otro cliente sin jar como B;
  borrador/publicación, respuesta, panel, revocación y cancelación. Requiere APP_AUTH_AUTOVERIFYEMAIL=true;
  sin ella, crear devuelve 403 EMAIL_NOT_VERIFIED. Preparado para ejecución manual, no se da por ejecutado.
- `rest-api-mail.http` y `rest-api-whatsapp.http`: manuales, con `{{realRecipient}}` y `{{realPhone}}` de
  `http-client.private.env.json`, que está ignorado por Git. No se ejecutan con "Run all": el paso de
  confirmación de WhatsApp debe lanzarse solo después de enviar el mensaje.
- Desde R12, los cuatro recorridos anteriores necesitan APP_AUTH_AUTOVERIFYEMAIL=true en la configuración
  de ejecución de la aplicación: sus anfitriones @example.com no tienen buzón y, sin verificar, el primer
  POST /api/events devuelve 403 con code=EMAIL_NOT_VERIFIED. Equivale a app.auth.auto-verify-email=true:
  verifica al registrar y omite el correo de verificación. No se configura en la aplicación real, en JUnit
  ni en su plantilla del IDE; la guarda exige app.public-base-url con host localhost o 127.0.0.1.
- `rest-api-verify.http` es la excepción: se ejecuta SIN APP_AUTH_AUTOVERIFYEMAIL, con realRecipient y
  verificationToken del entorno privado. Tras login comprueba /me=emailVerified:false, con un mensaje que
  detecta el bypass; luego verifica y crea en la misma sesión. Pendiente de ejecución manual con correo real.

---

## Verificaciones manuales con servicios reales

Estas comprobaciones son manuales; los tests automáticos no confirman por sí solos el comportamiento de los servicios reales.

**HECHO:**

- Correo: un envío real llegó a la bandeja de entrada con el asunto (tilde, guion y emoji) y el cuerpo íntegros.
- El enlace del correo contiene un token que la API acepta: GET de la invitación con respuesta 200.
- WhatsApp con el invitado ya abierto: la confirmación devolvió MARKED.

**PENDIENTE DE CONFIRMAR:**

- SPF, DKIM y DMARC en PASS, comprobados con «Mostrar original».
- El registro de actividad de Mailjet.
- El límite de 3 envíos y el rechazo del siguiente intento con LIMIT_REACHED.
- El correo de un evento en línea sin la URL de la sala.
- WhatsApp en orden normal: confirmar y después abrir.
- El estado final del panel tras cada orden de apertura y confirmación.
- Que el texto de WhatsApp llegue íntegro.

---

## 2. Lo que queda

### Eventos con fecha por definir — ampliación aprobada

Se incorpora sin alterar los eventos existentes. El paso 1 introdujo `EventType`, la configuración
temporal del dominio y el discriminador `eventType` en Mongo y en las vistas. Ausente equivale a
`FIXED_DATE`; rehidratar documentos antiguos no provoca escrituras de migración. El paso 2 incorpora
borradores `DATE_POLL` con candidatas y plazo obligatorio: alta, consulta y edición de datos generales.
El paso 4 incorpora publicación, vistas públicas, alta por enlace y mensajes con candidatas.
El paso 5 registra y muestra la última disponibilidad de la revisión actual. No hay migración ni nuevas dependencias.

Orden de implementación aprobado, con verificación completa antes de cada commit y sin push:

1. **Compatibilidad temporal (cerrada):** tipo explícito, fechas fijas encapsuladas y lectura de documentos antiguos.
2. **Borradores de consulta (cerrada):** entre 2 y 10 candidatas, con tope configurable, id, fecha obligatoria
   y hora opcional, sin fin; plazo obligatorio.
3. **Cambios de candidatas (cerrada):** revisión propia e invalidación lógica al cambiar fechas;
   repetir una configuración o reordenarla no invalida. Se mantienen invitados, tokens, aperturas y cuotas.
4. **Publicación y difusión (cerrada):** publicar sin fecha definitiva y adaptar vistas públicas, alta por enlace y mensajes.
5. **Disponibilidad (cerrada):** respuesta múltiple, incluida lista vacía explícita; última versión, revisión vigente,
   plazos y cambios controlados atómicamente. Una revisión nueva permite volver a contestar.
6. **Seguimiento (siguiente):** totales y personas por candidata, pendientes y no disponibles, sin consultas por evento.
7. **Fecha definitiva:** elegir o cambiar entre candidatas conservando disponibilidades; mantener la hora
   opcional y adaptar calendario y visibilidad sin inventar inicio ni fin.
8. **Aviso al anfitrión:** correo al vencer el plazo o cuando todos respondan sin enlace público activo,
   una vez por revisión; Clock, reclamación atómica, transporte falso en tests y recorrido manual.

Reglas acordadas: la selección de fecha no equivale a confirmación de asistencia. Editar candidatas
invalida respuestas y quita la selección definitiva; si venció el plazo, el anfitrión debe cambiarlo.
Los invitados no proponen fechas. Solo se guarda su última respuesta, sin histórico de versiones.
El aviso va únicamente al anfitrión por correo y no cierra las respuestas anticipadamente; los invitados
ven la elección en la aplicación o se enteran por sus propios medios. Un aviso automático a invitados
queda para una funcionalidad futura. Las protecciones del enlace público, privacidad, límites y cuotas
existentes siguen aplicándose.

### Punto de reanudación anterior, tras el paso 4 — 25 de noviembre de 2026

El plan anterior es el orden aprobado de ocho pasos; no se sustituye ni se amplía. Al contrastar el
código, los commits y los registros de Maven, **los pasos 1 a 4 están cerrados y el siguiente es el 5**.
Aunque la petición de continuidad sitúa el trabajo en la implementación del paso 4, este ya terminó
en `0bd61ee`. No queda una implementación parcial de ese paso ni se ha empezado el 5.

Estado de Git al hacer esta comprobación: rama `main`, HEAD `0bd61ee`, árbol limpio. Esta actualización
del seguimiento queda sin commit por petición del usuario. No se modifica código ni se ejecuta Maven
de nuevo: las cifras siguientes proceden de las ejecuciones completas ya realizadas sobre cada commit.

| Paso | Estado real y evidencia | Cambios y pruebas realizadas |
|---|---|---|
| 1. Compatibilidad temporal | Completado — `024d09a`; 1.089 tests en verde | `EventType`, `EventSchedule` y `FixedDateSchedule`; discriminador ausente leído como FIXED_DATE sin migrar ni escribir al consultar. Tipo expuesto en DTOs propios. `FixedDateScheduleTest` y `FixedDateCompatibilityIntegrationTest`: validación temporal, zona/cambios horarios y recorrido de documentos antiguos. |
| 2. Borradores de consulta | Completado — `1de2721`; 1.147 tests en verde | `CandidateDate` y `DatePollSchedule`, creación y edición de borradores sin inicio/fin, plazo obligatorio, de 2 a 10 candidatas por defecto (`app.events.max-candidate-dates`). Fecha y hora opcional persistidas sin valores ficticios. `DatePollScheduleTest`, `DatePollConfigTest` y `DatePollDraftIntegrationTest`: reglas, configuración, contratos HTTP y BSON. |
| 3. Cambios de candidatas | Completado — `03b9b10`; 1.203 tests en verde | PUT de candidatas con `pollRevision`, distinta de `version`; cambios reales avanzan ronda y limpian selección, reordenar conserva ambas e idéntico no escribe. Guardado con bloqueo optimista. `CandidateDatesTest` y `CandidateDatesIntegrationTest`: revisiones obsoletas/futuras, idempotencia, concurrencia real y conservación de invitados, tokens, aperturas y cuotas. |
| 4. Publicación y difusión | Completado — `0bd61ee`; 1.238 tests en verde | Publicación sin fecha definitiva; proyección pública de candidatas, OPENED, alta pública con filtro de revisión/selección y mensajes de correo/WhatsApp con formato compartido. `DatePollPublicationTest`, `DatePollInvitationComposerTest` y `DatePollPublicationIntegrationTest`: privacidad de cuerpos/logs, salas ocultas, calendario 409, plazo inclusivo, carreras de configuración y apertura concurrente. |
| 5. Disponibilidad | Pendiente; siguiente paso | No existe todavía endpoint ni modelo de disponibilidad múltiple. |
| 6. Seguimiento | Pendiente; depende del 5 | El panel/listado de asistencia actual no calcula disponibilidad por candidata. |
| 7. Fecha definitiva | Pendiente; depende de candidatas y disponibilidad | El campo `selectedCandidateDateId` está preparado, pero no hay operación para seleccionar/cambiar la fecha ni calendario adaptado a esa elección. |
| 8. Aviso al anfitrión | Pendiente; depende de revisión, disponibilidad y seguimiento | No está implementada la detección del cierre ni el envío del aviso por ronda. |

Las cuatro ejecuciones terminaron con cero fallos, errores y omitidos. Sus registros locales, ignorados
por Git, son `execution-logs/date-poll-step-1-final-verify.log`,
`execution-logs/date-poll-step-2-final-verify.log`,
`execution-logs/date-poll-step-3-final-verify.log` y `execution-logs/date-poll-step-4-verify.log`.
Son evidencia histórica; no hay que repetir los pasos ni sus pruebas de reproducción para retomarlos.
Las decisiones detalladas de los pasos 2, 3 y 4 están en §3 y los contratos en los modelos existentes.

**Límite exacto de lo implementado.** `InvitationViewFactory` descarta respuestas de asistencia para
DATE_POLL y devuelve `canRespond=false`, `canChangeResponse=false` y sin `myResponse`.
El PUT de asistencia no sirve para disponibilidad y rechaza consultas; el calendario de DATE_POLL
da 409 incluso en FINISHED. El alta pública sí admite invitados dentro del plazo mediante
`ResponseAcceptancePolicy`. El filtro Mongo protege tipo, revisión y selección leídos, además de las
condiciones existentes, sin comparar la versión del evento por una apertura de otro invitado.
La invalidación del paso 3 es por revisión: todavía no hay respuestas de disponibilidad que invalidar
ni filtros de lectura/escritura de esas respuestas. La selección preparada no es una funcionalidad cerrada.

**Siguiente acción concreta, al reanudar el desarrollo:** comenzar el paso 5, no rehacer el 4.
Leer `definicion-funcional.md`, `modelo-evento.md` y `modelo-invitado.md`; revisar los puertos/adaptadores
de `responses`, `ResponseAcceptancePolicy` y las vistas públicas. Introducir la disponibilidad como
concepto propio, sin convertir las candidatas en `ResponseOption`, y su operación pública de respuesta
múltiple (PUT `/api/invitations/{token}/availability`, todavía no existente). Mantener la colección
`responses` y el índice único `(eventId, guestToken)`; concretar el bloque de disponibilidad y su escritura
atómica con revisión antes de incorporarlos. No se empieza esa implementación en esta actualización.

**Dependencias y decisiones que debe respetar el trabajo restante:**

- Paso 5: una respuesta con lista vacía significa «ninguna fecha», distinta de no responder; solo se
  conserva la última versión. La escritura debe impedir que una respuesta de una ronda antigua pise
  la vigente. Una ronda nueva permite responder de nuevo aunque `allowResponseChange=false`.
  Reutilizar el Clock y la política temporal; el plazo es obligatorio e inclusivo, sin fin ficticio.
- Al incorporar disponibilidad, revisar también los consumidores de respuestas: proyección pública,
  elegibilidad para correo/WhatsApp y confirmación/cascada del borrado. Una respuesta de una revisión
  antigua no debe seguir contando como respuesta vigente. Conservar el funcionamiento de FIXED_DATE.
- Paso 6: partir de invitados actuales, ignorar respuestas huérfanas o de rondas antiguas; una persona
  puede contar en varias candidatas. Usar lecturas agrupadas, sin una consulta de respuestas por evento.
- Paso 7: el anfitrión puede elegir y cambiar la candidata definitiva sin borrar disponibilidades.
  Cambiar las candidatas sí invalida todas y quita la selección. Conservar la hora opcional y nunca
  inventar inicio ni fin. Estar disponible no equivale por sí solo a una confirmación de asistencia.
- Paso 8: correo únicamente al anfitrión, al vencer el plazo o si todos respondieron y no hay enlace
  público activo; una vez por revisión. Solo avisar: no cerrar antes ni notificar automáticamente a
  invitados. Clock inyectado, reclamación atómica y transporte falso en tests.
- Mantener las protecciones del enlace público, las cuotas y la exclusión del correo de PUBLIC_LINK,
  los DTOs explícitos, el filtrado de secretos de sala y el saneamiento de errores sin tokens/logs.
- En los pasos pendientes, cubrir dominio sin Spring, servidor real y concurrencia con hilos cuando
  proceda; probar revisión obsoleta, respuesta vacía, límites exactos del Clock y compatibilidad fija.
  Ejecutar Maven en primer plano, una ejecución a la vez, con suite completa verde antes de cada commit.

**Detalles pendientes de concretar, no hechos implementados:** forma exacta del bloque/DTO de
disponibilidad y de su condición atómica (paso 5); representación del calendario cuando la candidata
elegida no tiene hora ni fin y aplicación de visibilidad de sala a esa disponibilidad (paso 7);
mecanismo de detección/reclamación del aviso y tratamiento de sus fallos de envío (paso 8).
Concretarlos en su paso sin alterar las decisiones anteriores ni asignar valores temporales ficticios.

`AGENTS.md` ya referencia la definición funcional, este plan, los casos de uso y los tres modelos;
también remite a §2 para esta ampliación. Todas esas rutas existen. No requiere otra copia del plan
ni cambios de referencias para continuar; su tamaño comprobado es 27.698 bytes, inferior a 28 KB.

### Disponibilidad por ronda — paso 5 completado

La suite completa pasó con 1.250 tests, sin fallos, errores ni omitidos. La ejecución está registrada
en `execution-logs/date-poll-step-5-verify.log`. Las pruebas cubren selección múltiple y vacía,
plazo inclusivo, cambios simultáneos, una escritura tardía de otra ronda y elegibilidad para
envíos y borrado. Dos pruebas del paso 4 actualizan el indicador que antes era provisional.

El PUT público `/api/invitations/{token}/availability` recibe `pollRevision` y `candidateDateIds`.
Una lista vacía indica que ninguna fecha sirve. GET y PUT muestran `myAvailability` solo cuando la
respuesta corresponde a la revisión vigente. Reutilizan la política temporal y los indicadores de la
invitación. La asistencia de fecha fija conserva su contrato.

Mongo guarda el bloque `availability` en `responses` con el índice único existente. La actualización
atómica admite una revisión superior a la almacenada y, si se permiten cambios, la misma. Una ronda
obsoleta no sobrescribe una más nueva. Como la revisión del evento y su respuesta viven en colecciones
distintas, una edición concurrente puede dejar persistida una respuesta obsoleta que queda invisible.
El servicio relee el evento y devuelve 409 si detecta un cambio de ronda durante el PUT. La limpieza
por borrado del invitado retira respuestas vigentes u obsoletas. Los envíos y la confirmación de borrado
solo consideran la revisión vigente. El panel de disponibilidad sigue siendo el paso 6.

**Siguiente:** calcular disponibilidad a partir de los invitados actuales y sus respuestas vigentes,
con totales y personas por candidata, pendientes y no disponibles, y lecturas agrupadas.

### Aplazado

- **Telegram.** Sin endpoint. El enlace `t.me/share/url=` del diseño original está mal formado y la hipótesis
  de que abre un selector de chat y no un contacto concreto sigue sin verificar.
- **Frontend Angular.** No empezado. Notas acumuladas en §8.

---

## 3. Decisiones tomadas

### Borradores con fecha por definir — paso 2

- `eventType` es explícito e inmutable. Omitirlo al crear conserva `FIXED_DATE`. `DATE_POLL` lleva
  `datePoll.candidateDates`; no admite inicio/fin definitivos ni opciones de asistencia no vacías.
- Candidatas con id generado, fecha obligatoria y hora de inicio opcional, sin hora de fin, en el orden
  configurado y con la zona del evento. No se repite una fecha aunque cambie la hora. Entre dos y
  `app.events.max-candidate-dates` (10 por defecto; configuración menor de 2 impide el arranque).
- El plazo es obligatorio en todas las escrituras de consultas. Creación, edición general y plazo
  específico comparten validación de dominio. No se compara con un inicio definitivo inexistente.
- El límite de candidatas se aplica al crear y reemplazar; reducirlo no impide leer datos existentes. Persistencia
  embebida con fecha/hora como texto ISO, sin instantes auxiliares ni migración de eventos fijos.
- La validación web condicional conserva el 400 anterior al faltar inicio/fin en fecha fija. Las reglas
  de candidatas y plazo dan 422. Modalidad, propiedad, CSRF y correo verificado mantienen sus reglas.
- Editar datos generales de una consulta conserva ids, fechas, horas e intención pública; se incluye
  `eventType=DATE_POLL` en el PUT. Las candidatas cambian por su endpoint específico (paso 3);
  desde el paso 4 las consultas se publican sin fechas definitivas.

### Publicación y difusión de consultas — paso 4

- Publicar DATE_POLL conserva candidatas, revisión, invitados y cuotas; no crea inicio, fin ni opciones
  de asistencia. La intención del enlace público genera su token en la misma escritura de publicación.
- Invitación y vista previa de alta usan una proyección pública propia de candidatas/revisión, sin
  contactos ni metadatos de Mongo. Inicio/fin e instantes definitivos se omiten. OPENED sigue siendo
  atómico y solo en PUBLISHED; CANCELLED/FINISHED conservan sus vistas con la sala oculta.
- Hasta el paso 5, canRespond/canChangeResponse son false y no hay myResponse en consultas. El PUT
  de asistencia devuelve 409 sin escribir. El calendario sin fecha definitiva devuelve 409 saneado,
  también en FINISHED; el calendario de fecha fija conserva su comportamiento.
- ALWAYS conserva su regla. HOURS_BEFORE se oculta sin un inicio definitivo; ON_CONFIRMATION sigue
  sin tratar la disponibilidad como confirmación de asistencia. La vista previa solo muestra plataforma.
- El alta pública usa el plazo obligatorio, inclusive, sin fin ficticio. Su filtro añade tipo, revisión y
  selección a zona/plazo/fin y a los topes y contactos. Una carrera de configuración devuelve JOIN_CLOSED;
  una apertura concurrente no bloquea porque no se compara version. Revisión ausente equivale a cero.
- Correo y WhatsApp comparten formato temporal: candidatas, horas opcionales, zona y plazo; sin línea
  de fin ni secretos de sala. Se mantienen transporte falso en tests, privacidad, cuotas y límites; las
  invitaciones PUBLIC_LINK siguen excluidas del correo. La cota WhatsApp de 2000 no cambia.
- Se adaptan solo las dos expectativas temporales del paso 2 que exigían bloquear publicación:
  DatePollScheduleTest y DatePollDraftIntegrationTest; conservan las comprobaciones de integridad.
  Las pruebas de mensajes de fecha fija no se modifican.

### Cambios de candidatas — paso 3

- PUT /api/events/{id}/candidate-dates recibe `{pollRevision,candidateDates:[{id?,date,startTime?}]}`.
  Sesión, CSRF y propiedad en aplicación. DRAFT/PUBLISHED, sin convertir eventos de fecha fija;
  CANCELLED/FINISHED dan 409. Conserva validaciones de cantidad, fechas únicas y horas opcionales.
- `pollRevision` arranca en cero, también si falta en Mongo, y es distinta de `version`. Un cambio real
  de fecha/hora, alta o baja avanza una ronda y quita la selección. Los ids conocidos se conservan;
  sin id se reutiliza la misma fecha/hora o se genera uno. No se guarda historial.
- Idéntico es no-op sin escritura. Reordenar guarda el orden, conservando revisión y selección.
  Una edición obsoleta que cambia las candidatas da 409; repetir un cambio ya aplicado con la revisión
  anterior sigue siendo idempotente. Una revisión futura siempre da 409; ausente/negativa, 422.
  Una candidata ya eliminada en una pantalla antigua da 409, no el 422 de un id desconocido con revisión actual.
- Se usa guardado completo con @Version. El 409 concurrente es seguro y el cliente reintenta tras
  recargar. Invitados, tokens, aperturas y cuotas se conservan. Editar datos generales o el plazo no
  reinicia la ronda ni la selección; los campos nuevos se conservan en el mapper.
- La invalidez de disponibilidades se define por revisión. Cambiar candidatas no reescribe respuestas; el paso 5 aplica la revisión al escribir y consultar disponibilidades.
  `selectedCandidateDateId` queda preparado y se limpia al cambiar candidatas; elegirlo llega en el 7.
- Cambiar candidatas no prorroga el plazo. El anfitrión puede fijar un nuevo plazo obligatorio también
  en PUBLISHED cuando se trata de una consulta, sin depender de un inicio definitivo inexistente.

### R12 — Verificación de correo y recuperación de contraseña (cerrada)

Cerrada en tres commits: sesión manual, verificación con puerta de creación y recuperación de contraseña.
Decisiones implementadas:

- **Puerta:** sin correo verificado no se puede crear un evento (`POST /api/events`). Sí se puede registrar,
  iniciar sesión, listar y consultar. La comprobación lee `emailVerified` de la base de datos en cada
  creación, no de la sesión, para que tras verificar el anfitrión pueda crear en la misma sesión. Rechazo:
  403 con `code=EMAIL_NOT_VERIFIED`.
- **Tokens:** 32 bytes de `SecureRandom` en base64url. Se guarda solo su hash SHA-256 en una colección
  `auth_tokens`, con índice único del hash y un único token activo por (usuario, propósito). Propósitos
  `EMAIL_VERIFICATION` y `PASSWORD_RESET`. TTL de Mongo sobre `expiresAt` más validación de la caducidad en
  código, porque Mongo borra con retraso. Consumo atómico de un solo uso. Caducidad configurable: 24 h la
  verificación, 1 h el restablecimiento.
- **Correo de verificación (commit 2):** crear la cuenta es síncrono y mantiene 201/409/422. Todo el flujo
  de correo (buscar, reservar, emitir y enviar) va en el ejecutor, con respuesta inalterada ante fallo o
  saturación, sin reintento. app.auth.mail-executor.core-size=2, max-size=4 y queue-capacity=100; saturación
  a WARN, clase y correlación, nunca dirección ni mensaje original. Tests con ejecutor síncrono y transporte falso.
- **Límites propios:** authEmailSends, authEmailWindowStartedAt y lastAuthEmailAt en users, ausentes como
  0/null. Reserva con pipeline y Clock, 1 emisión cada 5 minutos y 5 por ventana de 24 h, incluidos los
  umbrales exactos; cuenta la emisión inicial. app.auth.mail-cooldown=PT5M y app.auth.mail-max-per-day=5.
  No utiliza ni altera emailSendsInWindow/windowStartedAt de invitaciones.
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
  El hash usa el mismo codificador del registro y se calcula tras consumir. Si falla el hash o la escritura,
  el token queda gastado y se informa 500 saneado; debe pedirse otro. app.auth.reset-ttl=PT1H y
  app.auth.reset-password-path=/reset-password. La mutación temporal que movió la búsqueda de forgot-password
  fuera del ejecutor hizo fallar su test controlado (un caso, un fallo); quedó restaurada sin versionarla.
- **Token inválido, caducado o usado:** un único 400 genérico con `instance` constante.
- **Escrituras parciales sobre `users`:** ese documento no tiene control de versión y un `save()` pisaría los
  contadores de cuota de correo.
- **`GET /api/auth/me`** y `emailVerified` en las respuestas de registro y login, para que el frontend sepa
  quién es el usuario al recargar.
- **Rutas:** verify-email, forgot-password y reset-password son públicas (POST, con CSRF); resend-verification
  y me exigen sesión. Se añaden a SecurityConfig una a una, porque /api/auth/** no es público en bloque.
- **Bypass local:** `app.auth.auto-verify-email`, falso por defecto, verifica la cuenta al registrarla para
  que los recorridos `.http` sigan funcionando con direcciones `@example.com`, sin correo de verificación.
  Su variable es APP_AUTH_AUTOVERIFYEMAIL. La aplicación se niega a arrancar si está activo y
  `app.public-base-url` no apunta a localhost o 127.0.0.1; el rechazo nombra propiedad y host sin credenciales.
  El WARN nombra propiedad y variable e indica que no es para producción. Un nombre mal escrito no avisa:
  no activa nada. Tests de binding con TestPropertyValues.Type.SYSTEM_ENVIRONMENT y guardas de arranque.
  Los tests fijan el bypass con propiedades explícitas o @AuthBypassDisabled; la suite se verifica también
  con APP_AUTH_AUTOVERIFYEMAIL=true en el entorno para comprobar que no los altera.
  Auditoría del commit 2: la suite original sin esa variable pasa los mismos 810 tests que con ella activa.
  Se añaden los hosts engañosos localhost.evil.com, localhost@evil.com y 127.0.0.1.evil.com; al retirar
  temporalmente la guarda fallaron los seis casos de arranque rechazado. La guarda quedó restaurada, sin
  cambios de producción. Se refuerzan la lectura directa del hash y la sustitución del mismo documento
  auth_tokens, y la comparación de la cuota de invitaciones antes y después de reenviar y verificar.
- **Sesión del login manual (commit 1 implementado):** antes, AuthController solo guardaba el contexto en
  HttpSession; el test con servidor real midió login 200 y el mismo id de la sesión previa. Ahora aplica
  ChangeSessionIdAuthenticationStrategy y RegisterSessionAuthenticationStrategy compartidas con la cadena.
  La composición excluye CsrfAuthenticationStrategy: el login no emite ni renueva XSRF-TOKEN. El principal
  se compara por userId. SessionInvalidationPort marca expiradas todas sus sesiones; el filtro invalida
  HttpSession al siguiente acceso y escribe 401 ProblemDetail directamente, instance `/api/errors` y
  no-store exacto, sin redirección ni sendError. HttpSessionEventPublisher limpia y actualiza el registro.
- Plan en tres commits: sesión del login manual, verificación con la puerta de creación, y recuperación.

### Producto

**Alta por enlace público (RF-2.19).** Alta inmediata sin aprobación: nombre obligatorio y contacto opcional.
El anfitrión puede fijar intención en DRAFT, pero el enlace solo nace al publicar, en la misma escritura.
En PUBLISHED se activa idempotentemente, se desactiva destruyendo el token y se reactiva con otro distinto.
token != null implica enabled=true y status != DRAFT; al cancelar o finalizar se conserva inerte.
GET del interruptor permite consultar el enlace conservado; PUT en CANCELLED/FINISHED devuelve 409.
La vista pública cerrada sigue dando 200 con canJoin=false; POST da JOIN_CLOSED, salvo cookie válida:
ALREADY_JOINED gana porque se comprueba antes de la ventana de admisión.

Tope configurable app.guests.public-link-max=25, dentro de app.guests.max-per-event=500, ambos dentro
de la escritura atómica. La política temporal es ResponseAcceptancePolicy con Clock, sin duplicación.
Si cualquier contacto normalizado ya existe, se descartan ambos en silencio antes de escribir, nunca se
devuelve el enlace existente. Conflicto conocido y camino normal usan la misma lectura/escritura; ante
una carrera, solo un reintento sin contactos. PUBLIC_LINK no recibe correo de gtog (UNVERIFIED_CONTACT),
ni siquiera tras editarlo, sin consumir cuota o reserva; WhatsApp sí se permite con teléfono.
source ausente = HOST y joinedAt solo para PUBLIC_LINK, sin migración. Ver los modelos para cookie y API.
La mutación que admitía cookies de un HOST o de un PUBLIC_LINK ajeno hizo fallar ambos casos y quedó
restaurada; no se versiona. Las pruebas parametrizadas de precedencia usan servidor real, sin csrf() de MockMvc.

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

**La fecha la fija el anfitrión.** La ampliación de consulta de disponibilidad entre candidatas está aprobada
y en desarrollo (§2); no seleccionará automáticamente una ganadora ni confundirá disponibilidad con asistencia.

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

Las escrituras sobre un invitado no comparan la versión esperada. Antes, el alta, la edición y el borrado
devolvían 409 si otro invitado abría su enlace entre la lectura y la escritura. Corregido en `9e929d4` y
`65ab6ec`.

**Alta en lote, todo o nada.** Una sola actualización con $push/$each, ausencia de contactos normalizados y
tokens, y capacidad comprobada dentro del filtro. app.guests.max-batch=100 y app.guests.max-per-event=500;
el segundo límite también se aplica al alta individual, con 409 y code=CAPACITY_EXCEEDED. No hay altas dentro
de POST /api/events. Rechazar no modifica el documento ni version, y el orden de alta es el de la petición.
Sin correo verificado se puede gestionar invitados de un evento existente: R12 solo bloquea crear eventos.

El endpoint usa @RequestBody normal: cuerpo ausente/ilegible → 400 del framework antes de comprobar propiedad;
con JSON válido, propiedad → 404, estado → 409, lista vacía/null o excesiva → 422, todos los errores de elemento
→ 422, duplicados contra el evento y capacidad → 409. No se difiere la deserialización. Los duplicados internos
marcan ambos índices. Éxito y rechazos del lote llevan no-store exacto; errores con detail e instance constantes
y solo index/code, nunca contactos ni nombres. La respuesta 201 reutiliza el DTO individual, sin cambiar las
cabeceras del endpoint individual. CSV y agenda futuros reutilizarán el lote; no forman parte de esta entrega.

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

**Fijación de sesión en R5.** El login de R5 no rotaba el id de sesión (fijación de sesión). Corregido en
8252fe8, junto con el registro de sesiones para poder invalidarlas por usuario.
La auditoría posterior quitó temporalmente la rotación y el registro por separado: fallaron sus tests
de rotación e invalidación, respectivamente; ambas condiciones se restauraron sin versionar la mutación.
Con servidor real se comprueba también el cambio A→B usando la misma cookie: el id antiguo deja de servir,
invalidar A no afecta a B e invalidar B sí lo rechaza. Logout y expiración eliminan los ids del registro.

**CSRF.** Login y registro lo conservan. Solo `/api/invitations/**` se excluye, porque su credencial es el
token de la ruta y no una cookie que el navegador envíe solo. La cookie `XSRF-TOKEN` se emite en el primer
`GET` y se compara el valor crudo con la cabecera. Un rechazo CSRF es 403.

**Rutas públicas:** `/api/invitations/**`, `POST /api/auth/register`, `POST /api/auth/login`,
`POST /api/auth/verify-email`, `POST /api/auth/forgot-password`, `POST /api/auth/reset-password` y
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

**Alcance de los errores saneados.** Instancia constante y detalle fijo en `/api/invitations/**`, en los
errores de seguridad y en el advice MVC. En invitaciones lo garantiza el punto único
`InvitationProblemDetailAdvice`: se ejecuta después de que Spring rellene `instance`, reconstruye cada
ProblemDetail sin datos de la petición y fija `Cache-Control: no-store` exacto. Admite únicamente code y
errors con claves y valores de lista blanca; ninguna extensión arbitraria. En errores del alta pública
elimina Set-Cookie: un 406 tras preparar un 201 podía heredar la cookie. La negociación JSON se hace ahora
antes del alta y el test compara también el BSON completo para demostrar que no escribe. Conserva solo
detalles constantes conocidos; los nuevos reciben uno fijo por estado. Los filtros de seguridad escriben
su ProblemDetail directamente. Solo los errores de dominio de las rutas del anfitrión admiten detalle
variable e instancia derivada de su ruta; nunca contactos de otras personas. Los errores de correo
inválido y ya registrado ya no repiten la dirección aportada (422 y 409, respectivamente).

Spring rellena instance con la ruta cuando un ProblemDetail no la fija; un handler genérico de dominio
exponía el token en /api/invitations/**. Corregido con un punto único de saneamiento.
Las excepciones de dominio al reconstruir un evento desde Mongo se envuelven en
`StoredDataIntegrityException`: son 500 con detalle fijo, no 422 del cliente. Se registra únicamente la
clase del fallo, la correlación y el id del evento, sin mensaje, causa, token ni datos.
Las pruebas con servidor real cubren las tres rutas públicas originales, además de GET/POST join,
y errores 400, 404, 405, 406, 409, 415, 422 y
500, incluido un error de dominio genérico en cada ruta. Desactivar temporalmente el punto único hizo
fallar los seis casos esperados; quedó restaurado.

### Convenciones

Código, rutas e identificadores en inglés; documentación y comentarios en español. Jakarta valida formato; las
reglas de negocio se validan en el dominio y salen como 422. Sin Lombok. Tests de integración contra una base
de datos real, nunca embebida. Se trabaja por rebanada vertical, directamente sobre `main` hasta el primer
despliegue, por decisión del usuario. Entonces se reconsiderará el uso de ramas `feat/` y `fix/`.

---

## 4. Decisiones pendientes

| Decisión | Bloquea | Notas |
|---|---|---|
| RF-3.9: notificar un cambio de ubicación tras publicar | Nada | Sacarlo del alcance, o abrir una excepción para editar la ubicación de un evento publicado |
| Causa del emoji roto en WhatsApp | Nada | Medir con `searchParams.get("text")`; ver §5 |
| Remitente `gtog@gtog.es`: buzón con alguien detrás, o `no-reply@` | Nada | El cuerpo dice que no admite respuestas |
| `FINISHED`: automático por fecha o manual | Nada | D-FINISHED |
| Eliminar un borrador | Nada | D-DELETE-DRAFT |
| Telegram | Nada | Verificar primero su enlace |
| Uso futuro del correo de la respuesta | Nada | Aprobación expresa y doble confirmación |
| Gestión futura de invitados PUBLIC_LINK | Nada | Verificar su contacto o convertirlos en invitados normales; editar no cambia source ni permite el correo |

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
| D-PUBLIC-JOIN-ABUSE | Alta pública sin límite por IP | Se pueden gastar las 25 plazas desde fuera de un navegador; la cookie no lo impide. Bloqueador junto a D-RATE-LIMIT |
| D-REFERRER | Política de `Referer` del frontend | La página del invitado lleva el token en la URL: debe servirse con `Referrer-Policy: no-referrer` |
| D-SWAGGER-PUBLIC | OpenAPI y Swagger UI sin autenticación | Decidir si protegerlos antes de producción |
| D-ACTUATOR-EXPOSURE | El matcher `/actuator/**` es público entero y `management.endpoint.health.show-details=always`; la decisión de R5 era que solo `/actuator/health` fuera público | Hoy solo health está expuesto por HTTP: el riesgo de abrir otro endpoint aún no es explotable, pero habilitarlo lo dejaría abierto sin aviso. El detalle de health (nombre de la base de datos, estado del disco) ya lo puede ver cualquiera |
| D-SECURE-COOKIE | `JSESSIONID` sin `Secure` en desarrollo | Verificar que Boot lo activa detrás de HTTPS |
| D-WHATSAPP-TRUST | `SENT` por WhatsApp es declarativo | Límite aceptado; mostrarlo en el frontend |
| D-EMAIL-NOT-SENT | La cita al responder no se envía | Sin rebanada aprobada; el correo de la respuesta no se usa |
| D-ORPHAN-OPTION | Un invitado puede responder con una opción mientras el anfitrión la elimina | Lectura defensiva: no confirma asistencia; sin transacciones |
| D-LIST-SCALE | El listado calcula contadores en memoria, sin paginar | Una sola consulta de respuestas evita N+1 pero no limita el volumen |
| D-FINISHED | Ningún mecanismo lleva un evento a `FINISHED` | La regla "no responder tras el fin del evento" cubre el hueco |
| D-DELETE-DRAFT | No se puede eliminar un borrador | Tampoco cancelarlo |
| D-DUP-KEY | `users` detecta el email duplicado por el texto del error de Mongo | Frágil si hay más índices únicos |
| D-5 | Tests de integración contra Atlas | Necesitan red y son más lentos; aceptable con un solo desarrollador. Los tests no pueden ejecutarse en paralelo: comparten gtog_test y se borran los datos entre sí. Los tests no se ejecutan en paralelo contra gtog_test, ni desde Codex ni desde el IDE a la vez. Mejora pendiente: una base distinta por ejecución. |
| expectedVersion | El parámetro expectedVersion de addGuest se conserva por compatibilidad con los tests, pero el adaptador no lo utiliza: delega en addGuests | Retirar la firma obsoleta cuando se autorice; no vuelve a introducirse como condición de escritura |
| D-6 | Sin herramienta de migraciones | Sin cambio de esquema versionado hasta que haya datos reales |
| D-UNVERIFIED-SQUATTING | (R12) Registrarse con el correo de otra persona deja esa dirección ocupada | Purga de cuentas sin verificar |

**Saldadas:** D-1 y D-2 (seguridad y `hostId`, R5), D-3 (índice de `guests.token`, R6), D-4 (indicador de
salud de Mongo), D-7 (opciones en eventos publicados, R8), D-CONFIRMED-LINK (R8) y D-ERROR-FORMAT.

### Límites conocidos, no deuda

- **Cookie de alta:** es fricción, no una barrera. Quitarla, usar otro nombre o cliente permite otra alta;
  el tope público acota el daño por evento. Solo reconoce un PUBLIC_LINK vivo del mismo evento.
- **Origen inmutable al editar:** un invitado PUBLIC_LINK sigue excluido del correo de gtog aunque el
  anfitrión cambie después su contacto. Verificarlo y convertirlo en normal es gestión futura, no implementada.

- **Ventana del alta por enlace:** se comprueba con la lectura previa y el Clock. El filtro Mongo verifica
  que plazo, fin y zona siguen siendo los leídos, pero no reevalúa el reloj. Si el cierre se alcanza entre
  esa comprobación y el alta, el invitado puede quedar creado y no poder responder después (409).

- **Responder mientras se envía:** hay una ventana entre la última lectura y el SMTP. Sin transacciones entre
  Mongo y el proveedor, no se puede garantizar exclusión absoluta. Solo se prueba con un spy.
- **SMTP aceptado y fallo al registrar `SENT`:** se informa `SENT` con `recorded=false`, no se reenvía y el
  panel puede mostrar `NOT_SENT`. Un reenvío posterior puede duplicar el correo, acotado por el tope de 3.
- **Compensación fallida al llegar al máximo de 3:** esperar el intervalo no libera el tope. Requiere
  intervención manual; el log `EMAIL_SEND_COMPENSATION_FAILED` la delata. Sin recuperación automática.
- **Errores que genera Tomcat antes de llegar a Spring** siguen viéndose como 401 para un anónimo.
- **Logs en DEBUG:** con `org.springframework.web` en DEBUG o `spring.mvc.log-resolved-exception`, la ruta de
  invitación con su token puede aparecer en los logs del framework.
- **Invalidación de sesiones:** implementada con SessionRegistry desde el commit 1 de R12, válida solo
  con una instancia y sesiones en memoria. El filtro rechaza cada sesión expirada en su siguiente acceso.
- **Versión y escrituras completas:** editar evento, opciones, candidatas, publicar y cancelar reescriben el documento
  completo y mantienen la comparación de versión esperada. Una apertura concurrente puede causar 409: es el
  resultado seguro y el cliente reintenta. Las escrituras parciales de invitado no necesitan esa comparación;
  alta individual, lote, edición y borrado ya no la usan. Todas siguen incrementando version para proteger los save completos.
- **Edición y borrado de invitados:** dos pestañas sobre la misma ficha son de última escritura. Una respuesta
  que llega entre la comprobación y la retirada se borra después del invitado, aunque no se hubiera confirmado.
  Si el invitado desaparece antes de retirarlo, devuelve 404 y no se limpia su respuesta.
- **Enumeración en registro:** el registro revela si un correo ya existe (409); forgot-password no lo revela.
  Es aceptado y se compensa con el límite por IP, D-REGISTER-RATE-LIMIT, aún pendiente y bloqueador.
- **Cola de recuperación:** forgot-password no puede limitarse por dirección para correos inexistentes,
  porque no hay documento donde guardar el contador. Un anónimo puede llenar la cola del ejecutor con
  direcciones inventadas y hacer que se pierdan correos legítimos. Se mitiga con el límite por IP
  (D-REGISTER-RATE-LIMIT), que es bloqueador de despliegue.
- **Correo de autenticación:** una caída del proveedor de correo gasta intentos del límite de autenticación
  del usuario. La reserva se conserva, se registra clase y correlación y no hay reintentos automáticos.
- **Lote cancelado a mitad:** la confirmación de WhatsApp devuelve 409 conservando las marcas previas. Es
  aceptable porque la operación es idempotente.

---

## 7. Bloqueadores de despliegue público

- D-REGISTER-RATE-LIMIT y D-RATE-LIMIT: límites de peticiones.
- D-PUBLIC-JOIN-ABUSE: impedir que un cliente sin navegador agote las plazas públicas por IP.
- La cookie de alta por enlace debe ir con Secure y con la política SameSite adecuada al despliegue
  (frontend y API en el mismo sitio o no). Desarrollo: deduce Secure de app.public-base-url y SameSite=Lax;
  comprobar y configurar ambos antes de desplegar.
- D-BOUNCES: procesar rebotes y quejas.
- D-SWAGGER-PUBLIC y D-SECURE-COOKIE.
- D-ACTUATOR-EXPOSURE: arreglo previsto, restringir el matcher público a `/actuator/health` y
  `/actuator/health/**`, y configurar `management.endpoint.health.show-details=when-authorized`.
- D-REFERRER del frontend.
- Dominio y remitente de Mailjet validados, con SPF, DKIM y DMARC en `PASS`, comprobados con un envío real.

## 8. Notas para el frontend

- Ruta de entrada `/j/{joinToken}`; llamadas join con withCredentials para que el navegador guarde y envíe
  la cookie HttpOnly. Usar alreadyJoined e invitationPath propios y redirigir a `/r/{token}` tras el alta.
  En DRAFT mostrar "El enlace público se creará al publicar". En PUBLISHED ofrecer copiar, desactivar y
  generar uno nuevo (desactivar y reactivar). Advertir al anfitrión que cualquiera con el enlace entra y,
  en línea con visibilidad al confirmar, ve la sala tras responder que asiste. Cerrado: mostrar el estado,
  canJoin=false y el enlace inerte conservado al anfitrión.
- Los nombres de invitados por enlace son texto de un anónimo: pintarlos siempre como texto, nunca como
  HTML; neutralizar en cualquier exportación futura a CSV los que empiecen por =, +, - o @.

- Tras POST /api/events, el asistente hace una sola llamada a POST /api/events/{id}/guests/batch.
  Si falla, no se guardó ningún invitado del lote y errors da el índice de base cero y el código de cada
  fila afectada, sin datos personales. En ese flujo, el borrador recién creado queda sin invitados y se
  puede reintentar. Los formatos pueden validarse en el navegador, pero la autoridad es el servidor.
  Los errores globales, como capacidad, estado o cuerpo ilegible, no tienen errores por fila.

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

1. `mvn clean verify` en verde, ejecutado desde la terminal; excepción para commits solo Markdown con
   el mismo código ya verificado y comprobación del diff, según AGENTS.md.
2. Test unitario de dominio por cada regla de negocio nueva.
3. Test de integración del endpoint, con al menos un caso de error. Con servidor real, sin `csrf()` de
   MockMvc, cuando importa el despacho de errores o la cookie.
4. Sin anotaciones de framework en `domain/`.
5. Documentada en OpenAPI, con anotaciones solo en `infrastructure/in/web`.
6. Cada afirmación de seguridad o concurrencia tiene su test y se comprueba que el test falla al quitar
   temporalmente la condición que protege. Los tests de concurrencia usan hilos reales.
7. Evidencia con fichero y línea en el informe de cierre.
8. Documentación actualizada y commit convencional en inglés.
9. `AGENTS.md` por debajo de 28 KB, comprobado con `wc -c AGENTS.md`; el material de referencia se mantiene en los documentos enlazados.
