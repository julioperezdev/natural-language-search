# Specification — Dynamic & Natural Language Search Engine

> Consolidated implementation specification for Spring Boot + JPA Criteria, semantic search schemas, dynamic joins, and natural-language interpretation.
>
> **Status:** Implementation baseline
> **Primary audience:** Backend engineers and coding agents (Codex)
> **Scope:** Production-oriented modular backend for reuse across teams
> **Source of truth:** This document supersedes the architectural decisions from the earlier Natural Language Search Adapter specification where they conflict with the current Criteria/JPA implementation or backend guidelines.

---

## 1. Executive summary

The project already has a deterministic dynamic-search mechanism based on:

```text
HTTP criteria request
        ↓
CriteriaAdapter
        ↓
Criteria (domain/shared model)
        ↓
Application service / use case
        ↓
Repository contract
        ↓
JPA repository adapter
        ↓
HibernateCriteriaConverter
        ↓
EntityManager + CriteriaBuilder
        ↓
Database
```

The new implementation **must extend this mechanism rather than introduce a second query engine based on `JpaSpecificationExecutor`**.

The target architecture adds these capabilities:

1. **Semantic dynamic search**: public fields such as `category`, `color`, `size`, `price` and `stock` are mapped safely to root attributes or controlled JPA joins.
2. **Natural-language interpretation**: Jeff/Jev converts a human message into the same semantic search contract; it never emits SQL, JPQL, JPA paths, table names or arbitrary fields.
3. **Customer response humanization**: a separate bounded context receives the original message and structured search result and produces a grounded customer-facing reply.
4. **Use-case orchestration**: an application service coordinates interpretation, catalog search and response humanization while each stage remains independently callable.

The structured product response contains only variant rows that satisfy the query's variant filters together on the same variant. If the query has no variant filters, all variants for each returned product are included. This keeps customer-facing details aligned with the matching criteria while still supporting broad product discovery.

The central architectural rule is:

> **AI interprets intent. Backend code owns query semantics.**

The main new abstraction is a per-search-context `SearchFieldRegistry` from which the backend derives:

- the semantic schema exposed to Jeff/Jev or a frontend;
- validation rules;
- allowed operators;
- allowed ordering;
- finite values when applicable;
- JPA path/join resolution;
- whether a field requires `distinct`;
- optional ordering semantics for joined collections.

This avoids duplicated allowlists and prevents the AI or frontend from knowing persistence paths.

---

## 2. Context

The application needs to support dynamic searches over existing relational models without forcing a company to redesign its database around a dedicated search table.

Current catalog schema:

```text
categories
    ↑
products
    ↑
product_variants
```

Relations:

```text
products.category_id → categories.id
product_variants.product_id → products.id
```

A user should be able to express searches such as:

```text
"Quiero remeras negras talle M por menos de 50 mil, primero las más baratas."
```

A React UI should also be able to express the same search through structured filters.

Both channels must converge on the deterministic Criteria flow.

---

## 3. Goals

The backend must provide a reusable, production-oriented foundation that can:

1. preserve the existing `Criteria` model and `HibernateCriteriaConverter` flow;
2. accept semantic field names rather than JPA property paths;
3. resolve fields across relationships using controlled joins;
4. reuse joins during a query execution;
5. validate fields, operators, value types, ordering and pagination before JPA execution;
6. expose search capabilities so Jeff/Jev knows which filters are valid;
7. translate natural language into a structured semantic search without giving AI access to SQL/JPA internals;
8. reuse the same engine with another domain by changing configuration/registry rather than rewriting the core;
9. keep code organized according to domain-first `application` / `domain` / `infrastructure` boundaries;
10. evolve incrementally without a mass refactor of the existing Criteria implementation.

---

## 4. Non-goals for the current delivery

This delivery does **not** include:

- cart management;
- checkout;
- payments;
- RAG;
- MCP;
- autonomous agents;
- complex conversational memory;
- AI-generated SQL;
- AI-generated JPQL;
- AI-generated Criteria/JPA paths;
- arbitrary joins requested by clients;
- a generic visual query builder;
- replacing JPA Criteria with another persistence technology;
- a mass migration of unrelated repositories.
- conversational memory or follow-up filter retention;
- model-generated customer text or a dependency on a text-generation provider;
- authentication; API access is intentionally left to the current deployment boundary.

---

## 5. Existing implementation that must be preserved

The existing Criteria infrastructure already provides:

- `CriteriaRestControllerRequest`;
- `FilterRestControllerRequest`;
- `CriteriaAdapter`;
- shared `Criteria`, `Filter`, `Filters`, `Order` and related types;
- `HibernateCriteriaConverter`;
- deterministic filtering;
- ordering;
- pagination;
- count queries;
- distinct query variants;
- repository adapters that map JPA entities to domain models.

Today, the converter resolves simple fields approximately as:

```java
root.get(field)
```

That is sufficient for direct attributes but not for semantic aliases or relationships.

The new feature must extend field resolution without invalidating current direct-field consumers.

---

## 6. Key architectural decisions

### 6.1 Keep `Criteria` as the deterministic internal query contract

Do not introduce `Specification<T>` / `JpaSpecificationExecutor<T>` as a parallel engine.

The deterministic flow remains:

```text
Criteria
  ↓
HibernateCriteriaConverter
  ↓
CriteriaQuery / Predicate / Join
```

Natural language is an upstream concern.

### 6.2 Semantic names are public; JPA paths are private

Allowed public fields:

```text
category
productName
color
size
price
stock
```

Forbidden public fields:

