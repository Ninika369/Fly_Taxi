# ADR-0003: Bound Order Finalization Recovery in `service-order`

- **Status:** Superseded by ADR-0004
- **Date:** 2026-07-28
- **Decision scope:** Existing FlyTaxi Java/Spring architecture
- **Implementation status:** Planned and implemented in Batch A6 for database state and scheduled recovery. This ADR does not authorize an unauthenticated administrator HTTP endpoint.

## Context

Passenger get-off finalization currently depends on a chain of order, car, trace-search, and pricing work. Failures in that chain must not silently lose recovery state, repeat pricing after duplicate client requests, or expand the trace-search window by using a new current time on each retry.

The finalization facts belong with the order aggregate because the final order status, price, drive distance, drive duration, retry attempts, and recovery outcome are order-domain state.

## Decision

`service-order` owns order finalization recovery.

The following finalization metadata must be persisted in the `service-order` database:

- claimed attempt count;
- next retry time and processing lease;
- stable last error code and domain message;
- fixed trace-search end epoch captured on the first accepted passenger get-off request.

Redis is not the authoritative retry ledger. Redis TTL counters are rejected because expiry, restart, flush, or data loss can reset attempts independently from the order database and leave finalization unbounded or unrecoverable.

Multiple instances coordinate by database compare-and-set claim of each attempt. The conditional claim includes the order ID, current order status, current attempt count, and due retry time for pending rows.

Batch A6 establishes:

- durable database metadata;
- a bounded internal retry method;
- scheduled retry scanning;
- safe structured diagnostics.

Batch A6 explicitly does not add an unauthenticated `api-boss` or administrator HTTP recovery endpoint.

An administrator recovery endpoint is deferred until ADMIN identity, RBAC, and audit logging exist. That future endpoint must reuse the internal retry service method instead of copying finalization recovery logic into a controller.

The ORDER-04 database migration must be applied before starting the Batch A6 `service-order` code in an environment backed by MySQL. Because Batch A6 treats statuses 6, 7, and 8 as idempotently completed, deployment must also verify that historical completed rows already have `drive_mile`, `drive_time`, and `price`. If any completed rows are missing those outputs, rollout must stop until test data is cleaned up or an explicit data-coordination plan is reviewed. This ADR does not authorize guessing or rewriting historical completed orders automatically.

Rollback has a manual state gate. Operators must stop inbound finalization traffic and the Batch A6 scheduler, run the ORDER-04 pre-rollback verification, and confirm there are zero status 10 or 11 rows before dropping columns. Status 10 or 11 rows must not be automatically mapped back to status 5, 6, or any other state. If unresolved rows remain, coordination must happen while the A6 code and schema are still available, or recovery must use a verified snapshot. After the gate is clear, deploy the previous `service-order` code while temporarily retaining the extra columns, then execute the schema rollback and post-rollback verification.

## Consequences

Positive:

- Retry attempts remain bounded across service restarts and Redis loss.
- Duplicate passenger get-off requests do not repeat remote calls after a pending claim.
- Scheduled retry can continue after the client stops retrying.
- The trace-search end instant remains stable across retries.
- Failed finalization becomes visible as durable order state.

Trade-offs:

- `service-order` gains additional order metadata and retry logic.
- A schema migration is required before the new fields can be used safely in a real database.
- Operators still need authenticated recovery tooling later for terminal failed cases.

## Non-goals

This ADR does not:

- expose a new administrator HTTP endpoint;
- implement ADMIN identity, RBAC, or audit;
- use Redis as the retry attempt source of truth;
- introduce Flyway, Liquibase, or automatic migration execution;
- solve payment, SSE delivery, outbox, service-to-service identity, or global time-zone governance.

## Future Work

After ADMIN identity, RBAC, and audit are implemented, an authenticated recovery endpoint may call the internal finalization retry method for selected failed orders. It must record who initiated the recovery and must not duplicate the scheduled retry implementation.

Material changes to this decision should be recorded in a later ADR that supersedes this one.
