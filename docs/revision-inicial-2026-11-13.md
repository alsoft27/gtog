# Revisión inicial del repositorio

Revisión del 13 de noviembre de 2026 sobre `1925062`, con los cambios documentales de esta sesión. No se ha modificado código, configuración ni dependencias. Las divergencias de este informe quedan pendientes de decisión; no se han corregido tomando el código o la documentación como autoridad por cuenta propia.

## Conversión de instrucciones

`AGENTS.md` procede literalmente de `CLAUDE.md`, sin ejecutar `/init` ni regenerar instrucciones desde el código. Se conserva toda su prosa salvo el bloque de comandos sustituido por indicación expresa del usuario. También se añade una sección de trabajo con aprobación del plan antes de escribir código, lectura de los modelos y respeto a las decisiones documentadas.

Se retiran únicamente las instrucciones de usar un repositorio Maven aislado, los flags `-s` con la ruta absoluta al `settings.xml` y la prohibición de consultar o escribir en `~/.m2/eci-repo`. Los comandos quedan así:

```bash
mvn clean verify
mvn test
mvn spring-boot:run
```

No había rutas de skills ni herramientas específicas de Claude en el archivo que hubiera que eliminar. Las referencias documentales a `CLAUDE.md` pasan a `AGENTS.md`; la mención de Claude Code como lector pasa a Codex. Se actualizan también las instrucciones Maven del README y las menciones de aislamiento en el plan y el documento de estado, conforme a la instrucción del usuario.

El archivo resultante ocupa 19.016 bytes, por debajo de 32 KiB. No ha hecho falta trasladar material del original a `docs/`. Se conservan las siete reglas de negocio, la sección completa «Lo que no debes hacer», las reglas de persistencia y concurrencia, y los ejemplos originales, incluidas las divergencias que se detallan más abajo.

## Verificación ejecutada

`mvn clean verify`, con Maven por defecto y `MONGODB_URI` del entorno, termina con `BUILD SUCCESS` en 27,674 segundos. Se ha necesitado ejecutar fuera del sandbox porque Maven no podía crear sus bloqueos en `~/.m2`. Los tests de integración usan MongoDB Atlas, base `gtog_test`, y limpian las colecciones `events` y `users` en sus respectivos `@BeforeEach`.

| Grupo | Tests |
|---|---:|
| Dominio de eventos | 84 |
| Dominio de usuarios | 21 |
| Aplicación de eventos | 11 |
| Indicador de salud Mongo | 2 |
| Integración de eventos | 35 |
| Integración de autenticación | 18 |
| Arranque del contexto | 1 |
| **Total** | **172** |

Resultado: **0 fallos, 0 errores, 0 omitidos**, en 14 clases. Son 118 unitarios y 54 de integración contando el arranque del contexto. La evidencia es la ejecución de Maven y los informes de `target/surefire-reports/`, contrastados con los métodos `@Test` del código.

Java ejecutado: Temurin 25.0.5. Maven: 3.10.0. El POM declara Spring Boot 4.1.0 y springdoc 3.1.0. El classpath de Surefire contiene JUnit Jupiter API y Engine **6.0.3**.

Esta verificación cubre los flujos de integración con MockMvc y Atlas; no es una prueba del cliente Angular ni de las cookies en un navegador real.

## Estado funcional real

Hay seis entregas implementadas: creación inicial de eventos más R1–R5. Si se cuentan únicamente las rebanadas numeradas, son cinco, R1–R5.

| Entrega | Implementación comprobada |
|---|---|
| Creación inicial | Evento en `DRAFT`, validaciones, opciones por defecto y persistencia |
| R1 | Consulta del detalle y listado del anfitrión autenticado, sin paginación ni contadores |
| R2 | Entre dos y cinco opciones, orden por posición, asistencia, etiquetas y conservación de identidad al editar |
| R3 | Ubicación presencial o acceso en línea, validaciones y regla de visibilidad probada en dominio |
| R4 | Edición en borrador, publicación y cancelación desde publicado |
| R5 | Registro, login, logout, seguridad por sesión, CSRF, listado propio y comprobación de propiedad en aplicación con 404 para eventos ajenos |

