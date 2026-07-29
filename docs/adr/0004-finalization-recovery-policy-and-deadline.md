# ADR-0004: Finalization Recovery Policy and Deadline

- **Status:** Accepted
- **Date:** 2026-07-29
- **Decision scope:** Batch A order-finalization hardening and database-governance artifacts
- **Implementation status:** Planned and partially implemented by this pull request. This ADR records the target boundary and does not add an administrator HTTP endpoint.

## Context

ADR-0003 established bounded order-finalization recovery in `service-order`. Subsequent review found two remaining operational gaps:

- status 11 (`FINALIZATION_FAILED`) blocks new passenger orders, but direct retry still rejects it and there was no controlled recovery transition back to status 10;
- the processing lease was longer than the default remote calls in practice, but the repository did not make the remote-deadline budget or safety margin machine-verifiable.

Batch A also changed ride-duration semantics so new completed orders store `drive_time` in seconds. Historical pre-A2 completed rows may still contain minute values produced by the old formula. Multiplying those rows by 60 can only produce minute-granularity lower-bound seconds; the old formula already discarded sub-minute precision before storage.

## Decision

### Failed-finalization recovery

Bounded automatic retry remains the default recovery path. Direct `retryFinalization(orderId)` continues to reject status 11 with `FINALIZATION_FAILED`.

`service-order` will expose one internal service method for controlled failed-finalization recovery. That method is not an HTTP endpoint. It is the only transition that may move an order from status 11 back to status 10 in this phase.

The recovery transition uses a database compare-and-set:

- `id = orderId`;
- `order_status = FINALIZATION_FAILED`;
- `finalization_attempts = persisted currentAttempts` from the row read by this recovery attempt.

Status 11 itself determines recovery eligibility. `finalization_attempts` is used only to lock the persisted version that was read before issuing the update. Raising or lowering the configured maximum attempt count must not make historical status-11 rows unrecoverable.

On success it sets:

- `order_status = FINALIZATION_PENDING`;
- `finalization_attempts = 0`;
- `finalization_next_retry_at = now`;
- `finalization_last_error = canonical 1609 recovery-scheduled message`;
- `gmt_modified = now`.

It preserves the frozen trace window, pickup/getoff timestamps and coordinates, distance, duration, and price fields. It does not call driver, map, or price services.

If the recovery CAS is lost, the service rereads the order and returns a stable domain result:

- missing order: 1607;
- already completed status 6/7/8: success;
- status 10: 1609;
- status 11: 1605;
- any other status: 1608.

Status 11 continues to block new passenger orders until controlled recovery moves it to status 10 and the finalization path eventually reaches a completed state.

A future administrator endpoint may call this internal method only after ADMIN identity, RBAC, and audit logging exist. This ADR does not authorize an anonymous management endpoint.

### Finalization state ownership and legacy writers

Statuses 10 (`FINALIZATION_PENDING`) and 11 (`FINALIZATION_FAILED`) are owned by the finalization state machine. Legacy lifecycle and payment writers must not overwrite those states.

The legacy lifecycle and payment writers use exact predecessor-state compare-and-set transitions:

- `toPickUpPassenger`: 2 -> 3;
- `arrivedDeparture`: 3 -> 4;
- `pickUpPassenger`: 4 -> 5;
- `pushPayInfo`: 6 -> 7;
- `pay`: 7 -> 8.

If a legacy writer loses its CAS, it rereads the order and returns a stable domain result:

- missing order: 1607;
- already at the target state: success;
- status 10 or 11: 1611;
- any other state: 1610.

`cancel()` has an explicit negative fence for statuses 10 and 11 and returns 1611 without writing. Other cancellation legality rules remain unchanged and are deferred to Batch B lifecycle and authorization governance.

`ORDER_FINALIZATION_NOT_ALLOWED` (1608) remains reserved for callers that enter the finalization path when the current order state is not eligible for finalization. Legacy lifecycle, payment, and cancellation writers use `ORDER_STATE_TRANSITION_NOT_ALLOWED` (1610) or `FINALIZATION_IN_PROGRESS` (1611) instead.

The following `OrderInfo` fields are internal recovery metadata and are excluded from ordinary JSON serialization with field-level `@JsonIgnore`:

- `finalizationAttempts`;
- `finalizationNextRetryAt`;
- `finalizationLastError`;
- `finalizationTraceEndEpochMs`.

Before adding the JSON exclusion, the repository was checked for `ResponseResult<OrderInfo>` exits and controller/remote `OrderInfo` references. The only `ResponseResult<OrderInfo>` service return is the client-facing current-order path; no internal JSON consumer was found that depends on these four fields.

The audited MySQL 8.0.30 schema currently defines both `order_info.gmt_create` and `order_info.gmt_modified` with `ON UPDATE CURRENT_TIMESTAMP`. A wrapper update that omits those columns can therefore change their stored values as a side effect. Every wrapper-based order transition explicitly preserves `gmt_create` with `gmt_create = gmt_create`. Legacy lifecycle and payment CAS transitions also self-assign `gmt_modified` to preserve the pre-patch audit-time behavior, while A6 finalization transitions continue to write their deliberate `gmt_modified` event time. This is tactical containment for the audited schema, not a claim that DB-07B physical DDL correction is complete.

### Dedicated finalization remote deadline

`service-order` finalization calls use dedicated OpenFeign clients with the same service names as the shared clients but unique `contextId` values:

- `finalizationDriverUserClient`;
- `finalizationMapClient`;
- `finalizationPriceClient`.

The existing shared clients remain for order creation and dispatch. This avoids changing non-finalization timeout behavior before those call sites have a reviewed timeout and exception-handling design.

