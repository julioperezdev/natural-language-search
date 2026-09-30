# Especificación del sistema — Natural Language Search

**Estado:** especificación funcional y técnica para la primera versión de producción

**Versión del contrato HTTP:** v1

**Idioma:** español

## 1. Resumen

Natural Language Search es un servicio backend reutilizable que convierte una consulta escrita en lenguaje natural en una solicitud de búsqueda estructurada y validada contra un esquema semántico aportado por el equipo consumidor.

~~~text
Consulta en lenguaje natural + SearchSchema
                   ↓
        Natural Language Search API
                   ↓
            SearchRequest validado
                   ↓
       Backend determinístico consumidor
                   ↓
       Su motor, datos y resultados propios
~~~

El servicio interpreta la intención. No consulta bases de datos de otros equipos, no genera SQL, JPQL ni Criteria, y no devuelve resultados de catálogo. El equipo consumidor conserva sus datos, autoriza la operación y ejecuta SearchRequest con su motor existente.

El resultado del proyecto es un servicio HTTP desplegable y operable, acompañado por contratos versionados, documentación OpenAPI, guía de integración, pruebas, evaluación de calidad, configuración segura y procedimientos de operación.

## 2. Propósito y alcance

### Incluido en v1

- API HTTP de interpretación bajo la ruta versionada /v1.
- Contrato semántico de búsqueda independiente de tablas, entidades y proveedores.
- Interpretación de filtros, ordenamiento y paginación.
- SearchSchema con campos, tipos, operadores, valores canónicos, alias y límites.
- Validación determinística y rechazo cerrado de salidas inválidas.
- Aclaraciones para consultas ambiguas o capacidades no soportadas.
- Adaptador de proveedor de IA intercambiable internamente.
- Autenticación entre servicios, límites de uso, control de tamaño y timeouts.
- Métricas, trazas y errores operativos sin almacenar consultas sensibles por defecto.
- OpenAPI, ejemplos de integración, guía de operación y criterios de producción.

### Fuera de alcance de v1

- Ejecutar búsquedas o acceder a datos de los consumidores.
- Crear SQL, JPQL, Criteria, código ejecutable o rutas físicas.
- Integrarse directamente con WhatsApp, email, React u otro canal.
- Mantener conversación, historial, memoria, carrito, checkout o pagos.
- Administrar centralmente catálogos o schemas de los equipos.
- Generar recomendaciones o explicar resultados que el servicio no recibió.
- Consultas booleanas anidadas, OR/NOT, agregaciones o facetas.
- Reemplazar el motor de búsqueda existente de un equipo.

Estos límites dejan la autenticación de usuarios finales, el acceso a datos y la ejecución en los sistemas propietarios.

## 3. Usuarios y responsabilidades

### Equipo consumidor

- Define y versiona su SearchSchema.
- Envía el texto, locale y schema al servicio.
- Continúa únicamente cuando el estado de respuesta sea READY.
- Mantiene el mapping de nombres semánticos a sus atributos internos.
- Aplica autorización del usuario final, reglas de negocio y límites de consulta.
- Presenta preguntas de aclaración cuando no se puede producir una búsqueda segura.

### Natural Language Search

- Valida la identidad del cliente de servicio y los límites de entrada.
- Interpreta la consulta dentro del schema recibido.
- Normaliza valores, filtros, orden y paginación.
- Valida toda salida del modelo de forma determinística.
- Devuelve READY, NEEDS_CLARIFICATION o UNSUPPORTED.
- Expone salud, métricas y errores sin filtrar credenciales ni respuestas crudas del proveedor.

### Proveedor de IA

- Recibe solo los datos necesarios para interpretar la solicitud.
- Devuelve una estructura restringida al formato solicitado.
- No tiene acceso a herramientas, bases de datos, redes de clientes ni acciones de ejecución.
- Se configura por ambiente; las credenciales no forman parte de requests ni del repositorio.

## 4. Principios de diseño