```text
variants.price
product.category.name
categoryEntity.name
root.variants.color
```

Clients and AI only work with semantic names.

### 6.3 One registration point, two projections

There must not be independent, manually maintained copies of:

- AI schema;
- frontend filter schema;
- validation allowlist;
- JPA field mapping.

A single backend registry declares a searchable field once.

From that registration, the application derives:

```text
SearchFieldRegistry
        ├── SemanticSearchSchema projection
        ├── Runtime validation
        └── JPA path resolution
```

The semantic projection never serializes JPA-specific data.

### 6.4 Do not expose persistence structure to Jeff/Jev

Jeff/Jev must receive only:

- semantic field key;
- human description when useful;
- value type;
- finite/open classification;
- allowed operators;
- finite values when applicable;
- whether sorting is supported.

It must never receive entity names, column names, join paths or SQL fragments.

### 6.5 Joins are controlled by backend code

A semantic field may resolve to:

- a root attribute;
- a to-one join;
- a to-many join;
- a controlled expression;
- in a future phase, an aggregate expression.

No caller can create a join directly.

---

## 7. Target architecture

```text
                    ┌──────────────────────┐
                    │       React UI       │
                    │ structured filters   │
                    └──────────┬───────────┘
                               │
                               │ semantic criteria
                               ▼
                    ┌──────────────────────┐
                    │ Criteria HTTP Adapter │
                    └──────────┬───────────┘
                               │
                               ▼
                            Criteria
                               │
                               │
┌─────────────────┐            │
│ WhatsApp / API  │            │
│ Email / Chat    │            │
└────────┬────────┘            │
         │                     │
         ▼                     │
 NaturalLanguage               │
 Search Interpreter            │
         │                     │
         ├── Search Schema ◄───┘
         │
         ▼
 Semantic interpretation
         │
         ▼
 Criteria adapter
         │
         └────────────────────► Criteria
                                  │
                                  ▼
                         Application service
                                  │
                                  ▼
                         Repository contract
                                  │
                                  ▼
                         JPA repository adapter
                                  │
                                  ▼
                    HibernateCriteriaConverter
                                  │
                     ┌────────────┴────────────┐
                     ▼                         ▼
             SearchFieldRegistry          JoinRegistry
                     │                         │
                     └────────────┬────────────┘
                                  ▼
                        CriteriaBuilder / JPA
                                  │
                                  ▼
                              Database
```

---

## 8. Search contract

### 8.1 Existing Criteria remains canonical internally

The existing domain/shared model remains the query contract used by application and repositories.

Conceptually:

```text
Criteria
├── Filters
│   └── Filter
│       ├── field
│       ├── operator
│       └── value
├── Order
├── limit
└── offset
```

### 8.2 Natural-language interpretation may use an intermediate DTO

Jeff/Jev may return a transport-oriented representation such as:

```java
public record SearchInterpretation(
    List<SearchFilterInterpretation> filters,
    List<SearchOrderInterpretation> order,
    Integer limit,
    Integer offset
) {}
```

This object is **not** a second persistence query model.

It is immediately validated and adapted to the existing `Criteria` model.

### 8.3 Operator compatibility

The current Criteria implementation already supports operators such as:

```text
=
!=
>
<
CONTAINS
NOT_CONTAINS
IN
NOT_IN
INCLUDES
INCLUDES_OR
```

Natural-language interpretation requires expressions equivalent to:

```text
<=
>=
```

Recommendation:

1. preserve existing operator values for backward compatibility;
2. extend the current `FilterOperator` and converter with `<=` and `>=`;
3. map any Jeff/Jev semantic token such as `LTE` / `GTE` to the canonical Criteria operator before execution;
4. do not create a permanent duplicate operator system unless a real boundary requires it.

---

## 9. SearchFieldRegistry — source of truth

### 9.1 Responsibility

`SearchFieldRegistry<T>` defines every field that a search context is allowed to use.

It is responsible for answering:

- Is this semantic field valid?
- Which operators are valid?
- What Java type does the value represent?
- Is the value finite or open?
- Which finite values are valid?
- Is sorting allowed?
- How is the field resolved in JPA?
- Does resolving it involve a to-many join?
- Does it require distinct results/counts?
- If ordering through a collection is supported, what is the ordering strategy?

### 9.2 Conceptual model

```java
public record SearchFieldDefinition<T>(
    String name,
    String description,
    SearchValueType valueType,
    Class<?> javaType,
    Set<FilterOperator> allowedOperators,
    boolean sortable,
    Set<?> allowedValues,
    PathResolver<T> pathResolver,
    boolean requiresDistinct
) {}
```

This is conceptual. Adapt naming and signatures to the existing codebase rather than creating ceremony for its own sake.

### 9.3 Finite and open fields

Examples of finite fields:

```text
category
color
size
status
country
```

Examples of open fields:

```text
price
stock
productName
quantity
```

A finite field may expose allowed values:

```json
{
  "name": "size",
  "type": "ENUM",
  "operators": ["=", "IN"],
  "values": ["S", "M", "L", "XL"]
}
```

An open numeric field should not require predeclared values:

```json
{
  "name": "price",
  "type": "NUMBER",
  "operators": ["=", "<", "<=", ">", ">="]
}
```

---

## 10. Semantic schema exposed to Jeff/Jev

### 10.1 Projection

The semantic schema is generated from the registry and may look like:

