# Modelo de datos: Usuario

Estado tras R5, R10 y R12: registro, login, logout, protección e invalidación de sesiones, cuota del anfitrión,
verificación de correo, puerta de creación y recuperación de contraseña.

---

## `User` (agregado raíz)

Vive en `com.gtog.user.domain.model.User`. Clase plana sin anotaciones de framework.

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | `String` | UUID generado por el dominio en `User.register(...)`. No lo asigna Mongo. |
| `name` | `String` | Nombre del anfitrión. Obligatorio. |
| `email` | `String` | Dirección de correo. Normalizada a minúsculas y sin espacios al registrar. Única en la colección `users` (índice único). |
| `password` | `Password` | Objeto de valor. Encapsula el hash; nunca expone el texto en claro. `Password.of(raw, hasher)` valida la longitud mínima (8 caracteres) y delega el hasheo; `Password.fromHash(hash)` rehidrata sin revalidar. |
| `timeZone` | `String` | Zona horaria IANA del anfitrión, usada como zona por defecto al crear eventos. Obligatoria. |
| `emailSendsInWindow` | `int` | Reservas de correo en la ventana de 24 h; ausente equivale a 0 |
| `windowStartedAt` | `Instant`, nullable | Inicio UTC de la ventana; ausente equivale a null |
| `emailVerified` | `boolean` | Ausente equivale a false; solo se exige al crear eventos |
| `authEmailSends` | `int` | Emisiones de autenticación en su ventana propia; ausente equivale a 0 |
| `authEmailWindowStartedAt` | `Instant`, nullable | Inicio de la ventana de autenticación; no es windowStartedAt |
| `lastAuthEmailAt` | `Instant`, nullable | Última reserva de autenticación; controla el enfriamiento |

Dos factory methods:
- `User.register(name, email, password, timeZone)` — valida todas las reglas, normaliza el email, genera el id.
- `User.reconstitute(id, name, email, password, timeZone)` — rehidrata sin revalidar.

Las sobrecargas conservan verificación y ambos grupos de contadores. El registro síncrono valida, hashea e
inserta la cuenta antes del 201; la política local puede marcarla verificada al crearla. El flujo completo
de correo se encola después, sin alterar la respuesta por errores de envío o saturación.

### Reglas de negocio y sus excepciones

| Regla | Excepción | HTTP |
|---|---|---|
| El nombre es obligatorio (no nulo ni en blanco) | `BlankUserNameException` | 422 |
| El email debe tener formato válido (`usuario@dominio.tld`) | `InvalidEmailException` | 422 |
| El email debe ser único en el sistema | `EmailAlreadyRegisteredException` | 409 |
| La contraseña en claro debe tener ≥ 8 caracteres | `PasswordTooShortException` | 422 |
| La zona horaria es obligatoria (no nula ni en blanco) | `BlankTimeZoneException` | 422 |
| La zona horaria debe ser válida según `ZoneId.of(...)` | `InvalidUserTimeZoneException` | 422 |

Todas las excepciones de dominio heredan de `UserDomainException`. El `@RestControllerAdvice`
(`GlobalExceptionHandler`) mapea las reglas a 422 y `EmailAlreadyRegisteredException` a 409. Los handlers
específicos de autenticación producen 400 genérico para token inválido/caducado/usado y 401 para una cuenta
que ya no existe, con instance constante `/api/auth` y no-store exacto.

---

## `Password` (objeto de valor)

Vive en `com.gtog.user.domain.model.Password`. No expone el texto en claro: el constructor privado solo acepta
el hash ya calculado.

```java
Password.of(String raw, PasswordHasherPort hasher)  // valida longitud, delega hasheo
Password.fromHash(String hash)                        // rehidratación sin validación
password.hash()                                       // devuelve el hash almacenado
```

`PasswordHasherPort` (`domain/port/out`) abstrae el algoritmo de hasheo. La implementación de producción,
`BCryptPasswordHasherAdapter` (en `user/infrastructure/out/security`), usa BCrypt con el coste por defecto del
`PasswordEncoder` de Spring Security, que también es el bean que usa Spring Security para verificar credenciales
en el login — ambos comparten la misma instancia (`@Bean PasswordEncoder` en `SecurityConfig`).

---

## Persistencia (`users`)

