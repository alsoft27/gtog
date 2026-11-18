# MVP — Alcance acotado

**Iteración 1.** Solo aplicación web. Angular + Spring Boot. **Objetivo de la iteración:** que un anfitrión pueda crear
un evento con ubicación real, invitar por el canal que prefiera, y recibir respuestas usando las opciones que él mismo
definió.

---

## 1. Qué entra y qué no

**Entra**

- Cuenta de anfitrión y creación de eventos.
- Evento presencial con ubicación de Google Maps, o evento en línea con datos de conexión.
- Alta de invitados por correo, por teléfono, o ambos.
- Envío de la invitación por correo, WhatsApp o Telegram, a elección del anfitrión y por invitado.
- Opciones de respuesta configurables por el anfitrión.
- Página pública de respuesta del invitado, sin registro.
- Panel con el estado de cada invitado.

**No entra en esta iteración**

Muro social, votación de varias fechas, modalidad híbrida, importación desde agenda, carga CSV, acompañantes, campos
adicionales, check-in, cuentas de empresa, recordatorios automáticos, aplicación móvil.

> El muro social y la votación de fechas siguen siendo el diferenciador del producto; quedan como iteraciones 2 y 3.
> Esta iteración construye la base sobre la que se apoyan: evento, invitado, enlace único.

---

## 2. Requisito 1 — Aplicación web

| ID     | Requisito                                                                                                                                                                                      |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RF-1.1 | Registro e inicio de sesión con correo y contraseña.                                                                                                                                           |
| RF-1.2 | Verificación de correo antes de crear el primer evento.                                                                                                                                        |
| RF-1.3 | Recuperación de contraseña.                                                                                                                                                                    |
| RF-1.4 | Perfil mínimo: nombre visible, correo, zona horaria.                                                                                                                                           |
| RF-1.5 | Listado de los eventos del usuario con su estado y contadores totalGuests, answered, pending y attending, sin byOption. Implementado en el commit 2 de R9. Listado y panel reutilizan el mismo cálculo puro basado en invitados vivos, sin contar respuestas huérfanas. Una sola consulta de respuestas para todos los eventos del anfitrión, sin paginación (D-LIST-SCALE). |
| RF-1.6 | Diseño responsivo. El anfitrión trabaja en escritorio; el invitado abrirá su enlace desde el teléfono en la mayoría de los casos, así que la página de respuesta se diseña primero para móvil. |

**Estados del evento en esta iteración:** `Borrador → Publicado`, más `Cancelado` (solo desde Publicado). Sin fase de votación todavía. `FINISHED` no está implementado — ni automático por fecha ni acción manual del anfitrión (ver D-FINISHED en el plan). Tampoco existe `DELETE` para borradores: `cancel()` solo funciona desde `PUBLISHED`, y no hay `DELETE /api/events/{id}` (ver D-DELETE-DRAFT en el plan).

---

## 3. Requisito 2 — Invitados y canal de envío

### Alta del invitado

| ID     | Requisito                                                                                                                                                          |
|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RF-2.1 | Agregar invitado con nombre, y correo o teléfono. Al menos uno de los dos es obligatorio.                                                                          |
| RF-2.2 | El teléfono se captura con selector de código de país y se guarda en formato internacional E.164, requisito para que funcionen los enlaces de WhatsApp y Telegram. |
| RF-2.3 | Validación de formato de correo y de teléfono antes de guardar.                                                                                                    |
| RF-2.4 | Alta rápida en serie: al guardar un invitado el formulario se limpia y mantiene el foco para capturar el siguiente.                                                |
| RF-2.5 | Detección de duplicados dentro del mismo evento por correo o teléfono.                                                                                             |
| RF-2.6 | Editar y eliminar invitados en DRAFT o PUBLISHED. Un invitado sin respuesta se elimina directamente. Si ya respondió requiere `confirmDeleteResponse=true`; sin confirmación, 409. La cascada de aplicación retira primero al invitado del evento y después borra su respuesta. Si falla la retirada, conserva la respuesta; si falla la limpieza posterior, queda una respuesta inalcanzable. Implementado en R8, commit 3. |
| RF-2.7 | Cada invitado recibe al crearse un token único e irrepetible que genera su enlace personal de respuesta.                                                           |

### Elección del canal

