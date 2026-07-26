# ADR-0002: Define a Shared Principal Model and Layered Security Support

- **Status:** Accepted
- **Date:** 2026-07-26
- **Decision scope:** Existing FlyTaxi Java/Spring architecture
- **Implementation status:** Planned. This ADR records the target boundary; this pull request does not implement it.
- **Lineage:** This decision refines FlyTaxi hardening baseline v6 DEC-02. DEC-02 already split security support into `security-support-core` and `security-support-session`, while its proposed shape placed `RequestPrincipal`, `Role`, and `TokenClaims` under `internal-common` and explicitly marked that placement as transitional if used. As of 2026-07-26, ADR-0002 preserves the core/session split, assigns the shared principal models directly to `security-support-core` from the start, and adds explicit consumer-dependency, package-naming, error-to-HTTP mapping, two-module ceiling, and test-fixture rules. The v6 baseline otherwise remains authoritative; this ADR is its accepted, more specific refinement of the security-support boundary.

## Context

FlyTaxi currently has 11 deployable Spring Boot applications and one shared
`internal-common` library.

Authentication and session concerns are not yet represented by one shared model
and one stable dependency boundary:

- passenger and driver edges contain separate JWT, Redis-session, interceptor,
  and token-handling code that can drift independently;
- `api-boss` does not yet have the planned administrator identity and session
  boundary;
- `service-sse-push` needs user-session authentication for connection
  establishment and service identity for internal push calls;
- internal service controllers need caller-service authentication, but must not
  acquire a Redis session dependency merely to verify a short-lived service
  token;
- authentication failures, authorization failures, session failures, and HTTP
  response writing are not yet separated by layer;
- principal and token-claim models do not yet have a dedicated home;
- `internal-common` already carries broad cross-service DTO and utility
  responsibilities and is scheduled for later slimming under ARCH-01.

The current `service-verificationCode` module remains an independently
deployable HTTP service on port 8082. It exposes `GET /numberCode/{size}` and is
called remotely by both passenger and driver edges. While that service remains
independent, it is part of the internal service-authentication boundary.

The architecture therefore needs a shared principal vocabulary without turning
every consumer into a Redis-aware web application or creating an open-ended
family of security modules.

## Decision

### Canonical terminology

The principal model is **shared**.

Security support is **layered**.

The canonical terms are:

```text
shared principal model
layered security support
security-support-core
security-support-session
```

The phrase `layered principal` is not used because the principal itself is not
divided into web, Redis, or service variants. One framework-neutral principal
vocabulary is reused across the system.

### Module topology

The current architecture will contain exactly two security-support runtime
modules:

```text
security-support-session
        |
        v
security-support-core
```

The dependency is one-way:

- `security-support-session` depends on `security-support-core`;
- `security-support-core` must never depend on `security-support-session`.

The Maven module directories and artifact IDs will be:

```text
security-support-core
security-support-session
```

They will enter the root reactor through two separate skeleton pull requests:
core first in Batch 0.6, then session in Batch 0.7.

### Shared model ownership

The following pure models belong directly to `security-support-core`:

```text
RequestPrincipal
Role
TokenClaims
```

Related framework-neutral security types, such as token type, verification
result, failure category, service identity, or validation requirement, also
belong in `security-support-core`.

These models will not be placed in `internal-common`, even temporarily.

This avoids creating a migration debt that ARCH-01 would later need to remove
and prevents a general shared DTO module from becoming the owner of
security-boundary semantics.

### Framework-neutral `RequestPrincipal`

`RequestPrincipal` represents the authenticated subject available to application
code after verification.

Its exact fields will be finalized in implementation pull requests, but it may
carry typed, sanitized facts such as:

```text
subjectId
role
token type
session or token identifier
issuer
```

Despite the word `Request` in its name, `RequestPrincipal` must remain
framework-neutral.

It must not contain, reference, wrap, or retain:

```text
HttpServletRequest
HttpServletResponse
Servlet API types
Spring MVC types
Spring Security types
Redis clients or Redis session objects
HTTP headers
HTTP status codes
request-scoped framework contexts
ThreadLocal-backed request state
```

It is a pure authenticated-subject snapshot, not a reference to the transport
request that produced it.

`TokenClaims` must likewise be a typed, sanitized model rather than a
vendor-specific JWT object or an unbounded map of raw claims.

### `security-support-core` responsibilities

`security-support-core` owns reusable, framework-neutral security semantics:

