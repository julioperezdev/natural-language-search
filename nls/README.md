# Natural Language Search API

Módulo Spring Boot 4.1.1, Java 25, PostgreSQL, Spring Data JPA, Liquibase y Testcontainers.

## Arranque local

```bash
docker compose up -d postgres
SERVER_ADDRESS=127.0.0.1 NLS_SWAGGER_ENABLED=true NLS_DB_SECRET_ID= NLS_DB_SCHEMA=public ./mvnw spring-boot:run
```

En el arranque normal la aplicación usa el secret AWS `wcs/prod/database` y el schema `nls`, tomando las credenciales AWS del perfil local. El comando anterior desactiva explícitamente esa conexión para usar PostgreSQL local en `localhost:5432`, base `nls`, usuario `nls` y contraseña `nls`.

Para conectar el API local a la base AWS de NLS, usa el secreto `wcs/prod/database` y el schema `nls`. El SDK de AWS toma las credenciales de tu perfil local:

```bash
SERVER_ADDRESS=127.0.0.1 \
NLS_SWAGGER_ENABLED=true \
./mvnw spring-boot:run
```

Estas son también las opciones predeterminadas de la aplicación; en IntelliJ no hace falta configurar usuario ni contraseña de base de datos. Para Swagger, agrega `NLS_SWAGGER_ENABLED=true` en la configuración de ejecución. `NLS_DB_SECRET_ID` y `NLS_DB_SCHEMA` pueden sobreescribirse para otro entorno.

Para correr la suite (requiere Docker):

```bash
./mvnw test
```

Con `NLS_SWAGGER_ENABLED=true`, la documentación interactiva se sirve en [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html); el documento OpenAPI está en `http://localhost:8080/v3/api-docs`. En Swagger UI podés probar el schema, la interpretación, la búsqueda, la humanización y la orquestación. `/api/products/search/interpret`, las búsquedas con `message` y `/api/products/search/answer` llaman a TypeSafe. Swagger está habilitado por defecto y se puede desactivar con `NLS_SWAGGER_ENABLED=false`.

Las pruebas no llaman a TypeSafe. El adaptador Jev se prueba con respuestas HTTP sintéticas.

## API

### Capacidades de búsqueda

```http
GET /api/products/search/schema
```

Devuelve `context`, los campos semánticos, tipos, operadores, valores finitos conocidos y paginación. No expone entidades, tablas ni paths JPA.

### Búsqueda estructurada

```http
POST /api/products/search
Content-Type: application/json
```

```json
{
  "filters": [
    {"field": "category", "operator": "=", "value": "REMERAS"},
    {"field": "color", "operator": "=", "value": "BLACK"},
    {"field": "size", "operator": "=", "value": "M"},
    {"field": "price", "operator": "<=", "value": 50000}
  ],
  "order_by": "productName",
  "order": "ASC",
  "limit": 10,
  "offset": 0
}
```

Campos disponibles: `productName`, `category`, `color`, `size`, `price`, `stock`. La API limita `limit` a 50, `offset` a 10000, filtros por solicitud a 20, elementos por filtro de lista a 50 y texto a 200 caracteres. El orden predeterminado es estable por ID. Los atributos de variantes no se pueden ordenar mientras no tengan una semántica de producto única.

### Interpretar lenguaje natural

```http
POST /api/products/search/interpret
Content-Type: application/json
```

```json
{"message":"Quiero remeras negras talle M por menos de 50 mil"}
```

Devuelve un `Criteria` validado. `POST /api/products/search` también acepta ese mismo objeto con `message` y ejecuta la búsqueda. TypeSafe recibe opciones construidas desde el schema semántico; los montos y la disponibilidad se extraen determinísticamente. Toda salida se valida antes de crear una query.

### Humanizar un resultado existente

```http
POST /api/product-search-responses/humanize
Content-Type: application/json
```

Recibe el mensaje original y el objeto `results` devuelto por `/api/products/search`; no interpreta el mensaje ni consulta la base. Devuelve un texto y un resultado tipado como `RESULTS` o `NO_RESULTS`.

```json
{
  "message": "Busco una remera negra talle M",
  "results": {
    "items": [{
      "id": "00000000-0000-0000-0000-000000000000",
      "name": "Remera básica de algodón",
      "category": "REMERAS",
      "variants": [{
        "id": "00000000-0000-0000-0000-000000000001",
        "color": "NEGRO",
        "size": "M",
        "price": 24990,
        "stock": 12
      }]
    }],
    "total": 1,
    "limit": 10,
    "offset": 0
  }
}
```

La humanización usa plantillas en español rioplatense y solo presenta nombres, categorías, variantes, precios y stocks recibidos. No atribuye moneda porque el catálogo no guarda ese dato. Para probar este contexto sin consultar el catálogo, se puede enviar directamente un resultado con este contrato; la ruta no verifica que los datos provengan de la base.

### Búsqueda y respuesta para el cliente

```http
POST /api/products/search/answer
Content-Type: application/json
```

```json
{"message":"Busco una remera negra talle M por menos de 30 mil"}
```

