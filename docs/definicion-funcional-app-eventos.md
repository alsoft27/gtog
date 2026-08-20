# Definición funcional — Aplicación de organización de eventos

**Versión:** 0.1 (borrador para discusión)
**Stack previsto:** Angular (web y móvil) + Java / Spring Boot (backend)
**Referencia de UX para coordinación de fechas:** Doodle

---

## 1. Propósito

Permitir que una persona o una empresa organice un evento de principio a fin en un solo lugar: acordar la fecha,
convocar a los invitados usando los datos de contacto que ya tiene, gestionar las confirmaciones, y mantener durante y
después del evento un espacio social privado donde los asistentes comparten fotos y comentarios.

La diferencia frente a herramientas existentes está en dos puntos:

- **Convocatoria sin fricción.** El anfitrión no obliga a nadie a crearse una cuenta; invita con los contactos que ya
  tiene y el invitado entra por un enlace.
- **El evento no termina cuando termina.** El muro privado convierte cada evento en un espacio social cerrado con vida
  propia antes, durante y después.

---

## 2. Actores

| Actor                           | Descripción                                                                                        | Necesita cuenta |
|---------------------------------|----------------------------------------------------------------------------------------------------|-----------------|
| **Anfitrión**                   | Crea el evento y tiene control total sobre él. Persona o empresa.                                  | Sí              |
| **Co-organizador**              | Invitado al que el anfitrión delega gestión (editar evento, invitar, moderar).                     | Sí              |
| **Invitado registrado**         | Asistente con cuenta en la aplicación. Participa en el muro con su identidad.                      | Sí              |
| **Invitado por enlace**         | Accede por un enlace único sin registrarse. Puede votar fecha, confirmar asistencia y ver el muro. | No              |
| **Administrador de plataforma** | Rol interno: soporte, atención de reportes de contenido.                                           | Sí              |

**Decisión de diseño:** el invitado por enlace es un actor de primera clase, no un caso degradado. Si publicar en el
muro exige registro, hay que decidirlo explícitamente (ver §10).

---

## 3. Módulos funcionales

### M1 — Cuentas y acceso

| ID     | Requisito                                                                                                                                      |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------|
| RF-1.1 | Registro con correo y contraseña, y con proveedor externo (Google, Apple en iOS).                                                              |
| RF-1.2 | Verificación de correo antes de poder crear eventos.                                                                                           |
| RF-1.3 | Recuperación de contraseña.                                                                                                                    |
| RF-1.4 | Perfil: nombre visible, foto, teléfono, zona horaria, idioma.                                                                                  |
| RF-1.5 | Tipo de cuenta *personal* o *empresa*. La cuenta empresa admite varios usuarios bajo una misma organización y logo propio en las invitaciones. |
| RF-1.6 | El acceso por enlace de invitación no crea cuenta; genera una sesión asociada al token del invitado.                                           |
| RF-1.7 | Un invitado por enlace puede convertir su participación en cuenta permanente conservando su historial.                                         |

### M2 — Gestión de eventos

| ID      | Requisito                                                                                                                                             |
|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| RF-2.1  | Crear evento con: título, descripción, tipo, fecha (o fechas candidatas), hora de inicio y fin, ubicación, imagen de portada.                         |
| RF-2.2  | Ubicación como texto libre, dirección con coordenadas, o enlace de videollamada para eventos virtuales.                                               |
| RF-2.3  | Tipos de evento con plantillas de configuración: social (boda, cumpleaños, reunión), corporativo (capacitación, junta, celebración), y personalizado. |
| RF-2.4  | Duplicar un evento existente como base para uno nuevo.                                                                                                |
| RF-2.5  | Editar el evento; los cambios de fecha, hora o lugar disparan notificación a todos los invitados.                                                     |
| RF-2.6  | Cancelar evento con motivo opcional; se notifica y el muro pasa a solo lectura.                                                                       |
| RF-2.7  | Archivar eventos pasados sin borrar su contenido.                                                                                                     |
| RF-2.8  | Asignar co-organizadores desde la lista de invitados.                                                                                                 |
| RF-2.9  | Configuración de privacidad por evento: quién ve la lista de invitados, quién puede publicar en el muro, si los invitados pueden invitar a otros.     |
| RF-2.10 | Cupo máximo de asistentes, con lista de espera opcional.                                                                                              |

**Ciclo de vida del evento:**

```
Borrador → Votación de fecha → Confirmado → En curso → Finalizado → Archivado
                                     ↓
                                Cancelado
```

