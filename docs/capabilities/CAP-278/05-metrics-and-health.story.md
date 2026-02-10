# Story: Add metrics and health checks for Posting Rule Engine

## Description
Add Prometheus metrics and a health check to monitor the posting engine's availability, throughput, and failure rates.

## Acceptance Criteria
- Metrics exported: processed_count, suspended_count, failed_count, reprocess_attempts_count, last_success_timestamp
- Health endpoint reflects rule engine readiness (e.g., rule cache warmed or DB connectivity)
- Alerts/thresholds documented for SRE use

## Tasks
- [ ] Add Micrometer counters/gauges to engine
- [ ] Expose readiness probe for rule metadata cache
- [ ] Document metric names and intended alerts
- [ ] Add unit/integration tests for metrics instrumentation

## Dependencies
- Micrometer/Prometheus setup in repo

## Notes for Agents
Reuse existing metric naming patterns in `pos-accounting` and attach `service.name` and `service.version` tags.