Only the dedicated context IDs receive timeout configuration. The default budget is:

- driver lookup: 2s connect + 10s read;
- trace search: 2s connect + 30s read;
- price calculation: 2s connect + 10s read;
- total remote budget: 56s.

The finalization processing lease defaults to 120s with a 30s safety margin. Startup validation requires:

```text
processing lease > remote budget + safety margin
```

The validation also rejects non-positive values, unreasonable attempt counts, `baseRetryDelay > maxRetryDelay`, and arithmetic overflow. The backoff policy is capped exponential:

```text
min(baseRetryDelay * 2^(attempt - 1), maxRetryDelay)
```

This invariant means normal supported remote calls should either complete or hit their configured timeout before the processing lease expires. It does not claim to eliminate JVM pauses, process freezes, host stalls, or other failures outside the remote-call timeout budget.

Shared add/dispatch timeout governance remains deferred.

The timeout design depends on Spring Cloud OpenFeign 3.0.1 applying `feign.client.config.<contextId>` entries to clients with explicit `contextId` values. Repository tests verify the configured property shape and the dedicated context IDs, while runtime binding remains an OpenFeign framework contract rather than custom application logic.

### READ-02 duration normalization

READ-02 keeps the Java property and physical column name `driveTime` / `drive_time` for compatibility. It normalizes historical completed rows to seconds.

Operators must provide the real A2 seconds-code deployment cutover in the same MySQL session before running READ-02:

```text
@read02_seconds_cutover
```

This cutover is the actual environment deployment time, not a Git commit or merge timestamp. It uses the same database wall-clock semantics as `order_info.passenger_getoff_time`. The cutover must be non-NULL and not later than current database time. If old and new `service-order` instances overlapped so that no single cutover can classify historical rows, operators must stop and use independent evidence or reset/reseed the disposable database.

READ-02 applies only to completed rows with status 6, 7, or 8, `passenger_getoff_time < cutover`, and non-NULL `drive_time`. It must not infer units from numeric size. It must prevent double conversion using the reviewed audit mechanism.

Zero candidate rows are not silently accepted. Operators must explicitly set the zero-candidate acknowledgement variable only after confirming the database is empty, disposable, already reseeded, or otherwise expected to contain no pre-A2 completed rows.

If READ-02 is rerun, every existing audit row must use the same cutover. A different cutover is a hard stop. With the same cutover, a completed rerun may update zero rows only when every target row has a matching audit entry, the current `drive_time` equals the audited normalized value, and the audit value equals `original_drive_time * 60`.

The migration turns pre-A2 minute values into minute-granularity lower-bound seconds by multiplying by 60. It cannot restore the original ride-duration seconds because those were already truncated before storage. This granularity difference is a known historical data-quality boundary, not a new migration bug.

The audited schema has `order_info.gmt_create` and `order_info.gmt_modified` with `ON UPDATE CURRENT_TIMESTAMP`. READ-02 explicitly self-assigns both columns during the update so the normalization does not change their original values. Migration timing is recorded only in the audit table's `migrated_at` column. This pull request does not modify the `gmt_create`, `gmt_modified`, or `drive_time` column definitions because the repository does not yet contain a complete schema baseline. Global `gmt_create` / `gmt_modified` DDL governance remains DB-07B. A future DDL change must first verify the real column definition from `SHOW CREATE TABLE` or `information_schema`.

If pre-deployment verification finds completed rows with missing required finalization outputs, or READ-02 finds ambiguous historical rows, rollout must stop for explicit data cleanup or a reviewed coordination plan. This pull request does not guess or rewrite such rows automatically.

Rollback is semantic, not a safe blind inverse. To restore the old representation, operators must stop traffic, deploy code compatible with minute-based historical storage while retaining audit artifacts, restore from a verified snapshot or audited original values, and run post-rollback verification. If those preconditions are not satisfied, seconds semantics must be retained.

Disposable synthetic databases may be reset or reseeded instead of preserving historical rows.

## Consequences

Positive:

- status 11 no longer has to be an operational dead end;
- direct retry still cannot silently reset exhausted retry budgets;
- future administrator tooling has one internal recovery transition to reuse;
- finalization remote timeout changes do not spill into order creation or dispatch;
- the processing-lease invariant is machine-verifiable at startup and in tests;
- READ-02 has an explicit cutover, audit, verification, and rollback boundary.

Trade-offs:

- `service-order` now has three additional Feign interfaces for the finalization path;
- operators must manage a real deployment cutover for READ-02;
- historical duration precision remains lower than new rows because pre-A2 data already lost sub-minute detail;
- administrator recovery still requires a later authenticated endpoint with audit support.

## Non-goals

This ADR does not:

- expose a controller or HTTP endpoint for recovery;
- add ADMIN identity, RBAC, audit logging, or `api-boss` recovery UI;
- implement full principal, role, or object-ownership enforcement;
- change shared Feign client timeout behavior for add or dispatch;
- implement durable notification outbox or guaranteed downstream delivery;
- execute READ-02, ORDER-04, or any database script;
- rename the `drive_time` physical column;
- claim true historical ride-duration seconds can be recovered;
- solve payment, SSE delivery, outbox, or service-to-service identity.

## Future Work

Batch B may add an authenticated administrator endpoint that calls the internal recovery method. That endpoint must verify ADMIN identity, enforce RBAC, record audit metadata, and avoid duplicating the recovery CAS logic in controller code.

REMOTE-01 remains open for a separate review of shared add/dispatch timeout behavior and thrown Feign exception handling.

Material changes to this decision should be recorded in a later ADR that supersedes this one.