El historial contiene los commits de R3, R4 y R5, además de las entregas previas de consulta y opciones. La suite completa está en verde. Se confirma la separación de dominio, puertos, aplicación, documentos Mongo, mappers, adaptadores y DTOs web. No se encuentran imports externos a Java y al propio proyecto en el dominio, anotaciones de framework allí, inyección por campos, índices declarados con `@Indexed` ni transacciones multidocumento. Los índices actuales son `events.hostId` y `users.email` único.

R6–R12 no están implementadas. No existen `guests[]`, tokens, colección `responses`, respuesta pública, calendario, panel con contadores, envío por correo o enlaces de WhatsApp/Telegram, verificación de correo ni recuperación de contraseña. La regla de seguridad permite `/api/invitations/**`, pero todavía no existe un controlador para esas rutas.

Persisten las deudas declaradas: D-3, D-5, D-6, D-7, D-FINISHED, D-DELETE-DRAFT, D-SWAGGER-PUBLIC, D-SECURE-COOKIE y D-DUP-KEY. `FINISHED` figura en el enum, pero no hay una transición que lo alcance. D-1 y D-2 están saldadas, y el indicador Mongo propio de D-4 está implementado.

## Endpoints implementados

| Método | Ruta | Resultado satisfactorio |
|---|---|---|
| POST | `/api/auth/register` | 201, usuario registrado |
| POST | `/api/auth/login` | 200, sesión autenticada |
| POST | `/api/auth/logout` | 204, logout gestionado por Spring Security |
| POST | `/api/events` | 201 con `Location` |
| GET | `/api/events` | 200, listado del anfitrión autenticado |
| GET | `/api/events/{id}` | 200, detalle propio |
| PUT | `/api/events/{id}` | 200, edición de datos básicos y configuración |
| PUT | `/api/events/{id}/response-options` | 200, reemplazo de opciones |
| PUT | `/api/events/{id}/venue` | 200, reemplazo de ubicación |
| PUT | `/api/events/{id}/online-access` | 200, reemplazo del acceso en línea |
| POST | `/api/events/{id}/publish` | 200, publicación |
| POST | `/api/events/{id}/cancel` | 200, cancelación |

Son doce operaciones de negocio. Además están configurados `/actuator/health`, `/v3/api-docs`, `/swagger-ui.html` y los recursos de Swagger UI. Las rutas de eventos requieren autenticación y las escrituras están protegidas por CSRF; el 401 de falta de autenticación no describe por sí solo todos los posibles rechazos de los filtros.

## Divergencias conservadas para decidir

| Documento o regla | Código o evidencia | Situación |
|---|---|---|
| `AGENTS.md`: JUnit 5 | Surefire resuelve JUnit Jupiter 6.0.3 | No se cambia la instrucción ni la dependencia |
| `AGENTS.md`: ejemplo de `CreateEventRequest` con `hostId`, y afirmación de que conserva `@NotBlank` | `CreateEventRequest` no contiene `hostId`; el controlador lo toma del principal desde R5 | Ejemplo y explicación anteriores a R5 |
| `AGENTS.md`: ejemplo de fecha de evento como `Instant` | Dominio, DTO y documento usan `LocalDateTime` más `timeZone`; los instantes se calculan al consultar | El código coincide con la decisión de fechas locales del documento de estado; el ejemplo permanece sin actualizar |
| `AGENTS.md`: `guest/` como hexágono en el esquema | No existe todavía; el documento de estado propone alojar invitados dentro de `event` | Hay que aclarar el esquema antes de R6 sin cambiar la decisión por inferencia |
| `AGENTS.md`: 201 al crear con `Location` | `AuthController.register()` devuelve 201 sin `Location` | Convención sin cumplir en registro |
| `AGENTS.md`: errores con `ProblemDetail` y un único advice | El advice cubre las excepciones de dominio y credenciales; los filtros de seguridad gestionan 401/403 por separado, sin configurar allí `ProblemDetail` | La uniformidad del formato de error no está garantizada para todas las respuestas |
| `docs/modelo-evento.md`: modalidad incompatible en `PUT /venue` o `PUT /online-access` produce 422 y reutiliza las excepciones de creación | `Event` lanza `ModalityConflictException`, el advice responde 409 y dos tests de integración lo exigen | El commit `fdba6d8` introdujo expresamente ese comportamiento; no se revierte |
| `docs/modelo-usuario.md`: zona IANA del anfitrión usada por defecto al crear eventos | `User.register()` solo rechaza zona vacía; el request de evento exige su propia zona y el controlador no aplica el valor del principal | Ni validación IANA del usuario ni valor por defecto implementados |
| Criterio de cierre del plan: documentación OpenAPI con anotaciones web | `AuthController`, sus DTOs y el logout de Security no tienen las anotaciones descriptivas que sí usa `EventController` | R5 no cumple literalmente ese criterio; la generación automática no sustituye la descripción de errores y del logout |

