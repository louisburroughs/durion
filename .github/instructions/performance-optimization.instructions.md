---
name: 'Backend Performance Optimization Guidelines'
applyTo: '*'
description: 'The most comprehensive, practical, and engineer-authored performance optimization instructions for all languages, frameworks, and stacks. Covers frontend, backend, and database best practices with actionable guidance, scenario-based checklists, troubleshooting, and pro tips.'
---

# Performance Optimization Best Practices

## Introduction

Performance isn't just a buzzword—it's the difference between a product people love and one they abandon. I've seen firsthand how a slow app can frustrate users, rack up cloud bills, and even lose customers. This guide is a living collection of the most effective, real-world performance practices I've used and reviewed, covering frontend, backend, and database layers, as well as advanced topics. Use it as a reference, a checklist, and a source of inspiration for building fast, efficient, and scalable software.

---

## General Principles

- **Measure First, Optimize Second:** Always profile and measure before optimizing. Use benchmarks, profilers, and monitoring tools to identify real bottlenecks. Guessing is the enemy of performance.
  - *Pro Tip:* Use tools like Chrome DevTools, Lighthouse, New Relic, Datadog, Py-Spy, or your language's built-in profilers.
- **Optimize for the Common Case:** Focus on optimizing code paths that are most frequently executed. Don't waste time on rare edge cases unless they're critical.
- **Avoid Premature Optimization:** Write clear, maintainable code first; optimize only when necessary. Premature optimization can make code harder to read and maintain.
- **Minimize Resource Usage:** Use memory, CPU, network, and disk resources efficiently. Always ask: "Can this be done with less?"
- **Prefer Simplicity:** Simple algorithms and data structures are often faster and easier to optimize. Don't over-engineer.
- **Document Performance Assumptions:** Clearly comment on any code that is performance-critical or has non-obvious optimizations. Future maintainers (including you) will thank you.
- **Understand the Platform:** Know the performance characteristics of your language, framework, and runtime. What's fast in Python may be slow in JavaScript, and vice versa.
- **Automate Performance Testing:** Integrate performance tests and benchmarks into your CI/CD pipeline. Catch regressions early.
- **Set Performance Budgets:** Define acceptable limits for load time, memory usage, API latency, etc. Enforce them with automated checks.


## Backend Performance

### Algorithm and Data Structure Optimization
- **Choose the Right Data Structure:** Arrays for sequential access, hash maps for fast lookups, trees for hierarchical data, etc.
- **Efficient Algorithms:** Use binary search, quicksort, or hash-based algorithms where appropriate.
- **Avoid O(n^2) or Worse:** Profile nested loops and recursive calls. Refactor to reduce complexity.
- **Batch Processing:** Process data in batches to reduce overhead (e.g., bulk database inserts).
- **Streaming:** Use streaming APIs for large data sets to avoid loading everything into memory.

### Concurrency and Parallelism
- **Asynchronous I/O:** Use async/await, callbacks, or event loops to avoid blocking threads.
- **Thread/Worker Pools:** Use pools to manage concurrency and avoid resource exhaustion.
- **Avoid Race Conditions:** Use locks, semaphores, or atomic operations where needed.
- **Bulk Operations:** Batch network/database calls to reduce round trips.
- **Backpressure:** Implement backpressure in queues and pipelines to avoid overload.

### Caching
- **Cache Expensive Computations:** Use in-memory caches (Redis, Memcached) for hot data.
- **Cache Invalidation:** Use time-based (TTL), event-based, or manual invalidation. Stale cache is worse than no cache.
- **Distributed Caching:** For multi-server setups, use distributed caches and be aware of consistency issues.
- **Cache Stampede Protection:** Use locks or request coalescing to prevent thundering herd problems.
- **Don't Cache Everything:** Some data is too volatile or sensitive to cache.

### API and Network
- **Minimize Payloads:** Use JSON, compress responses (gzip, Brotli), and avoid sending unnecessary data.
- **Pagination:** Always paginate large result sets. Use cursors for real-time data.
- **Rate Limiting:** Protect APIs from abuse and overload.
- **Connection Pooling:** Reuse connections for databases and external services.
- **Protocol Choice:** Use HTTP/2, gRPC, or WebSockets for high-throughput, low-latency communication.