```json
{
  "context": "product-search",
  "fields": [
    {
      "name": "category",
      "description": "Product category",
      "type": "ENUM",
      "operators": ["=", "IN"],
      "values": ["REMERAS", "BUZOS", "CAMPERAS"],
      "sortable": false
    },
    {
      "name": "color",
      "type": "ENUM",
      "operators": ["=", "IN"],
      "values": ["BLACK", "WHITE", "BLUE"],
      "sortable": false
    },
    {
      "name": "price",
      "type": "NUMBER",
      "operators": ["=", "<", "<=", ">", ">="],
      "sortable": true
    }
  ],
  "pagination": {
    "defaultLimit": 10,
    "maxLimit": 50
  }
}
```

### 10.2 Schema endpoint

For debugging and frontend integration, expose a read-only endpoint such as:

```http
GET /api/products/search/schema
```

It returns only semantic capabilities.

This endpoint can also be used by Jeff/Jev integration code instead of duplicating filter knowledge in prompts or configuration.

### 10.3 Jeff/Jev rule

Jeff/Jev may select only fields and operators present in this schema.

If a concept cannot be represented by the schema, it must not invent a query.

Example:

```text
User: "Quiero productos baratos"
```

If the domain has no configured meaning for `barato`, valid behaviors are:

1. omit the unsupported condition;
2. request clarification at the conversational layer;
3. apply an explicit configured domain rule.

It must never invent `price < 50000` without a configured rule or evidence from the message.

---

## 11. JPA field resolution

### 11.1 PathResolver

A field definition resolves a semantic field into a JPA `Path<?>`.

Conceptually:

```java
@FunctionalInterface
public interface PathResolver<T> {
    Path<?> resolve(Root<T> root, JoinRegistry joins);
}
```

`PathResolver` is infrastructure/JPA code. It does not belong in a pure domain model.

### 11.2 Product-root example

If the root entity is `ProductJpaEntity`:

```text
category    → root.join("category").get("name")
productName → root.get("name")
color       → root.join("variants").get("color")
size        → root.join("variants").get("size")
price       → root.join("variants").get("price")
stock       → root.join("variants").get("stock")
```

### 11.3 Variant-root example

The reusable engine must also support a context whose root is `ProductVariantJpaEntity`:

```text
category    → root.join("product").join("category").get("name")
productName → root.join("product").get("name")
color       → root.get("color")
size        → root.get("size")
price       → root.get("price")
stock       → root.get("stock")
```

Changing the root entity changes registry configuration, not the generic Criteria engine.

---

## 12. JoinRegistry / JoinResolver

### 12.1 Responsibility

A `JoinRegistry` exists per query execution and caches joins by logical path.

Example:

```text
"category"         → Join<Product, Category>
"variants"         → Join<Product, ProductVariant>
"product"          → Join<Variant, Product>
"product.category" → Join<Product, Category>
```

### 12.2 Reuse

If these fields are requested:

```text
color
size
price
stock
```

and all use `variants`, they must reuse the same `variants` join.

Conceptually:

```java
class JoinRegistry {
    private final Root<?> root;
    private final Map<String, From<?, ?>> joins = new HashMap<>();

    <X, Y> Join<X, Y> join(String path) {
        // resolve segments and reuse previously created joins
    }
}
```

### 12.3 Why reuse matters semantically

Join reuse is not only a performance optimization.

For a query:

```text
color = BLACK
size = M
```

reusing the same `variants` join means both conditions apply to the **same joined variant row**.

Creating independent joins could allow a product with:

```text
Variant A: BLACK / L
Variant B: WHITE / M
```

to match incorrectly because one join satisfies color and another satisfies size.

Therefore the join registry is part of query correctness.

### 12.4 Join type

Default to `INNER JOIN` when a filter requires a related row.

Use `LEFT JOIN` only when the field semantics explicitly require preserving roots with no relation.

Do not let the client choose join type.

---

## 13. Extending HibernateCriteriaConverter

### 13.1 Backward compatibility

Existing direct-field consumers should continue to work.

A safe incremental approach is:

```text
if a SearchFieldRegistry is configured for this search context
    resolve semantic field through registry
else
    use the existing direct root.get(field) behavior
```

Alternatively, repositories that need semantic relations can use an overloaded converter method that accepts the registry.

Example conceptual API:

```java
criteriaConverter.convert(
    criteria,
    ProductJpaEntity.class,
    productSearchFieldRegistry
);
```

and:

```java
criteriaConverter.convertToCount(
    criteria,
    ProductJpaEntity.class,
    productSearchFieldRegistry
);
```

Avoid rewriting unrelated callers.

### 13.2 Predicate creation

The existing converter should remain responsible for translating canonical `FilterOperator` values into predicates.

Do not create a second `PredicateFactory` unless extracting the existing transformer logic demonstrably simplifies the converter.

### 13.3 Value conversion

The current converter already has explicit conversion behavior for several types.

For the current implementation, ensure support for the types actually used by the schema, especially:

```text
String / Enum-like strings
Integer
Long
BigDecimal
Boolean
Date/Timestamp if needed
```

If prices use `BigDecimal`, add explicit conversion and integration tests.

---

## 14. Distinct, count and collection joins

### 14.1 Product as root

With:

```text
Product 1 --- N ProductVariant
```

joining variants can duplicate product rows.

The query layer must know when `distinct` is required.

### 14.2 Result query

When a resolved field traverses a to-many relationship:

```text
query.distinct(true)
```

or the equivalent existing `convertDistinct(...)` path must be used.

### 14.3 Count query

The count must use distinct root identity when a to-many join participates:

```text
countDistinct(root)
```

or the existing distinct-count implementation.

A plain `count(root)` can overcount products after a collection join.

