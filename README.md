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