- `RequestPrincipal`, `Role`, `TokenClaims`, and related pure models;
- access, refresh, and service-token type semantics;
- signature, expiry, issuer, audience, role, and required-claim verification;
- framework-neutral token verification contracts and results;
- service-token verification and service-principal creation;
- authentication-versus-authorization failure classification;
- the `SESSION_VALIDATION_REQUIRED` handoff marker;
- framework-neutral security exceptions or result types;
- shared test fixtures exposed only through a test artifact.

`security-support-core` may use a framework-neutral JWT library, but it must not
depend on:

```text
Spring Web
Servlet APIs
Spring Security web infrastructure
Redis
StringRedisTemplate
HTTP status types
HTTP response writers
application controllers
security-support-session
```

It must not depend on `internal-common` merely to reuse response DTOs or
constants.

### The `SESSION_VALIDATION_REQUIRED` handoff

A cryptographically and semantically valid user access token may still require
session-store validation for exact-token matching, revocation, logout, or
device/session policy.

Core must not perform that Redis lookup.

Instead, core returns a framework-neutral `SESSION_VALIDATION_REQUIRED`
requirement as part of the verification result.

This marker is an internal layer-handoff requirement, not an HTTP error and not
evidence that the token is invalid.

`security-support-session` consumes the marker, performs the required session
validation, and only then creates the final `RequestPrincipal`.

Internal service tokens are self-contained, short-lived service credentials and
do not require user-session Redis validation.

### Framework-neutral failure semantics

Core owns stable security meanings, not HTTP responses.

Authentication failures include semantics such as:

```text
MALFORMED_TOKEN
INVALID_SIGNATURE
TOKEN_EXPIRED
INVALID_TOKEN_TYPE
INVALID_ISSUER
INVALID_AUDIENCE
MISSING_REQUIRED_CLAIM
```

Authorization failures include semantics such as:

```text
ROLE_NOT_ALLOWED
```

The exact Java type names may be refined during implementation, but one stable
classification must be shared by all consumers.

Core must distinguish:

- authentication failure: the caller has not established a valid identity;
- authorization failure: a valid identity exists, but its role or permission
  is not allowed.

Core must not import or return `HttpStatus`, `ResponseEntity`, servlet responses,
or a web-specific JSON error body.

### `security-support-session` responsibilities

`security-support-session` owns user-session and HTTP adaptation:

- extracting `Authorization: Bearer <token>`;
- invoking core verification;
- satisfying `SESSION_VALIDATION_REQUIRED`;
- validating Redis-backed user sessions;
- exact-token, revocation, logout, and refresh-session support;
- resolving the authenticated `RequestPrincipal` for HTTP application code;
- reusable interceptor or argument-resolver support;
- mapping framework-neutral failures to HTTP responses;
- writing one stable security error body without exposing tokens or sensitive
  claims.

The initial implementation may use Spring MVC interceptors and resolvers.
This ADR does not require adoption of Spring Security.

Web, servlet, Redis, and resolver code may exist as packages inside
`security-support-session`; those packages are not separate Maven modules.

### HTTP status ownership

HTTP translation belongs only to `security-support-session`.

The target mapping is:

| Condition | Semantic owner | HTTP result |
|---|---|---:|
| Missing Bearer credential | session/web adapter | 401 |
| Malformed, invalid, expired, wrong-type, wrong-issuer, or wrong-audience token | core meaning, session translation | 401 |
| Missing or revoked user session, or exact-token mismatch | session | 401 |
| Valid authenticated principal with disallowed role or permission | core meaning, session translation | 403 |
| Session store unavailable or operational session dependency failure | session | 503 or another explicit 5xx operational result |
| `SESSION_VALIDATION_REQUIRED` | core-to-session handoff | No direct HTTP response |

A session infrastructure outage must not be misreported as invalid user
credentials.

The stable HTTP error body should carry a machine-readable code, message, and
trace or correlation identifier where available. It must not contain the raw
token, private claims, credentials, or full sensitive identity data.

### Consumer-to-layer mapping

