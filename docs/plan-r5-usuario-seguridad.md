# R5 — Usuario y seguridad

Registro, login, logout y cadena de Spring Security. Salda D-1 y D-2.

RF-1.2 (verificación de correo) y RF-1.3 (recuperación de contraseña) quedan fuera de esta rebanada: dependen del proveedor de correo, cuya elección sigue pendiente. Se implementan en R12, después de R10.

---

## 1. Hallazgos del código antes de empezar

- `spring-boot-starter-security` no está en el pom. Añadirlo rompe todos los endpoints con 401 por defecto. El `SecurityConfig` debe escribirse en el mismo commit para no dejar los tests rotos.
- `CreateEventRequest` tiene `@NotBlank String hostId` — sale en R5 (D-2).
- `GET /api/events` recibe `@RequestParam String hostId` — sale en R5 (D-2).
- `GET /api/events/{id}` y el resto de endpoints de evento no verifican ownership en absoluto — se añade en R5 (D-1 consecuencia).
- `MongoIndexInitializer` solo crea el índice de `events.hostId`. Falta el índice único de `users.email`.
- El paquete `user/` no existe. Se construye desde cero.
- 37 tests de integración en `EventControllerIntegrationTest` usan `"hostId"` en el cuerpo o como `@RequestParam`. Todos se actualizan.

---

## 2. Dependencias nuevas — `pom.xml`

Solo en el commit 2 (ver §13):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- test -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

`spring-security-test` trae `SecurityMockMvcConfigurers` y soporte de sesiones en `MockMvc`, necesario para simular sesiones autenticadas en los tests de integración.

---

## 3. Hexágono `com.gtog.user`

```
user/
├── domain/
│   ├── model/
│   │   ├── User.java
│   │   └── Password.java            ← objeto de valor; valida longitud y encapsula el hash
│   └── port/
│       ├── in/
│       │   ├── RegisterUserUseCase.java
│       │   └── RegisterUserCommand.java
│       └── out/
│           ├── UserRepositoryPort.java
│           └── PasswordHasherPort.java
├── application/
│   └── RegisterUserService.java
└── infrastructure/
    ├── in/web/
    │   ├── AuthController.java
    │   ├── RegisterUserRequest.java
    │   ├── LoginRequest.java
    │   └── AuthUserResponse.java
    ├── out/persistence/
    │   ├── UserDocument.java
    │   ├── UserMongoRepository.java
    │   ├── UserRepositoryAdapter.java
    │   └── UserMapper.java
    └── out/security/
        ├── BCryptPasswordHasherAdapter.java
        └── UserDetailsServiceAdapter.java
```

`SecurityConfig` va en `shared/config/`, igual que `MongoIndexInitializer` y `OpenApiConfig`: es configuración transversal, no pertenece a un hexágono concreto.

---

## 4. Dominio `User` y objeto de valor `Password`

### `Password`

```java
// user/domain/model/Password.java  — sin anotaciones de framework
public final class Password {
    private final String hash;

    private Password(String hash) { this.hash = hash; }

    /** Para usuarios nuevos: valida longitud y hashea. */
    public static Password of(String raw, PasswordHasherPort hasher) {
        if (raw == null || raw.length() < 8)
            throw new PasswordTooShortException();
        return new Password(hasher.hash(raw));
    }

    /** Para rehidratación desde persistencia: envuelve un hash ya almacenado sin revalidar. */
    public static Password fromHash(String hash) {
        return new Password(hash);
    }

    public String hash() { return hash; }
}
```

`Password.of(raw, hasher)` valida la longitud antes de hashear. La contraseña en crudo nunca sale de este método ni llega al factory method de `User`. El puerto `PasswordHasherPort` es una interfaz del dominio, así que `Password` no importa nada de infraestructura.

`Password.fromHash(hash)` existe solo para que `UserMapper` pueda reconstruir un `User` desde un `UserDocument` sin necesidad de revalidar ni rehashear.

### `User`

