# FlyTaxi Database Change Management

## Status

This directory establishes the repository structure and review process for FlyTaxi database changes. It now contains reviewed ORDER-04 finalization metadata artifacts and READ-02 drive-time normalization artifacts, plus reusable templates. It does not yet contain the complete current schema, so it must not be treated as proof that an empty database can already be rebuilt.

The audited database baseline is MySQL 8.0.30. Reconfirm the target version before executing a future change.

## Layout

| Path | Purpose |
|---|---|
| `schema/` | Future empty-database baseline definitions, grouped by the service or database that owns the data |
| `migrations/` | Forward application scripts for reviewed database changes |
| `rollback/` | A paired rollback script or explicit restore plan for each forward change |
| `verification/` | Read-only preflight checks and post-migration or post-rollback assertions |
| `benchmark/` | Reproducible performance evidence, including query plans, read cost, and write-path impact |

## Current Reviewed Artifacts

| Change ID | Purpose | Status |
|---|---|---|
| ORDER-04 | Durable order finalization metadata, rollback, verification, and due-scan benchmark plan | Planned artifacts; execute only in a controlled MySQL 8.0.30 environment |
| READ-02 | Normalize historical completed `order_info.drive_time` rows to seconds while retaining the physical column name | Planned artifacts; requires an operator-provided A2 seconds-code deployment cutover, reviewed candidate ceiling, explicit zero-candidate acknowledgement when applicable, audit evidence, and benchmark planning |

READ-02 multiplies pre-A2 completed rows by 60 to produce minute-granularity lower-bound seconds. The old formula already lost sub-minute precision before storage; READ-02 does not and cannot restore true ride-duration seconds. It requires `@read02_max_candidate_rows`, verifies the actual `drive_time` column is signed BIGINT before applying BIGINT capacity checks, rejects NULL or negative historical duration inputs, and uses NULL-safe current/audit comparisons. The audited schema has `order_info.gmt_create` and `order_info.gmt_modified` with `ON UPDATE CURRENT_TIMESTAMP`, so READ-02 explicitly self-assigns both columns to preserve their original values; `migrated_at` is written only to the audit table. This pull request does not modify either timestamp column DDL or the physical `drive_time` column definition. Global `gmt_create` / `gmt_modified` DDL governance remains DB-07B. Disposable synthetic databases may be reset or reseeded instead of preserving historical rows.

READ-02 performance and locking review is tracked in [`benchmark/READ-02__normalize_order_drive_time_seconds.md`](benchmark/READ-02__normalize_order_drive_time_seconds.md).

## Naming and Pairing

- Actual change files should use `<change-id>__<short-description>.<ext>`.
- A migration, rollback, and verification set must reuse the same change ID.
- Performance-related changes must also include benchmark evidence.
- Files containing `.template` are scaffolding only and must never be executed.
- This skeleton does not select or configure Flyway, Liquibase, or another migration framework.

## Required Workflow

1. Copy the relevant templates and assign one stable change ID.
2. Identify the owning service and target database.
3. Run and record read-only preflight checks.
4. Publish a cleanup plan before applying constraints to existing data.
5. Review the forward change, rollback or restore path, locking risk, and backup requirements.
6. Apply the change first in a disposable or controlled non-production environment.
7. Run post-migration assertions and, where practical, rehearse rollback.
8. Capture reproducible benchmark evidence for query or index changes.
9. Record the operator, execution time, environment, and result in the associated pull request or issue.

Constraint work must be split into reviewable stages: first the read-only preflight and cleanup plan, then the cleanup and constraint application. Do not jump directly to `ALTER TABLE` without inspecting existing duplicate, orphaned, or `NULL` data.

## Safety Rules

- Do not make manual schema changes without committing the corresponding migration, rollback, and verification artifacts.
- Do not store credentials, connection strings, production data, or database dumps in this directory.
- Verification scripts should be read-only and document the expected result of every check.
- MySQL DDL may implicitly commit; do not assume a surrounding transaction can provide rollback.
- Every change must either be independently rollbackable or explicitly document why it is irreversible and how backup restoration will work.
- Database changes must not span remote calls, SSE delivery, or long-running application transactions.
- This directory does not configure automatic migration execution at application startup.