1. SearchRequest es la frontera estable entre interpretación y búsqueda.
2. El consumidor es dueño del esquema y del significado de cada campo.
3. SearchSchema contiene nombres y valores semánticos, nunca paths físicos.
4. La IA propone una interpretación; el código determina si se puede ejecutar.
5. Campos, operadores, valores, direcciones y límites no permitidos se rechazan.
6. Una consulta ambigua no se convierte silenciosamente en una búsqueda.
7. La IA no determina permisos ni reglas de negocio.
8. Application no depende de HTTP ni de SDKs concretos de IA.
9. Cada integración externa vive detrás de una frontera con una razón concreta.
10. No se introduce persistencia ni un contexto shared mientras no exista una necesidad real.

## 5. Arquitectura

~~~text
Equipo consumidor
  ├── canal de entrada propio
  ├── autenticación del usuario final
  ├── SearchSchema versionado
  └── motor de búsqueda propio
          │ HTTPS + token de servicio
          ▼
Natural Language Search
  ├── infrastructure/http
  │     ├── autenticación de servicio
  │     ├── DTOs y validación del request
  │     └── controller delgado
  ├── application
  │     └── SearchInterpretationService
  ├── domain
  │     ├── SearchSchema
  │     ├── SearchRequest
  │     ├── SearchInterpretation
  │     └── SearchInterpreter
  └── infrastructure/ai/<proveedor>
        ├── construcción del prompt
        ├── cliente del proveedor
        └── parser y validador de respuesta
~~~

Flujo principal:

1. El cliente se autentica y envía una petición HTTPS.
2. El controller valida el formato externo y delega en application.
3. Application valida el schema y coordina SearchInterpreter.
4. El adaptador solicita una interpretación en formato restringido.
5. Application valida el resultado contra el schema recibido.
6. El servicio devuelve una interpretación o una aclaración.
7. El consumidor ejecuta su búsqueda solo si el estado es READY.

El servicio no necesita JPA ni base de datos en v1. El schema se envía con cada request y pertenece al consumidor. Esto evita almacenar configuración de distintos equipos y mantiene clara la responsabilidad. Los límites del request controlan el tamaño del schema y del catálogo.

## 6. Organización del backend

Se adoptan las convenciones del guideline de backend recibido: capas limpias, nombres simples, fronteras externas explícitas, controllers delgados y pruebas acordes al riesgo. Se adaptan al dominio de este producto; bounded contexts y nombres específicos de tesis.dev no se copian.

Estructura inicial:

~~~text
backend/src/main/java/<paquete-base>/
├── search/
│   ├── application/
│   │   └── SearchInterpretationService.java
│   ├── domain/
│   │   ├── SearchSchema.java
│   │   ├── SearchRequest.java
│   │   ├── SearchInterpretation.java
│   │   ├── SearchFilter.java
│   │   ├── SearchOrder.java
│   │   ├── Pagination.java
│   │   ├── Operator.java
│   │   ├── InterpretationStatus.java
│   │   └── SearchInterpreter.java
│   └── infrastructure/
│       ├── http/
│       │   ├── SearchController.java
│       │   ├── SearchInterpretationHttpRequest.java
│       │   └── SearchInterpretationHttpResponse.java
│       └── ai/
│           └── <proveedor>/
│               ├── <Provider>SearchInterpreter.java
│               ├── SearchPromptBuilder.java
│               ├── SearchResponseParser.java
│               └── <Provider>Client.java
└── shared/
    └── infrastructure/
        ├── config/
        ├── http/
        │   └── GlobalExceptionHandler.java
        ├── security/
        └── observability/
~~~

Reglas:

- Search es el único bounded context de negocio inicial.
- SearchInterpreter existe porque el proveedor de IA es una frontera externa intercambiable.
- No crear paquetes raíz controller, service, repository, dto, exception o util.
- DTOs HTTP viven junto al controller. No se exponen modelos internos directamente.
- Application no construye prompts inline, no importa SDKs del proveedor y no serializa respuestas HTTP.
- Construcción del prompt, invocación, parsing y controles de costo permanecen separados.
- Los modelos de dominio no dependen de Spring, Jackson, JPA, proveedores, HTTP ni filesystem.
- No agregar Value Objects ni capas de mapeo ceremoniales en v1.
- shared se limita a capacidades técnicas transversales y no aloja reglas de search.
- No crear persistencia mientras el servicio no almacene datos propios.
- Usar nombres de métodos simples y expresivos según las convenciones del guideline.

