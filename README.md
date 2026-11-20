# gtog

Backend de **gtog**, una aplicación de organización de eventos. El anfitrión crea un evento, invita por correo,
WhatsApp o Telegram, y cada invitado responde desde un enlace único, sin crear cuenta.

Este repositorio es solo el backend. La especificación funcional completa vive en
[`docs/definicion-funcional.md`](docs/definicion-funcional.md).

---

## Stack

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.x (Spring Framework 7) |
| Arquitectura | Hexagonal (puertos y adaptadores) |
| Base de datos | MongoDB Atlas |
| Documentación de API | springdoc-openapi / Swagger UI |
| Tests | JUnit Jupiter 6 (transitivo de Spring Boot 4) |

---

## Requisitos

- JDK 25.
- Una base de datos en **MongoDB Atlas** (no hace falta Mongo local ni Docker).
- Maven con su configuración por defecto.

---

## Puesta en marcha

1. Exporta la cadena de conexión de Atlas como variable de entorno. **Nunca la guardes en un archivo
   versionado.**

   ```bash
   export MONGODB_URI="mongodb+srv://usuario:contraseña@cluster.mongodb.net/?retryWrites=true&w=majority"
   ```

   La aplicación usa `gtog_dev` como base de datos en desarrollo y `gtog_test` en los tests de integración
   (misma URI, base distinta — ver `application.properties` / `application-test.properties`).

2. Arranca la aplicación:

   ```bash
   mvn spring-boot:run
   ```

3. Compila y pasa toda la suite:

   ```bash
   mvn clean verify
   ```

Desde R12, `POST /api/events` devuelve **403 con `code=EMAIL_NOT_VERIFIED`** si el correo del anfitrión no
está verificado. Los recorridos `.http` registran anfitriones `@example.com`, que no tienen buzón: sin el
bypass local, ese será el resultado del primer `POST /api/events` de cada recorrido.

Para ejecutarlos en desarrollo local, añade **`APP_AUTH_AUTOVERIFYEMAIL=true`** a la configuración de
ejecución de la aplicación en el IDE. Equivale a `app.auth.auto-verify-email=true`: verifica la cuenta al
registrarla y no envía correo de verificación. Para la aplicación real y para los tests, deja la variable
sin definir; no la añadas a la configuración ni a la plantilla de JUnit del IDE.
Un error al escribir su nombre **no produce ningún aviso: simplemente no hace nada**, y el primer evento
seguirá rechazado con 403 y `EMAIL_NOT_VERIFIED`.

La aplicación se niega a arrancar con el bypass activo si `app.public-base-url` no apunta a `localhost` o
`127.0.0.1`. Para probar la verificación real, arranca sin esa variable y usa `rest-api-verify.http` con
`{{realRecipient}}` del entorno privado del cliente HTTP. Ese recorrido comprueba tras el login que
`/api/auth/me` sigue mostrando `emailVerified=false` antes de verificar. No se ejecuta automáticamente.

Con la aplicación arrancada:

- Documentación OpenAPI (JSON): `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Salud de la aplicación: `http://localhost:8080/actuator/health` (incluye un indicador propio que hace
  `ping` contra la base de datos real de la app, no contra el indicador genérico de Mongo)

---

## Estructura del proyecto

Arquitectura hexagonal, un hexágono por contexto de negocio:

```
com.gtog
├── event/
│   ├── domain/            modelo y reglas de negocio, sin anotaciones de framework
│   │   ├── model/
│   │   └── port/
│   │       ├── in/        casos de uso
│   │       └── out/       puertos de salida (repositorio, ...)
│   ├── application/       implementación de los casos de uso
│   └── infrastructure/
│       ├── in/web/        controladores REST y DTOs
│       └── out/persistence/  documentos de Mongo y adaptadores
├── user/                  registro y autenticación por sesión (R5)
├── invitation/            envío de invitaciones (pendiente)
└── shared/                excepciones, configuración transversal (índices, salud, OpenAPI)
```

El dominio no depende de Spring ni de Mongo: los documentos de persistencia y los DTOs web son clases
distintas del modelo de dominio, traducidas por mappers.

---

## Documentación del proyecto

| Documento | Contenido |
|---|---|
| [`docs/definicion-funcional.md`](docs/definicion-funcional.md) | Contexto funcional, alcance y requisitos del MVP y visión posterior |
| [`docs/plan-iteracion-1.md`](docs/plan-iteracion-1.md) | Estado del plan: qué está cerrado, deuda técnica abierta, rebanadas pendientes |
| [`docs/modelo-evento.md`](docs/modelo-evento.md) | Modelo de datos del evento: campos, reglas de negocio, excepciones, endpoints |
| [`docs/modelo-invitado.md`](docs/modelo-invitado.md) | Invitados, respuestas, consulta pública y envío de invitaciones |
| [`docs/modelo-usuario.md`](docs/modelo-usuario.md) | Cuenta del anfitrión, autenticación y cuota de correo |
| [`AGENTS.md`](AGENTS.md) | Convenciones de arquitectura, código y flujo de trabajo del proyecto |