### 14.4 Registry metadata

The registry should allow the converter to derive whether any active filter/order resolver requires distinct semantics.

Do not blindly enable `distinct` for every query unless measurements show that the simplicity is worth the cost.

---

## 15. Ordering over relationships

Ordering by a root or to-one field is straightforward.

Ordering a `Product` root by a to-many field such as `variants.price` is **not** automatically well-defined because one product can have multiple prices.

The schema must not mark such a field sortable until its semantics are explicit.

Possible future strategies:

```text
MIN(variant.price)  → cheapest matching variant
MAX(variant.price)  → most expensive matching variant
selected/matched variant projection
variant-root search instead of product-root search
```

For the search contract, choose one of these options explicitly:

- **Option A — simplest:** do not allow ordering products by collection-valued fields yet;
- **Option B — variant-root search:** use `ProductVariant` as the search root when price/size/color/stock define the returned item;
- **Option C — explicit aggregate:** implement and test `MIN`/`MAX` ordering.

The generic engine must not guess this behavior.

---

## 16. Root entity decision

The reusable component must not hardcode a root entity.

The root is chosen by the result granularity of the search context.

### Product root

Use when the API returns one logical product and variants are supporting data.

Pros:

- natural product-level response;
- category and product fields are direct/to-one.

Costs:

- collection joins can duplicate rows;
- distinct count required;
- ordering by variant attributes needs explicit semantics.

### ProductVariant root

Use when each sellable/searchable row is a concrete variant.

Pros:

- color, size, price and stock are direct fields;
- price ordering is deterministic;
- fewer to-many join issues.

Costs:

- multiple variants of the same product are separate results;
- product-level grouping becomes a response concern.

### Root model decision

The current API returns products as results and uses variant fields only as controlled filters and product details. Keep `Product` as the query root; variants are not separate search-result rows.

---

## 17. Validation pipeline

Validation occurs before executing JPA.

### 17.1 Request shape

Validate:

- `filters` is not `null`; normalize missing filters to empty when compatible with the public contract;
- no null filter elements;
- `field` is non-blank;
- `operator` is non-blank and recognized;
- required values are present;
- order direction is valid;
- `offset >= 0`;
- `limit >= 0` and `limit <= configuredMax`.

### 17.2 Semantic field validation

```text
filter.field ∈ SearchFieldRegistry
```

### 17.3 Operator validation

```text
filter.operator ∈ field.allowedOperators
```

A known global operator may still be invalid for a particular field.

Example:

```text
CONTAINS on price → invalid
> on category     → invalid
```

### 17.4 Type validation/conversion

Validate or convert values to the declared Java type before predicate creation.

Examples:

```text
price → BigDecimal
stock → Integer
size  → String / enum-compatible token
```

### 17.5 Finite values

For finite fields:

```text
value ∈ field.allowedValues
```

For `IN`, validate every member.

### 17.6 Ordering

Only fields declared `sortable = true` may be ordered.

### 17.7 Defensive limits

Recommended configurable guardrails for this API:

```text
max filters        = 20
max limit          = 50
max IN values      = 50
max text value len = project-defined bounded value
```

These are implementation defaults, not immutable domain rules.

---

## 18. Natural-language interpretation

### 18.1 Boundary

The interpretation layer solves only:

```text
Natural language + SemanticSearchSchema
                 ↓
       SearchInterpretation
```

Then deterministic code:

```text
SearchInterpretation
        ↓ validate
        ↓ adapt
      Criteria
```

### 18.2 Decision-engine boundary

An external AI/decision provider is a legitimate interface boundary.

Conceptually:

```java
public interface SearchDecisionEngine {
    SearchInterpretation interpret(
        String message,
        SemanticSearchSchema schema
    );
}
```

Concrete implementation:

```text
JevSearchDecisionEngine
```

A mock implementation should exist for tests.

Do not create extra `Port`, `Impl` or `Adapter` suffixes unless they clarify a real boundary.

### 18.3 Prompt/question construction

The prompt/question builder is generated from the semantic schema.

Do not manually maintain one question set per project.

Finite example:

```text
category → REMERAS | BUZOS | CAMPERAS | NONE
color    → BLACK | WHITE | BLUE | NONE
size     → S | M | L | XL | NONE
```

Open numeric example:

```text
"menos de 50 lucas"
      ↓
field    = price
operator = <=
value    = 50000
```

### 18.4 AI output rules

AI output may contain only semantic concepts.

Allowed:

```json
{
  "field": "price",
  "operator": "<=",
  "value": 50000
}
```

Forbidden:

```text
variants.price
product.category.name
SELECT ...
JOIN ...
JPQL fragments
Criteria paths
entity class names
column names
```

### 18.5 Deterministic extraction first

When a value can be extracted deterministically, prefer deterministic code.

Examples:

- numeric limits;
- explicit quantities;
- known finite values;
- obvious sort directions.

Use the model for interpretation where it adds value, not for logic already represented safely in code.

---

## 19. End-to-end flows

### 19.1 Structured frontend

```text
React filters
    ↓
Criteria HTTP request
    ↓
validation against SearchFieldRegistry
    ↓
CriteriaAdapter
    ↓
Criteria
    ↓
repository
    ↓
HibernateCriteriaConverter + registry + joins
    ↓
Database
```

### 19.2 Natural language

```text
WhatsApp / API message
    ↓
Search service
    ↓
SemanticSearchSchema from registry
    ↓
JevSearchDecisionEngine
    ↓
SearchInterpretation
    ↓
validation
    ↓
Criteria adaptation
    ↓
repository
    ↓
HibernateCriteriaConverter + registry + joins
    ↓
Database
```