Este caso de uso coordina interpretación, búsqueda y humanización. Devuelve `{ "outcome": "RESULTS", "reply": "...", "results": { ... } }`, para mostrar el texto y conservar acceso a los hechos estructurados. Si la interpretación no es segura, devuelve `NEEDS_CLARIFICATION` con una pregunta y `results: null`; si la consulta es válida pero no encuentra productos, devuelve `NO_RESULTS`.

Cada producto de `/api/products/search` incluye su categoría y `variants` con las variantes que cumplen los filtros aplicados (`id`, `color`, `size`, `price`, `stock`). Si la búsqueda no filtra por atributos de variante, aparecen todas las variantes del producto. Cuando hay filtros de color, talle, precio o stock, todos deben cumplirse sobre la misma variante. El precio es numérico y no incluye moneda porque el esquema no la define.

| Estado | Código | Significado |
| --- | --- | --- |
| 400 | `SEARCH_FIELD_NOT_ALLOWED` | Campo fuera del registro semántico |
| 400 | `SEARCH_OPERATOR_NOT_ALLOWED` | Operador desconocido o no admitido para el campo |
| 400 | `SEARCH_VALUE_INVALID` | Tipo, valor o límite no válido |
| 400 | `SEARCH_ORDER_NOT_ALLOWED` | Campo de orden no disponible |
| 422 | `SEARCH_INTERPRETATION_FAILED` | Jev no devolvió una interpretación segura |
| 503 | `SEARCH_INTERPRETATION_UNAVAILABLE` | TypeSafe o su clave no están disponibles temporalmente |

Los errores usan `{code, message, timestamp, requestId}` y no filtran respuestas crudas del proveedor ni excepciones SQL.

## Configuración

| Variable | Predeterminado | Uso |
| --- | --- | --- |
| `NLS_DB_URL` | `jdbc:postgresql://localhost:5432/nls` | JDBC PostgreSQL |
| `NLS_DB_USERNAME` | `nls` | Usuario DB |
| `NLS_DB_PASSWORD` | `nls` | Contraseña DB |
| `NLS_DB_SECRET_ID` | `wcs/prod/database` | Carga `jdbc_url`, `username` y `password` desde AWS Secrets Manager; vacío selecciona el datasource local |
| `NLS_DB_SCHEMA` | `nls` | Schema usado por Hibernate y Liquibase |
| `NLS_AWS_REGION` | `us-east-1` | Región AWS para leer secretos |
| `NLS_SWAGGER_ENABLED` | `true` | Habilita Swagger UI y OpenAPI |
| `NLS_TYPESAFE_ENDPOINT` | `https://api.typesafe.ai` | Endpoint System One |
| `NLS_TYPESAFE_MODEL` | `jev-1.13.0` | Modelo de interpretación |
| `NLS_TYPESAFE_SECRET_ID` | `wcs/prod/typesafe` | Secreto que contiene `API_KEY` como JSON en AWS Secrets Manager |
| `NLS_TYPESAFE_REQUEST_TIMEOUT` | `5s` | Timeout HTTP; máximo 30 s |
| `NLS_TYPESAFE_MINIMUM_CONFIDENCE` | `0.65` | Umbral para aceptar opciones |

El arranque normal carga URL, usuario y contraseña desde `wcs/prod/database` usando la cadena de credenciales predeterminada del SDK de AWS, sin valores AWS estáticos ni credenciales de base explícitas en IntelliJ. El rol necesita permisos de conexión, `USAGE` y `CREATE` en `nls`; Liquibase guarda su historial dentro de ese schema. Para desarrollo con PostgreSQL local, define `NLS_DB_SECRET_ID=` y `NLS_DB_SCHEMA=public`.

La clave de TypeSafe se solicita a Secrets Manager al procesar la primera búsqueda en lenguaje natural y se conserva solo en memoria durante cinco minutos. El SDK usa su cadena de credenciales predeterminada de AWS: el desarrollador puede usar el perfil ya configurado en su PC (por ejemplo, `AWS_PROFILE` y `aws sso login --profile <perfil>`). La aplicación no configura credenciales AWS estáticas. El rol/perfil necesita `secretsmanager:GetSecretValue` para cada secreto utilizado. Si la clave de TypeSafe no se puede leer, las búsquedas estructuradas y el schema siguen operativos.

El módulo no agrega autenticación de consumidores. Los endpoints nuevos también quedan sin autenticación.

## Catálogo

El módulo es de lectura para búsqueda. Las tablas `categories`, `products` y `product_variants` pertenecen al catálogo PostgreSQL y deben ser pobladas por el sistema que posee esos datos. La primera migración está en `src/main/resources/db/changelog/changes/001-create-product-catalog.yaml`.

## Decisiones de seguridad y semántica

- El consumidor envía nombres de campo semánticos, nunca rutas JPA.
- La IA no genera SQL, JPQL ni joins; solo elige opciones disponibles.
- Los filtros sobre variantes comparten el mismo join, así color/talle/precio aplican a una misma variante.
- Conteos y filas de producto usan distinct cuando participa una relación uno-a-muchos.
- Precio/stock de variantes no se ofrecen para ordenamiento a nivel producto.
