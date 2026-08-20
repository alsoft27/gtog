# gtog — Plan de la iteración 1

Estado a 19 de agosto de 2026.

---

## 1. Dónde estamos

**Cerrado**

| Rebanada | Estado |
|---|---|
| Crear evento en `DRAFT` | `POST /api/events` funcionando de punta a punta, con dominio, puertos, adaptador Mongo, controlador y tests |
| Infraestructura | Java 25, Spring Boot 4.1, Maven aislado, MongoDB Atlas, OpenAPI |

**Lo que eso valida:** la arquitectura hexagonal funciona en la práctica, el mapeo entre `Event` y `EventDocument` es asumible, y el circuito completo de desarrollo está operativo.

---

## 2. Deuda técnica abierta

Esto no es una lista de pendientes cualquiera: son cosas que ya están mal a propósito y que hay que devolver a su sitio. Escritas para que ninguna se pierda.

| # | Deuda | Consecuencia si se olvida | Cuándo se salda |
|---|---|---|---|
| D-1 | **Spring Security retirado** | Todos los endpoints son públicos | Rebanada 5, ver §4 |
| D-2 | **`hostId` viaja en el cuerpo del request** | Cualquiera crea eventos en nombre de otro | Con D-1 |
| D-3 | **Sin componente de creación de índices** | Los índices no existen en Atlas; las consultas por token harán escaneo completo | Rebanada 6, antes de que haya invitados |
| D-4 | **Health indicator de Mongo desactivado** | No hay sonda real de base de datos | Cuando haya despliegue |
| D-5 | **Tests de integración contra Atlas** | Los tests necesitan red y son lentos | Aceptable mientras seas el único desarrollador |
| D-6 | **Sin herramienta de migraciones** | No hay forma versionada de cambiar el esquema | Cuando haya datos reales |

---

## 3. Las rebanadas que faltan

Cada una es vertical: dominio, puerto, adaptador, controlador y test, funcionando de punta a punta.

### Bloque A — Completar el evento

**R1. Consultar y listar eventos**
`GET /api/events/{id}` y `GET /api/events`. Cierra la lectura y te da con qué comprobar todo lo demás sin abrir Atlas.

**R2. Opciones de respuesta**
Las define el anfitrión al crear o editar. Entre dos y cinco, ordenadas, cada una marcando si cuenta como asistencia. Se embeben en el documento del evento. Bloquea todo el flujo del invitado, así que va antes.

**R3. Ubicación y acceso en línea**
`Venue` con datos de Google Places para presencial, `OnlineAccess` con la regla de `LinkVisibility` para en línea. La regla se aplica al leer, no al escribir, y el filtrado va en el backend.

**R4. Editar, publicar y cancelar**
La máquina de estados: `publish()` solo desde `DRAFT`, `cancel()` desde `PUBLISHED`. Es donde el modelo de dominio empieza a ganarse el no ser un `record`, y donde se prueba de verdad el `@Version`.

### Bloque B — Identidad del anfitrión

**R5. Usuario y seguridad**
Registro, login y la cadena de Spring Security. Salda D-1 y D-2: el `hostId` sale del DTO y pasa a venir del usuario autenticado. Dos reglas de acceso desde el principio: `/api/events/**` autenticado, `/api/invitations/**` público resuelto por token.

### Bloque C — El núcleo del producto

**R6. Invitados**
Alta con nombre y correo o teléfono, normalización a E.164, detección de duplicados, y generación del token criptográficamente aleatorio. Salda D-3: aquí es donde el índice único sobre `guests.token` deja de ser opcional.

**R7. Página pública del invitado**
`GET /api/invitations/{token}`. Devuelve el evento tal como lo ve ese invitado, con el enlace de la reunión omitido si no cumple la regla de visibilidad. Es el endpoint más delicado del sistema.

**R8. Registrar respuesta**
`POST /api/invitations/{token}/response`. Crea la colección `responses`, con el índice único sobre `(eventId, guestToken)` y el upsert al cambiar de respuesta. Aquí se implementa que `ANSWERED` se derive y no se almacene.

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
| **Clave de Google Maps con facturación activa** | R3 | Alta: crear el proyecto tarda |
| **Proveedor de correo** (SES, SendGrid, SMTP) | R10 | Media |
| **¿Puede el anfitrión registrar a mano la respuesta de un invitado?** | R8, R9 | Baja, pero decide el modelo |
| **Formato del token**: longitud y alfabeto | R6 | Baja, pero irreversible una vez haya enlaces circulando |

---

## 6. Qué significa "terminada" una rebanada

Para no discutirlo cada vez:

1. `clean verify` en verde.
2. Test unitario de dominio para cada regla de negocio nueva.
3. Test de integración del endpoint, incluyendo al menos un caso de error.
4. Sin anotaciones de framework en `domain/`.
5. Documentada en OpenAPI, con anotaciones solo en `infrastructure/in/web`.
6. Commit con mensaje convencional.