`UserDocument` (en `user/infrastructure/out/persistence`) — campos: `id`, `name`, `email`, `passwordHash`
(string), `timeZone`, `emailSendsInWindow` (`Integer`, ausente leído como 0) y `windowStartedAt`
(`Instant`, nullable, ausente leído como null), más `emailVerified` (`Boolean`, ausente leído como false),
`authEmailSends` (`Integer`, ausente leído como 0), `authEmailWindowStartedAt` y `lastAuthEmailAt`
(`Instant`, nullable). Los dos grupos de cuotas son independientes. `UserMapper` conserva todos los campos;
`Password.fromHash(...)` rehidrata el objeto de valor al leer de Mongo. El alta inserta; verificar solo hace
$set de emailVerified, sin save del documento existente y sin tocar cuotas.

Índice creado en el arranque (`MongoIndexInitializer`):

- `email` — ascendente, único.

La violación de este índice (registro con email duplicado) se captura en `UserRepositoryAdapter` como
`DuplicateKeyException`; se inspecciona el mensaje para confirmar que es el índice de email y se lanza
`EmailAlreadyRegisteredException` → 409. Deuda anotada: D-DUP-KEY.

---

## Autenticación por sesión

Spring Security mantiene la sesión en `HttpSession` (servidor). El cliente recibe una cookie `JSESSIONID`
(HttpOnly, SameSite=Lax). El Angular SPA recibe también la cookie CSRF via `CookieCsrfTokenRepository`
(no HttpOnly) y debe incluir el token en el header `X-XSRF-TOKEN` en las peticiones de escritura del
anfitrión. Solo las rutas públicas `/api/invitations/**` están excluidas de CSRF.

Comportamiento medido con servidor real:

- La cookie `XSRF-TOKEN` se emite en el primer GET fuera de `/api/invitations/**`, por ejemplo
  `/actuator/health`; no se emite como parte del login correcto.
- El login usa la cookie existente y no la renueva.
- La comparación usa el valor crudo de la cookie, enviado también en la cabecera `X-XSRF-TOKEN`.
- Un rechazo CSRF devuelve 403 con `ProblemDetail`, también para una petición anónima.
- El logout expira tanto `JSESSIONID` como `XSRF-TOKEN`; el siguiente GET vuelve a emitir una cookie CSRF nueva.

Desde el commit 1 de R12, el login manual ejecuta la estrategia compartida con la cadena de seguridad antes
de guardar un contexto de autenticación nuevo: `ChangeSessionIdAuthenticationStrategy` cambia el id de una
sesión previa y `RegisterSessionAuthenticationStrategy` registra el nuevo id en `SessionRegistry`.
`UserSessionConfig` define esa composición. La estrategia adicional de CSRF es `NullAuthenticatedSessionStrategy`,
no `CsrfAuthenticationStrategy`: autenticar no emite ni renueva `XSRF-TOKEN` ni cambia su comparación.
Antes, el controlador solo guardaba el contexto en la sesión: una medición con servidor real devolvió
login 200 y el mismo id de sesión, y el test de rotación falló. Tras la corrección cambia el id y el anterior
no permite acceder a los eventos.

`AuthenticatedUser.equals/hashCode` usa únicamente `userId`; los cambios de perfil o hash no dividen la
identidad del principal que emplea `SessionRegistry` como clave. `HttpSessionEventPublisher` mantiene el
registro ante cambio de id, logout y destrucción de sesiones.

El puerto de salida `SessionInvalidationPort.invalidateAll(userId)` pertenece al dominio de user.
`SessionInvalidationAdapter`, en `user/infrastructure/out/security`, marca expiradas todas las sesiones del
usuario en el registro, sin afectar a otros usuarios. `ConcurrentSessionFilter` invalida cada `HttpSession`
en su siguiente acceso; `ProblemDetailSessionExpiredStrategy` escribe directamente 401 ProblemDetail,
instance constante `/api/errors` y Cache-Control exactamente `no-store`, sin redirección ni sendError.
Los tests con servidor real comprueban rotación, cookie CSRF conservada, sesiones independientes del mismo
usuario, invalidación conjunta, aislamiento entre usuarios y limpieza del registro y cookies al hacer logout.
Límite: solo es válido con una instancia y sesiones en memoria; no hay invalidación distribuida.

