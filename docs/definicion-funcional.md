# gtog — Definición funcional

Qué hace la aplicación y qué requisitos cumple. El estado de la implementación, las decisiones y la deuda
están en `plan-iteracion-1.md`; el contrato técnico de cada agregado, en `modelo-evento.md`,
`modelo-invitado.md` y `modelo-usuario.md`.

**Estado:** el backend del MVP (iteración 1) está implementado de R1 a R12, incluida la verificación de correo
y la recuperación de contraseña. El frontend Angular no se ha empezado; los bloqueadores de despliegue
público siguen en el plan.

---

## 1. Qué es gtog

Organizar un evento exige dos trabajos que suelen hacerse en herramientas distintas: definir el evento y
perseguir a los invitados. Lo segundo acaba en un grupo de WhatsApp, una hoja de cálculo y el anfitrión
contando cabezas a mano. gtog junta las dos cosas con una restricción autoimpuesta: **el invitado no debe
hacer nada que no haría por un mensaje normal.** Ni instalar una aplicación, ni crearse una cuenta, ni
recordar una contraseña.

Dos rasgos lo distinguen de las herramientas existentes:

- **Convocatoria sin fricción.** El anfitrión invita con los contactos que ya tiene y el invitado entra por
  un enlace único.
- **El evento no termina cuando termina.** A medio plazo, cada evento tendrá un muro privado donde los
  asistentes comparten fotos y comentarios. El modelo de datos ya está preparado para recibirlo.

## 2. Actores

| Actor | Descripción | Cuenta |
|---|---|---|
| **Anfitrión** | Crea el evento y lo controla. Persona o empresa. | Sí |
| **Invitado por enlace** | Entra con su enlace único, ve el evento y responde. Su identidad es el token. | No, nunca |
| **Persona con enlace público del evento** | Se da de alta con nombre y recibe su enlace individual. | No |

Actores posteriores al MVP: co-organizador, invitado registrado y administrador de plataforma (§8).

---

## 3. El recorrido completo

**Crear el evento con fecha definida.** El anfitrión define título, descripción, fecha, hora de inicio y fin y la zona horaria en
que ocurre. Elige la modalidad, que condiciona el resto:

- **Presencial:** dirección con el autocompletado de Google Places y marcador ajustable. Se guardan el
  nombre del lugar, la dirección, las coordenadas y el `place_id`, más indicaciones libres. El backend no
  llama a Google: confía en los datos que el cliente ya resolvió.
- **En línea:** plataforma, enlace de la reunión y, opcionalmente, identificador de sala y contraseña. El
  anfitrión decide cuándo se muestra el enlace: al confirmar asistencia, N horas antes del inicio, o siempre.

**Definir cómo se responde en fecha definida.** Las opciones de respuesta no son fijas: el anfitrión define entre dos y cinco,
con el texto y el orden que quiera, y marca cuáles cuentan como asistencia. Por defecto, "Asisto" y "No
asisto". Configura además si el invitado puede comentar, si puede cambiar su respuesta y un plazo opcional.

**Añadir invitados.** El anfitrión los da de alta con nombre y correo, teléfono o ambos. El teléfono se normaliza a E.164, porque de ahí
dependen los enlaces de WhatsApp. Cada invitado recibe un token único e irrepetible que genera su enlace.
Se pueden dar de alta varios en una sola operación, todo o nada, con todos los errores por posición. El
backend admite por defecto hasta 100 por petición y 500 por evento, configurables. El alta rápida en serie
sigue siendo trabajo del frontend (RF-2.4). La futura importación CSV y desde agenda usará el mismo endpoint
de lote; no se implementan esos importadores en esta iteración.

También puede pedir un enlace público: en borrador es una intención y el enlace se crea al publicar.
Cualquiera con él se da de alta inmediatamente con nombre; el contacto es opcional. Por defecto hay 25
plazas de este origen, dentro del máximo de 500. Si un contacto ya está en el evento se descarta en silencio,
sin revelar a quién pertenece. Revocar y reactivar crea otro enlace; los invitados existentes se conservan.
No reciben correo de gtog, aunque se edite después su contacto; sí pueden recibir enlaces WhatsApp con teléfono.

