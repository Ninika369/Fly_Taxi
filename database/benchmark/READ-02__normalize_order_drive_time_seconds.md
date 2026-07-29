# READ-02 Benchmark Evidence Plan

## Change Metadata

- Change ID: READ-02
- Owner: service-order
- Target database: service-order
- Migration: `database/migrations/READ-02__normalize_order_drive_time_seconds.sql`
- Verification: `database/verification/READ-02__normalize_order_drive_time_seconds.sql`
- Status: Planned evidence - execute in a disposable or controlled environment before migration approval

No database was contacted while drafting this report.
No measurements, lock observations, replication lag, or execution plans in this file are claimed as observed evidence.

## Candidate Dataset

Record the exact candidate population before execution:

- total `order_info` row count;
- status distribution for all rows;
- status distribution for READ-02 candidate rows;
- candidate rows where `passenger_getoff_time < @read02_seconds_cutover`;
- candidate rows with `NULL passenger_getoff_time`;
- candidate rows with `NULL drive_time` or negative `drive_time`;
- overflow-risk rows where `drive_time * 60` would exceed BIGINT range;
- actual `order_info.drive_time` column type and signed BIGINT capacity check;
- reviewed maximum candidate count approved for one execution through `@read02_max_candidate_rows`.

The cutover must use the same database wall-clock semantics as `passenger_getoff_time`. If old and new application instances overlapped so that one cutover cannot separate minute-based and seconds-based rows, stop and use independent evidence or reset/reseed the disposable database.

## Audit Insert Cost

Benchmark the audit insert separately from the update:

- rows inserted into `read02_drive_time_seconds_audit`;
- elapsed time;
- rows examined;
- redo and undo growth where observable;
- lock wait or deadlock observations;
- replication lag impact where applicable;
- disk growth from the audit table.

The audit table is required for idempotent same-cutover reruns and rollback evidence. Do not skip it for speed.

## Join Update Cost

Benchmark the `order_info` join update:

- rows updated;
- rows examined;
- elapsed time;
- lock wait time;
- redo and undo growth;
- replication lag impact where applicable;
- before/after sample verification of `normalized_drive_time = original_drive_time * 60`;
- confirmation that `order_info.gmt_create` remains unchanged;
- confirmation that `order_info.gmt_modified` remains unchanged;
- confirmation that `migrated_at` is written only to `read02_drive_time_seconds_audit`.

The audited schema has `order_info.gmt_create` and `order_info.gmt_modified` with `ON UPDATE CURRENT_TIMESTAMP`. READ-02 explicitly self-assigns both columns during the join update to preserve their original values. This benchmark evidence should include before/after samples proving both values stayed unchanged.

## Single Statement vs Chunking Decision

Use `@read02_max_candidate_rows` as the reviewed candidate-count threshold before choosing execution shape:

- If the candidate count is at or below `@read02_max_candidate_rows`, a single audited insert and single join update may be acceptable.
- If the candidate count exceeds `@read02_max_candidate_rows`, stop and prepare a reviewed chunking plan with deterministic ordering, per-chunk verification, retry rules, lock limits, and rollback evidence.

Record `@read02_max_candidate_rows`, the observed candidate count, and the reason for the decision. Do not raise the threshold during execution without a separate chunking or capacity review.

## Maintenance Window

Record:

- planned window start and end;
- expected traffic state;
- scheduler state;
- backup or snapshot reference;
- operator;
- abort criteria;
- rollback owner.

## Before and After Timing

For each measured step, record:

- command text without credentials or production data;
- start and end time;
- elapsed time;
- affected row count;
- warnings;
- verification query result.

## Column Definition Boundary

READ-02 does not modify the physical `drive_time`, `gmt_create`, or `gmt_modified` column definitions in this pull request because the repository does not yet contain a complete schema baseline. Global `gmt_create` / `gmt_modified` DDL governance remains DB-07B. A future DDL change must first capture and verify `SHOW CREATE TABLE` or equivalent `information_schema` facts.

If a future reviewed version adds `ALTER TABLE`, record:

- `ALGORITHM`;
- `LOCK`;
- whether MySQL rebuilds the table;
- expected lock duration;
- rollback or restore path;
- compatibility with the audited MySQL version.

## Conclusion

Pending. No benchmark result has been observed in this pull request.