From `Criteria` downward, both flows are identical.

---

## 20. HTTP endpoints

### 20.1 Inspect capabilities

```http
GET /api/products/search/schema
```

Purpose:

- make capabilities inspectable;
- support frontend filter generation if desired;
- supply Jeff/Jev semantic context;
- simplify debugging and contract tests.

### 20.2 Interpret only

```http
POST /api/products/search/interpret
Content-Type: application/json
```

Request:

```json
{
  "message": "Quiero remeras negras talle M por menos de 50 mil"
}
```

Response example:

```json
{
  "filters": [
    {"field": "category", "operator": "=", "value": "REMERAS"},
    {"field": "color", "operator": "=", "value": "BLACK"},
    {"field": "size", "operator": "=", "value": "M"},
    {"field": "price", "operator": "<=", "value": 50000}
  ],
  "order": null,
  "limit": 10,
  "offset": 0
}
```

This endpoint allows testing interpretation without executing a database query.

### 20.3 Interpret and execute

```http
POST /api/products/search
```

Request:

```json
{
  "message": "Quiero remeras negras talle M por menos de 50 mil"
}
```

Flow:

```text
message
→ interpretation
→ validation
→ Criteria
→ repository
→ database
→ response
```

### 20.4 Existing Criteria endpoint

Do not remove or break existing structured Criteria endpoints.

Where possible, add registry-backed validation/resolution behind them without changing the public contract.

### 20.5 Humanize structured results independently

```http
POST /api/product-search-responses/humanize
Content-Type: application/json
```

Request contains the original `message` and a `results` object matching the structured response of `/api/products/search`. This bounded context does not interpret the message or query the catalog. It returns `outcome` (`RESULTS` or `NO_RESULTS`) and a Spanish `reply`, grounded only in the submitted product names, categories, variants, prices and stock.

### 20.6 Coordinate the customer search flow

```http
POST /api/products/search/answer
Content-Type: application/json
```

Request: `{ "message": "..." }`.

The application coordinator runs interpretation, validated catalog search and response humanization. It returns `outcome`, `reply` and the full structured `results`. Outcomes are `RESULTS`, `NO_RESULTS` and `NEEDS_CLARIFICATION`. Provider failures keep the existing error contract; an unsafe or unsupported interpretation produces `NEEDS_CLARIFICATION` without executing a product query.

These two routes make response humanization testable with supplied facts and expose one end-to-end customer flow, while the existing `/interpret` and `/search` routes remain independently usable.

---

## 21. Package design adapted to backend guidelines

The codebase is domain-first.

Do not create a new root package such as `controller`, `service`, `repository`, `dto` or `util`.

### 21.1 Generic/transversal Criteria infrastructure

Only components that are truly reusable across bounded contexts belong in `shared`.

Example:

```text
shared/
├── domain/
│   └── criteria/
│       ├── Criteria.java
│       ├── Filter.java
│       ├── Filters.java
│       ├── FilterOperator.java
│       └── Order.java
└── infrastructure/
    └── criteria/
        ├── CriteriaAdapter.java
        ├── HibernateCriteriaConverter.java
        ├── JoinRegistry.java
        └── ...
```

Do not move business-specific product schema definitions into `shared`.

### 21.2 Product/catalog bounded context example

Use the real bounded-context name from the repository. Conceptually:

```text
products/
├── application/
│   └── ProductSearchService.java
├── domain/
│   ├── Product.java
│   └── SearchDecisionEngine.java       # only if this boundary is owned here
└── infrastructure/
    ├── http/
    │   ├── ProductSearchController.java
    │   ├── ProductCriteriaRequest.java
    │   └── ProductSearchResponse.java
    ├── search/
    │   ├── ProductSearchFieldRegistry.java
    │   └── ProductSearchSchemaMapper.java  # only if mapping is non-trivial
    ├── ai/
    │   └── jev/
    │       ├── JevSearchDecisionEngine.java
    │       └── SearchPromptBuilder.java
    └── repository/
        └── postgres/
            ├── ProductJpaRepository.java
            ├── ProductBaseJpaRepository.java
            └── ProductJpaEntity.java
```

### 21.3 Naming rules

Prefer:

```text
ProductSearchService
SearchDecisionEngine
JevSearchDecisionEngine
ProductSearchFieldRegistry
JoinRegistry
HibernateCriteriaConverter
```

Avoid ceremonial names such as:

```text
ExecuteProductDynamicSearchUseCase
SearchDecisionEnginePort
JevSearchDecisionEngineAdapterImpl
GenericSearchManagerHelper
```

unless the extra terminology communicates a real architectural boundary.

### 21.4 Interfaces

Use interfaces for real boundaries, for example:

- decision/AI provider;
- repository contract when domain models are isolated from persistence;
- dynamic source of finite schema values if multiple sources/providers are expected.

Do not add interfaces for simple helpers with one obvious implementation just for layering aesthetics.

### 21.5 Search response bounded contexts

The current implementation keeps the catalog search and response presentation separate:

```text
products/
  application/ProductSearchService
productsearchresponses/
  application/ProductSearchResponseService
  domain/ProductSearchResponseHumanizer
productsearch/
  application/ProductSearchConversationService
```

`ProductSearchConversationService` is the application-level coordinator. It calls the product search use case, maps its result into response facts, and delegates wording to `ProductSearchResponseService`. The response context accepts a structured facts model of its own instead of depending on the product context's domain model. HTTP controllers stay thin and expose each stage separately.