**Enviar las invitaciones.** El canal se elige por invitado:

- **Correo:** el sistema lo envía (Mailjet por SMTP), con límites por invitado y por anfitrión.
- **WhatsApp:** envío asistido. La aplicación genera un enlace `wa.me` con el mensaje ya redactado, el
  anfitrión lo abre y pulsa enviar desde su propia cuenta, y después confirma el envío.
- **Copiar el enlace:** para pegarlo en cualquier otro medio.

Los canales disponibles dependen de los datos del invitado: sin teléfono, WhatsApp no está disponible.

**Responder.** El invitado abre su enlace, normalmente desde el móvil. Ve el evento con las fechas en su
zona horaria, el lugar o la información de conexión, y las opciones del anfitrión. Elige una, deja un
comentario si está permitido y puede descargar un archivo de calendario. Puede indicar un correo opcional,
que se guarda sin uso: no se muestra al anfitrión ni se le escribe. Cualquier uso futuro exige aprobación
expresa y doble confirmación de la dirección.

**Seguir las respuestas.** El panel muestra contadores por opción, quién no ha contestado y el estado de cada
invitación: sin enviar, enviada, abierta, respondida. Con una honestidad deliberada: de un mensaje de
WhatsApp nunca se sabrá si llegó, solo si el invitado abrió el enlace, y "enviada" por correo significa
aceptada por el proveedor, no entregada. La interfaz no debe fingir lo contrario.

---

## 4. Alcance del MVP

**Entra:** cuenta de anfitrión; evento presencial o en línea; invitados del anfitrión por correo o teléfono,
o alta por enlace público con contacto opcional; invitación por
correo o WhatsApp; opciones de respuesta configurables; página pública del invitado sin registro; panel de
seguimiento.

**No entra:** muro social, modalidad híbrida, importación desde agenda, carga CSV,
acompañantes, campos adicionales, check-in, cuentas de empresa, recordatorios automáticos, Telegram, plantilla
de mensaje editable, aplicación móvil.

**Ampliación aprobada, en desarrollo: eventos con fecha por definir.** El anfitrión propondrá fechas
candidatas con fecha obligatoria y hora opcional, sin hora de fin, y un plazo obligatorio para contestar.
El invitado puede indicar disponibilidad en una, varias o ninguna; una respuesta sin disponibilidad se
distinguirá de no haber respondido. Cambiar las candidatas invalidará las disponibilidades anteriores;
se conserva solo la última respuesta. Elegir o cambiar la candidata definitiva sin alterar las candidatas
conservará las disponibilidades y la hora opcional, sin inventar horarios ni convertir disponibilidad en
confirmación de asistencia. El vencimiento del plazo, o todas las respuestas sin enlace público activo,
solo provocará un aviso por correo al anfitrión; no cerrará anticipadamente las respuestas ni avisará a
los invitados. El desarrollo se divide en pasos en el plan. Desde el paso 2 se puede crear y consultar
un borrador `DATE_POLL`, y editar sus datos generales conservando las candidatas. Admite entre dos y
diez fechas distintas (máximo configurable), mezclando horas opcionales, y plazo obligatorio. El paso 3
permite reemplazar candidatas: los cambios reales avanzan la revisión e invalidan la ronda anterior,
mientras que repetir o reordenar la conserva. Invitados, tokens y estados de invitación no cambian.
Desde el paso 4 se publica sin fecha definitiva, se muestra en los enlaces públicos y permite convocar
por correo o WhatsApp. Los mensajes muestran candidatas, horas opcionales, zona y plazo, sin inventar
inicio ni fin. El alta pública mantiene sus restricciones y usa el plazo como ventana. La disponibilidad
se registra por invitación y revisión, con un plazo inclusivo. Las consultas no aceptan una respuesta
de asistencia. Sin fecha definitiva, el calendario devuelve 409. Los eventos de fecha
definida mantienen el flujo completo; omitir `eventType` conserva `FIXED_DATE`, también en documentos antiguos.