## 7. Contrato semántico

### 7.1 SearchSchema

El consumidor envía un SearchSchema validado junto con cada consulta.

Campos requeridos:

- id: identificador estable del consumidor.
- version: versión del esquema; una versión identifica contenido inmutable.
- defaultLocale: locale usado si el request no lo especifica.
- fields: campos semánticos disponibles.
- defaultLimit y maxLimit: paginación permitida.
- allowEmptySearch: indica si se permite una solicitud sin filtros.

Cada field define:

- name: nombre semántico estable, por ejemplo price.
- description: significado conciso para interpretar consultas.
- type: STRING, ENUM, DECIMAL, INTEGER, BOOLEAN, DATE o DATETIME.
- operators: subset explícito de operadores aceptados para ese campo.
- sortable: indica si se permite ordenar por el campo.
- aliases: expresiones alternativas válidas en los locales admitidos.
- enumValues: valores canónicos, etiquetas y alias, solo para ENUM.
- unit o currency cuando la interpretación dependa de ellas.
- límites de rango solo cuando el consumidor pueda aplicarlos consistentemente.

No se permiten nombres de tablas, columnas, atributos JPA, paths, fragments de query, credenciales ni reglas de acceso a datos.

### 7.2 Tipos y operadores

| Tipo | Operadores admitidos |
| --- | --- |
| STRING | EQ, NE, CONTAINS |
| ENUM | EQ, NE, IN |
| DECIMAL | EQ, LT, LTE, GT, GTE |
| INTEGER | EQ, LT, LTE, GT, GTE |
| BOOLEAN | EQ |
| DATE | EQ, LT, LTE, GT, GTE |
| DATETIME | EQ, LT, LTE, GT, GTE |

El consumidor solo anuncia los operadores que soporta para cada campo. La intersección de ambas listas define lo permitido.

Las consultas v1 contienen filtros combinados con AND. OR, NOT, grupos anidados, agregaciones y texto libre sin campo declarado no se devuelven como SearchRequest. Si se necesitan para representar la intención, se solicita aclaración o se marca como UNSUPPORTED.

### 7.3 Valores y normalización

- Los ENUM se devuelven con su valor canónico. Alias como “negra”, “color carbón” o “black” solo se aceptan si el consumidor los declara.
- Valores numéricos respetan la unidad y moneda declaradas. Si price usa ARS, “50 mil” se normaliza a 50000 cuando locale y schema eliminan la ambigüedad. En caso contrario, se pide aclaración.
- DECIMAL se procesa con precisión decimal arbitraria, sin usar aritmética de punto flotante binario para aplicar límites.
- Para DATE y DATETIME, expresiones relativas usan referenceTime y timeZone enviados por el consumidor. Sin esos valores, se pide aclaración. El modelo no consulta su propio reloj.
- Campos, operadores o valores no declarados nunca se reemplazan por otros “parecidos”.

### 7.4 SearchRequest

~~~json
{
  "filters": [
    {"field": "price", "operator": "LTE", "value": 100000}
  ],
  "order": [
    {"field": "price", "direction": "ASC"}
  ],
  "pagination": {"limit": 5, "offset": 0}
}
~~~

- filters puede estar vacío solo si allowEmptySearch es true.
- Cada filtro tiene campo registrado, operador permitido y valor del tipo declarado.
- IN recibe un array no vacío limitado por la configuración del servicio.
- order solo contiene campos ordenables y direcciones válidas.
- pagination siempre contiene limit y offset; sin indicación del usuario se usan defaultLimit y offset 0.
- limit no supera maxLimit ni el máximo global.
- La respuesta no agrega criterios no expresados, salvo paginación declarada por defecto.

## 8. Resultado de interpretación

### READY

Hay una solicitud completa y validada. Solo este estado habilita al consumidor a continuar.