Humanization currently uses deterministic Spanish templates. This keeps the reply grounded in the exact structured data and requires no additional provider credentials. Adding model-generated prose is a separate provider decision; the model must not invent prices, stock, currency, variants or other catalog facts. Since the catalog has no currency field, replies and structured results must not attach a currency symbol or name.

---

## 22. Repository integration

Keep the existing domain-to-JPA adapter pattern.

Example:

```java
@Repository
@RequiredArgsConstructor
class ProductJpaRepository implements ProductRepository {

    private final ProductBaseJpaRepository jpa;
    private final HibernateCriteriaConverter<ProductJpaEntity> criteriaConverter;
    private final ProductSearchFieldRegistry searchFields;

    @Override
    public List<Product> matching(Criteria criteria) {
        return criteriaConverter
            .convert(criteria, ProductJpaEntity.class, searchFields)
            .getResultList()
            .stream()
            .map(this::toModel)
            .toList();
    }

    @Override
    public long count(Criteria criteria) {
        return criteriaConverter
            .convertToCount(criteria, ProductJpaEntity.class, searchFields)
            .getSingleResult();
    }

    private Product toModel(ProductJpaEntity entity) {
        // mapping
    }
}
```

The exact method signatures may differ after inspecting the existing repository. The important constraint is that application/domain code must not receive `Root`, `Join`, `CriteriaBuilder` or JPA entities.

---

## 23. Example product registry

Illustrative only; adapt names to actual entities.

```java
@Component
class ProductSearchFieldRegistry {

    private final Map<String, SearchFieldDefinition<ProductJpaEntity>> fields;

    ProductSearchFieldRegistry() {
        fields = Map.of(
            "productName",
            field(
                "productName",
                String.class,
                Set.of(CONTAINS, EQUALS),
                true,
                (root, joins) -> root.get("name"),
                false
            ),
            "category",
            field(
                "category",
                String.class,
                Set.of(EQUALS, IN),
                false,
                (root, joins) -> joins.join(root, "category").get("name"),
                false
            ),
            "color",
            field(
                "color",
                String.class,
                Set.of(EQUALS, IN),
                false,
                (root, joins) -> joins.join(root, "variants").get("color"),
                true
            ),
            "size",
            field(
                "size",
                String.class,
                Set.of(EQUALS, IN),
                false,
                (root, joins) -> joins.join(root, "variants").get("size"),
                true
            ),
            "price",
            field(
                "price",
                BigDecimal.class,
                Set.of(EQUALS, LESS_THAN, LESS_OR_EQUAL, GREATER_THAN, GREATER_OR_EQUAL),
                false, // until collection-order semantics are defined
                (root, joins) -> joins.join(root, "variants").get("price"),
                true
            )
        );
    }
}
```

Do not copy this blindly. Use the actual operator names and existing coding conventions.

---

## 24. Security and correctness rules

The following are mandatory:

1. Every public field must be registered.
2. Every operator must be recognized globally and allowed by the field.
3. No public input is passed directly to `root.get(...)` unless it was resolved through the trusted registry/direct-field allowlist.
4. No AI output is executed without the same validation used for structured requests.
5. AI cannot select table names, entity names, columns, joins or functions.
6. Pagination is bounded.
7. Count queries use the same filters as result queries.
8. Collection joins use correct distinct semantics.
9. Unknown fields/operators fail deterministically; they do not fall through to arbitrary JPA resolution.
10. Provider errors are converted to stable application errors and are not leaked raw to clients.

---

## 25. Error behavior

Use the backend's existing error contract.

Examples of stable error codes for this feature may include:

```text
SEARCH_FIELD_NOT_ALLOWED
SEARCH_OPERATOR_NOT_ALLOWED
SEARCH_VALUE_INVALID
SEARCH_ORDER_NOT_ALLOWED
SEARCH_LIMIT_INVALID
SEARCH_INTERPRETATION_FAILED
```

Do not expose raw Jev/provider errors, Hibernate exceptions or SQL errors to the frontend.

Reuse the existing global exception mechanism where possible.

---

## 26. Testing strategy

### 26.1 Search registry tests

Verify:

- every configured semantic name is unique;
- invalid names are rejected;
- allowed operators match the field type;
- finite values are enforced;
- sortable metadata matches actual resolver capability.

### 26.2 Criteria validation tests

Cover:

- unknown field;
- unknown operator;
- known but disallowed operator;
- invalid value type;
- invalid finite value;
- null filter;
- negative offset;
- excessive limit;
- invalid order field/direction.

### 26.3 JoinRegistry tests

Verify:

- same path returns the same join instance within one query;
- nested joins are reused;
- different paths remain distinct;
- join type is deterministic.

### 26.4 Repository integration tests

Use a real test database when practical for custom Criteria/joins.

Minimum scenarios:

#### Direct field

```text
productName CONTAINS "air"
```

#### To-one join

```text
category = "SHOES"
```

#### To-many join

```text
color = "BLACK"
```

#### Same-related-row correctness

Dataset:

```text
Product A
  Variant 1: BLACK / L
  Variant 2: WHITE / M

Product B
  Variant 1: BLACK / M
```

Query:

```text
color = BLACK
size = M
```

Expected:

```text
Product B only
```

This test protects join reuse semantics.

#### Distinct result

A product with several matching variants must appear once when product is the root.

#### Distinct count

`total` must count products, not joined rows.

#### Pagination

Result page and total must be consistent.

### 26.5 Natural-language tests

The application tests should not require a real AI provider.