**Estados del evento:** `DRAFT → PUBLISHED`, más `CANCELLED` (solo desde `PUBLISHED`, irreversible).
`FINISHED` existe en el modelo pero ningún mecanismo lleva un evento a ese estado. Un borrador no se puede
eliminar. Ambas limitaciones son deuda anotada en el plan.

---

## 5. Requisitos del MVP

Estado: ✓ implementado · F frontend · ✗ fuera o aplazado. El detalle de cada comportamiento
está en los modelos.

### Cuenta y acceso

| ID | Requisito | Estado |
|---|---|---|
| RF-1.1 | Registro e inicio de sesión con correo y contraseña | ✓ R5 |
| RF-1.2 | Verificación de correo antes de crear eventos, comprobada desde la base de datos en cada creación | ✓ R12 |
| RF-1.3 | Recuperación de contraseña | ✓ R12 |
| RF-1.4 | Perfil mínimo: nombre, correo y zona horaria IANA | ✓ |
| RF-1.5 | Listado de eventos con estado y contadores (`totalGuests`, `answered`, `pending`, `attending`) | ✓ R9 |
| RF-1.6 | Diseño responsivo; la página del invitado se diseña primero para móvil | F |

### Invitados y envío

| ID | Requisito | Estado |
|---|---|---|
| RF-2.1 | Alta del anfitrión con nombre y correo o teléfono, al menos uno; el contacto es opcional en el alta pública | ✓ R6 |
| RF-2.2 | Teléfono guardado en E.164 | ✓ R6 |
| RF-2.3 | Validación de formato de correo y teléfono | ✓ R6 |
| RF-2.4 | Alta rápida en serie | F |
| RF-2.5 | Duplicados por correo o teléfono dentro del evento | ✓ R6 |
| RF-2.6 | Editar y eliminar en DRAFT o PUBLISHED; con respuesta exige confirmación y borra la respuesta en cascada | ✓ R6, R8 |
| RF-2.7 | Token único por invitado que genera su enlace | ✓ R6 |
| RF-2.8 | Canal elegido por invitado: correo o WhatsApp | ✓ |
| RF-2.9 | Canales disponibles según los datos del invitado | ✓ |
| RF-2.10 | Correo por SMTP, solo con el evento publicado, invitados HOST y sin respuesta previa; PUBLIC_LINK se omite sin cuota | ✓ R10 |
| RF-2.11 | WhatsApp asistido: generar enlaces `wa.me` con el mensaje redactado | ✓ R11 |
| RF-2.12 | Telegram | ✗ aplazado |
| RF-2.13 | Confirmar el envío de WhatsApp, declarativo e idempotente | ✓ R11 |
| RF-2.14 | Envío en bloque por canal | ✓ R10, R11 |
| RF-2.15 | Plantilla editable con variables | ✗ fuera del MVP |
| RF-2.16 | Copiar el enlace individual; el mensaje de WhatsApp sirve de alternativa de copia | ✓ |
| RF-2.17 | Reenvío a quien no ha respondido, también a quien abrió el enlace | ✓ |
| RF-2.18 | Alta de varios invitados en una sola operación, todo o nada, con todos los errores por posición | ✓ |
| RF-2.19 | Alta inmediata por enlace público del evento: se crea al publicar si se pidió, es revocable, nombre obligatorio y contacto opcional; 25 plazas por defecto y dentro del máximo total | ✓ |

### Ubicación y acceso del evento

| ID | Requisito | Estado |
|---|---|---|
| RF-3.1 | Modalidad presencial o en línea, nunca ambas | ✓ R3 |
| RF-3.2 | Presencial: nombre, dirección, coordenadas y `place_id` | ✓ R3 (autocompletado: F) |
| RF-3.3 | Mapa con marcador arrastrable | F |
| RF-3.4 | Indicaciones adicionales en texto libre | ✓ R3 |
| RF-3.5 | Mapa embebido y botón "cómo llegar" en la página del invitado | F |
| RF-3.6 | En línea: plataforma, enlace, sala y contraseña opcionales | ✓ R3 |
| RF-3.7 | Visibilidad del enlace: al confirmar, N horas antes, o siempre | ✓ R3, R7, R8 |
| RF-3.8 | El filtrado del enlace se hace en el backend, nunca en el cliente | ✓ R7 |
| RF-3.9 | Notificar a los invitados un cambio de ubicación tras publicar | pendiente de decisión |

