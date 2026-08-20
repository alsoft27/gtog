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
| RF-1.5 | Listado de los eventos del usuario con su estado y contadores de respuesta.                                                                                                                    |
| RF-1.6 | Diseño responsivo. El anfitrión trabaja en escritorio; el invitado abrirá su enlace desde el teléfono en la mayoría de los casos, así que la página de respuesta se diseña primero para móvil. |

**Estados del evento en esta iteración:** `Borrador → Publicado → Finalizado`, más `Cancelado`. Sin fase de votación
todavía.

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
| RF-2.6 | Editar y eliminar invitados mientras el evento no esté finalizado.                                                                                                 |
| RF-2.7 | Cada invitado recibe al crearse un token único e irrepetible que genera su enlace personal de respuesta.                                                           |

### Elección del canal

| ID      | Requisito                                                                                                                                                                                                                                         |
|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RF-2.8  | Al invitar, el anfitrión elige el canal por invitado: correo, WhatsApp o Telegram.                                                                                                                                                                |
| RF-2.9  | Los canales disponibles dependen de los datos capturados: correo requiere correo; WhatsApp y Telegram requieren teléfono. Los no disponibles se muestran deshabilitados con la razón.                                                             |
| RF-2.10 | **Correo:** el backend envía el mensaje con la plantilla del evento y el enlace único. Es el único canal con envío automático real.                                                                                                               |
| RF-2.11 | **WhatsApp:** la aplicación abre `https://wa.me/<E164>?text=<mensaje>` en una pestaña nueva, con el mensaje y el enlace ya redactados. El anfitrión confirma el envío en WhatsApp.                                                                |
| RF-2.12 | **Telegram:** mismo mecanismo con `https://t.me/share/url=<enlace>&text=<mensaje>`.                                                                                                                                                               |
| RF-2.13 | Tras abrir WhatsApp o Telegram, la aplicación pregunta al anfitrión si el envío se completó y marca el invitado en consecuencia.                                                                                                                  |
| RF-2.14 | Envío en bloque por canal: seleccionar varios invitados y disparar el envío. En correo es automático; en WhatsApp y Telegram se abre una pestaña por invitado, en secuencia asistida para evitar el bloqueo de ventanas emergentes del navegador. |
| RF-2.15 | Plantilla del mensaje editable por el anfitrión, con variables de nombre del invitado, título del evento, fecha y enlace.                                                                                                                         |
| RF-2.16 | Botón de copiar el enlace individual, para pegarlo en cualquier otro medio.                                                                                                                                                                       |
| RF-2.17 | Reenvío individual o a todos los que no han respondido.                                                                                                                                                                                           |

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
| RF-3.9 | Si el evento cambia de ubicación o de enlace después de publicado, se notifica por correo a los invitados que ya respondieron.                                                                                   |

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
| RF-4.7 | Interruptor para permitir o no que el invitado cambie su respuesta, y hasta qué fecha límite.                                                                                      |
| RF-4.8 | Las opciones se pueden editar mientras el evento esté en borrador. Una vez publicado, solo se pueden renombrar y agregar; no eliminar opciones que ya tengan respuestas asociadas. |
| RF-4.9 | El panel del anfitrión muestra el conteo por cada opción definida, más el total de invitados sin responder.                                                                        |

---

## 6. Flujos principales

**Crear y publicar un evento**

1. Datos básicos: título, descripción, fecha, hora de inicio y fin.
2. Modalidad y ubicación (presencial con mapa, en línea con enlace, o ambas).
3. Opciones de respuesta: elegir conjunto predefinido o construir uno propio.
4. Invitados: agregarlos uno a uno.
5. Revisión: vista previa de la página que verá el invitado.
6. Publicar y enviar invitaciones eligiendo canal por invitado.

**Responder una invitación**

1. El invitado abre su enlace único. No hay registro ni contraseña.
2. Ve título, descripción, fecha en su zona horaria, y la ubicación o la nota de evento en línea.
3. Elige una de las opciones que definió el anfitrión y, si está habilitado, deja un comentario.
4. Confirmación en pantalla, con opción de descargar el archivo de calendario.
5. Si el anfitrión lo permitió, puede volver al mismo enlace y cambiar su respuesta.

**Gestionar respuestas**

El anfitrión ve la lista de invitados con canal usado, estado de invitación, respuesta y comentario; filtra por estado y
reenvía a los pendientes.

---

## 7. Modelo de datos

**Base de datos: MongoDB.** Cuatro colecciones, separadas según su patrón de escritura y su crecimiento. Los nombres de
colecciones y campos van en inglés, igual que el código; la tabla de equivalencias con la terminología de este documento
está en el `CLAUDE.md` del repositorio.

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

  responseOptions[]            // entre 2 y 5, ordenadas
    { id, label, countsAsAttendance, order }

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
- Al invitado que llegó por WhatsApp o Telegram se le pide el correo en el momento de responder, a cambio del archivo de
  calendario. Es la única forma de obtener ese dato cuando la invitación no viajó por correo.
- **Base de datos documental (MongoDB) en lugar de relacional.** El evento se trabaja como un todo y las opciones de
  respuesta configurables encajan de forma natural en un documento, sin la tabla adicional que exigiría un modelo
  relacional.
- **Arquitectura hexagonal** en el backend, con el modelo de dominio libre de anotaciones de framework y separado de los
  documentos de persistencia.
- **El nombre del producto es gtog.** El artefacto del backend es `gtog-api`, con paquete raíz `com.gtog`.
- **Todo el código, las rutas de la API y los identificadores en inglés.** Esta especificación y los comentarios del
  código siguen en español.

## 9. Pendientes por resolver antes de codificar

1. **Zona horaria del evento:** ¿la fija el anfitrión y se convierte para cada invitado, o se muestra siempre la del
   evento? Recomendación: guardar en UTC con la zona del evento, y mostrar ambas cuando difieran de la del navegador del
   invitado.
2. **Proveedor de correo:** SendGrid, Amazon SES o SMTP propio. Afecta la entregabilidad y el costo desde el primer día.
3. **Clave de Google Maps:** hay que crear el proyecto y activar facturación antes de empezar la pantalla de ubicación.
4. **¿El anfitrión puede registrar manualmente la respuesta de alguien que contestó por WhatsApp?** Es barato de
   implementar y muy usado en la práctica.
5. **Idioma:** ¿solo español en esta iteración?