```java
// user/domain/model/User.java  — sin anotaciones de framework
public class User {
    private final String id;
    private final String name;
    private final String email;        // normalizado a minúsculas
    private final Password password;   // objeto de valor, no String suelto
    private final String timeZone;

    /** Para usuarios nuevos: genera UUID, normaliza email, valida campos. */
    public static User register(String name, String rawEmail, Password password, String timeZone) {
        // reglas de dominio:
        //   name en blanco        → BlankUserNameException
        //   email formato inválido → InvalidEmailException
        //   timeZone en blanco    → BlankTimeZoneException
        // la longitud de contraseña ya fue validada en Password.of(...)
        return new User(UUID.randomUUID().toString(), name,
                        rawEmail.strip().toLowerCase(), password, timeZone);
    }

    /** Para rehidratación desde persistencia: recibe todos los campos tal como se guardaron. */
    public static User reconstitute(String id, String name, String email,
                                    Password password, String timeZone) {
        return new User(id, name, email, password, timeZone);
    }
}
```

`User` almacena `Password` como campo, no un `String passwordHash` suelto. Así `Password.fromHash(...)` tiene uso real en el mapper y no es código muerto.

El caso de uso de registro hace:

```java
Password password = Password.of(command.rawPassword(), passwordHasher); // valida + hashea
User user = User.register(command.name(), command.email(), password, command.timeZone());
userRepository.save(user);
```

El mapper de persistencia usa `Password.fromHash(document.getPasswordHash())` al rehidratar, y `user.getPassword().hash()` al persistir. Siguiendo el mismo patrón que `Event.reconstitueBuilder()`.

### Puertos de salida

```java
// UserRepositoryPort
Optional<User> findByEmail(String email);
User save(User user);

// PasswordHasherPort
String hash(String rawPassword);
boolean matches(String rawPassword, String hash);
```

---

## 5. Endpoints de autenticación

| Método | Ruta | Éxito | Notas |
|---|---|---|---|
| `POST` | `/api/auth/register` | 201, `Location: /api/users/{id}` | body: `AuthUserResponse` |
| `POST` | `/api/auth/login` | 200 | body: `AuthUserResponse`, cookie de sesión |
| `POST` | `/api/auth/logout` | 204 | gestionado por Spring Security, no hace falta método en el controlador |

`AuthUserResponse` devuelve `{ id, name, email }` — nunca el hash.

**Login:** `AuthController` llama a `AuthenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))`, luego persiste el contexto de seguridad en la sesión usando `HttpSessionSecurityContextRepository.saveContext(...)`. Spring Security crea la cookie de sesión automáticamente. Si la autenticación falla, `BadCredentialsException` → `GlobalExceptionHandler` → 401.

**Logout:** configurado en `SecurityConfig` como `logout.logoutUrl("/api/auth/logout")` con un `LogoutSuccessHandler` que responde 204. No hace falta método en `AuthController`.

---

## 6. `SecurityConfig` — reglas de acceso

Orden de prioridad (de más específico a más general):

| Ruta | Acceso |
|---|---|
| `/api/invitations/**` | público — escrito ya aunque no exista ningún endpoint ahí todavía, para que R7 no tenga que tocar esta configuración |
| `/api/auth/**` | público |
| `/actuator/health` | público |
| `/v3/api-docs/**`, `/swagger-ui/**` | público *(deuda D-SWAGGER-PUBLIC)* |
| `/actuator/**` | autenticado |
| todo lo demás | autenticado |

**Sesión:** `SessionCreationPolicy.IF_REQUIRED`. Cookie configurada en `application.properties`:

```properties
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
server.servlet.session.cookie.secure=false   # true solo en producción (deuda D-SECURE-COOKIE)
```

**CSRF:** `CookieCsrfTokenRepository.withHttpOnlyFalse()`. El servidor escribe la cookie `XSRF-TOKEN` (no HttpOnly para que Angular la pueda leer); Angular la envía de vuelta como cabecera `X-XSRF-TOKEN`. El manejador de token para SPA en Spring Security 6+ es `SpaCsrfTokenRequestHandler`; verificar la API exacta contra Spring Security 7 antes de escribirlo — puede haber movido clases respecto a la rama 6.

Los endpoints `/api/auth/login` y `/api/auth/register` quedan fuera de la protección CSRF (en la ignorelist explícita): en ese momento el cliente no tiene todavía ningún token de sesión para enviarlo. Spring Security no requiere CSRF en su propio endpoint de login; seguimos el mismo patrón.

**CORS:** bean `CorsConfigurationSource` que lee la propiedad `app.cors.allowed-origin`. Parametrizado para no hardcodear el origen del frontend.

**FormLogin y BasicAuth desactivados.** Solo sesión con cookie.

---