La decisión de usar sesión en lugar de JWT es reversible cuando llegue la app móvil.
Ver docs/plan-iteracion-1.md, sección Decisiones tomadas, para la justificación.

`UserDetailsServiceAdapter` (en `user/infrastructure/out/security`) implementa `UserDetailsService` y devuelve
`AuthenticatedUser`, que extiende `UserDetails` con los campos `userId`, `name` y `timeZone` — así los
controladores pueden leer el id del anfitrión sin una segunda consulta a la base de datos.

---

## Puertos y casos de uso

| Caso de uso (`port/in`) | Servicio (`application`) | Qué hace |
|---|---|---|
| `RegisterUserUseCase` | `RegisterUserService` | Hashea la contraseña, valida y persiste el usuario. |
| `VerifyEmailUseCase` | `VerifyEmailService` | Consume el token y verifica con escritura parcial. |
| `ResendVerificationUseCase` | `VerificationEmailService` | Encola búsqueda, cuota y envío de verificación. |
| `GetCurrentUserUseCase` | `GetCurrentUserService` | Lee el perfil vigente de la base de datos. |
| `ForgotPasswordUseCase` | `ForgotPasswordService` | Valida formato; encola búsqueda, cuota, emisión y envío. |
| `ResetPasswordUseCase` | `ResetPasswordService` | Valida contraseña, consume, hashea, actualiza y revoca tokens y sesiones. |

Login y logout se gestionan directamente en `AuthController` vía `AuthenticationManager` y la configuración
de logout de `SecurityConfig`, sin caso de uso de dominio separado: no hay lógica de negocio en ellos más allá
de la autenticación de Spring Security.

---

## API HTTP

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| `POST` | `/api/auth/register` | `RegisterUserRequest` (`name`, `email`, `rawPassword`, `timeZone`) | `201` + `Location: /api/users/{id}` + `AuthUserResponse` (`id`, `name`, `email`, `timeZone`, `emailVerified`), `409` (email duplicado), `422` (regla de dominio) |
| `POST` | `/api/auth/login` | `LoginRequest` (`email`, `password`) | `200` + `AuthUserResponse`, `401` (credenciales incorrectas) |
| `POST` | `/api/auth/logout` | — | `204` |
| `POST` | `/api/auth/verify-email` | `{token}` | Público con CSRF: 200 sin cuerpo; token inválido/caducado/usado → mismo 400 |
| `POST` | `/api/auth/resend-verification` | — | Sesión y CSRF: 204 uniforme, también verificado, limitado o envío fallido |
| `GET` | `/api/auth/me` | — | Sesión: AuthUserResponse vigente, incluido emailVerified leído de la base; no-store exacto |
| `POST` | `/api/auth/forgot-password` | `{email}` | Público con CSRF: 202 sin cuerpo uniforme; formato inválido → 422 |
| `POST` | `/api/auth/reset-password` | `{token, newPassword}` | Público con CSRF: 200 sin cuerpo; token inválido/caducado/usado → mismo 400; contraseña corta → 422 |

Todos los endpoints de `/api/events/**` requieren sesión activa → 401 si no la hay.
`/api/invitations/**` es público (resuelto por token de invitado).

