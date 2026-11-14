# gtog — Dónde estamos y por qué

Documento para retomar el proyecto. Recoge qué hace la aplicación, qué decisiones se tomaron y con qué razón, en qué punto está el desarrollo y cuál es el siguiente paso.

---

# Parte 1 — Qué hace la aplicación

## El problema

Organizar un evento implica dos trabajos que hoy se hacen en herramientas distintas: definir el evento y perseguir a los invitados. Lo segundo acaba siempre en un grupo de WhatsApp, una hoja de cálculo, y el anfitrión contando cabezas a mano.

gtog junta las dos cosas, con una restricción autoimpuesta: **el invitado no debe tener que hacer nada que no haría por un mensaje normal.** Ni instalar una aplicación, ni crearse una cuenta, ni recordar una contraseña.

## Los dos actores

**El anfitrión** tiene cuenta. Es quien crea el evento, decide a quién invita y ve los resultados. Puede ser una persona o una empresa.

**El invitado** no tiene cuenta y nunca la tendrá. Su identidad es un enlace único que solo él conoce. Lo abre, ve el evento, responde, y si cambia de opinión vuelve al mismo enlace.

## El recorrido completo

### Crear el evento

El anfitrión define lo básico: título, descripción, fecha, hora de inicio y fin, y la zona horaria en la que ocurre.

Después elige la modalidad, que condiciona todo lo demás:

- **Presencial**: busca la dirección con el autocompletado de Google Places, ajusta el marcador en el mapa si hace falta, y añade indicaciones libres como el piso, el salón o dónde aparcar. Se guardan el nombre del sitio, la dirección formateada, las coordenadas y el identificador de Google.
- **En línea**: indica la plataforma, el enlace de la reunión, y opcionalmente el identificador de sala y la contraseña. Además decide **cuándo se muestra ese enlace**: en cuanto alguien confirme, un número de horas antes del comienzo, o siempre.

### Definir cómo se responde

Aquí está una de las diferencias con las herramientas habituales: **las opciones de respuesta no son fijas.** El anfitrión define entre dos y cinco, les pone el texto que quiera, decide el orden, y marca cuáles cuentan como asistencia para los totales.

Una cena de empresa puede usar "Voy / Voy y llevo acompañante / No puedo". Una reunión de trabajo, "Confirmo / Declino / Pendiente". Si no se especifica nada, el sistema aplica "Asisto" y "No asisto".

Además configura dos cosas: si el invitado puede dejar un comentario junto a su respuesta, y si puede cambiarla después, con fecha límite opcional.

### Añadir invitados

Uno a uno, con nombre y correo electrónico, teléfono, o ambos. El teléfono se normaliza a formato internacional, porque de ahí dependen los enlaces de WhatsApp y Telegram.

Al crearse, cada invitado recibe un **token único e irrepetible**, que genera su enlace personal.

### Enviar las invitaciones

El anfitrión elige el canal **por invitado**, no para todos:

- **Correo**: el sistema lo envía automáticamente.
- **WhatsApp y Telegram**: la aplicación abre la conversación con el mensaje y el enlace ya redactados. El anfitrión pulsa enviar.
- **Copiar el enlace**: para pegarlo donde quiera.

Los canales disponibles dependen de los datos de cada invitado. Si solo tienes su correo, WhatsApp aparece deshabilitado.

### Responder

El invitado abre su enlace, normalmente desde el móvil y desde un chat. Ve el evento, la fecha en su propia zona horaria, el mapa con el botón de cómo llegar o la información de conexión, y las opciones que definió el anfitrión.

Elige una, deja un comentario si está permitido, y se le ofrece dejar su correo para recibir la cita de calendario. Ese es el momento en el que el anfitrión consigue el correo de quien llegó por WhatsApp.

### Seguir las respuestas

El panel muestra los contadores por cada opción definida, quién no ha contestado, y el estado de cada invitación: sin enviar, enviada, abierta, respondida.

Con una honestidad deliberada: en WhatsApp y Telegram nunca se sabrá si el mensaje llegó, solo si el invitado abrió el enlace. La interfaz no finge lo contrario.