### Logging and Monitoring
- **Minimize Logging in Hot Paths:** Excessive logging can slow down critical code.
- **Structured Logging:** Use JSON or key-value logs for easier parsing and analysis.
- **Monitor Everything:** Latency, throughput, error rates, resource usage. Use Prometheus, Grafana, Datadog, or similar.
- **Alerting:** Set up alerts for performance regressions and resource exhaustion.

### Language/Framework-Specific Tips
#### Java
- Use efficient collections (`ArrayList`, `HashMap`, etc.).
- Profile with VisualVM, JProfiler, or YourKit.
- Use thread pools (`Executors`) for concurrency.
- Tune JVM options for heap and garbage collection (`-Xmx`, `-Xms`, `-XX:+UseG1GC`).
- Use `CompletableFuture` for async programming.

#### .NET
- Use `async/await` for I/O-bound operations.
- Use `Span<T>` and `Memory<T>` for efficient memory access.
- Profile with dotTrace, Visual Studio Profiler, or PerfView.
- Pool objects and connections where appropriate.
- Use `IAsyncEnumerable<T>` for streaming data.

### Common Backend Pitfalls
- Synchronous/blocking I/O in web servers.
- Not using connection pooling for databases.
- Over-caching or caching sensitive/volatile data.
- Ignoring error handling in async code.
- Not monitoring or alerting on performance regressions.

### Backend Troubleshooting
- Use flame graphs to visualize CPU usage.
- Use distributed tracing (OpenTelemetry, Jaeger, Zipkin) to track request latency across services.
- Use heap dumps and memory profilers to find leaks.
- Log slow queries and API calls for analysis.

---

## Database Performance

### Query Optimization
- **Indexes:** Use indexes on columns that are frequently queried, filtered, or joined. Monitor index usage and drop unused indexes.
- **Avoid SELECT *:** Select only the columns you need. Reduces I/O and memory usage.
- **Parameterized Queries:** Prevent SQL injection and improve plan caching.
- **Query Plans:** Analyze and optimize query execution plans. Use `EXPLAIN` in SQL databases.
- **Avoid N+1 Queries:** Use joins or batch queries to avoid repeated queries in loops.
- **Limit Result Sets:** Use `LIMIT`/`OFFSET` or cursors for large tables.

### Schema Design
- **Normalization:** Normalize to reduce redundancy, but denormalize for read-heavy workloads if needed.
- **Data Types:** Use the most efficient data types and set appropriate constraints.
- **Partitioning:** Partition large tables for scalability and manageability.
- **Archiving:** Regularly archive or purge old data to keep tables small and fast.
- **Foreign Keys:** Use them for data integrity, but be aware of performance trade-offs in high-write scenarios.

### Transactions
- **Short Transactions:** Keep transactions as short as possible to reduce lock contention.
- **Isolation Levels:** Use the lowest isolation level that meets your consistency needs.
- **Avoid Long-Running Transactions:** They can block other operations and increase deadlocks.

### Caching and Replication
- **Read Replicas:** Use for scaling read-heavy workloads. Monitor replication lag.
- **Cache Query Results:** Use Redis or Memcached for frequently accessed queries.
- **Write-Through/Write-Behind:** Choose the right strategy for your consistency needs.
- **Sharding:** Distribute data across multiple servers for scalability.

### NoSQL Databases
- **Design for Access Patterns:** Model your data for the queries you need.
- **Avoid Hot Partitions:** Distribute writes/reads evenly.
- **Unbounded Growth:** Watch for unbounded arrays or documents.
- **Sharding and Replication:** Use for scalability and availability.
- **Consistency Models:** Understand eventual vs strong consistency and choose appropriately.

### Common Database Pitfalls
- Missing or unused indexes.
- SELECT * in production queries.
- Not monitoring slow queries.
- Ignoring replication lag.
- Not archiving old data.

### Database Troubleshooting
- Use slow query logs to identify bottlenecks.
- Use `EXPLAIN` to analyze query plans.
- Monitor cache hit/miss ratios.
- Use database-specific monitoring tools (pg_stat_statements, MySQL Performance Schema).

---

## Code Review Checklist for Performance

