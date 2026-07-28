# ORDER-04 Benchmark Evidence Plan

## Change Metadata

- Change ID: ORDER-04
- Owner: service-order
- Target database: service-order
- Migration: `database/migrations/ORDER-04__add_order_finalization_metadata.sql`
- Verification: `database/verification/ORDER-04__add_order_finalization_metadata.sql`
- Status: Planned evidence - execute in a disposable or controlled environment before migration approval

No database was contacted while drafting this report.
No measurements or execution plans in this file are claimed as observed evidence.

## Due-Scan Workload

The scheduler due-scan query to benchmark is:

```sql
EXPLAIN ANALYZE
SELECT id
FROM order_info
WHERE order_status = 10
  AND finalization_next_retry_at <= ?
ORDER BY finalization_next_retry_at ASC
LIMIT 50;
```

For read-only preflight optimizer inspection where execution is not desired, record:

```sql
EXPLAIN
SELECT id
FROM order_info
WHERE order_status = 10
  AND finalization_next_retry_at <= ?
ORDER BY finalization_next_retry_at ASC
LIMIT 50;
```

Use parameter placeholders or synthetic controlled values. Do not paste production values.

## Expected Plan Hypothesis

Expected:
MySQL uses `idx_order_finalization_due` with equality on `order_status`
and a range condition on `finalization_next_retry_at`, preserving index order
for the `LIMIT 50` due scan.

Must be confirmed with real `EXPLAIN` / `EXPLAIN ANALYZE` output before apply.

If the optimizer does not choose this index, do not edit the evidence to match the hypothesis. Record the actual `key`, `type`, estimated rows, actual rows, whether filesort appears, and the full relevant plan. If filesort, full table scan, or unexpectedly high rows examined appears, the migration must be reviewed again before approval.

## Write-Path Impact

The migration adds three indexed columns to a secondary index and one additional metadata column:

- Each `order_info` insert must maintain the extra secondary index.
- Finalization claim, failure, and success updates touch `order_status`, `finalization_next_retry_at`, and `finalization_attempts`, which can cause secondary-index delete/insert or page maintenance.
- The index increases disk usage, buffer-pool pressure, redo/undo volume, and possible page splits.
- Because the index serves only the bounded due scan, the read benefit must be verified at a controlled data scale before migration approval.

Before applying the migration, fill in controlled evidence for:

- dataset row count and status distribution;
- pending and due row distribution;
- before and after query plan;
- rows examined;
- latency;
- insert and update cost;
- index size.

## Reproduction

Future benchmark execution should:

1. Create a representative fixture in a disposable or controlled environment.
2. Record row count and pending/due distribution.
3. Run and save the pre-migration query plan.
4. Apply `database/migrations/ORDER-04__add_order_finalization_metadata.sql`.
5. Run `ANALYZE TABLE order_info`.
6. Rerun the same parameters under cold and warm cache where practical.
7. Record before/after plans, rows examined, latency, and write-path cost.
8. Decide whether to retain, modify, or reject the index.

## Conclusion

Pending. No benchmark result has been observed in this pull request.