## Lo que viene después del MVP

**El muro social.** Cada evento con su espacio privado donde los asistentes suben fotos, comentan y reaccionan. Es el diferenciador real del producto a medio plazo, y la razón por la que el modelo de datos ya está preparado para recibirlo.

**La votación de fechas.** Proponer varias fechas candidatas y que los invitados marquen su disponibilidad.

**La aplicación móvil.** Con lo que aporta el teléfono: importar desde la agenda, cámara directa al muro, notificaciones push, y check-in por código QR el día del evento.

---

# Parte 2 — Decisiones tomadas

Esta es la parte que más cuesta reconstruir después. Cada una con su razón.

## Producto

**El invitado no crea cuenta. El token es su credencial.**
Cualquier fricción de registro mata la tasa de respuesta. El coste es que la seguridad del enlace es toda la seguridad que hay, y de ahí que el token tenga que ser criptográficamente aleatorio y revocable.

**WhatsApp y Telegram son envío asistido, no automático.**
No es una limitación de diseño, es que no se puede hacer de otra forma: ninguna de las dos plataformas permite a terceros enviar mensajes sin una cuenta de empresa verificada, plantillas aprobadas y coste por mensaje. La idea original de "importar contactos desde Instagram y WhatsApp" se descartó por lo mismo: esas plataformas no exponen sus listas de contactos. Lo que sí funciona es la agenda del teléfono y el enlace compartible.

**Las opciones de respuesta las define el anfitrión.**
Convierte lo que en otras herramientas es un enum fijo en datos. A cambio, el modelo tiene que conservar la identidad de cada opción cuando se renombra, porque las respuestas la referencian.

**Un evento es presencial o en línea, nunca ambos.**
El híbrido obliga a que la respuesta indique el canal de asistencia —"asisto presencial" frente a "asisto en línea"—, lo que acopla la ubicación con las opciones de respuesta. Se descartó para el MVP con el modelo preparado para recibirlo después.

**La fecha la fija el anfitrión.**
La votación entre fechas candidatas es una iteración posterior, aunque fuera el punto de partida de la idea.

## Arquitectura

**Hexagonal, con una regla que no se negocia:** el dominio no lleva ni una anotación de framework, y el modelo de dominio y el documento de persistencia son clases distintas con un mapper en medio. Es exactamente la concesión que casi todo el mundo hace para ahorrarse el mapeo, y es lo que vacía la arquitectura.

**Un hexágono por contexto:** `event`, `user`, y los que vendrán.

**La autorización de recursos vive en la capa de aplicación**, no en anotaciones del controlador. Comprobar que un evento pertenece al anfitrión que pregunta es una regla de negocio, y tiene que poder probarse sin levantar Spring.

## Datos

**MongoDB, con cuatro colecciones separadas por patrón de escritura**, no por entidad:

- `users`: escritura rara.
- `events`: el evento completo con sus opciones e invitados embebidos. Un solo escritor.
- `responses`: un documento por respuesta. Es la única escritura concurrente del sistema.
- `wall_posts`: crecimiento sin límite, por eso nunca embebido.

**Las respuestas van fuera del evento.** Si estuvieran embebidas, veinte invitados contestando a la vez escribirían sobre el mismo documento y se pisarían.

**El estado `ANSWERED` no se almacena, se deriva** de que exista un documento de respuesta. Responder solo escribe en `responses`, sin actualizar un estado duplicado en el evento.

**La cascada del invitado retira primero al invitado y después su respuesta** (R8, commit 3).
Es una operación sobre dos colecciones sin transacción multidocumento. Si falla la retirada, la respuesta
permanece con el invitado vivo; si falla la limpieza posterior, queda una respuesta inalcanzable por token.
Se prioriza no perder una respuesta mientras su invitado todavía existe.

**La unicidad se impone con índices, no con código.** Un invitado por token, una respuesta por invitado y evento, un correo por usuario. Las comprobaciones previas en la capa de aplicación fallan bajo concurrencia.