## 7. `UserDetailsServiceAdapter`

Vive en `user/infrastructure/out/security/`. Implementa `UserDetailsService` de Spring Security. Llama a `UserRepositoryPort.findByEmail(email)` y construye un `UserDetails` con el email como `username`, el hash como contraseña y el rol `ROLE_HOST`. Sin anotaciones de framework en el dominio.

---

## 8. Saldando D-1 y D-2

### Eliminar `hostId` del request (D-2)

`CreateEventRequest`: se elimina `@NotBlank String hostId`.

El controlador obtiene el hostId del principal autenticado:

```java
@PostMapping
public ResponseEntity<EventResponse> createEvent(
        @Valid @RequestBody CreateEventRequest request,
        @AuthenticationPrincipal UserDetails principal) {
    String hostId = resolveHostId(principal);
    ...
}
```

`GET /api/events`: elimina `@RequestParam String hostId`. El hostId sale del principal autenticado.

### Verificación de ownership en todos los endpoints de evento (D-1)

Patrón en la capa de aplicación, repetido en cada servicio que opera sobre un `{eventId}`:

```java
Event event = eventRepository.findById(eventId)
    .orElseThrow(() -> new EventNotFoundException(eventId));
if (!event.getHostId().equals(hostId)) {
    throw new EventNotFoundException(eventId); // 404, nunca 403
}
```

Un 403 confirmaría al atacante que ese id existe.

Los comandos de los siguientes casos de uso añaden un campo `hostId`:

- `GetEventByIdUseCase`
- `UpdateEventService`
- `PublishEventService`
- `CancelEventService`
- `EventLocationService` (venue y onlineAccess)
- `ReplaceResponseOptionsService`

`ListEventsByHostUseCase` no cambia de firma (ya recibe `hostId`); ahora ese valor viene del usuario autenticado en lugar del `@RequestParam`.

---

## 9. `MongoIndexInitializer` — índice nuevo y gestión de duplicados

```java
mongoTemplate.indexOps("users").createIndex(
    new Index().on("email", Sort.Direction.ASC).unique());
```

La unicidad del email se impone con el índice, no con una comprobación previa en la capa de aplicación. Cuando el email ya existe, MongoDB lanza `DuplicateKeyException`.

**La captura ocurre en el adaptador de persistencia, no en el `GlobalExceptionHandler`:**

```java
// UserRepositoryAdapter.save()
try {
    return userMapper.toDomain(userMongoRepository.save(userMapper.toDocument(user)));
} catch (DuplicateKeyException e) {
    if (e.getMessage() != null && e.getMessage().contains("email")) {
        throw new EmailAlreadyRegisteredException(user.getEmail());
    }
    throw e;
}
```

El `GlobalExceptionHandler` mapea `EmailAlreadyRegisteredException` (semántica clara) a 409. Los `DuplicateKeyException` de otros índices suben sin atrapar hasta que se añada su rama.

Ver deuda D-DUP-KEY en §11.

---

## 10. Tests

### Patrón para los 37 tests existentes de `EventControllerIntegrationTest`

No se usa registro + login real para los tests de evento: serían 74 llamadas HTTP extra contra Atlas en una suite ya lenta. En su lugar:

- **`@WithUserDetails`** para todos los tests que no prueban la autenticación en sí. Carga el `UserDocument` sembrado en `@BeforeEach` a través del `UserDetailsServiceAdapter` real, sin HTTP.
- **Sesión real** (POST `/api/auth/login` en el cuerpo del test) solo para los tests de login, logout y acceso no autenticado.

**Setup del test:**

`@WithUserDetails` resuelve el usuario **antes** de `@BeforeEach` por defecto, así que intentará cargarlo antes de que esté sembrado y fallará con "user not found". Se evita con `setupBefore = TestExecutionEvent.TEST_EXECUTION`, que pospone la resolución hasta justo antes de ejecutar el método de test, cuando `@BeforeEach` ya ha corrido.

El hash no se escribe a mano: una constante mágica se rompe si cambia el coste del encoder. Se calcula una vez en un inicializador estático con el `PasswordEncoder` real. `@WithUserDetails` no comprueba la contraseña, así que el hash solo hace falta para los tests de login que sí hacen POST `/api/auth/login`.

Se siembran **dos** usuarios: uno que crea los eventos y otro para `accessToAnotherHostsEventReturns404`.