| ID      | Requisito                                                                                                                                                                                                                                         |
|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RF-2.8  | Al invitar, el anfitrión elige el canal por invitado: correo, WhatsApp o Telegram.                                                                                                                                                                |
| RF-2.9  | Los canales disponibles dependen de los datos capturados: correo requiere correo; WhatsApp y Telegram requieren teléfono. Los no disponibles se muestran deshabilitados con la razón.                                                             |
| RF-2.10 | **Correo:** Mailjet por SMTP, mensaje fijo en español y enlace individual; solo PUBLISHED y sin respuesta previa. Cuotas, reservas y transporte implementados en R10, commits 1–2; endpoint pendiente del commit 3. Texto plano, un destinatario, remitente gtog y sin Reply-To. Para eventos online solo se incluye la plataforma, nunca el acceso a la sala. Sin envío al correo dejado en la respuesta. |
| RF-2.11 | **WhatsApp:** la aplicación abre `https://wa.me/<E164>?text=<mensaje>` en una pestaña nueva, con el mensaje y el enlace ya redactados. El anfitrión confirma el envío en WhatsApp.                                                                |
| RF-2.12 | **Telegram:** mismo mecanismo con `https://t.me/share/url=<enlace>&text=<mensaje>`.                                                                                                                                                               |
| RF-2.13 | Tras abrir WhatsApp o Telegram, la aplicación pregunta al anfitrión si el envío se completó y marca el invitado en consecuencia.                                                                                                                  |
| RF-2.14 | Envío en bloque por canal. Correo síncrono con máximo configurable (20 por defecto), resultado por invitado y sin reintentos automáticos. Tras tres fallos consecutivos de proveedor se corta el lote. WhatsApp/Telegram siguen asistidos. Contrato de correo pendiente de completar en R10. |
| RF-2.15 | Fuera del MVP por decisión del usuario: plantilla editable con variables. Queda como seguimiento; R10 usa texto fijo en español y no incorpora messageTemplate. |
| RF-2.16 | Botón de copiar el enlace individual, para pegarlo en cualquier otro medio.                                                                                                                                                                       |
| RF-2.17 | Reenvío individual o a los invitados que no respondieron, incluidos OPENED. Sin correo o con respuesta se salta con motivo. Máximo 3 por invitado, intervalo PT1H y cuota de anfitrión 100 por ventana de 24 h, configurables. Reservas implementadas; endpoint de R10 pendiente. |

**Estado de invitación por invitado:** `Sin enviar → Enviada → Abierta → Respondida`. El estado *Abierta* solo se
detecta al abrir el enlace, no por el canal. En WhatsApp y Telegram, *Enviada* se basa en la confirmación manual del
anfitrión (RF-2.13); esta limitación debe ser visible en la interfaz para que el dato no se malinterprete.

---

## 4. Requisito 3 — Ubicación del evento

| ID     | Requisito                                                                                                                                                                                                        |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RF-3.1 | Al crear el evento, el anfitrión elige la modalidad: **presencial** o **en línea**.                                                                                                                              |
| RF-3.2 | **Presencial:** buscador de direcciones con autocompletado de Google Places. Al seleccionar, se guardan nombre del lugar, dirección formateada, latitud, longitud y `place_id`.                                  |
| RF-3.3 | Mapa con marcador arrastrable para ajustar el punto exacto cuando la dirección no es precisa.                                                                                                                    |
| RF-3.4 | Campo libre de indicaciones adicionales: piso, salón, referencias, instrucciones de estacionamiento.                                                                                                             |
| RF-3.5 | La página del invitado muestra el mapa embebido y un botón "cómo llegar" que abre Google Maps con la ruta.                                                                                                       |
| RF-3.6 | **En línea:** campos para plataforma, enlace de la reunión, y opcionalmente identificador y contraseña de la sala.                                                                                               |
| RF-3.7 | Regla de visibilidad del enlace de la reunión, a elección del anfitrión: **al confirmar** (solo quien respondió con una opción que cuenta como asistencia), **N horas antes** del inicio, o **siempre visible**. |
| RF-3.8 | El filtrado del enlace se resuelve en el backend. El endpoint de la página del invitado omite el campo cuando el invitado no cumple la regla; nunca se envía al cliente para que Angular decida si lo pinta.     |
| RF-3.9 | Si el evento cambia de ubicación o de enlace después de publicado, se notifica por correo a los invitados que ya respondieron. **Pendiente de decisión.** R4 dejó el evento no editable fuera de `DRAFT`: `replaceVenue` y `replaceOnlineAccess` devuelven 409 si el estado es `PUBLISHED`, por lo que este requisito es inalcanzable sin un cambio previo. Dos salidas posibles: (a) sacarlo del alcance del MVP; (b) abrir una excepción en R4 para permitir cambiar la ubicación de un evento publicado, disparando la notificación como efecto secundario. |