| Consumer or boundary | Required security capability | Dependency decision |
|---|---|---|
| `api-passenger` | User access/refresh session and HTTP principal | Depend on `security-support-session`; declare `security-support-core` directly if importing core public types |
| `api-driver` | User access/refresh session and HTTP principal | Depend on `security-support-session`; declare `security-support-core` directly if importing core public types |
| `api-boss` | Administrator login, session, HTTP principal, and RBAC boundary | Depend on `security-support-session`; declare `security-support-core` directly if importing core public types |
| `service-sse-push` user connect | Authenticated passenger or driver session | Depend on `security-support-session` |
| `service-sse-push` internal push | Short-lived service identity | Depend directly on `security-support-core` |
| `service-order` internal endpoints | Short-lived service identity | Security dependency is `security-support-core` only |
| `service-price` internal endpoints | Short-lived service identity | Security dependency is `security-support-core` only |
| `service-driver-user` internal endpoints | Short-lived service identity | Security dependency is `security-support-core` only |
| `service-map` internal endpoints | Short-lived service identity | Security dependency is `security-support-core` only |
| `service-pay` internal endpoints | Short-lived service identity | Security dependency is `security-support-core` only |
| `service-passenger-user` internal endpoints | Short-lived service identity | Security dependency is `security-support-core` only |
| `service-verificationCode` internal endpoint | Short-lived service identity while the service remains independent | Security dependency is `security-support-core` only |

`service-pay` has a separate provider-callback boundary: an Alipay notification
is authenticated by provider-signature and provider-identity verification, not
by pretending the external provider is a FlyTaxi service principal.

The seven internal-service rows above mean that session support and its Redis
requirements must not be introduced merely to authenticate service-to-service
traffic. A service may independently use Redis for unrelated business logic;
the security dependency itself must not force a user-session Redis model.

If `service-verificationCode` is later absorbed under ARCH-03, its row will be
removed as part of that reviewed architectural change.

### Direct Maven dependency discipline

Maven dependencies must describe actual source usage.

- `security-support-session` declares a normal dependency on
  `security-support-core`.
- Any consumer that directly imports a core public type must declare
  `security-support-core` directly, even if session also provides it
  transitively.
- A module must not rely on an undeclared transitive dependency for
  `RequestPrincipal`, `Role`, `TokenClaims`, or core verification APIs.
- Internal services must not declare `security-support-session` for
  service-token verification.
- `security-support-core` must never declare a dependency on session.
- Test-fixture dependencies must remain test-scoped and must not enter runtime
  dependency graphs.

This rule supports later Maven dependency-analysis checks and prevents
`used-undeclared` or hidden coupling.

### Package naming

New modules start with clean, all-lowercase Java package names:

```text
com.george.securitysupport.core
com.george.securitysupport.session
```

Expected package families include:

```text
com.george.securitysupport.core.model
com.george.securitysupport.core.error
com.george.securitysupport.core.token
com.george.securitysupport.core.verification

com.george.securitysupport.session.session
com.george.securitysupport.session.redis
com.george.securitysupport.session.web
com.george.securitysupport.session.resolver
```

The legacy camel-case package style represented by `com.george.internalCommon`
must not be copied into new modules.

Package families may be adjusted within the two modules as implementation
clarifies responsibilities, but artifact IDs and the core/session dependency
direction remain fixed by this ADR.

### Two-module ceiling

The runtime module boundary is capped at:

```text
security-support-core
security-support-session
```

This phase must not create additional runtime modules such as:

```text
security-support-web
security-support-redis
security-support-jwt
security-support-servlet
security-support-feign
security-support-internal
```

Web, Redis, servlet, JWT, Feign, and internal-service adapters must first live as
packages or adapters inside the two accepted modules.

A third runtime module requires a new ADR supported by a concrete dependency or
ownership problem, not a preference for smaller folders.

### Shared test fixtures without a third module

`security-support-core` will attach a Maven test JAR with the standard `tests`
classifier.

That test artifact may contain reusable, non-production test utilities such as:

```text
principal builders
typed claim factories
test token builders
verification-result fixtures
```

`security-support-session` may consume the core test JAR only with test scope.

No `security-support-test` module will be created.

The test JAR must satisfy all of these constraints:

- it is produced from `security-support-core`;
- it is consumed only by tests;
- it does not enter any production compile or runtime classpath;
- production modules do not import test-fixture packages;
- reusable fixtures do not move into `internal-common`.

The existing CI sequence is expected to support this staged structure without a
workflow change:

- Batch 0.6 must verify that `mvn clean install -DskipTests` compiles the core
  test sources, produces and installs the attached `tests` classifier artifact,
  and keeps that artifact outside production runtime dependencies.
- Batch 0.7 must verify that session consumes the core `tests` artifact only
  with test scope and that it is absent from session's production compile and
  runtime dependency graphs.
