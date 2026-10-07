# Natural Language Search

Backend Spring Boot para consultar catálogos de productos con criterios estructurados o lenguaje natural. La búsqueda estructurada funciona sin proveedor de IA; la interpretación natural usa Jev mediante TypeSafe System One y valida su salida contra el schema semántico del backend antes de ejecutar JPA.

## Módulo

La aplicación está en [`nls/`](nls/). Su guía de ejecución, configuración y contrato HTTP está en [`nls/README.md`](nls/README.md).

## Desarrollo local

Requisitos: Java 25, Docker y Docker Compose.

```bash
cd nls
docker compose up -d postgres
SERVER_ADDRESS=127.0.0.1 NLS_SWAGGER_ENABLED=true NLS_DB_SECRET_ID= NLS_DB_SCHEMA=public ./mvnw spring-boot:run
```

Por defecto, la aplicación conecta al RDS `wcs/prod/database`, schema `nls`, usando el perfil AWS local. Para usar la instancia PostgreSQL local del comando anterior, se desactiva esa conexión con `NLS_DB_SECRET_ID=`.

Para usar la base AWS `wcs/prod/database`, schema `nls`, conectando con las credenciales AWS locales:

```bash
cd nls
SERVER_ADDRESS=127.0.0.1 \
NLS_SWAGGER_ENABLED=true \
./mvnw spring-boot:run
```

Las pruebas de integración usan PostgreSQL real con Testcontainers:

```bash
cd nls
./mvnw test
```

## Diseño

- `products/domain/search`: contrato de Criteria, schema y resultados.
- `products/application`: coordinación de búsqueda y frontera del motor de interpretación.
- `conversation`: resolución persistente de identidad del canal, historial reciente e idempotencia de mensajes.
- `conversation/infrastructure/whatsapp`: webhook Meta firmado y emisor de respuestas de WhatsApp Cloud API.
- `productsearchresponses`: humanización independiente de resultados estructurados.
- `productsearch`: coordinación de interpretación, búsqueda y respuesta para el cliente.
- `products/infrastructure/search`: registro semántico, resolución de paths y CriteriaBuilder.
- `products/infrastructure/repository/postgres`: persistencia JPA y migraciones Liquibase.
- `products/infrastructure/ai/typesafe`: cliente Jev; solo propone campos semánticos.

El backend mantiene un único motor de búsqueda con JPA Criteria. Los campos y operadores se validan desde un registro controlado por el servidor; joins de variantes se comparten por query y los resultados/conteos usan distinct cuando corresponda. `/api/products/search/answer` coordina búsqueda y respuesta; `/api/product-search-responses/humanize` permite probar la humanización de forma aislada; `/api/conversations/messages` agrega estado persistente de búsqueda por identidad del canal y conserva hasta 20 mensajes recientes.

El procedimiento completo para preparar un bot nuevo de WhatsApp, validar cada paso y diagnosticar fallos está en [`nls/WHATSAPP_SETUP.md`](nls/WHATSAPP_SETUP.md). Incluye el registro del número, la suscripción de la app a la WABA, la configuración del callback, Secrets Manager, IntelliJ, ngrok y una prueba de punta a punta.

## Documentación de referencia

- [Especificación consolidada](dynamic-search-engine-consolidated-specification.md)
- [Guía de arquitectura backend](backend-architecture-guidelines.md)
- [Referencia de Criteria JPA](criteria-jpa-spring-boot.md)