**Las fechas se guardan como hora local más zona horaria**, y el instante se calcula al leer. Un evento ocurre "el 12 de diciembre a las 19:00 en Madrid", no en un instante absoluto; si cambian las reglas horarias del país, un instante almacenado dejaría de corresponder a esa hora.

**Sin herramienta de migraciones.** Mongock no tiene compatibilidad confirmada con Spring Boot 4. Los índices se crean en el arranque con un componente idempotente.

## Seguridad

**Sesión de servidor con cookie `HttpOnly`, no JWT.**
El único cliente es Angular, sin móvil ni terceros. Una cookie `HttpOnly` elimina el riesgo de exfiltración por XSS y es más difícil de hacer mal que un JWT en el navegador. Decisión reversible cuando exista la app móvil.

**404 y no 403 al acceder a un evento ajeno.**
Un 403 le confirmaría a un atacante que ese identificador existe.

**El enlace de la reunión se filtra en el servidor.**
Si viajara en la respuesta confiando en que Angular no lo pinte, sería legible desde las herramientas del navegador. El endpoint omite el campo.

**El acceso al muro social depende de estar invitado, no de haber respondido ni confirmado.**
El token válido acredita la invitación. La respuesta expresa una decisión de asistencia, no la pertenencia
al evento: quien no ha respondido o ha elegido "No asisto" sigue siendo un invitado. La confirmación solo
gobierna la visibilidad del enlace de la sala en ON_CONFIRMATION, y exige una opción con
`countsAsAttendance = true`. Esta decisión se documenta en R8; el muro social sigue fuera del MVP.

## Convenciones

**El código, las rutas y los identificadores en inglés. La documentación y los comentarios, en español.** Hay una tabla de equivalencias en el `AGENTS.md` para que la traducción sea consistente.

**La validación se reparte según su naturaleza:** Jakarta se encarga del formato, y lo que es una regla de negocio —un título obligatorio, una etiqueta no vacía— se valida en el dominio y sale como 422, no como 400.

**Sin Lombok.** Los `record` de Java 25 cubren lo que hacía falta.

**Tests de integración contra una base de datos real**, no embebida. Las diferencias de un motor falso producen tests verdes y producción rota.

**Se trabaja por rebanada vertical:** dominio, puerto, adaptador, controlador y test, funcionando de punta a punta antes de pasar a la siguiente.

---

# Parte 3 — El estado actual

## Lo que funciona

Seis entregas cerradas, todas del lado del anfitrión: creación inicial más cinco rebanadas numeradas, R1–R5.

| | Qué quedó |
|---|---|
| **Crear evento** | `POST /api/events`, en borrador, con validación completa |
| **R1 · Consultar y listar** | Detalle por id y listado propio del anfitrión |
| **R2 · Opciones de respuesta** | De dos a cinco, con orden, identidad estable al renombrar, y valores por defecto |
| **R3 · Ubicación y acceso** | Datos de Google Places o sala virtual, con regla de visibilidad del enlace |
| **R4 · Editar, publicar, cancelar** | Máquina de estados con sus precondiciones |
| **R5 · Usuario y seguridad** | Registro, login, logout, cadena de Spring Security y comprobación de propiedad en los siete servicios de evento |

**Endpoints vivos**

```
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/logout

POST   /api/events
GET    /api/events
GET    /api/events/{id}
PUT    /api/events/{id}
PUT    /api/events/{id}/response-options
PUT    /api/events/{id}/venue
PUT    /api/events/{id}/online-access
POST   /api/events/{id}/publish
POST   /api/events/{id}/cancel
```

**174 tests en verde**, entre dominio, aplicación e integración: los 172 de la revisión inicial más dos tests de validación de zona horaria del usuario.

**Stack**: Java 25, Spring Boot 4.1, MongoDB Atlas, Maven por defecto, OpenAPI en `/swagger-ui.html`.

## Lo que todavía no existe

**Nadie puede ser invitado y nadie puede responder.** Todo lo construido es configuración: un anfitrión puede dejar un evento perfectamente definido y seguir sin poder repartirlo.