> **Modalidad híbrida: descartada en esta iteración.** Un evento con sede y sala simultáneas obliga a que la opción de
> respuesta indique el canal de asistencia (`Asisto presencial` / `Asisto en línea`), lo que acopla este requisito con el
> 4 y agrega un campo `modo_asistencia` a `OpcionRespuesta`. Entra en una iteración posterior mediante una migración
> aditiva.

---

## 5. Requisito 4 — Opciones de respuesta configurables

| ID     | Requisito                                                                                                                                                                          |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RF-4.1 | Al crear el evento, el anfitrión define el conjunto de opciones con las que los invitados pueden responder.                                                                        |
| RF-4.2 | Conjuntos predefinidos disponibles al crear: *Asisto / No asisto*, *Asisto / No asisto / Tal vez*, *Confirmo / Declino / Pendiente*.                                               |
| RF-4.3 | El anfitrión puede crear opciones propias: etiqueta, y si esa opción cuenta como asistencia para los totales.                                                                      |
| RF-4.4 | Mínimo dos opciones, máximo cinco. Al menos una debe contar como asistencia.                                                                                                       |
| RF-4.5 | Orden de las opciones definible por el anfitrión; ese orden se respeta en la página del invitado.                                                                                  |
| RF-4.6 | Interruptor para permitir o no que el invitado deje un comentario junto con su respuesta.                                                                                          |
| RF-4.7 | Interruptor para permitir o no que el invitado cambie su respuesta. El plazo se aplica a toda escritura, tanto la primera respuesta como los cambios: se acepta en el instante exacto del plazo y se rechaza después. Solo se responde en PUBLISHED y antes del fin del evento (en el fin exacto ya no). El anfitrión puede cambiar o quitar el plazo en DRAFT o en PUBLISHED antes del inicio (en el inicio exacto ya no); se interpreta en la zona del evento y no puede ser posterior al inicio. Un plazo pasado es válido y cierra las respuestas. Implementado en R8, commits 0 y 1. |
| RF-4.8 | Las opciones se pueden editar en DRAFT sin restricciones por respuestas. En PUBLISHED se pueden renombrar y agregar; una opción sin respuestas se puede quitar o cambiar, pero no se puede eliminar una opción con respuestas ni cambiar su countsAsAttendance: ambos 409 con excepciones propias. Se mantienen 2–5 opciones, al menos una que cuente, etiquetas únicas y no vacías. CANCELLED y FINISHED bloquean los cambios. Implementado en R8, commit 3 (D-7 saldada). La carrera entre respuesta y eliminación de opción queda documentada como D-ORPHAN-OPTION, sin transacciones. |
| RF-4.9 | El panel del anfitrión muestra el conteo por cada opción definida, más el total de invitados sin responder.                                                                        |

---

## 6. Flujos principales

**Crear y publicar un evento**

1. Datos básicos: título, descripción, fecha, hora de inicio y fin.
2. Modalidad y ubicación (presencial con mapa o en línea con enlace).
3. Opciones de respuesta: elegir conjunto predefinido o construir uno propio.
4. Invitados: agregarlos uno a uno.
5. Revisión: vista previa de la página que verá el invitado.
6. Publicar y enviar invitaciones eligiendo canal por invitado.

**Responder una invitación**

1. El invitado abre su enlace único sin registro, sesión ni contraseña. El frontend consulta
   `GET /api/invitations/{token}` sin CSRF. Un token desconocido y un evento en borrador devuelven el mismo 404.
2. En un evento publicado ve solo su nombre y los datos públicos del evento. Recibe fechas locales, zona del
   evento e instantes UTC para que el frontend convierta la fecha a su zona; la ubicación presencial incluye
   los datos para el mapa. No se revelan anfitrión, contactos, otros invitados, tokens ni marcas de asistencia
   de las opciones. La primera consulta marca OPENED/openedAt desde NOT_SENT o SENT; las siguientes no cambian
   la fecha. Ese registro se implementa en R7, también si se copió el enlace sin marcar envío.
3. En un evento online recibe siempre metadatos sin secretos (plataforma, regla de visibilidad y horas). El
   backend solo incluye URL, sala, contraseña e instrucciones si el evento está PUBLISHED y se cumple la regla.
   En ON_CONFIRMATION solo ve el acceso quien eligió una opción que cuenta como asistencia, no simplemente
   quien respondió. Cambiar a "No asisto" vuelve a ocultarlo; cambiar a una opción que cuenta lo muestra.
   Sin respuesta o con una opción que ya no existe, no se confirma. Implementado en el commit 2 de R8.
   La API no envía los secretos para que el frontend los oculte.