~~~json
{
  "contractVersion": "1",
  "schema": {
    "id": "catalog",
    "version": "4",
    "sha256": "hash-del-schema-canonico"
  },
  "status": "READY",
  "searchRequest": {
    "filters": [
      {"field": "category", "operator": "EQ", "value": "JACKETS"},
      {"field": "color", "operator": "EQ", "value": "BLACK"},
      {"field": "size", "operator": "EQ", "value": "L"},
      {"field": "price", "operator": "LTE", "value": 100000}
    ],
    "order": [{"field": "price", "direction": "ASC"}],
    "pagination": {"limit": 5, "offset": 0}
  },
  "clarifications": []
}
~~~

### NEEDS_CLARIFICATION

Falta información, existen varias interpretaciones válidas o la búsqueda vacía no está permitida. No hay SearchRequest ejecutable.

~~~json
{
  "contractVersion": "1",
  "schema": {
    "id": "catalog",
    "version": "4",
    "sha256": "hash-del-schema-canonico"
  },
  "status": "NEEDS_CLARIFICATION",
  "searchRequest": null,
  "clarifications": [
    {
      "code": "AMBIGUOUS_VALUE",
      "field": "price",
      "question": "¿Qué precio máximo querés usar?"
    }
  ]
}
~~~

### UNSUPPORTED

La intención requiere una capacidad no ofrecida por el schema o por v1. No hay SearchRequest ejecutable.

~~~json
{
  "contractVersion": "1",
  "schema": {
    "id": "catalog",
    "version": "4",
    "sha256": "hash-del-schema-canonico"
  },
  "status": "UNSUPPORTED",
  "searchRequest": null,
  "clarifications": [
    {
      "code": "UNSUPPORTED_FILTER_LOGIC",
      "field": null,
      "question": "Esta búsqueda combina alternativas que el esquema no permite expresar."
    }
  ]
}
~~~

Los mensajes de aclaración usan el locale solicitado y describen la decisión pendiente sin inventar disponibilidad, resultados ni reglas del consumidor. No se devuelve una interpretación parcial que un cliente pueda ejecutar accidentalmente.

## 9. API HTTP

### 9.1 Interpretar una consulta

~~~http
POST /v1/search-interpretations
Authorization: Bearer <service-token>
Content-Type: application/json
X-Request-Id: <optional-client-id>
~~~

~~~json
{
  "locale": "es-AR",
  "referenceTime": "2026-09-30T12:00:00Z",
  "timeZone": "America/Argentina/Buenos_Aires",
  "message": "Busco camperas negras talle L, hasta 100 mil, primero las más baratas. Mostrame cinco.",
  "schema": {
    "id": "catalog",
    "version": "4",
    "defaultLocale": "es-AR",
    "defaultLimit": 10,
    "maxLimit": 50,
    "allowEmptySearch": false,
    "fields": [
      {
        "name": "category",
        "type": "ENUM",
        "description": "Tipo de prenda",
        "operators": ["EQ", "IN"],
        "sortable": false,
        "enumValues": [
          {"value": "JACKETS", "label": "camperas", "aliases": ["chaquetas"]}
        ]
      },
      {
        "name": "color",
        "type": "ENUM",
        "description": "Color del producto",
        "operators": ["EQ", "IN"],
        "sortable": false,
        "enumValues": [
          {"value": "BLACK", "label": "negro", "aliases": ["negra", "black"]}
        ]
      },
      {
        "name": "size",
        "type": "ENUM",
        "description": "Talle",
        "operators": ["EQ", "IN"],
        "sortable": false,
        "enumValues": [
          {"value": "L", "label": "L", "aliases": ["grande"]}
        ]
      },
      {
        "name": "price",
        "type": "DECIMAL",
        "description": "Precio de venta en pesos argentinos",
        "currency": "ARS",
        "operators": ["EQ", "LT", "LTE", "GT", "GTE"],
        "sortable": true
      }
    ]
  }
}
~~~

Reglas del request:

- locale identifica la lengua y convenciones; si se omite, se usa defaultLocale.
- referenceTime y timeZone son necesarios para interpretar referencias temporales relativas.
- message es UTF-8, no vacío y limitado en caracteres.
- schema supera validación estructural antes de invocar al proveedor.
- clientId/tenant se obtiene del token validado, nunca de un campo libre del JSON.
- X-Request-Id se valida; el servicio devuelve un requestId correlacionable.

### 9.2 Salud

- GET /health/live indica que el proceso está vivo.
- GET /health/ready indica que el servicio puede aceptar requests y tiene configuración obligatoria cargada.
- Health no expone credenciales, configuración sensible ni datos internos del proveedor.

### 9.3 Errores HTTP

Los errores de transporte usan el contrato:

~~~json
{
  "code": "INVALID_SEARCH_SCHEMA",
  "message": "El esquema de búsqueda no es válido.",
  "timestamp": "2026-09-30T12:00:00Z",
  "requestId": "req-..."
}
~~~

| HTTP | code | Uso |
| --- | --- | --- |
| 400 | INVALID_REQUEST | JSON inválido o campos requeridos ausentes |
| 400 | INVALID_SEARCH_SCHEMA | Schema mal formado, duplicado o fuera de límites |
| 401 | UNAUTHENTICATED | Token ausente, inválido o vencido |
| 403 | FORBIDDEN | Cliente autenticado sin permiso |
| 413 | REQUEST_TOO_LARGE | Límite del body excedido |
| 429 | RATE_LIMITED | Cuota o concurrencia excedida |
| 503 | INTERPRETER_UNAVAILABLE | Proveedor temporalmente no disponible |
| 500 | INTERNAL_ERROR | Error interno no clasificado |

READY, NEEDS_CLARIFICATION y UNSUPPORTED son resultados de interpretación y usan HTTP 200. Inventario, resultados y autorización de usuario final pertenecen al consumidor.

Mensajes de error seguros salen por HTTP. Stack traces y respuestas crudas del proveedor no salen al cliente.

## 10. Reglas determinísticas

- “Menos de 50 mil” es LT 50000; “hasta 50 mil” es LTE 50000.
- “Más de” es GT; “como máximo” es LTE; “al menos” es GTE.
- “Los más baratos primero” es price ASC solo si price está declarado y es ordenable.
- “Mostrame cinco” define limit 5 sujeto a máximos.
- Alias de enum deben estar declarados.
- “Barato”, “cerca” o “rápido” no se convierten en límites numéricos sin una regla explícita del consumidor.
- Negación, alternativas, rangos abiertos ambiguos o términos contradictorios requieren aclaración si no son representables fielmente.
- No se agrega un filtro que el usuario no pidió.
- Los filtros de la respuesta se ordenan por nombre de campo para facilitar comparación; el orden no cambia la semántica AND.
- Si allowEmptySearch es false, una consulta sin filtros no puede terminar en READY.

## 11. Validación y seguridad de la salida

La salida del proveedor es entrada no confiable. Antes de READY, application comprueba:

1. Formato estructural esperado.
2. Campos declarados en SearchSchema.
3. Operadores habilitados para campo y tipo.
4. Tipo, formato, unidad y moneda de cada valor.
5. Membresía de valores enum en el catálogo declarado.
6. Campos y direcciones de sort.
7. Límites de paginación.
8. Ausencia de claves, filtros o contenido ejecutable adicionales.
9. Política de búsqueda vacía.

Ante una salida inválida se permite como máximo un intento de reparación restringida si queda tiempo en el deadline. Si sigue inválida, se devuelve error de servicio y se registra un fallo sanitizado. Nunca se convierte una respuesta corrupta en READY.

El prompt trata message como datos no confiables. Instrucciones del usuario que intenten cambiar reglas, revelar prompts, invocar herramientas, ignorar el schema o generar código se ignoran. No se habilitan herramientas ni acciones externas.

## 12. Seguridad y privacidad