```java
// ── Inicializador estático: se ejecuta una vez al cargar la clase ─────────────
private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
private static final String TEST_PASSWORD        = "password12";
private static final String TEST_HASHED_PASSWORD = PASSWORD_ENCODER.encode(TEST_PASSWORD);

// ── Constantes de los dos usuarios de test ───────────────────────────────────
static final String TEST_HOST_ID         = "00000000-0000-0000-0000-000000000001";
static final String TEST_HOST_EMAIL      = "host@gtog.com";
static final String TEST_OTHER_HOST_ID   = "00000000-0000-0000-0000-000000000002";
static final String TEST_OTHER_HOST_EMAIL = "other@gtog.com";

@BeforeEach
void setUp() {
    mongoTemplate.dropCollection("events");
    mongoTemplate.dropCollection("users");

    mongoTemplate.save(buildUserDoc(TEST_HOST_ID,       TEST_HOST_EMAIL,       "Test Host"),  "users");
    mongoTemplate.save(buildUserDoc(TEST_OTHER_HOST_ID, TEST_OTHER_HOST_EMAIL, "Other Host"), "users");
}

private UserDocument buildUserDoc(String id, String email, String name) {
    var doc = new UserDocument();
    doc.setId(id);
    doc.setEmail(email);
    doc.setName(name);
    doc.setPasswordHash(TEST_HASHED_PASSWORD);
    doc.setTimeZone("Europe/Madrid");
    return doc;
}
```

**Un test migrado (antes y después):**

```java
// ── ANTES (R4) ───────────────────────────────────────────────────────────────
@Test
void returnsEventById() throws Exception {
    String eventId = createEvent("host-1", "Cena de fin de año");
    mockMvc.perform(get("/api/events/" + eventId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Cena de fin de año"));
}

private String createEvent(String hostId, String title) throws Exception {
    var result = mockMvc.perform(post("/api/events")
            .contentType(APPLICATION_JSON)
            .content("""
                { "hostId": "%s", "title": "%s",
                  "startsAt": "2026-12-01T20:00:00", "endsAt": "2026-12-01T23:00:00",
                  "timeZone": "Europe/Madrid", "modality": "IN_PERSON" }
                """.formatted(hostId, title)))
        .andExpect(status().isCreated()).andReturn();
    return extractIdFromLocation(result);
}

// ── DESPUÉS (R5) ─────────────────────────────────────────────────────────────
@Test
// setupBefore garantiza que @BeforeEach siembra el usuario antes de que
// @WithUserDetails intente cargarlo desde el UserDetailsServiceAdapter
@WithUserDetails(value = TEST_HOST_EMAIL, setupBefore = TestExecutionEvent.TEST_EXECUTION)
void returnsOwnEventById() throws Exception {
    String eventId = createEvent("Cena de fin de año");
    mockMvc.perform(get("/api/events/" + eventId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Cena de fin de año"));
}

private String createEvent(String title) throws Exception {
    var result = mockMvc.perform(post("/api/events")
            .contentType(APPLICATION_JSON)
            .content("""
                { "title": "%s",
                  "startsAt": "2026-12-01T20:00:00", "endsAt": "2026-12-01T23:00:00",
                  "timeZone": "Europe/Madrid", "modality": "IN_PERSON" }
                """.formatted(title)))
        .andExpect(status().isCreated()).andReturn();
    return extractIdFromLocation(result);
}
```

Los tests que hoy esperan 400 por `hostId` ausente o por `hostId` como `@RequestParam` obligatorio se actualizan: ese campo ya no existe.

### Nuevos tests de integración de R5

| Test | Autenticación | Espera |
|---|---|---|
| `registersUserSuccessfully` | ninguna | 201, cabecera `Location` |
| `rejectsRegistrationWithDuplicateEmail` | ninguna | 409 |
| `rejectsRegistrationWithPasswordTooShort` | ninguna | 422 |
| `rejectsRegistrationWithInvalidEmail` | ninguna | 422 |
| `loginSuccessfully` | POST `/api/auth/login` | 200, cookie de sesión presente |
| `rejectsLoginWithWrongPassword` | POST `/api/auth/login` | 401 |
| `rejectsLoginWithUnknownEmail` | POST `/api/auth/login` | 401 |
| `logoutInvalidatesSession` | sesión real | 204; petición posterior → 401 |
| `unauthenticatedRequestToProtectedEndpointReturns401` | ninguna | 401 |
| `authenticatedAccessToOwnEventSucceeds` | `@WithUserDetails` | 200 |
| `accessToAnotherHostsEventReturns404` | `@WithUserDetails` (otro host) | 404 |
| `invitationsEndpointIsPublicWithoutSession` | ninguna | no 401 (404 esperado) |