- *Borrador*: solo visible para el anfitrión; no se han enviado invitaciones.
- *Votación de fecha*: fase opcional; se omite si el anfitrión fija la fecha desde el inicio.
- *En curso*: se activa automáticamente en la ventana horaria del evento; habilita el check-in y destaca el muro.
- *Finalizado*: el muro sigue activo durante un periodo configurable.

### M3 — Coordinación de fecha

| ID     | Requisito                                                                                                  |
|--------|------------------------------------------------------------------------------------------------------------|
| RF-3.1 | El anfitrión propone entre 2 y N fechas candidatas, con hora opcional.                                     |
| RF-3.2 | Cada invitado marca su disponibilidad por opción: *sí*, *no*, *si es necesario*.                           |
| RF-3.3 | Vista de matriz con el conteo por fecha y resaltado de la opción con mayor disponibilidad.                 |
| RF-3.4 | Fecha límite de votación; al vencer, la votación se cierra automáticamente.                                |
| RF-3.5 | Recordatorio automático a quienes no han votado.                                                           |
| RF-3.6 | El anfitrión confirma la fecha final (no es automático: puede elegir una opción que no sea la más votada). |
| RF-3.7 | Al confirmar, se notifica a todos y el evento pasa a estado *Confirmado*.                                  |
| RF-3.8 | Las horas se muestran en la zona horaria de cada usuario.                                                  |
| RF-3.9 | Exportar el evento confirmado a calendario (archivo .ics) y enlace de "añadir a Google Calendar".          |

### M4 — Invitados

**Alta de invitados**

| ID     | Requisito                                                                                                                                                    | Plataforma  |
|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| RF-4.1 | Importar contactos desde la agenda del dispositivo, con selección múltiple y buscador.                                                                       | Móvil       |
| RF-4.2 | Agregar invitado manualmente indicando nombre y teléfono o correo.                                                                                           | Web y móvil |
| RF-4.3 | Carga masiva desde archivo CSV o Excel con validación previa y reporte de errores.                                                                           | Web         |
| RF-4.4 | Reutilizar la lista de invitados de un evento anterior.                                                                                                      | Web y móvil |
| RF-4.5 | Grupos o etiquetas de invitados (familia, trabajo, proveedores) para invitar y filtrar por bloques.                                                          | Web y móvil |
| RF-4.6 | Detección de duplicados por teléfono o correo antes de guardar.                                                                                              | Web y móvil |
| RF-4.7 | Enlace público de invitación que el anfitrión difunde por el canal que quiera (WhatsApp, Instagram, redes), con opción de requerir aprobación del anfitrión. | Web y móvil |

**Envío de la invitación**

| ID      | Requisito                                                                                                                                                                                          |
|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RF-4.8  | Cada invitado recibe un enlace único e irrepetible que lo identifica sin necesidad de contraseña.                                                                                                  |
| RF-4.9  | Canales de envío: correo electrónico, SMS, y compartir el enlace a través de la hoja de compartir del sistema operativo (que cubre WhatsApp, Telegram, Instagram y demás sin integración directa). |
| RF-4.10 | Plantilla de invitación editable, con la imagen del evento y el logo de la organización en cuentas empresa.                                                                                        |
| RF-4.11 | Reenviar invitación individualmente o en bloque a los que no han respondido.                                                                                                                       |
| RF-4.12 | Estado de entrega visible por invitado: pendiente, enviada, abierta, respondida.                                                                                                                   |

**Confirmación de asistencia**

| ID      | Requisito                                                                                                       |
|---------|-----------------------------------------------------------------------------------------------------------------|
| RF-4.13 | Respuesta del invitado: asiste, no asiste, tal vez.                                                             |
| RF-4.14 | Acompañantes: el anfitrión define si se permiten y cuántos por invitado.                                        |
| RF-4.15 | Campos adicionales configurables por el anfitrión (restricciones alimentarias, talla, transporte, texto libre). |
| RF-4.16 | El invitado puede cambiar su respuesta hasta una fecha límite configurable.                                     |
| RF-4.17 | Recordatorios automáticos programables (por ejemplo, 7 días y 1 día antes).                                     |
| RF-4.18 | El anfitrión puede registrar manualmente la respuesta de un invitado que contestó por otro medio.               |

**Durante el evento**

| ID      | Requisito                                                                             |
|---------|---------------------------------------------------------------------------------------|
| RF-4.19 | Check-in de asistentes mediante código QR individual o marcado manual desde la lista. |
| RF-4.20 | Contador en vivo de asistentes registrados frente a confirmados.                      |

### M5 — Muro social del evento