4. Si el evento está CANCELLED, ve el estado, fecha y motivo de cancelación; si está FINISHED, ve su estado.
   Ambos devuelven 200 para conservar la utilidad de los enlaces ya repartidos, sin acceso online ni registro
   de nuevas aperturas. La transición a FINISHED sigue sin implementar (D-FINISHED).
5. Envía `PUT /api/invitations/{token}/response`, sin sesión ni CSRF, con `responseOptionId`, `comment` y `email`
   opcionales salvo la opción. La opción debe pertenecer al evento. Un comentario con contenido requiere
   `allowComment` y admite hasta 500 caracteres; en blanco se trata como ausente y se guarda como null.
   El correo, si viene, se valida y guarda. No se muestra al anfitrión ni se usa para enviar nada en R10.
   Cualquier uso futuro necesita aprobación expresa y doble confirmación de la dirección antes de escribirle.
   D-EMAIL-NOT-SENT sigue abierta para la cita al responder.
6. Recibe 200 con la invitación y `myResponse` (opción, comentario, answeredAt y updatedAt, sin email).
   GET también incluye esos datos, o los omite si no hay respuesta, y calcula `canChangeResponse` en el backend:
   exige respuesta existente, permiso de cambio, PUBLISHED, plazo no vencido y evento aún no terminado.
   Un nuevo PUT cambia la respuesta si se cumplen esas condiciones; de lo contrario devuelve 409.
   El plazo rige también la primera respuesta. DRAFT conserva el 404 indistinguible del token desconocido;
   CANCELLED y FINISHED rechazan escrituras con 409.
7. Descarga `GET /api/invitations/{token}/calendar`, sin sesión ni CSRF, como archivo `event.ics`,
   `text/calendar;charset=UTF-8` y attachment. Incluye título, descripción, inicio y fin con la zona del evento,
   ubicación presencial y, si es online, la URL únicamente cuando el mismo filtro del JSON la permite.
   No incluye contactos, invitados, respuesta propia, token, contraseña ni datos de sala. UID basado en el
   evento, nunca en el token. CANCELLED devuelve 409 con ProblemDetail; FINISHED sigue permitiendo descargar
   (200), y DRAFT/token desconocido conservan el mismo 404 de R7. La descarga no marca OPENED ni requiere
   respuesta previa. Implementado en el commit 4 de R8; no se envía el archivo por correo todavía (R10).

La respuesta pública y sus errores llevan `Cache-Control: no-store` exacto y una instancia de error constante
`/api/invitations`, sin token. Solo `/api/invitations/**` se excluye de CSRF: la credencial es el token de la
ruta, no una cookie que el navegador envíe automáticamente. El frontend deberá servir la página que
contiene el token en su URL con `Referrer-Policy: no-referrer`, también al mostrar un mapa embebido de Google.
No hay límite de peticiones todavía; D-RATE-LIMIT permanece abierta.

**Gestionar respuestas**

El anfitrión consulta `GET /api/events/{id}/dashboard`: totales guests, answered, pending y attending; contadores
por opción en su orden; y filas de invitados en orden de alta con contacto, canal, estado de invitación,
invitationPath como en GET /guests y su respuesta (opción, etiqueta, comentario y fechas). El email que el
invitado deja en la respuesta no se expone. Solo el 200 del panel lleva Cache-Control: no-store exacto.
Propiedad comprobada en aplicación, evento ajeno → 404; no escribe ni modifica version.

El cálculo recorre guests[] y asocia respuestas por evento/token. Una respuesta sin invitado no cuenta.
Una respuesta con opción desconocida sí cuenta como answered, pero no en attending ni en una opción;
su fila lleva optionKnown=false y label=null. Si la opción existe, optionKnown=true; sin respuesta, response
y optionKnown son null. La lectura de respuestas es agrupada por eventId in [...], sin $lookup.

`GET /api/events` añade totalGuests, answered, pending y attending a la proyección del listado, sin byOption
ni filas de invitados. Sus cifras coinciden con totals del panel para el mismo evento. La aplicación lee los
eventos del anfitrión y sus respuestas en una sola consulta adicional para todos sus ids; si no hay eventos,
no consulta respuestas. No se añade una cabecera de caché propia al listado. El cálculo en memoria sobre
todos los eventos y respuestas, sin paginación, queda como D-LIST-SCALE.

El filtrado de presentación y reenvío sigue pendiente: R10 reenviará a todos los invitados que no respondieron,
también si ya abrieron la invitación. La respuesta manual del anfitrión queda fuera del MVP.

---

## 7. Modelo de datos

**Base de datos: MongoDB.** Cuatro colecciones, separadas según su patrón de escritura y su crecimiento. Los nombres de
colecciones y campos van en inglés, igual que el código; la tabla de equivalencias con la terminología de este documento
está en el `AGENTS.md` del repositorio.