Use a mock `SearchDecisionEngine` and test:

```text
message
→ interpretation
→ validation
→ Criteria
→ repository invocation
```

Test provider parsing separately with valid and invalid structured responses.

### 26.6 Real-provider smoke tests

If Jev/provider calls have cost or require credentials, keep them opt-in and outside normal `mvn test` execution.

---

## 27. Acceptance scenarios

### Scenario 1 — category only

Input:

```text
"Quiero remeras"
```

Expected semantic result:

```text
category = REMERAS
```

### Scenario 2 — relation filter without category

Input:

```text
"Mostrame todo lo negro"
```

Expected:

```text
color = BLACK
```

Category must not be invented.

### Scenario 3 — several filters

Input:

```text
"Algo negro talle M por menos de 50 lucas"
```

Expected:

```text
color = BLACK
size = M
price <= 50000
```

### Scenario 4 — sorting

Input:

```text
"Mostrame las más baratas primero"
```

Expected only if `price` has an explicitly supported ordering strategy for the selected root.

Otherwise the interpretation may be valid but execution must reject unsupported ordering clearly.

### Scenario 5 — pagination

Input:

```text
"Mostrame cinco"
```

Expected:

```text
limit = 5
```

### Scenario 6 — ambiguous domain concept

Input:

```text
"Quiero productos baratos"
```

Expected:

- do not invent a threshold;
- use a configured domain rule or clarification strategy if one exists.

---

## 28. Implementation plan

### Phase 0 — inspect and protect existing behavior

Before coding:

- locate current Criteria classes and converter registrations;
- locate repository consumers;
- identify public endpoints already using Criteria;
- identify actual Product / Category / Variant JPA mappings;
- identify actual result granularity;
- run existing tests;
- add characterization tests where current behavior is not protected.

Do not rename or reorganize unrelated code.

### Phase 1 — harden existing Criteria validation

Implement only the missing validations required by the feature:

- recognized operators;
- valid direction;
- non-negative limit/offset;
- max limit;
- null-filter safety;
- `<=` / `>=` if required by the search contract;
- `BigDecimal` conversion if price uses it.

Preserve existing contracts.

### Phase 2 — add controlled field resolution

Implement:

```text
SearchFieldDefinition
SearchFieldRegistry
PathResolver
JoinRegistry
```

Extend `HibernateCriteriaConverter` through a backward-compatible overload or resolver strategy.

Test manually created `Criteria`; no AI yet.

### Phase 3 — configure Product search

Create the product-specific registry with fields such as:

```text
category
productName
color
size
price
stock
```

Add integration tests for joins, same-variant semantics, distinct results and counts.

### Phase 4 — expose semantic schema

Create:

```http
GET /api/products/search/schema
```

Generate its response from the product field registry.

At this point React or any external consumer can discover valid filters without knowing JPA.

### Phase 5 — add natural-language interpretation

Implement the real external boundary:

```text
SearchDecisionEngine
JevSearchDecisionEngine
SearchPromptBuilder
```

Generate provider instructions/questions from the semantic schema.

Validate the provider response before adapting it to `Criteria`.

### Phase 6 — add interpretation endpoint

Create:

```http
POST /api/products/search/interpret
```

This isolates AI interpretation from catalog queries.

### Phase 7 — end-to-end search

Create or extend:

```http
POST /api/products/search
```

Flow:

```text
message → interpretation → Criteria → JPA → results
```

### Phase 8 — channels

Only after the backend search flow is stable:

```text
WhatsApp webhook
Email
other channels
```

These channels should delegate to the same application service.

---

## 29. Definition of done

- [ ] Existing Criteria endpoints continue working.
- [ ] No parallel JPA Specification engine was introduced unnecessarily.
- [ ] Product search accepts semantic field names.
- [ ] Semantic names are validated against one registry.
- [ ] Operators are validated per field.
- [ ] Value types are validated/converted safely.
- [ ] Filters can traverse JPA relationships.
- [ ] Joins are reused within one query.
- [ ] Same-variant filtering behavior is protected by a test.
- [ ] To-many joins do not duplicate product results.
- [ ] Count queries return distinct product totals when required.
- [ ] Pagination is bounded and deterministic.
- [ ] Ordering is supported only where semantics are explicit.
- [ ] Search schema is exposed without JPA details.
- [ ] Jeff/Jev receives only semantic capabilities.
- [ ] Jeff/Jev cannot emit executable SQL/JPQL/JPA paths.
- [ ] AI output is validated before Criteria construction.
- [ ] Natural-language interpretation can be tested with a mocked decision engine.
- [ ] A second schema/context can be added without modifying the generic Criteria core.
- [ ] New code follows the repository's domain-first backend guidelines.
- [ ] No unrelated mass refactor was introduced.

---

## 30. Future evolution

### Phase 2 — conversational state

Possible later additions:

- previous filters retained between turns;
- structured conversation state;
- reference to previous result sets;
- incremental constraints;
- explicit reset/replace semantics.

Example:

```text
User: "Quiero remeras negras"
→ category = REMERAS
→ color = BLACK

User: "Solo las de menos de 40 mil"
→ retain category = REMERAS
→ retain color = BLACK
→ add price <= 40000
```

### Phase 3 — actions

Only once search is stable, consider actions such as:

```text
VIEW_PRODUCT
ADD_TO_CART
COMPARE_PRODUCTS
CHECKOUT
```

The current `ProductSearchConversationService` coordinates one search turn. Extend it only when multi-turn state or actions need their own explicit contracts.

### Dynamic finite values