| ID      | Requisito                                                                                                       |
|---------|-----------------------------------------------------------------------------------------------------------------|
| RF-5.1  | Muro privado por evento, accesible solo para invitados y organizadores.                                         |
| RF-5.2  | Publicar fotos (varias por publicación), video corto y texto.                                                   |
| RF-5.3  | Comentar publicaciones y responder comentarios (un nivel de anidación).                                         |
| RF-5.4  | Reaccionar a publicaciones y comentarios.                                                                       |
| RF-5.5  | Feed cronológico con carga progresiva; actualización en tiempo real de publicaciones y comentarios nuevos.      |
| RF-5.6  | Vista de álbum: todas las fotos del evento en cuadrícula, separadas del feed.                                   |
| RF-5.7  | Descargar una foto individual o el álbum completo comprimido (solo organizadores, o todos según configuración). |
| RF-5.8  | El autor puede editar o eliminar su propia publicación; el organizador puede eliminar cualquiera.               |
| RF-5.9  | Reportar contenido inapropiado; el reporte llega al organizador y, si escala, a la plataforma.                  |
| RF-5.10 | Moderación previa opcional: el organizador aprueba las publicaciones antes de que se vean.                      |
| RF-5.11 | Etiquetar personas en fotos entre los invitados del evento.                                                     |
| RF-5.12 | El muro se cierra a solo lectura pasado un periodo configurable tras el evento; el organizador puede reabrirlo. |
| RF-5.13 | Límites por evento en cantidad de archivos y peso total, según el plan de la cuenta.                            |

### M6 — Notificaciones

| ID     | Requisito                                                                                                                                                                                                                    |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RF-6.1 | Notificaciones push en móvil y correo en web.                                                                                                                                                                                |
| RF-6.2 | Eventos que notifican: invitación recibida, cambio de fecha o lugar, cancelación, recordatorio de votación, recordatorio de asistencia, confirmación de fecha, actividad en el muro (según preferencia), mención o etiqueta. |
| RF-6.3 | Centro de notificaciones dentro de la aplicación con historial.                                                                                                                                                              |
| RF-6.4 | Preferencias por usuario y silenciado por evento.                                                                                                                                                                            |
| RF-6.5 | Agrupación de notificaciones del muro para evitar saturación en eventos activos.                                                                                                                                             |

### M7 — Panel del anfitrión

| ID     | Requisito                                                                                     |
|--------|-----------------------------------------------------------------------------------------------|
| RF-7.1 | Resumen del evento: confirmados, rechazados, sin responder, acompañantes, total esperado.     |
| RF-7.2 | Lista de invitados filtrable y ordenable, con búsqueda.                                       |
| RF-7.3 | Exportar la lista de invitados y sus respuestas a CSV o Excel.                                |
| RF-7.4 | Resumen de las respuestas a los campos adicionales (por ejemplo, cuántos menús vegetarianos). |
| RF-7.5 | Actividad del muro: publicaciones, participantes más activos, fotos totales.                  |
| RF-7.6 | Vista general con todos los eventos del usuario u organización.                               |

---

## 4. Diferencias entre plataformas

| Funcionalidad                           | Web (escritorio) | Móvil                         |
|-----------------------------------------|------------------|-------------------------------|
| Crear y editar evento                   | Sí               | Sí                            |
| Importar desde agenda del dispositivo   | No               | Sí                            |
| Alta manual por teléfono o correo       | Sí               | Sí                            |
| Carga masiva CSV                        | Sí               | No                            |
| Compartir enlace por apps de mensajería | Copiar enlace    | Hoja de compartir nativa      |
| Subir fotos al muro                     | Sí               | Sí, además cámara directa     |
| Check-in por QR                         | Solo lector web  | Sí, cámara nativa             |
| Notificaciones push                     | No (correo)      | Sí                            |
| Panel y exportaciones                   | Completo         | Consulta y exportación básica |

El móvil es la plataforma del invitado y del día del evento. La web es la plataforma del organizador en la fase de
planeación.

---

## 5. Reglas de negocio

- **RN-1.** Un evento siempre tiene exactamente un anfitrión propietario; los co-organizadores no pueden eliminar el
  evento ni cambiar al propietario.
- **RN-2.** El enlace de invitación es único por invitado y por evento; si se reenvía, el token no cambia salvo que el
  anfitrión lo revoque.
- **RN-3.** Un contacto se identifica dentro de un evento por su teléfono o su correo; no puede haber dos invitados con
  el mismo identificador en el mismo evento.
- **RN-4.** Solo se puede confirmar una fecha si el evento está en estado *Votación de fecha* o *Borrador*.
- **RN-5.** Cancelar un evento es irreversible en cuanto a notificaciones: no existe "descancelar" silencioso.
- **RN-6.** El contenido del muro pertenece a quien lo publicó; al eliminar su cuenta, el usuario decide si su contenido
  se borra o se anonimiza.
