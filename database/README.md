# FlyTaxi Database Change Management

## Status

This directory establishes the repository structure and review process for FlyTaxi database changes. It currently contains templates only. It does not yet contain the complete current schema or executable migrations, so it must not be treated as proof that an empty database can already be rebuilt.

The audited database baseline is MySQL 8.0.30. Reconfirm the target version before executing a future change.

## Layout

| Path | Purpose |
|---|---|
| `schema/` | Future empty-database baseline definitions, grouped by the service or database that owns the data |
| `migrations/` | Forward application scripts for reviewed database changes |
| `rollback/` | A paired rollback script or explicit restore plan for each forward change |
| `verification/` | Read-only preflight checks and post-migration or post-rollback assertions |
| `benchmark/` | Reproducible performance evidence, including query plans, read cost, and write-path impact |

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