---

## 11. Deuda nueva que genera esta rebanada

| # | Deuda | Consecuencia si se olvida | Cuándo se salda |
|---|---|---|---|
| D-SWAGGER-PUBLIC | `/v3/api-docs/**` y `/swagger-ui/**` son públicos en todos los entornos | La especificación OpenAPI es accesible sin autenticar en producción | Antes del primer despliegue en producción |
| D-SECURE-COOKIE | `server.servlet.session.cookie.secure=false` en dev y test | La cookie de sesión no tiene el atributo `Secure` en producción si no se parametriza por perfil | Antes del primer despliegue en producción |
| D-DUP-KEY | La detección de índice violado en `UserRepositoryAdapter` usa `contains("email")` sobre el mensaje de error de MongoDB | Estable en la práctica, pero no contractual. En cuanto haya más índices únicos en `users`, hay que añadir una rama por cada uno o cambiar a parseo del nombre del índice desde la excepción | Cuando se añada un segundo índice único en `users` |

---

## 12. Decisión de sesión vs JWT

**Decidido: sesión de servidor con cookie HttpOnly, SameSite=Lax, Secure.**

Motivo: el único cliente es Angular, sin app móvil ni terceros. Una cookie HttpOnly elimina el riesgo de exfiltración de token por XSS, y es más difícil de hacer mal que un JWT almacenado en el navegador.

**Decisión reversible:** cuando exista la app móvil o un cliente nativo sin soporte de cookies, se reevalúa el mecanismo de sesión.

---

## 13. Orden de implementación — dos commits

### Commit 1 — Hexágono `user/` (sin security)

Build verde al terminar. No toca Spring Security, ni el pom, ni los endpoints de evento.

1. `user/domain/model/Password.java` con `Password.of(raw, hasher)` y `Password.fromHash(hash)`; excepción `PasswordTooShortException`.
2. `user/domain/model/User.java` con `User.register(...)` y `User.reconstitute(...)`; excepciones `BlankUserNameException`, `InvalidEmailException`, `BlankTimeZoneException`. `reconstitute` es necesario en el mapper del paso 6 — sin él el adaptador no puede compilar.
3. `user/domain/port/in/` — `RegisterUserUseCase`, `RegisterUserCommand`.
4. `user/domain/port/out/` — `UserRepositoryPort`, `PasswordHasherPort`.
5. `user/application/RegisterUserService`.
6. `user/infrastructure/out/persistence/` — `UserDocument`, `UserMongoRepository`, `UserRepositoryAdapter` (con captura de `DuplicateKeyException`), `UserMapper`. El mapper usa `User.reconstitute(...)` y `Password.fromHash(hash())` para reconstruir el dominio desde MongoDB.
7. Ampliar `MongoIndexInitializer` con el índice único de `users.email`.
8. Tests unitarios de dominio: `PasswordTest` (longitud mínima, fromHash no revalida), `UserTest` (register, reconstitute, email normalizado).
9. `clean verify` en verde.

### Commit 2 — Cadena de seguridad, AuthController y migración de tests

Build verde al terminar.

1. Añadir `spring-boot-starter-security` y `spring-security-test` al pom.
2. `user/infrastructure/out/security/BCryptPasswordHasherAdapter` y `UserDetailsServiceAdapter`.
3. `shared/config/SecurityConfig` con todas las reglas de acceso, CSRF y CORS.
4. `user/infrastructure/in/web/AuthController` con registro y login; logout configurado en `SecurityConfig`.
5. Eliminar `@NotBlank String hostId` de `CreateEventRequest`; actualizar `EventController` para leer el hostId del principal autenticado.
6. Añadir verificación de ownership en todos los servicios de evento; actualizar los comandos correspondientes.
7. Actualizar los 37 tests existentes: sembrar usuario en `@BeforeEach`, anotar con `@WithUserDetails`, quitar `hostId` de los bodies.
8. Escribir los 12 tests nuevos de R5.
9. `clean verify` en verde.
