# Database Benchmark Report Template

> Template only. Copy and rename this file before recording evidence for a real database change. Do not insert fabricated measurements.

## Change Metadata

- Change ID:
- Title:
- Owner:
- Owning service:
- Target database:
- Migration file:
- Verification file:

## Environment

- MySQL version:
- Hardware or runner:
- Configuration relevant to the result:
- Cold or warm cache:
- Number of repetitions:
- Measurement method:

## Dataset

- Generation or fixture script:
- Row counts:
- Data distribution:
- Representative edge cases:
- Dataset scale, such as 100k or 1m rows:

## Workload

- Query or write path:
- Parameters:
- Concurrency:
- Expected production pattern:

## Before

- Query latency:
- Rows examined:
- Execution plan:
- Indexes used:
- Index size:
- Insert or update cost:

## After

- Query latency:
- Rows examined:
- Execution plan:
- Indexes used:
- Index size:
- Insert or update cost:

## `EXPLAIN ANALYZE` Evidence

Document the complete reproducible command and retain the relevant plan output without credentials or production data.

## Write-Path Impact

Describe additional index maintenance, lock duration, storage growth, and any regression observed for inserts or updates.

## Reproduction

Document the exact data-generation, `ANALYZE TABLE`, query, and measurement steps required to reproduce the comparison.

## Conclusion

State whether the change should be retained, revised, or rejected, including both read improvements and write costs.
