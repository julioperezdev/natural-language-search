# Backend Guidelines — tesis.dev

Guía de estilo para desarrollar y migrar el backend de tesis.dev.

El objetivo es ordenar el código por dominio y bounded context, manteniendo una
arquitectura limpia por dentro sin caer en nombres largos, interfaces
decorativas o capas que no agregan valor.

---

## Principios

- Organizar primero por dominio, no por tipo técnico.
- Dentro de cada dominio usar `application`, `domain` e `infrastructure`.
- Mantener nombres simples y expresivos.
- Evitar sufijos redundantes como `UseCase`, `Port`, `Impl`, `Adapter` salvo que
  aclaren una frontera real.
- Crear interfaces solo cuando exista una frontera externa clara, más de una
  implementación razonable o una ventaja concreta para tests.
- No introducir Value Objects por ahora.
- Migrar el código existente de forma gradual cuando se toque una funcionalidad.
- No hacer refactors masivos si el cambio pedido no lo necesita.

---

## Estructura objetivo

```text
backend/src/main/java/dev/tesis/
├── shared/
│   ├── domain/
│   └── infrastructure/
│       ├── config/
│       ├── http/
│       └── security/
└── <bounded-context>/
    ├── application/
    ├── domain/
    └── infrastructure/
        ├── http/
        ├── messaging/
        │   └── sqs/
        ├── jobs/
        ├── repository/
        │   └── postgres/
        ├── ai/
        │   └── bedrock/
        ├── email/
        │   └── ses/
        ├── payments/
        │   └── stripe/
        └── <adapter-kind>/
            └── <provider-or-source>/
```

Ejemplos de bounded contexts esperados:

- `auth`: registro, login, sesiones y refresh tokens.
- `users`: usuario y perfil base.
- `projects`: proyectos de tesis.
- `sections`: generación, versionado y contexto de secciones.
- `waitlist`: lista de espera y referidos.
- `billing`: Stripe, planes y suscripciones.
- `usage`: cuotas, límites y consumo.
- `notifications`: email y eventos salientes.

No todos los contextos tienen que existir desde el día uno. Crear un contexto
cuando haya código real que alojar.

---

## Responsabilidades

### `application`

Orquesta casos de uso del dominio.

Puede:

- coordinar entidades, repositorios y servicios externos;
- manejar transacciones;
- validar reglas de aplicación;
- devolver DTOs de respuesta o resultados simples.

No debe:

- depender de detalles HTTP;
- construir prompts largos inline;
- conocer APIs concretas de Bedrock, Stripe, SES o Postgres;
- serializar respuestas finales del transporte.

Nombres preferidos:

- `SectionService`
- `ProjectService`
- `UsageService`

Evitar nombres ceremoniales si no ayudan:

- `GenerateSectionUseCase`
- `GenerateSectionInteractor`
- `SectionApplicationService`

Convención de métodos:

- En `application`, usar verbos de caso de uso simples y consistentes.
- Usar `create(...)` para crear recursos del sistema.
- Usar `find...(...)` para lecturas opcionales. Debe devolver `Optional<T>` si
  es un único resultado, o `List<T>`/`Page<T>` si son múltiples. No debe lanzar
  excepción por no encontrar datos.
- Usar `get...(...)` para lecturas obligatorias. Debe devolver `T` y lanzar una
  excepción de aplicación si el recurso no existe.
- Usar `update(...)` para modificaciones.
- Usar `delete(...)` para eliminaciones.
- Reservar verbos más específicos solo cuando `create/update` oculten una
  intención importante del dominio.
- En controllers HTTP se permite usar nombres orientados al usuario o endpoint,
  por ejemplo `join`, aunque deleguen en `service.create(...)`.
- En repositories usar `findBy...(...)` para búsquedas opcionales y
  `save(model)` para crear o actualizar persistencia.

### `domain`

Contiene el lenguaje central del contexto.

Puede incluir:

- modelos de dominio sin dependencias de infraestructura;
- enums;
- reglas de dominio;
- contratos internos cuando exista una frontera real.

Por ahora evitar Value Objects. Usar tipos simples y validaciones claras hasta
que el dominio pida más estructura.

Los modelos de dominio no deben depender de JPA, PostgreSQL, Bedrock, Stripe,
SES, SQS, HTTP ni filesystem.

### `infrastructure`

Contiene adapters concretos por canal, proveedor o source.

No usar `input` y `output` como carpetas obligatorias. La dirección del adapter
se expresa con el nombre de la clase:

- `Controller`: entrada HTTP.
- `Consumer`: entrada desde mensajería.
- `Job`: entrada programada o batch.
- `Publisher`: salida a mensajería.
- `Client`: salida hacia API externa.
- `Repository`: persistencia o lectura/escritura.