- HTTPS entre consumidor y servicio.
- JWT de servicio validado por issuer, audience, expiración y scope search:interpret.
- El principal autenticado determina clientId para cuotas y métricas.
- El consumidor autentica y autoriza al usuario final; el token de servicio no lo sustituye.
- Credenciales de proveedores se inyectan mediante el gestor de secretos del entorno.
- Schema y message se minimizan a los datos requeridos para interpretar.
- Raw message, prompt completo y payload del proveedor no se registran por defecto.
- Logs incluyen requestId, clientId seudonimizado, estado, latencia, proveedor/modelo lógico, tokens y códigos de validación cuando estén disponibles.
- Métricas y trazas excluyen texto del usuario, alias privados y valores de catálogo.
- El proveedor debe pasar revisión de retención, región, entrenamiento con datos, subprocesadores, acceso y residencia antes de producción.
- No se persisten message, schema, prompt ni respuesta. Retenerlos requeriría necesidad de producto y revisión explícita de privacidad/seguridad.
- Rate limits y límites de entrada controlan abuso y gasto.
- Aclaraciones no revelan datos de otros clientes ni información interna.

Límites iniciales, configurables por deployment pero aplicados consistentemente:

- Body máximo: 256 KiB.
- Mensaje máximo: 4.000 caracteres Unicode.
- Máximo de fields por schema: 100.
- Máximo de valores enum por field: 200.
- limit entre 1 y min(maxLimit, 50).
- offset entre 0 y 10.000.
- Máximo de filtros devueltos: 100.
- Un locale y un SearchSchema por request.

El tamaño total del schema también debe caber en el límite del body. Catálogos más grandes requieren filtrar previamente opciones o diseñar una capacidad futura de schema referenciado con gobierno explícito.

## 13. Disponibilidad, latencia y costo

Objetivos iniciales bajo la carga acordada:

- disponibilidad mensual de respuestas válidas: 99,9%, medida sobre requests autenticados y válidos; errores 5xx y timeouts del proveedor cuentan como indisponibilidad;
- latencia end-to-end p95 menor o igual a 5 segundos y p99 menor o igual a 10 segundos;
- timeout finito para cada llamada al proveedor;
- concurrencia, cuotas y gasto máximo configurables por clientId;
- presupuesto de tokens de entrada y salida;
- ningún fallback automático a SearchRequest vacío o permisivo;
- reintentos de red limitados por deadline y diseñados para no duplicar llamadas costosas sin control.

Los estados READY, NEEDS_CLARIFICATION y UNSUPPORTED cuentan como respuestas válidas. Los errores 4xx causados por requests inválidos no forman parte del denominador. Estos objetivos se validan antes de producción con el proveedor elegido, tamaño real de schema, tráfico esperado y pruebas de carga. Cualquier cambio de SLO registra la decisión y sus efectos para los consumidores.

Proveedor, modelo, región, límites de tokens y retención se configuran por ambiente. Su elección debe quedar documentada en runbook antes del deployment productivo.

## 14. Observabilidad y operación

Métricas:

- requests por estado de interpretación y código HTTP;
- latencia total y del proveedor, con percentiles;
- errores, timeouts, rate limits y fallos de parsing;
- conteos de filtros, aclaraciones y schemas inválidos sin valores sensibles;
- tokens y costo estimado por proveedor/modelo y clientId seudonimizado;
- concurrencia y rechazos por cuota.

Dashboards separan errores del servicio de los del proveedor y permiten detectar cambios en aclaraciones o respuestas inválidas por versión de modelo.

Alertas iniciales:

- readiness caída o aumento sostenido de 5xx;
- incumplimiento de latencia;
- timeouts o respuestas inválidas del proveedor;
- cuotas y costos cerca de su límite;
- expiración o fallo de credenciales.

Cada ambiente documenta propietario de guardia, degradación, proveedor/región/modelo configurado, rotación de credenciales, límites y cómo suspender la integración. Ante indisponibilidad del proveedor se devuelve 503; el consumidor decide si ofrece búsqueda estructurada como alternativa.

## 15. Pruebas y evaluación de calidad

Estas pruebas son requisitos de publicación, no una demostración aislada.

### Dominio y application

