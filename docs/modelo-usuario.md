# Modelo de datos: Usuario

Estado del modelo tras R5: registro, login, logout y autenticación por sesión. Sin verificación de correo ni
recuperación de contraseña (R12).

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

Dos factory methods:
- `User.register(name, email, password, timeZone)` — valida todas las reglas, normaliza el email, genera el id.
- `User.reconstitute(id, name, email, password, timeZone)` — rehidrata sin revalidar.

### Reglas de negocio y sus excepciones

| Regla | Excepción | HTTP |
|---|---|---|
| El nombre es obligatorio (no nulo ni en blanco) | `BlankUserNameException` | 422 |
| El email debe tener formato válido (`usuario@dominio.tld`) | `InvalidEmailException` | 422 |
| El email debe ser único en el sistema | `EmailAlreadyRegisteredException` | 409 |
| La contraseña en claro debe tener ≥ 8 caracteres | `PasswordTooShortException` | 422 |
| La zona horaria es obligatoria (no nula ni en blanco) | `BlankTimeZoneException` | 422 |

Todas las excepciones de dominio heredan de `UserDomainException`. El `@RestControllerAdvice`
(`GlobalExceptionHandler`) mapea `UserDomainException` → 422 y `EmailAlreadyRegisteredException` → 409
(más específica, gana).

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
(string), `timeZone`. `UserMapper` traduce en ambos sentidos; `Password.fromHash(...)` rehidrata el objeto de
valor al leer de Mongo.

Índice creado en el arranque (`MongoIndexInitializer`):

- `email` — ascendente, único.

La violación de este índice (registro con email duplicado) se captura en `UserRepositoryAdapter` como
`DuplicateKeyException`; se inspecciona el mensaje para confirmar que es el índice de email y se lanza
`EmailAlreadyRegisteredException` → 409. Deuda anotada: D-DUP-KEY.

---

## Autenticación por sesión

Spring Security mantiene la sesión en `HttpSession` (servidor). El cliente recibe una cookie `JSESSIONID`
(HttpOnly, SameSite=Lax). El Angular SPA recibe también la cookie CSRF via `CookieCsrfTokenRepository`
(no HttpOnly) y debe incluir el token en el header `X-XSRF-TOKEN` en todas las peticiones de escritura.

La decisión de usar sesión en lugar de JWT es reversible cuando llegue la app móvil. Ver
`docs/plan-r5-usuario-seguridad.md` para la justificación completa.

`UserDetailsServiceAdapter` (en `user/infrastructure/out/security`) implementa `UserDetailsService` y devuelve
`AuthenticatedUser`, que extiende `UserDetails` con los campos `userId`, `name` y `timeZone` — así los
controladores pueden leer el id del anfitrión sin una segunda consulta a la base de datos.

---

## Puertos y casos de uso

| Caso de uso (`port/in`) | Servicio (`application`) | Qué hace |
|---|---|---|
| `RegisterUserUseCase` | `RegisterUserService` | Hashea la contraseña, valida y persiste el usuario. |

Login y logout se gestionan directamente en `AuthController` vía `AuthenticationManager` y la configuración
de logout de `SecurityConfig`, sin caso de uso de dominio separado: no hay lógica de negocio en ellos más allá
de la autenticación de Spring Security.

---

## API HTTP

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| `POST` | `/api/auth/register` | `RegisterUserRequest` (`name`, `email`, `rawPassword`, `timeZone`) | `201` + `AuthUserResponse` (`id`, `name`, `email`, `timeZone`), `409` (email duplicado), `422` (regla de dominio) |
| `POST` | `/api/auth/login` | `LoginRequest` (`email`, `password`) | `200` + `AuthUserResponse`, `401` (credenciales incorrectas) |
| `POST` | `/api/auth/logout` | — | `204` |

Todos los endpoints de `/api/events/**` requieren sesión activa → 401 si no la hay.
`/api/invitations/**` es público (resuelto por token de invitado).

---

## Fuera de alcance por ahora

- Verificación de correo electrónico (`RF-1.2`): R12.
- Recuperación de contraseña (`RF-1.3`): R12.
- Roles de usuario (hoy solo existe el rol de anfitrión).