- [ ] Are there any obvious algorithmic inefficiencies (O(n^2) or worse)?
- [ ] Are data structures appropriate for their use?
- [ ] Are there unnecessary computations or repeated work?
- [ ] Is caching used where appropriate, and is invalidation handled correctly?
- [ ] Are database queries optimized, indexed, and free of N+1 issues?
- [ ] Are large payloads paginated, streamed, or chunked?
- [ ] Are there any memory leaks or unbounded resource usage?
- [ ] Are network requests minimized, batched, and retried on failure?
- [ ] Are assets optimized, compressed, and served efficiently?
- [ ] Are there any blocking operations in hot paths?
- [ ] Is logging in hot paths minimized and structured?
- [ ] Are performance-critical code paths documented and tested?
- [ ] Are there automated tests or benchmarks for performance-sensitive code?
- [ ] Are there alerts for performance regressions?
- [ ] Are there any anti-patterns (e.g., SELECT *, blocking I/O, global variables)?

---

## Advanced Topics

### Profiling and Benchmarking
- **Profilers:** Use language-specific profilers (Chrome DevTools, Py-Spy, VisualVM, dotTrace, etc.) to identify bottlenecks.
- **Microbenchmarks:** Write microbenchmarks for critical code paths. Use `benchmark.js`, `pytest-benchmark`, or JMH for Java.
- **A/B Testing:** Measure real-world impact of optimizations with A/B or canary releases.
- **Continuous Performance Testing:** Integrate performance tests into CI/CD. Use tools like k6, Gatling, or Locust.

### Memory Management
- **Resource Cleanup:** Always release resources (files, sockets, DB connections) promptly.
- **Object Pooling:** Use for frequently created/destroyed objects (e.g., DB connections, threads).
- **Heap Monitoring:** Monitor heap usage and garbage collection. Tune GC settings for your workload.
- **Memory Leaks:** Use leak detection tools (Valgrind, LeakCanary, Chrome DevTools).

### Scalability
- **Horizontal Scaling:** Design stateless services, use sharding/partitioning, and load balancers.
- **Auto-Scaling:** Use cloud auto-scaling groups and set sensible thresholds.
- **Bottleneck Analysis:** Identify and address single points of failure.
- **Distributed Systems:** Use idempotent operations, retries, and circuit breakers.

### Security and Performance
- **Efficient Crypto:** Use hardware-accelerated and well-maintained cryptographic libraries.
- **Validation:** Validate inputs efficiently; avoid regexes in hot paths.
- **Rate Limiting:** Protect against DoS without harming legitimate users.

### Mobile Performance
- **Startup Time:** Lazy load features, defer heavy work, and minimize initial bundle size.
- **Image/Asset Optimization:** Use responsive images and compress assets for mobile bandwidth.
- **Efficient Storage:** Use SQLite, Realm, or platform-optimized storage.
- **Profiling:** Use Android Profiler, Instruments (iOS), or Firebase Performance Monitoring.

### Cloud and Serverless
- **Cold Starts:** Minimize dependencies and keep functions warm.
- **Resource Allocation:** Tune memory/CPU for serverless functions.
- **Managed Services:** Use managed caching, queues, and DBs for scalability.
- **Cost Optimization:** Monitor and optimize for cloud cost as a performance metric.

---

## Practical Examples

### Example: Efficient SQL Query
```sql
-- BAD: Selects all columns and does not use an index
SELECT * FROM users WHERE email = 'user@example.com';

-- GOOD: Selects only needed columns and uses an index
SELECT id, name FROM users WHERE email = 'user@example.com';
```

## References and Further Reading
- [PostgreSQL Performance Optimization](https://wiki.postgresql.org/wiki/Performance_Optimization)
- [Java Performance Tuning](https://www.oracle.com/java/technologies/javase/performance.html)
- [Lighthouse](https://developers.google.com/web/tools/lighthouse)
- [Prometheus](https://prometheus.io/)
- [Grafana](https://grafana.com/)
- [k6 Load Testing](https://k6.io/)
- [Gatling](https://gatling.io/)
- [Locust](https://locust.io/)
- [OpenTelemetry](https://opentelemetry.io/)
- [Jaeger](https://www.jaegertracing.io/)
- [Zipkin](https://zipkin.io/)

---

## Conclusion

Performance optimization is an ongoing process. Always measure, profile, and iterate. Use these best practices, checklists, and troubleshooting tips to guide your development and code reviews for high-performance, scalable, and efficient software. If you have new tips or lessons learned, add them here—let's keep this guide growing!

---

<!-- End of Performance Optimization Instructions --> 