- Validación del SearchSchema y sus límites.
- Comparadores, orden, paginación y normalización.
- Estados READY, NEEDS_CLARIFICATION y UNSUPPORTED.
- Rechazo de campos u operadores no declarados.
- Búsqueda vacía, límites, unidades, moneda y tiempos relativos.

### HTTP

- Contrato request/response, OpenAPI, errores y status codes.
- Autenticación, scopes, rate limit y health.
- No filtración de secretos.

### Proveedor

- Prompt builder con message como dato no confiable.
- Parser con respuesta válida, truncada, malformada y con campos inesperados.
- Timeout, fallos y política de reparación sin llamadas reales en CI.
- Smoke tests opt-in con costo y credenciales documentados.

### Evaluación semántica

Mantener un corpus versionado y aprobado por locale que cubra filtros simples y combinados, sinónimos, valores fuera de catálogo, unidades, moneda, comparadores, orden, paginación, lenguaje coloquial, errores de escritura, mensajes fragmentados, ambigüedad, contradicciones, capacidades no soportadas y prompt injection.

Medir por separado exactitud de campos, operadores, valores canónicos, orden, paginación, aclaraciones y tasa de salida inválida. Cada locale tiene umbrales antes de lanzarse. Cambios de modelo, prompt o parser ejecutan la misma evaluación y requieren revisión de regresiones.

Proveedores reales no se ejecutan en mvn test. Tests externos usan una bandera opt-in, fallan rápido sin credenciales y documentan costo e impacto.

## 16. Compatibilidad y evolución

- La ruta HTTP de v1 es /v1.
- Cambios aditivos compatibles pueden incorporarse en v1.
- Cambios que alteren semántica o eliminen campos requieren una ruta mayor nueva.
- Parejas schema id/version son inmutables.
- La respuesta devuelve id, version y SHA-256 de la representación canónica recibida.
- Cambios de catálogo incrementan schema.version para evitar reinterpretación silenciosa.
- OpenAPI publicado es fuente de verdad del contrato HTTP.
- Cada versión documenta límites, locales, errores y matriz de proveedor/modelo validada.
- No se requiere registry central de schemas en v1. El consumidor versiona su configuración junto a su backend. Si escala o gobierno requiere registro, debe diseñarse con aislamiento de tenant, auditoría, propietarios y ciclo de vida.

## 17. Guía de integración

1. Definir SearchSchema con nombres semánticos, alias, operadores, unidad/moneda, orden y límites.
2. Mantenerlo versionado con el backend consumidor.
3. Invocar POST /v1/search-interpretations con token y request ID.
4. Presentar aclaraciones en NEEDS_CLARIFICATION.
5. Informar capacidad no disponible o usar el flujo normal en UNSUPPORTED.
6. Continuar solo si status es READY y searchRequest no es null.
7. Aplicar autorización del usuario final y validar el contrato.
8. Traducir campos semánticos a paths internos mediante código controlado por el consumidor.
9. Ejecutar con su motor existente, como JPA Criteria, buscador dedicado o API propia.
10. Registrar versiones de API y schema cuando se requiera diagnóstico sin almacenar texto crudo sin política aprobada.

El mapping JPA pertenece al consumidor. Si usa Spring Data, mantiene FieldDefinition, PathResolver, JoinRegistry y construcción de Specifications dentro de su bounded context; no forman parte del servicio central.

Un consumidor JPA no debe pasar SearchRequest.field directamente a root.get(field) salvo que ese nombre esté permitido y corresponda a una propiedad de la entidad consultada. Los aliases semánticos y campos relacionados requieren mapping explícito o una consulta especializada con joins controlados. El consumidor valida también operadores, tipos, dirección de orden y límites en runtime; documentarlos en OpenAPI no sustituye esas validaciones. Debe traducir los operadores del contrato v1 a los que soporte su converter y rechazar combinaciones no soportadas antes de ejecutar la consulta.

## 18. Criterios de salida a producción