- Root-level `mvn test` must execute the focused tests after each module enters
  the reactor.

These behaviours must be proven by build output, artifact inspection, and
dependency-tree checks rather than merely assumed.

### Batch 0.6 and Batch 0.7 implementation boundaries

#### Batch 0.6 — `security-support-core`

The core skeleton pull request will be permitted to modify only:

```text
pom.xml
security-support-core/**
```

Its scope is capped at:

```text
root-POM registration of security-support-core
one security-support-core runtime module
framework-neutral shared model and verification skeletons
framework-neutral failure semantics
one core-produced test-only classifier artifact
focused core and test-fixture tests
```

It must verify:

- the core module has no Redis, Servlet, Spring Web, Spring Security web, or
  session-module dependency;
- the attached `tests` classifier is generated and installed;
- the test artifact does not enter production compile or runtime classpaths;
- root-level build and tests remain green.

It must not:

- create `security-support-session`;
- register session in the root POM;
- migrate passenger, driver, boss, SSE, or internal-service authentication;
- add Redis or HTTP adaptation.

#### Batch 0.7 — `security-support-session`

The session skeleton pull request will be permitted to modify only:

```text
pom.xml
security-support-session/**
```

It assumes the accepted core module and core test artifact already exist.

Its scope is capped at:

```text
root-POM registration of security-support-session
one security-support-session runtime module
normal production dependency on security-support-core
test-scope dependency on the core tests classifier
Redis-session and HTTP-adaptation skeleton contracts
focused session tests using shared core fixtures
```

It must verify:

- session depends on core in the permitted direction;
- core does not acquire a reverse dependency;
- the core test JAR is consumed only in test scope;
- the test JAR is absent from session's production compile and runtime
  dependency graphs;
- root-level build and tests remain green.

It must not:

- create a third security module;
- rewrite `security-support-core` as part of ordinary session construction;
- migrate every consumer or change existing authentication behaviour;
- modify CI merely to make the staged module split work.

## Required invariants

- One shared principal and claim vocabulary is used across user and service
  boundaries.
- Principal and claim models live in `security-support-core`, not
  `internal-common`.
- `RequestPrincipal` remains free of web, servlet, Spring Security, Redis, and
  request-context types.
- Core performs no Redis lookup and writes no HTTP response.
- Session performs no cryptographic policy fork that contradicts core.
- `SESSION_VALIDATION_REQUIRED` is fulfilled by session and is not returned
  directly to clients.
- Authentication failures and authorization failures remain distinct.
- HTTP 401, 403, and operational 5xx mapping is owned by session.
- Internal service authentication does not introduce a user-session Redis
  dependency.
- Direct imports require direct Maven dependencies.
- Core never depends on session.
- Exactly two security-support runtime modules exist in the current phase.
- Shared test fixtures remain test-only and do not create a third runtime
  module.
- No raw token, secret, or sensitive claim is written to an error response or
  log.

## Consequences

### Positive

- Passenger, driver, boss, SSE, and internal-service authentication share one
  principal vocabulary.
- Internal services can verify service identity without acquiring Redis-backed
  user sessions.
- HTTP and Redis concerns remain outside the reusable token-verification core.
- Error semantics become consistent while preserving correct 401-versus-403
  behaviour.
- Security models avoid adding further weight to `internal-common`.
- New modules start with clean package naming.
- Shared test fixtures remain reusable without creating another production
  module.
- The boundary can be tested through dependency, package, and classpath
  assertions.
- Staging core before session proves the test-artifact mechanism before any
  consumer relies on it.

### Costs and trade-offs

- The root Maven reactor gains two modules across two pull requests.
- Consumer POMs will later need explicit, correctly scoped dependencies.
- Existing passenger and driver authentication code must be migrated
  incrementally.
- `service-sse-push` will have a deliberate dual dependency because its
  user-connect and internal-push boundaries are different.
- Session availability becomes an explicit operational dependency for
  user-authenticated edges.
- Core and session APIs must remain small enough to avoid becoming another
  general-purpose shared library.
- The staged introduction adds one extra review cycle in exchange for smaller
  diffs and independently proven dependency mechanics.

## Alternatives considered

### Put `RequestPrincipal`, `Role`, and `TokenClaims` in `internal-common`

Rejected. `internal-common` is already scheduled for slimming, and these models
belong to the security boundary that validates and produces them. Placing them
there would create avoidable migration debt from the day they are introduced.