RF-3.9 es inalcanzable hoy: un evento publicado no admite cambios de ubicación. O sale del alcance, o se abre
una excepción para editar la ubicación de un evento publicado, con notificación como efecto secundario.

### Opciones de respuesta de los eventos con fecha definida

| ID | Requisito | Estado |
|---|---|---|
| RF-4.1 | El anfitrión define las opciones al crear el evento | ✓ R2 |
| RF-4.2 | Conjuntos predefinidos: viven en el frontend como listas que el cliente envía | F |
| RF-4.3 | Opciones propias: etiqueta y si cuenta como asistencia | ✓ R2 |
| RF-4.4 | Entre dos y cinco; al menos una cuenta como asistencia | ✓ R2 |
| RF-4.5 | Orden definido por el anfitrión | ✓ R2 |
| RF-4.6 | Interruptor de comentario del invitado | ✓ R2, R8 |
| RF-4.7 | Interruptor de cambio de respuesta y plazo | ✓ R8 |
| RF-4.8 | Edición de opciones en DRAFT y, con límites, en PUBLISHED | ✓ R8 |
| RF-4.9 | Panel con el conteo por opción y los invitados sin responder | ✓ R9 |

**Plazo (RF-4.7).** Se aplica a toda escritura, la primera respuesta y los cambios. Se acepta en el instante
exacto del plazo y se rechaza después. Solo se responde con el evento publicado y antes de su fin (en el fin
exacto ya no). El anfitrión puede cambiar o quitar el plazo en DRAFT, y en PUBLISHED antes del inicio. Nunca
puede ser posterior al inicio. Un plazo pasado es válido y cierra las respuestas.

**Opciones en PUBLISHED (RF-4.8).** Se pueden renombrar y añadir. No se puede eliminar una opción con
respuestas ni cambiar su `countsAsAttendance`, porque cambiaría retroactivamente quién está confirmado y quién
ve el enlace de la sala.

---

## 6. Flujos principales

**Crear y publicar.** Datos básicos → modalidad y ubicación → opciones de respuesta → invitados → publicar →
enviar invitaciones por el canal de cada invitado.

Si se activó la intención de enlace público, publicar genera `/j/{joinToken}`. En borrador no hay enlace.
La persona abre la vista previa, se une mientras el evento acepta una primera respuesta y recibe su propio
`/r/{token}` para responder sin cuenta. Una cookie evita la repetición accidental en el mismo navegador;
no es una barrera contra nuevas altas desde otro cliente. Cancelar o finalizar conserva la vista con
canJoin=false y bloquea el alta; desactivar o revocar el enlace da el mismo 404 que uno inventado.

Antes de crear, el anfitrión verifica su correo mediante el enlace enviado al registrarse. El token viaja
en el fragmento del enlace y se consume una sola vez al enviarlo a POST /api/auth/verify-email, con CSRF.
El registro sigue devolviendo 201 aunque falle el correo; se puede reenviar con sesión y límites propios.
Sin verificar se puede iniciar sesión, listar y consultar, pero crear devuelve 403 y EMAIL_NOT_VERIFIED.
Verificar permite crear en la misma sesión; /api/auth/me refleja el estado vigente de la base de datos.

**Recuperar el acceso (RF-1.3).** forgot-password recibe el correo y responde siempre 202, exista o no la
cuenta y aunque esté limitada. El flujo de búsqueda y envío es asíncrono. El correo fijo lleva un enlace
con el token en el fragmento, válido durante una hora y de un solo uso. reset-password valida primero la
contraseña; si es demasiado corta, devuelve 422 y conserva el token. Si es válida, consume el token antes
de calcular y guardar el hash. El éxito verifica también el correo, borra los demás tokens de autenticación
del usuario e invalida sus sesiones abiertas, sin iniciar sesión. Un fallo posterior al consumo obliga a
pedir otro enlace. Ambas operaciones son públicas y requieren CSRF.