- **RN-7.** Los datos de contacto importados de la agenda solo se usan para invitar a ese evento; no alimentan un
  directorio global ni se comparten entre cuentas.
- **RN-8.** Si un invitado responde *no asiste*, conserva acceso de lectura al muro salvo que el organizador lo retire.

---

## 6. Requisitos no funcionales

| Área                 | Requisito                                                                                                                                                                                         |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Privacidad           | Cumplimiento de la normativa de protección de datos aplicable. Consentimiento explícito antes de acceder a la agenda del dispositivo. Los contactos importados que no se invitan no se almacenan. |
| Seguridad            | Tokens de invitación no adivinables y revocables. Cifrado en tránsito. Contenido multimedia servido con URLs firmadas y temporales.                                                               |
| Rendimiento          | El muro debe cargar las primeras publicaciones en menos de 2 segundos con conexión móvil típica. Compresión de imágenes en el cliente antes de subir.                                             |
| Disponibilidad       | El día del evento es crítico: el check-in y el muro deben tolerar picos de uso concentrados.                                                                                                      |
| Accesibilidad        | Contraste y navegación por teclado en web; tamaños de toque adecuados en móvil.                                                                                                                   |
| Internacionalización | Español como idioma base, arquitectura preparada para más idiomas. Manejo correcto de zonas horarias.                                                                                             |
| Offline              | En móvil, el invitado puede ver el evento y el álbum ya cargado sin conexión; las publicaciones se encolan y se envían al recuperar red.                                                          |

---

## 7. Alcance del MVP

**Dentro del MVP**

- M1 completo salvo cuentas de empresa multiusuario (RF-1.5 parcial).
- M2: creación, edición, cancelación, estados. Sin plantillas por tipo ni lista de espera.
- M3 completo. Es el gancho de entrada y el punto de comparación con Doodle.
- M4: alta manual, importación desde agenda, enlace único, envío por correo y compartir nativo, confirmación con
  acompañantes. Sin CSV, sin QR, sin campos personalizados.
- M5: publicar fotos y texto, comentar, reaccionar, álbum, eliminación por el autor y el organizador. Sin etiquetado ni
  moderación previa.
- M6: invitación, cambio de fecha, recordatorios, resumen diario del muro.
- M7: contadores básicos y lista de invitados.

**Fuera del MVP**

Carga masiva CSV, check-in por QR, campos personalizados, lista de espera, moderación previa, etiquetado en fotos, lista
de regalos, gestión de presupuesto, venta de entradas, proveedores, mapa interactivo, cuentas empresa multiusuario,
aplicación de escritorio empaquetada.

---

## 8. Explícitamente fuera del producto

- Cobros o venta de entradas.
- Gestión de proveedores y presupuesto del evento.
- Mensajería privada uno a uno entre invitados.
- Cualquier lectura automática de contactos, seguidores o mensajes de WhatsApp, Instagram o X. Estas plataformas no lo
  permiten a terceros; el producto usa la agenda del dispositivo y el enlace compartible, que cubren la misma necesidad
  sin depender de ellas.

---

## 9. Modelo conceptual de datos

Entidades principales y sus relaciones, como base para el diseño del backend:

```
Usuario ──< Organización
   │
   └──< Evento >──── Ubicación
          │
          ├──< OpciónFecha >──< VotoDisponibilidad >── Invitado
          │
          ├──< Invitado >──── Contacto (teléfono / correo)
          │        │
          │        └──── RespuestaAsistencia (+ acompañantes)
          │
          ├──< Publicación >──< Archivo
          │        │
          │        ├──< Comentario
          │        └──< Reacción
          │
          └──< Notificación
```

---

## 10. Decisiones pendientes

1. **¿El invitado por enlace puede publicar en el muro, o solo leer?** Permitirlo maximiza la participación; exigir
   cuenta protege contra abuso y da identidad a las fotos. Una opción intermedia: publicar sí, pero con nombre
   obligatorio y límite de subidas.
2. **¿Cuánto tiempo vive el muro y las fotos después del evento?** Define el costo de almacenamiento y probablemente el
   modelo de negocio.
3. **¿Hay plan gratuito y plan de pago?** Los límites naturales serían número de invitados, almacenamiento de fotos y
   duración del muro.
4. **¿"Móvil" significa Angular con Ionic o Capacitor, o una app nativa aparte?** Afecta directamente el acceso a la
   agenda, la cámara y las notificaciones push.
5. **¿El envío de SMS entra en el MVP?** Tiene costo por mensaje; el correo y el enlace compartible pueden bastar al
   inicio.
6. **¿Qué pasa si un invitado no responde nunca?** Definir si cuenta como ausente para efectos del cupo.