```
users
  _id, name, email, passwordHash, timeZone, emailVerified

events
  _id, hostId, title, description, startsAt, endsAt, timeZone,
  modality (IN_PERSON | ONLINE),
  status (DRAFT | PUBLISHED | FINISHED | CANCELLED),
  allowComment, allowResponseChange, responseDeadline,

  venue                        // solo si modality = IN_PERSON
    { placeName, address, latitude, longitude, placeId, directions }

  onlineAccess                 // solo si modality = ONLINE
    { platform, url, roomId, password, instructions,
      linkVisibility (ON_CONFIRMATION | HOURS_BEFORE | ALWAYS), hoursBefore }

  responseOptions[]            // entre 2 y 5; el orden es la posición en la lista
    { id, label, countsAsAttendance }

  guests[]
    { id, name, email, phoneE164, token,
      invitationStatus (NOT_SENT | SENT | OPENED),
      deliveryChannel (EMAIL | WHATSAPP | TELEGRAM | LINK),
      sentAt, openedAt }

responses                      // un documento por respuesta de invitado
  _id, eventId, guestToken, responseOptionId, comment, email,
  answeredAt, updatedAt

wall_posts                     // iteración 2, referencia el evento
  _id, eventId, authorToken, ...
```

**Índices obligatorios**

| Colección   | Índice                           | Motivo                                                     |
|-------------|----------------------------------|------------------------------------------------------------|
| `events`    | `guests.token` único, multiclave | Vía de acceso de toda petición pública de invitación       |
| `events`    | `hostId`                         | Panel del anfitrión                                        |
| `responses` | `(eventId, guestToken)` único    | Impone una respuesta por invitado a nivel de base de datos |
| `responses` | `eventId`                        | Agregación del panel                                       |

**Decisiones del modelo**

- **`token`** debe ser criptográficamente aleatorio, largo, con índice único y revocable. Es la única credencial del
  invitado.
- **El estado `ANSWERED` no se almacena, se deriva.** Un invitado ha respondido si y solo si existe un documento en
  `responses` con su token. Esto evita que una respuesta tenga que escribir en dos colecciones y elimina la necesidad de
  transacciones multidocumento.
- **Las respuestas van fuera del evento** porque son la única escritura concurrente del sistema: veinte invitados
  contestando a la vez sobre un mismo documento producirían pérdidas de escritura.
- **El contenido social nunca se embebe.** Fotos y comentarios crecen sin límite y chocarían con el máximo de 16 MB por
  documento.

---

## 8. Decisiones tomadas en esta iteración

- El invitado nunca crea cuenta. Su identidad es el token del enlace.
- El envío por WhatsApp y Telegram es asistido, no automático. Se documenta como tal en la interfaz.
- La fecha del evento es única y la fija el anfitrión. La votación entre varias fechas llega en la siguiente iteración.
- Sin acompañantes ni campos personalizados: una respuesta por invitado.
- Un evento es presencial o en línea, nunca ambos. El híbrido queda para una iteración posterior.
- El correo al responder es opcional y se mantiene almacenado para posibles funcionalidades futuras. No se
  expone al anfitrión ni a otros invitados, y R10 no lo usa. Antes de cualquier uso futuro se exige aprobación
  expresa y doble confirmación, porque la operación pública permite indicar una dirección sin conocimiento de su dueño.
- **Base de datos documental (MongoDB) en lugar de relacional.** El evento se trabaja como un todo y las opciones de
  respuesta configurables encajan de forma natural en un documento, sin la tabla adicional que exigiría un modelo
  relacional.
- **Arquitectura hexagonal** en el backend, con el modelo de dominio libre de anotaciones de framework y separado de los
  documentos de persistencia.
- **El nombre del producto es gtog.** El artefacto del backend es `gtog`, con paquete raíz `com.gtog`.
- **Todo el código, las rutas de la API y los identificadores en inglés.** Esta especificación y los comentarios del
  código siguen en español.

## 9. Pendientes por resolver antes de codificar

Zona horaria, clave de Google Maps e idioma están resueltos. El proveedor de correo también: Mailjet por
SMTP con el starter existente, sin dependencias nuevas. Registrar respuestas manuales queda fuera del MVP;
si se incorpora después, GuestResponse tendrá source y su ausencia significará invitado, sin migración.

Los comportamientos de envío que aún hay que concretar están en R10 de `docs/plan-iteracion-1.md`, incluida
la cifra del tope por invitado para envíos de cita. Primero se cierra R9 y después se implementa R10.