Static lists are enough for the current product schema.

Later, fields such as category/brand may obtain values from a provider backed by the database, cache or another service. If this is implemented, preserve the same semantic schema projection and apply limits/caching.

### Complex boolean groups

The current Criteria flow primarily combines filters with AND.

Nested boolean expression groups such as:

```text
(color = BLACK OR color = BLUE)
AND
(size = M OR size = L)
```

should be treated as a separate contract evolution, not hidden inside the first join implementation.

---

## 31. Decisions Codex must not make autonomously

When implementing this specification, Codex must inspect the repository and avoid guessing the following:

1. actual bounded-context/package name;
2. actual JPA entity/root used by the existing endpoint;
3. exact entity relationship property names;
4. exact current `FilterOperator` enum names/values;
5. exact `HibernateCriteriaConverter` public API;
6. whether price is `BigDecimal`, integer or another type;
7. whether search returns products or variants;
8. whether an existing endpoint contract can be extended without breaking consumers;
9. whether existing distinct helpers already satisfy the join use case;
10. whether Jev already has a provider/client abstraction in the repository.

Codex should adapt the design to the existing implementation and keep diffs minimal.

---

## 32. Recommended Codex execution instructions

Give Codex an implementation prompt similar to:

```text
Implement the Dynamic & Natural Language Search Engine using
`dynamic-search-engine-consolidated-specification.md` as the primary
functional/architectural specification.

Use `backend-architecture-guidelines.md` as the authoritative repository
style and package/naming guideline.

Use `criteria-jpa-spring-boot.md` as implementation reference for the current
Criteria model, existing HibernateCriteriaConverter behavior, repository
patterns and known validation gaps.

Before changing code, inspect the repository and summarize:
1. the current Criteria flow;
2. the Product/Category/Variant JPA mappings;
3. the existing search endpoints;
4. the minimal files that need modification;
5. any conflict between the specification and actual code.

Then implement incrementally. Do not introduce a parallel
JpaSpecificationExecutor query engine, do not mass-refactor unrelated code,
and do not change public contracts unless required and explicitly documented.
Run the relevant tests after each implementation slice.
```

---

## 33. How to use the source documents with Codex

### Recommended context pack

Pass these three documents:

1. **`dynamic-search-engine-consolidated-specification.md`** — primary source of truth for this feature.
2. **`backend-architecture-guidelines.md`** — authoritative style, package, naming and architectural-boundary rules for the repository.
3. **`criteria-jpa-spring-boot.md`** — reference describing the current Criteria/JPA implementation and behavior that should be reused.

### Optional historical document

`natural-language-search-adapter-specification.md` should normally **not** be passed as an equal fourth source of truth.

It is useful as historical/product rationale, but it contains earlier design proposals such as a separate `GenericSpecificationBuilder` / Spring Data `Specification` path that this consolidated specification intentionally adapts to the existing `Criteria` + `HibernateCriteriaConverter` implementation.

If it is supplied, instruct Codex explicitly:

```text
The natural-language-search-adapter-specification.md document is historical
context only. If it conflicts with dynamic-search-engine-consolidated-specification.md,
the consolidated specification wins.
```

### Precedence order

When documents conflict, use this order:

```text
1. Actual repository behavior and public contracts
   (unless the issue explicitly requests changing them)

2. dynamic-search-engine-consolidated-specification.md
   (feature architecture and target behavior)

3. backend-architecture-guidelines.md
   (code organization, naming and dependency boundaries)

4. criteria-jpa-spring-boot.md
   (reference for current Criteria behavior)

5. natural-language-search-adapter-specification.md
   (historical rationale / optional context)
```

The repository itself must still be inspected before implementation because documentation can become stale.

---

## 34. Final architecture summary

```text
HUMAN MESSAGE / STRUCTURED UI
            │
            ├──────────────────────────┐
            │                          │
            ▼                          ▼
   SEARCH INTERPRETER          CRITERIA HTTP ADAPTER
   (Jev behind boundary)               │
            │                          │
            ▼                          │
   SEMANTIC INTERPRETATION             │
            │                          │
            └────────────┬─────────────┘
                         ▼
                      CRITERIA
                         │
                         ▼
               APPLICATION SERVICE
                         │
                         ▼
                 REPOSITORY CONTRACT
                         │
                         ▼
                   JPA ADAPTER
                         │
                         ▼
            HIBERNATE CRITERIA CONVERTER
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
     SEARCH FIELD REGISTRY      JOIN REGISTRY
              │                     │
              └──────────┬──────────┘
                         ▼
               CRITERIA BUILDER / JPA
                         │
                         ▼
                      DATABASE
```

The reusable backend capability is:

```text
Semantic field + operator + value
            ↓
Trusted field registry
            ↓
Controlled JPA path / joins
            ↓
Existing Criteria execution
```

The reusable AI capability is:

```text
Message + SemanticSearchSchema
            ↓
SearchInterpretation
            ↓
Validation
            ↓
Criteria
```

The AI does not own query construction. The database schema does not need to be redesigned for the search feature. Existing relational models remain valid; the new layer navigates them in a controlled, testable and reusable way.

The single-turn customer response flow is:

```text
POST /api/products/search/answer
            ↓
ProductSearchConversationService
            ├── ProductSearchService: message → Criteria → database results
            └── ProductSearchResponseService: structured facts → customer reply
            ↓
outcome + reply + structured results
```

`POST /api/product-search-responses/humanize` exposes only the second step for isolated use. Its current templates are deterministic and do not add a currency absent from the catalog data.