- [ ] Existe una aplicación Spring Boot desplegable y configuración de ambientes documentada.
- [ ] OpenAPI coincide con DTOs y contiene ejemplos válidos.
- [ ] Hay autenticación service-to-service y autorización por scope.
- [ ] SearchSchema y todas las salidas del proveedor pasan validación determinística.
- [ ] Consultas ambiguas o no soportadas nunca devuelven SearchRequest ejecutable.
- [ ] Se cubren errores HTTP, proveedor, parsing, límites y rate limits.
- [ ] Existe corpus de evaluación por locale y umbrales aprobados.
- [ ] Dashboard, alertas, runbook y propietarios operativos están definidos.
- [ ] SLOs se probaron con volumen y concurrencia acordados.
- [ ] Proveedor completó revisión de privacidad, seguridad, retención y región.
- [ ] Secretos se inyectan desde el entorno y su rotación está documentada.
- [ ] Hay ejemplo funcional de integración para consumidores.
- [ ] Existe política de versionado y migración de contrato.
- [ ] El dominio no depende de JPA ni de un proveedor concreto.
- [ ] Está documentada la estrategia del consumidor ante 503.
- [ ] Se comunica con precisión qué se implementó, configuró, probó y verificó.

Este documento define el diseño; no afirma que el servicio, proveedor, SLO o integración estén implementados o verificados.

## 19. Secuencia de implementación

### Entrega 1 — Contrato y dominio

Definir tipos, validación de schema, interpretación, aclaraciones y límites. Publicar OpenAPI inicial y pruebas unitarias determinísticas.

### Entrega 2 — API segura

Crear Spring Boot con bounded context search, controller delgado, DTOs, errores, JWT de servicio, scopes, rate limits, health y OpenAPI.

### Entrega 3 — Proveedor de interpretación

Elegir proveedor y modelo, revisar privacidad y seguridad, implementar SearchInterpreter, prompt builder, parser, timeouts y controles de costo. Probar con respuestas simuladas.

### Entrega 4 — Calidad y operación

Crear corpus por locale, medir exactitud, añadir métricas, dashboards, alertas y runbook. Probar carga y validar SLOs.

### Entrega 5 — Integración y publicación

Integrar un consumidor de referencia sin acoplar persistencia. Revisar fallos, degradación y autorización. Publicar onboarding y cambio de contrato. Habilitar despliegue gradual con rollback.

Cada entrega produce artefactos revisables. Una demostración local o una llamada exitosa a un modelo no equivale a producción.

## 20. Decisiones registradas

### Fijadas

- El producto v1 es un servicio HTTP Spring Boot de interpretación.
- Los consumidores envían SearchSchema y son dueños de su versión.
- La API no persiste mensajes, schemas ni resultados.
- Búsqueda, permisos, mapping y ejecución pertenecen a consumidores.
- La IA está detrás de SearchInterpreter.
- La validación y seguridad de salida son determinísticas.
- Aclaraciones y capacidades no soportadas no producen SearchRequest ejecutable.
- Se adopta la organización por dominio y capas del backend guideline, con un único contexto inicial llamado search.

### Requisitos previos al primer deployment productivo

- Proveedor, modelo, región, credenciales y condiciones de retención.
- Identity provider, audience y valores finales de scopes.
- Capacidad, concurrencia, cuotas y presupuesto por clientId.
- Locales iniciales y umbrales de su corpus.
- SLOs finales validados con carga y proveedor.
- Entornos, estrategia de despliegue, monitoreo y responsables.

Estas decisiones no alteran el contrato semántico, pero bloquean la publicación productiva hasta quedar documentadas en configuración y runbook.

## 21. Resumen

~~~text
POST /v1/search-interpretations
    SearchSchema + mensaje + locale
                    ↓
        validación determinística
                    ↓
       SearchInterpreter externo
                    ↓
      validación contra SearchSchema
          ↙          ↓           ↘
   NEEDS_CLARIFICATION READY   UNSUPPORTED
                       ↓
             consumidor ejecuta
             su propia búsqueda
~~~

Natural Language Search es la frontera segura entre lenguaje humano y contratos de búsqueda determinísticos. El consumidor mantiene datos, permisos, mapping y ejecución; el servicio entrega una interpretación estructurada, validada, versionada y operable.