**Responder una invitación con fecha definida.** Las consultas muestran sus candidatas y registran
la disponibilidad con su propia operación por invitación y revisión.

1. El invitado abre su enlace sin sesión ni contraseña. Un token desconocido y un evento en borrador dan el
   mismo 404. La primera apertura pasa al invitado a `OPENED`.
2. Ve solo su nombre y los datos públicos del evento: fechas locales, zona e instantes UTC. Nunca el anfitrión,
   contactos, otros invitados, tokens ni qué opciones cuentan como asistencia.
3. En un evento en línea recibe siempre plataforma y regla de visibilidad. URL, sala, contraseña e
   instrucciones llegan solo si el evento está publicado y se cumple la regla. Con "al confirmar", solo quien
   eligió una opción que cuenta como asistencia: responder "No asisto" no abre el acceso.
4. Un evento cancelado o finalizado devuelve 200 con su estado, para que los enlaces ya repartidos sigan
   siendo útiles, sin acceso en línea.
5. Responde con una opción del evento y, si está permitido, un comentario de hasta 500 caracteres y un correo
   opcional. Puede cambiar su respuesta si el anfitrión lo permite y el plazo está abierto.
6. La respuesta indica `canRespond` y `canChangeResponse`, calculados en el servidor con la misma política que
   el envío, para que el frontend no reimplemente las reglas.
7. Puede descargar el archivo de calendario `.ics`, que no incluye contactos ni token y respeta el mismo
   filtro de la sala que el JSON.

**Seguir las respuestas.** El panel del evento da los totales (invitados, respondidos, pendientes,
asistentes), los contadores por opción en su orden y una fila por invitado con contacto, canal, estado de la
invitación y su respuesta. El correo que el invitado dejó al responder no se expone nunca.

---

## 7. Reglas de seguridad del producto

- El token es la única credencial del invitado: aleatorio, largo, único y nunca derivado de otros datos. No
  aparece en logs, mensajes de error ni cuerpos ajenos a su propio enlace.
- Las respuestas públicas con credenciales llevan `Cache-Control: no-store` exacto.
- Un evento ajeno devuelve 404, nunca 403: un 403 confirmaría que ese identificador existe.
- Los secretos de la sala virtual no salen del servidor mientras no se cumpla la regla de visibilidad.

---

## 8. Después del MVP

Capacidades previstas y no construidas, sin numeración de requisitos porque no están comprometidas.

- **Muro social.** Espacio privado por evento con fotos, comentarios y reacciones; moderación y límites de
  almacenamiento. El acceso depende de estar invitado (el token), no de haber respondido ni confirmado. La
  confirmación solo gobierna la visibilidad del enlace de la sala.
- **Aplicación móvil.** Importación desde la agenda del teléfono, cámara directa al muro, notificaciones push,
  check-in por código QR y consulta sin conexión.
- **Más modalidades y datos del evento.** Modalidad híbrida (exige que la respuesta indique el canal de
  asistencia), acompañantes, campos adicionales por invitado, lista de espera y cupo.
- **Organización.** Co-organizadores, cuentas de empresa con varios usuarios, tipos de evento con plantillas,
  duplicar un evento, exportación de la lista de invitados.
- **Comunicación.** Recordatorios automáticos, notificaciones al invitado, plantilla de mensaje editable y
  Telegram si se comprueba que su enlace permite dirigir un contacto concreto.
- **Respuesta manual del anfitrión.** Registrar una respuesta recibida por otro medio. `GuestResponse` ganaría
  un campo `source`, con ausencia equivalente a "invitado" y sin migración.

El diseño completo de la visión original (siete módulos con sus requisitos) está en la versión 0.1 de este
documento, recuperable del historial de Git.

## 9. Fuera del producto

- Cobros, venta de entradas, gestión de proveedores y presupuesto del evento.
- Mensajería privada entre invitados.
- Lectura automática de contactos, seguidores o mensajes de WhatsApp, Instagram o X: esas plataformas no lo
  permiten a terceros. El producto usa la agenda del dispositivo y enlaces compartibles, que cubren la misma
  necesidad.