Ejemplo:

```text
sections/
└── infrastructure/
    ├── http/
    │   └── SectionController.java
    ├── messaging/
    │   └── sqs/
    │       ├── SectionGenerationConsumer.java
    │       └── SectionEventPublisher.java
    ├── jobs/
    │   └── SectionRetryJob.java
    ├── ai/
    │   └── bedrock/
    │       └── BedrockSectionGenerator.java
    └── repository/
        └── postgres/
            ├── ThesisSectionJpaRepository.java
            ├── ThesisSectionBaseJpaRepository.java
            └── ThesisSectionJpaEntity.java
```

### `infrastructure.http`

Adaptadores de entrada HTTP.

Contiene:

- controllers;
- request/response DTOs específicos de la API;
- mapeos entre HTTP y application.

Los controllers deben ser delgados. No deben contener reglas de negocio,
prompts, queries complejas ni llamadas directas a proveedores externos.

### `infrastructure.repository`

Persistencia o lectura/escritura del contexto.

Ordenar por source concreta:

```text
<bounded-context>/
└── infrastructure/
    └── repository/
        ├── postgres/
        ├── s3/
        ├── cloudwatch/
        └── excel/
```

Para PostgreSQL/JPA usar este patrón:

```text
repository/postgres/
├── XJpaRepository.java
├── XBaseJpaRepository.java
└── XJpaEntity.java
```

Responsabilidades:

- `XJpaEntity`: mapeo JPA a la tabla.
- `XBaseJpaRepository`: interfaz que extiende `JpaRepository<XJpaEntity, UUID>`.
- `XJpaRepository`: adapter usado por `application`; recibe/devuelve modelos de
  dominio y contiene los mapeos privados `toEntity` y `toModel`.

Ejemplo:

```java
interface WaitlistEntryRepository {
    WaitlistEntry save(WaitlistEntry entry);
    boolean existsByEmail(String email);
    Optional<WaitlistEntry> findByEmail(String email);
    Optional<WaitlistEntry> findByReferralCode(String referralCode);
    int findMaxPosition();
}
```

```java
@Repository
@RequiredArgsConstructor
class WaitlistEntryJpaRepository implements WaitlistEntryRepository {
    private final WaitlistEntryBaseJpaRepository jpa;

    public WaitlistEntry save(WaitlistEntry entry) {
        return toModel(jpa.save(toEntity(entry)));
    }

    private WaitlistEntryJpaEntity toEntity(WaitlistEntry model) {
        // mapear campos
    }

    private WaitlistEntry toModel(WaitlistEntryJpaEntity entity) {
        // mapear campos
    }
}
```

El modelo de dominio no debe tener anotaciones JPA.

### `infrastructure.ai`

Integración con LLMs.

Separar:

- construcción del prompt;
- invocación del modelo;
- parsing/validación de respuesta;
- estimación o guardrails de costo cuando aplique.

Ejemplo deseado:

```text
sections/
├── application/
│   └── SectionService.java
├── domain/
│   ├── ThesisSection.java
│   ├── SectionContext.java
│   └── SectionGenerator.java
└── infrastructure/
    ├── http/
    │   └── SectionController.java
    ├── ai/
    │   └── bedrock/
    │       └── BedrockSectionGenerator.java
    └── repository/
        └── postgres/
            ├── ThesisSectionJpaRepository.java
            ├── ThesisSectionBaseJpaRepository.java
            └── ThesisSectionJpaEntity.java
```

No mezclar prompt building con `BedrockRuntimeClient.invokeModel`.

### `shared`

Código realmente transversal.

Puede contener:

- `shared/domain`: conceptos transversales sin infraestructura, por ejemplo
  `ApiException`.
- `shared/infrastructure/config`: configuración Spring global, por ejemplo
  seguridad, Jackson o clientes AWS compartidos.
- `shared/infrastructure/http`: adapters HTTP globales, por ejemplo
  `GlobalExceptionHandler`.
- `shared/infrastructure/security`: helpers o componentes técnicos de seguridad,
  por ejemplo JWT.

No usar `shared` como cajón para evitar decidir el contexto.

`shared` no debe contener reglas de negocio propias de `auth`, `users`,
`billing`, `sections`, `projects`, `usage` o cualquier otro bounded context.
Si una clase cambia por una regla de negocio de un contexto, probablemente no
pertenece a `shared`.

Estructura actual esperada:

```text
shared/
├── domain/
│   └── ApiException.java
└── infrastructure/
    ├── config/
    │   ├── AwsConfig.java
    │   ├── JacksonConfig.java
    │   ├── JwtAuthFilter.java
    │   └── SecurityConfig.java
    ├── http/
    │   └── GlobalExceptionHandler.java
    └── security/
        └── JwtUtil.java
```