Login también devuelve emailVerified. Los nuevos endpoints y /me usan Cache-Control: no-store exacto.
Un GET anónimo de /me devuelve 401 y emite XSRF-TOKEN: enviarlo como cookie y X-XSRF-TOKEN permite alcanzar
verify-email y reset-password (400 para un token inventado), y forgot-password (202), sin sesión ni csrf()
de MockMvc. Se abren esos POST uno a uno; /api/auth/** no es público en bloque y la exclusión de CSRF sigue
limitada a invitaciones.

## Verificación de correo (R12, commit 2)

AuthTokenPurpose distingue EMAIL_VERIFICATION y PASSWORD_RESET; un propósito no sirve para el otro.
La colección auth_tokens guarda id, userId, purpose, hash SHA-256 y expiresAt, nunca el token en claro.
SecureAuthTokenGenerator usa 32 bytes de SecureRandom y base64url sin relleno. El token vive solo en memoria,
en el correo y en el cuerpo de la petición; los DTOs sensibles ocultan su contenido en toString.
MongoIndexInitializer crea índices únicos de hash y (userId, purpose), y TTL sobre expiresAt. Emitir
sustituye el anterior con un upsert atómico; consumir usa un solo find-and-delete por hash, propósito y
expiresAt > ahora. La caducidad se comprueba sin depender del borrado TTL y todos los instantes vienen del Clock.

El enlace es <app.public-base-url><app.auth.verify-email-path>#token=<token>, con ruta /verify-email y TTL
app.auth.verification-ttl=PT24H. El asunto y cuerpo son texto fijo español, sin nombre, correo ni otros datos
escritos por el usuario, y contienen «Si no lo has pedido, ignóralo.». /me y la puerta de creación leen de
nuevo emailVerified; la sesión no decide el permiso. HostEmailVerificationPort pertenece al dominio de event,
y HostEmailVerificationAdapter a user/infrastructure/out/persistence. El rechazo es 403 ProblemDetail con
instance /api/events y code=EMAIL_NOT_VERIFIED. No impide consultar ni iniciar sesión.

VerificationEmailService encola búsqueda, reserva, emisión y envío, no solo SMTP. Su ejecutor acotado tiene
app.auth.mail-executor.core-size=2, max-size=4 y queue-capacity=100, sin CallerRunsPolicy. La saturación se
registra a WARN con clase y correlación, sin dirección ni mensaje original y sin reintento; la respuesta
permanece igual. Los tests usan SynchronousAuthMailExecutor en src/test con perfil test y correo capturado,
nunca SMTP real.

AuthEmailSenderPort pertenece a user. AuthEmailSenderAdapter, solo en infraestructura, reutiliza
com.gtog.event.domain.port.out.EmailSenderPort y convierte AuthEmail a InvitationEmail para aprovechar el
SMTP existente. Dominio y aplicación de user no importan event; no se mueve el transporte ni se usan sus cuotas.

La reserva de autenticación es un findAndModify con pipeline, enfriamiento inclusivo de 5 minutos
(app.auth.mail-cooldown=PT5M) y máximo 5 por ventana de 24 horas (app.auth.mail-max-per-day=5), contando el correo
inicial. Los límites se omiten en silencio. No hay lectura seguida de escritura para reservar y no se usa
$$NOW: el instante procede del Clock. Una caída del proveedor gasta intentos del límite de autenticación del
usuario: no se compensa ni se reintenta. Se registra ERROR AUTH_MAIL_SEND_FAILED con clase y correlación.

### Arranque local y recorridos manuales

Desde R12, POST /api/events devuelve 403 con code=EMAIL_NOT_VERIFIED si el correo del anfitrión no está
verificado. Los recorridos .http registran anfitriones @example.com, que no tienen buzón: el primer POST
/api/events de cada recorrido dará 403. Para desarrollo local, añade APP_AUTH_AUTOVERIFYEMAIL=true a la
configuración de ejecución de la aplicación (equivale a app.auth.auto-verify-email=true): verifica la cuenta
al registrarla y no envía correo de verificación. Para la aplicación real y para los tests, déjala sin definir.
No la añadas a la configuración de ejecución de JUnit ni a la plantilla de JUnit del IDE.
La aplicación se niega a arrancar si está activa y app.public-base-url no apunta a localhost o 127.0.0.1.
Para probar la verificación real, arranca sin esa variable y usa rest-api-verify.http, con una dirección real
del entorno privado y el token del fragmento del correo. No ejecutes ese recorrido automáticamente.

El WARN nombra la propiedad, la variable y la prohibición de producción; el rechazo de arranque nombra
la propiedad y el host sin volcar credenciales. Los tests fijan el bypass explícitamente.
rest-api-verify.http usa realRecipient y verificationToken del entorno privado: tras login comprueba
/me=false, verifica y crea en la misma sesión.

## Recuperación de contraseña (R12, commit 3)

ForgotPasswordService comparte el ejecutor acotado y los contadores authEmailSends/authEmailWindowStartedAt/
lastAuthEmailAt con la verificación; nunca los de invitaciones. Solo valida y normaliza el formato del correo
antes de encolar. Búsqueda de cuenta, reserva con Clock, emisión y SMTP ocurren dentro de la tarea. Cuenta
existente, inexistente, en enfriamiento o con el máximo diario devuelven el mismo 202, cuerpo y cabeceras.
Las cuentas verificadas también pueden reservar para recuperar; la reserva de verificación sigue exigiendo
emailVerified=false. Una caída SMTP gasta el intento y la saturación descarta la tarea, sin reintento ni
alterar la respuesta; logs solo clase y correlación. Para cuentas inexistentes no hay documento ni contador:
pueden llenar la cola; el límite por IP D-REGISTER-RATE-LIMIT es un bloqueador de despliegue.

PasswordResetEmailComposer usa texto fijo en español sin datos escritos por el usuario, con la frase
«Si no lo has pedido, ignóralo.». AuthEmailLink comparte la construcción del fragmento con la verificación.
app.auth.reset-ttl=PT1H y app.auth.reset-password-path=/reset-password generan
<app.public-base-url>/reset-password#token=<token>. No se envía correo real desde ningún test.

ResetPasswordService aplica este orden: Password.validateRaw (misma regla que Password.of), consumo atómico
del hash PASSWORD_RESET con expiresAt > Clock, PasswordHasherPort (mismo BCrypt del registro) y $set parcial
de passwordHash/emailVerified=true. Después borra todos los auth_tokens del usuario e invalida sus sesiones
con SessionInvalidationPort. No inicia sesión ni modifica las cuotas. Si falla el hash o la escritura tras
consumir, responde 500 saneado y el token queda gastado: hay que pedir otro. No hay transacción multidocumento.
Los errores de token son el mismo 400, con instance constante; éxito y errores llevan no-store exacto.

Los tests cubren primera carga con cookie real, respuestas uniformes, trabajo aplazado hasta ejecutar la
tarea, límites con Clock fijo, consumo concurrente, errores tras consumo, separación de propósitos,
revocación de todos los tokens y de dos sesiones propias conservando la de otro usuario. La mutación temporal
que adelantó la búsqueda fuera del ejecutor hizo fallar su test; quedó restaurada sin versionarla.

---

## Cuota de correo (R10, commit 1)

HostSendQuotaPort se define en event/domain/port/out; HostSendQuotaAdapter vive en la infraestructura
de user y escribe users con MongoTemplate. La dependencia apunta desde infraestructura hacia el dominio
consumidor, nunca desde event hacia user. No se crean colecciones ni índices adicionales.

La reserva usa un único findAndModify con pipeline condicionado. Una ventana ausente o con 24 h cumplidas
inclusive empieza con contador 1 y windowStartedAt=ahora; una vigente solo incrementa por debajo del máximo.
No hay lectura previa para decidir la escritura. Se trata de 24 h desde la primera reserva, no del día civil.
La compensación comprueba la misma ventana y contador positivo, lo decrementa y elimina el inicio si queda
en cero. No libera cuota de una ventana posterior ni produce valores negativos. Se ejecuta una sola vez,
sin reintentos automáticos, antes de compensar al invitado; su fallo deja al invitado reservado.

read consulta el documento tras el lote para obtener dailyLimit: limit, remaining y resetsAt. Una ventana
vigente devuelve windowStartedAt+24h; una ausente o vencida devuelve null y todo el límite disponible, sin
escribir. Clock se obtiene en aplicación y los puertos reciben el instante. Los mappers conservan ambos
campos al rehidratar; no se exponen en AuthUserResponse. Los campos ausentes se leen como 0/null.

users no tiene @Version. Sus actualizaciones deben ser parciales: $set/$inc sobre los campos afectados,
nunca leer y save() del usuario existente, porque pisaría las cuotas concurrentes. La inserción inicial
de registro se mantiene. R12 actualiza emailVerified y la contraseña de esta forma.

El endpoint de R10 devuelve dailyLimit desde esta lectura después de todo el lote, no desde una estimación
en memoria. Un fallo SMTP (incluido EMAIL_NOT_CONFIGURED) compensa primero la cuota del anfitrión y luego
la reserva del invitado. La aceptación SMTP seguida de fallo al registrar SENT conserva ambas; no reenvía.
Las cuotas no sustituyen el límite de registro/login, aún bloqueador del despliegue público. El email
opcional de GuestResponse no modifica User ni se usa para enviar nada;
cualquier uso futuro requiere aprobación expresa y doble confirmación de su dueño.

## Fuera de alcance por ahora

- Roles de usuario (hoy solo existe el rol de anfitrión).
