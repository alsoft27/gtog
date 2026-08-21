# gtog

Backend de **gtog**, una aplicación de organización de eventos. El anfitrión crea un evento, invita por correo,
WhatsApp o Telegram, y cada invitado responde desde un enlace único, sin crear cuenta.

Este repositorio es solo el backend. La especificación funcional completa vive en
[`docs/mvp-alcance-iteracion-1.md`](docs/mvp-alcance-iteracion-1.md).

---

## Stack

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.x (Spring Framework 7) |
| Arquitectura | Hexagonal (puertos y adaptadores) |
| Base de datos | MongoDB Atlas |
| Documentación de API | springdoc-openapi / Swagger UI |
| Tests | JUnit 5 |

---

## Requisitos

- JDK 25.
- Una base de datos en **MongoDB Atlas** (no hace falta Mongo local ni Docker).
- El `settings.xml` del repositorio Maven aislado de este proyecto (no es el `~/.m2` global).

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
   mvn -s <ruta-al-settings.xml-aislado> spring-boot:run
   ```

3. Compila y pasa toda la suite:

   ```bash
   mvn -s <ruta-al-settings.xml-aislado> clean verify
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
├── guest/ · invitation/ · user/   (siguientes iteraciones)
└── shared/                excepciones, configuración transversal (índices, salud, OpenAPI)
```

El dominio no depende de Spring ni de Mongo: los documentos de persistencia y los DTOs web son clases
distintas del modelo de dominio, traducidas por mappers.

---

## Documentación del proyecto

| Documento | Contenido |
|---|---|
| [`docs/mvp-alcance-iteracion-1.md`](docs/mvp-alcance-iteracion-1.md) | Especificación funcional de la iteración 1 |
| [`docs/definicion-funcional-app-eventos.md`](docs/definicion-funcional-app-eventos.md) | Definición funcional completa de la aplicación |
| [`docs/plan-iteracion-1.md`](docs/plan-iteracion-1.md) | Estado del plan: qué está cerrado, deuda técnica abierta, rebanadas pendientes |
| [`docs/modelo-evento.md`](docs/modelo-evento.md) | Modelo de datos del evento: campos, reglas de negocio, excepciones, endpoints |
| [`CLAUDE.md`](CLAUDE.md) | Convenciones de arquitectura, código y flujo de trabajo del proyecto |