Las reglas pendientes de invitados, tokens, teléfonos E.164 y respuestas no son incumplimientos nuevos: dependen de R6–R8. El filtrado de `OnlineAccess` existe solo en dominio, como ya advierten las instrucciones. El bloqueo de todas las opciones en `PUBLISHED` coincide con D-7, y los cambios de ubicación tras publicar siguen sin permitir RF-3.9, ya marcado como decisión pendiente en el alcance.

## Comparación con el plan y el documento de estado

- `docs/plan-iteracion-1.md` acierta en el cierre funcional de R1–R5, el total actual de **172 tests**, las rebanadas pendientes y la deuda principal.
- Su fila de R1 y el texto de R9 siguen mostrando `GET /api/events?hostId=`. El endpoint actual obtiene el anfitrión de la sesión, como correctamente dice la fila de R5. Las anotaciones OpenAPI del listado también conservan la explicación antigua del parámetro obligatorio y su error 400.
- La cifra de **37 tests de integración** en la fila de R4 corresponde a un estado anterior. Actualmente `EventControllerIntegrationTest` tiene **35** y `AuthControllerIntegrationTest` tiene **18**, más un test de contexto. El plan R5 preveía retirar los tests de `hostId` obligatorio.
- El apartado 4 plantea como pendiente el retorno de Spring Security, aunque R5 ya lo incorporó. Es texto histórico pendiente de actualizar.
- `docs/gtog-estado-y-decisiones.md` dice **166 tests**; la ejecución actual confirma **172**. También dice «cinco rebanadas» pero enumera creación inicial más R1–R5: seis entregas, cinco numeradas.
- `README.md` aún presenta `user` como futuro; ya existe desde R5. También conserva la referencia a JUnit 5.
- El alcance dice que el artefacto es `gtog-api`, mientras el POM y las instrucciones dicen `gtog`. Su flujo de creación menciona «o ambas» para la modalidad, contradiciendo la exclusión explícita del híbrido en ese mismo alcance y el modelo del código.
- La documentación de cancelación describe `Instant.now()` en el servicio; actualmente el caso de uso recibe `now` y el controlador lo aporta. El comportamiento funcional coincide.
- El plan detallado de R5 proponía excluir login y registro de CSRF. `SecurityConfig` protege ambos y sus tests proporcionan CSRF explícitamente. No se cambia esta elección; queda pendiente comprobar el flujo completo de obtención del token para Angular.

Las menciones de Maven aislado sí se actualizan en esta sesión porque el usuario ha resuelto expresamente ese cambio. No se corrigen las demás divergencias.

## Siguiente paso

R6: invitados. Antes de escribir código, presentar un plan y esperar aprobación. Siguen por decidir el formato final del token, el encaje definitivo de invitados en `event` y cómo representar la futura cascada de respuestas mientras `responses` no existe. La propuesta de 32 bytes aleatorios en Base64 URL sin relleno sigue siendo propuesta, no decisión implementada.

El primer recorrido completo del producto requiere R6 + R7 + R8. Continúan abiertas la elección del proveedor de correo y la posibilidad de que el anfitrión registre respuestas recibidas por otro medio.