---

## Puertos e interfaces

Usar interfaces cuando haya una frontera externa clara o necesidad real de
desacoplamiento.

Buenos candidatos:

- generador de secciones con IA;
- repository de dominio cuando la persistencia debe mapear modelos limpios a una
  source concreta;
- cliente de pagos;
- email sender;
- storage/exporter;
- clock si se necesita determinismo fuerte en tests.

Evitar interfaces cuando solo exista una implementación obvia y no mejore los
tests.

Preferir nombres simples:

```java
interface SectionGenerator { ... }
class BedrockSectionGenerator implements SectionGenerator { ... }
```

Evitar:

```java
interface SectionGeneratorPort { ... }
class SectionGeneratorAdapterImpl implements SectionGeneratorPort { ... }
```

---

## DTOs y mappers

- Los DTOs HTTP viven cerca del controller en `infrastructure.http`.
- Los DTOs internos viven en `application` solo si son necesarios.
- No crear mappers para asignaciones triviales.
- Crear mapper cuando evita duplicación, protege una frontera o mejora claridad.
- En repositories con JPA entity separada, mantener `toEntity` y `toModel`
  privados dentro del adapter mientras el mapeo sea simple.

---

## Errores

Mantener el contrato HTTP actual:

```json
{ "code": "...", "message": "...", "timestamp": "...", "requestId": "..." }
```

Las excepciones de aplicación deben expresar códigos de negocio estables:

- `PROJECT_NOT_FOUND`
- `SECTION_NOT_READY`
- `GENERATION_FAILED`
- `QUOTA_EXCEEDED`

No filtrar errores crudos de proveedores externos al frontend.

---

## Tests esperados

Para cada cambio de backend, elegir el nivel mínimo que cubra el riesgo.

Tests recomendados:

- application service con mocks para fronteras externas;
- controller HTTP con `MockMvc` para contrato y validación;
- prompt builders con snapshots pequeños o asserts sobre partes críticas;
- parsers de respuesta IA con JSON válido e inválido;
- repositories con integración ligera si hay queries custom;
- providers externos con smoke tests opt-in, nunca obligatorios en CI local.

Bedrock, Stripe y SES no deben ejecutarse en `mvn test` por defecto.

Para pruebas reales contra proveedores:

- usar variables tipo `RUN_BEDROCK_IT=true`;
- documentar costo/impacto;
- fallar rápido si faltan credenciales;
- mantener prompts de prueba pequeños.

---

## Evolución del backend

La migración estructural principal ya fue realizada. El código nuevo debe nacer
dentro del bounded context correspondiente o dentro de `shared` cuando sea
realmente transversal.

Reglas para nuevos desarrollos:

1. No crear nuevos paquetes raíz legacy como `controller`, `service`, `domain`,
   `repository`, `dto`, `exception` o `util`.
2. Mantener endpoints y contratos públicos salvo que el issue pida cambiarlos.
3. Evitar mezclar cambios arquitectónicos con cambios de negocio grandes.
4. No renombrar clases solo por pureza si no mejora lectura o límites.
5. No modificar migraciones Liquibase ya aplicadas en ambientes compartidos o
   productivos.
6. En etapa local/pre-release, una migración ya aplicada puede corregirse solo
   si la base se puede resetear y el cambio se valida desde cero con Liquibase.
7. Si aparece una integración compartida, decidir explícitamente si pertenece a
   un bounded context o a `shared`. No moverla a `shared` por comodidad.

Bounded contexts actuales:

```text
auth/
billing/
projects/
sections/
usage/
users/
waitlist/
```

Ejemplos reales de naming:

```text
auth/application/AuthService.java
auth/infrastructure/http/AuthController.java
users/domain/User.java
projects/infrastructure/repository/postgres/ThesisProjectJpaRepository.java
sections/infrastructure/ai/BedrockSectionGenerator.java
sections/infrastructure/ai/SectionPromptBuilder.java
billing/infrastructure/stripe/StripeCheckoutProvider.java
usage/application/UsageService.java
waitlist/application/WaitlistService.java
```

---

## Checklist antes de terminar

- El código nuevo está dentro del bounded context correcto.
- El controller es delgado.
- La lógica principal vive en `application`.
- La integración externa vive en el adapter correcto dentro de `infrastructure`.
- Los modelos de dominio no dependen de JPA ni proveedores externos.
- Los nombres son cortos y no redundantes.
- No se agregaron interfaces decorativas.
- No se agregaron Value Objects.
- Hay tests del nivel adecuado.
- `mvn test` o `mvn verify` fue ejecutado cuando aplica.
