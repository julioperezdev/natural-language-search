# Evaluaciones de búsqueda conversacional

NLS mantiene un corpus versionado y un runner asíncrono que ejecuta el mismo servicio de conversación utilizado por la API. El runner compara cada turno con una referencia etiquetada, mide la resolución de filtros y contexto, verifica los productos y variantes contra PostgreSQL, y persiste el reporte en `evaluation_runs`.

## Corpus 1.1.1

El corpus contiene 60 conversaciones y 207 turnos:

| Suite | Casos | Uso |
| --- | ---: | --- |
| `catalog-conversation` | 2 | Regresión de compatibilidad; suite determinista de integración |
| `catalog-conversation-pilot` | 12 | Piloto local con llamadas reales a Jev |
| `catalog-conversation-development` | 45 | Iteración de desarrollo; incluye los 12 casos del piloto |
| `catalog-conversation-holdout` | 15 | Casos reservados para medir cambios después del ajuste |

Las conversaciones ejercitan filtros iniciales, refinamiento y reemplazo de filtros, referencias al historial, presupuestos, correcciones, ortografía informal, aclaraciones, reinicio, consultas amplias y una consulta en inglés. Las referencias de productos/variantes se calculan a partir del catálogo de evaluación.

La fuente del corpus y del catálogo está en `evaluation/generate_catalog_conversation_corpus.py`. El script genera juntos `src/main/resources/evaluation/catalog-conversation-v1.1.1.json` y `src/main/resources/evaluation/catalog-conversation-seed-v1.1.0.sql`; así no hay dos matrices de catálogo editables por separado. Para modificar casos o catálogo, cambie el generador, incremente la versión del corpus (y también la del catálogo si cambia la matriz) y regenere los artefactos:

```shell
python3 evaluation/generate_catalog_conversation_corpus.py
```

El reporte incluye SHA-256 del corpus y de la semilla, versiones de corpus/catálogo, revisión de aplicación, modelo, conteos de tokens y llamadas, latencia p50/p95, razones/campos de interpretación rechazada, precisión/recall de aclaraciones, coincidencia exacta de criterios, acciones de contexto y grounding de productos/variantes. También desglosa métricas por etiquetas como `typo`, `ambiguity`, `context_replace` y `multilingual`. No estima costo monetario porque los precios de Jev no son parte de la configuración.

## Aislamiento y seguridad

Los endpoints de evaluación existen solo bajo perfiles Spring `local` o `test`, y requieren `nls.evaluation.enabled=true` (desactivado por defecto). Antes de crear el run, la API valida que el host JDBC esté en `nls.evaluation.allowed-database-hosts`, cuyo valor predeterminado permite solo `localhost`, `127.0.0.1` y `::1`.

Ejecute la suite con una base local exclusiva y una instancia de API aparte. No use la RDS, el PostgreSQL de WCS, el puerto 5432 compartido ni ngrok. El runner crea conversaciones sintéticas y las borra al finalizar cada caso; la fila de evaluación y su reporte quedan persistidos.

Desde `nls/`, inicie un contenedor y volumen Docker aislados en el puerto 55433:

```shell
NLS_DB_PORT=55433 NLS_DB_NAME=nls_eval NLS_DB_USERNAME=nls_eval NLS_DB_PASSWORD=nls_eval \
  docker compose -p nls-eval up -d postgres
```

Configure `src/main/resources/application-local.properties` (archivo local ignorado por Git) y use `local` como perfil activo en IntelliJ:

```properties
server.port=8081
server.address=127.0.0.1
spring.datasource.url=jdbc:postgresql://localhost:55433/nls_eval
spring.datasource.username=nls_eval
spring.datasource.password=nls_eval
spring.jpa.properties.hibernate.default_schema=public
spring.liquibase.default-schema=public
spring.liquibase.liquibase-schema=public
nls.database.secret-id=
nls.database.schema=public
nls.evaluation.enabled=true
nls.evaluation.allowed-database-hosts=localhost,127.0.0.1,::1
nls.search.interpretation.typesafe.secret-id=wcs/prod/typesafe
nls.whatsapp.enabled=false
nls.build.revision=eval-local-<sha-y-hash-del-working-tree>
```

Use AWS credentials ya configuradas en la máquina para que el proveedor de TypeSafe lea el secreto. `nls.build.revision` es obligatorio en cada run: use el SHA de Git más un sufijo que identifique los cambios locales no committeados. No active esta configuración en una API expuesta por túnel o accesible fuera de la máquina.

Arranque la API. Liquibase creará el esquema/tablas de evaluación; después cargue el catálogo sintético en esa misma base:

```shell
PGPASSWORD=nls_eval psql -h localhost -p 55433 -U nls_eval -d nls_eval \
  -v ON_ERROR_STOP=1 -f src/main/resources/evaluation/catalog-conversation-seed-v1.1.0.sql
```

La semilla usa identificadores estables e `INSERT ... ON CONFLICT` para poder repetirse en la base aislada. No la ejecute contra una base compartida o productiva.

## Ejecutar y consultar un run

Lista las suites y versiones disponibles:

```shell
curl http://localhost:8081/api/evaluations/suites
```

Inicia el piloto de 12 casos; el endpoint devuelve `202 Accepted`, el identificador del run y un encabezado `Location`:

```shell
curl -i -X POST http://localhost:8081/api/evaluations/runs \
  -H 'Content-Type: application/json' \
  -d '{"suiteId":"catalog-conversation-pilot"}'
```

Consulta el estado y reporte con la URL de `Location` o el ID devuelto:

```shell
curl http://localhost:8081/api/evaluations/runs/<run-id>
```

También se pueden iniciar `catalog-conversation-development` y `catalog-conversation-holdout`. El holdout debe permanecer fuera de las iteraciones usadas para ajustar prompts/reglas; ejecútelo para medir un cambio candidato después de cerrar ese ajuste.

`COMPLETED` significa que se procesaron todos los casos, no que la calidad sea suficiente. Revise `passedCaseCount`, métricas, etiquetas y `mismatches` por turno. `FAILED` indica fallo de infraestructura y expone el tipo seguro de error. Los textos del corpus son sintéticos; no agregue teléfonos, identificadores ni conversaciones privadas de clientes.

## Verificación automatizada y piloto real

`./mvnw verify` valida el runner, persistencia, métricas y API con PostgreSQL Testcontainers y un motor de decisión determinista, sin invocar Jev. El piloto es una acción local separada que sí llama al proveedor y consume tokens/costo. El reporte guarda tokens y latencias reales para su análisis posterior.