### Use one security-support module for JWT, Redis, HTTP, and service identity

Rejected. Internal services would inherit web and Redis session dependencies
merely to verify service tokens, and framework-neutral verification could not be
tested or reused independently.

### Split security support into web, Redis, JWT, Feign, and service modules

Rejected for the current phase. It would create dependency sprawl before a
concrete ownership need exists. Packages inside core and session are sufficient.

### Keep separate principal and interceptor models in every edge

Rejected. The implementations and error meanings would continue to drift.

### Require internal services to use user-session validation

Rejected. Service identity and user session identity have different lifecycle
and dependency requirements.

### Create a third `security-support-test` module

Rejected. A core-produced test JAR provides shared fixtures without adding
another runtime or reactor ownership boundary.

### Hide core usage behind session's transitive dependency

Rejected. Direct imports without direct Maven declarations create hidden
coupling and fail dependency-analysis hygiene.

### Create core and session in one skeleton pull request

Rejected. The v6 Batch 0 plan separates the two skeletons. Creating core first
allows its framework neutrality, attached test artifact, and production
classpath to be verified before session introduces Redis and HTTP adaptation.

## Implementation sequence

This decision will be implemented through separate reviewable pull requests:

1. Batch 0.6 creates `security-support-core`, registers only core in the root
   POM, adds the framework-neutral shared models, failure semantics,
   verification skeleton, focused tests, and attached test JAR.
2. Batch 0.7 creates `security-support-session`, registers session in the root
   POM, depends normally on core, consumes the core test JAR only in test scope,
   and adds focused Redis-session and HTTP-adaptation skeleton tests.
3. Migrate passenger and driver token/session handling to the shared boundary.
4. Establish administrator identity and migrate `api-boss`.
5. Bind SSE user connect through session and internal push through core.
6. Add short-lived service identity to the seven internal services.
7. Remove duplicated JWT, Redis-session, and HTTP-error implementations after
   each consumer is covered by regression and contract tests.
8. Add dependency-analysis and architecture checks once the migration is
   stable.

Consumer migrations must remain separate from both skeleton pull requests so
that module creation, behaviour changes, and authorization changes are
independently reviewable.

## Non-goals

This ADR does not:

- create either Maven module;
- add models, token verifiers, interceptors, resolvers, Redis adapters, or
  tests;
- update the root POM or CI;
- select final token TTLs, signing algorithms, keys, rotation mechanisms, or
  claim names;
- complete passenger, driver, boss, SSE, or internal-service authentication;
- define the full administrator permission catalogue;
- adopt Spring Security as a framework requirement;
- select mTLS, a service mesh, or workload identity;
- change verification-code service naming, line endings, or module granularity;
- claim that current authentication and authorization paths are already
  secure.

## Future reassessment

A new ADR may supersede this decision if the system later requires a separate
identity service, external identity provider, Spring Security standardization,
workload identity, mTLS, or a demonstrably necessary third security module.

Material changes to this accepted decision must be recorded in a new ADR that
supersedes ADR-0002 rather than silently changing the shared-principal or
core/session ownership boundary.

## Repository evidence

This decision was based on the following repository areas:

- `pom.xml`
- `README.md`
- `api-passenger/pom.xml`
- `api-passenger/src/main/java/com/george/apipassenger/interceptor/`
- `api-passenger/src/main/java/com/george/apipassenger/service/`
- `api-passenger/src/main/java/com/george/apipassenger/remote/serviceVerificationCodeClient.java`
- `api-driver/pom.xml`
- `api-driver/src/main/java/com/george/apidriver/interceptor/`
- `api-driver/src/main/java/com/george/apidriver/service/`
- `api-driver/src/main/java/com/george/apidriver/remote/ServiceVerificationcodeClient.java`
- `api-boss/pom.xml`
- `service-sse-push/pom.xml`
- `service-order/pom.xml`
- `service-price/pom.xml`
- `service-driver-user/pom.xml`
- `service-map/pom.xml`
- `service-pay/pom.xml`
- `service-passenger-user/pom.xml`
- `service-verificationCode/pom.xml`
- `service-verificationCode/src/main/resources/application.yml`
- `service-verificationCode/src/main/java/com/george/serviceverificationcode/ServiceVerificationCodeApplication.java`
- `service-verificationCode/src/main/java/com/george/serviceverificationcode/controller/NumberCodeController.java`
- `internal-common/pom.xml`
- `docs/adr/0001-payment-state-ownership.md`