Esto no es un retraso, es dónde está el proyecto. Pero conviene tenerlo presente al medir el avance: cinco de las doce rebanadas numeradas, además de la creación inicial, no es "casi la mitad", porque la parte que aporta valor está entera por delante.

| | Rebanada | Qué desbloquea |
|---|---|---|
| **R6** | Invitados | Alta con token, teléfonos en E.164, edición y borrado |
| **R7** | Página pública del invitado | El endpoint sin autenticación que resuelve el token |
| **R8** | Registrar respuesta | La colección `responses` y el archivo de calendario |
| **R9** | Panel del anfitrión | Contadores por opción y seguimiento |
| **R10** | Envío por correo | Bloqueado por la elección de proveedor |
| **R11** | WhatsApp y Telegram | Enlaces profundos y estados de envío |
| **R12** | Verificación de correo y recuperación de contraseña | Depende de R10 |

---

# Parte 4 — El siguiente paso

**R6, invitados.** Y antes de empezar, tres decisiones pendientes:

**El formato del token.** Es lo primero, porque es irreversible en cuanto circule el primer enlace. La propuesta: 32 bytes de `SecureRandom` en Base64 sin relleno y seguro para URLs, lo que da 43 caracteres. Nada del token puede derivar del identificador del invitado o del evento.

**Dónde viven los invitados en el código.** Dentro del hexágono `event`, no como uno propio: el invitado no tiene vida independiente del evento, comparte agregado y comparte documento. Un hexágono `guest` que escribiera en la colección `events` sería una separación falsa.

**El invitado que ya respondió.** Ya está decidido que eliminarlo requiere confirmación explícita y borra su respuesta en cascada. Queda por resolver cómo se implementa el hueco mientras la colección `responses` todavía no existe.

**El hito que importa** no es una rebanada, es R6 más R7 más R8: el primer camino completo, donde se crea un evento, se añade un invitado, se le pasa el enlace y responde. Ahí el producto se puede enseñar a una persona real por primera vez, y ahí se sabrá si el diseño del token y de la página del invitado aguanta el contacto con alguien que no lo construyó.

## Decisiones abiertas

| Decisión | Bloquea | Urgencia |
|---|---|---|
| Proveedor de correo (SES, SendGrid, SMTP) | R10, R12 | Alta |
| Formato del token | R6 | Irreversible una vez circule un enlace |
| ¿Puede el anfitrión registrar a mano una respuesta recibida por fuera? | R8, R9 | Decide el modelo |

## Deuda técnica

| # | Qué |
|---|---|
| D-3 | El índice único de `guests.token` no existe porque `guests[]` no existe. Entra con R6 |
| D-6 | Sin herramienta de migraciones de esquema |
| D-7 | Saldada en R8, commit 3: PUBLISHED admite renombrar y agregar, pero no eliminar opciones con respuestas ni cambiar su countsAsAttendance |
| D-ORPHAN-OPTION | Una respuesta concurrente puede referenciar una opción que el anfitrión acaba de eliminar. Lectura defensiva: opción inexistente no confirma asistencia. Sin transacciones |
| D-FINISHED | Ningún mecanismo pasa un evento a `FINISHED` |
| D-DELETE-DRAFT | No se puede eliminar un borrador |
| D-SWAGGER-PUBLIC | La especificación OpenAPI es pública en todos los entornos |
| D-SECURE-COOKIE | La cookie de sesión no lleva `Secure` fuera de producción |
| D-DUP-KEY | La detección de qué índice único se violó depende del texto del error de MongoDB |

---

## Dónde está cada cosa

| Archivo | Para qué |
|---|---|
| `AGENTS.md` | Convenciones, arquitectura y reglas de negocio. Lo lee Codex al empezar cada sesión |
| `docs/mvp-alcance-iteracion-1.md` | Alcance funcional con requisitos numerados |
| `docs/plan-iteracion-1.md` | Estado de las rebanadas, deuda y decisiones |
| `docs/modelo-evento.md` | El agregado del evento en detalle |
| `docs/modelo-usuario.md` | El agregado del usuario |
