# Performance Rules

> Правила оптимизации производительности Java-кода

## Performance (IMPORTANT)

- MUST Avoid Boxing: ALWAYS AVOID unnecessary boxing
- MUST Prefer Primitives in Hot Paths: Integer -> int, thread safety: Integer -> AtomicInteger
- MUST Reuse Objects in Loops: string concat in every loop -> StringBuilder
- MUST REMEMBER Collection Sizing: known size -> set capacity
- MUST REMEMBER Map Capacity: calculate capacity considering load factor (0.75) or use Guava's
  Maps.newHashMapWithExpectedSize
- Choose Right Collection:
    - | Use Case | Collection |
      |----------|------------|
      | Ordered, frequent random access | `ArrayList` |
      | Frequent insertions/removals | `LinkedList` |
      | Unique elements, fast lookup | `HashSet` |
      | Sorted unique elements | `TreeSet` |
      | Key-value, fast lookup | `HashMap` |
      | Key-value, sorted keys | `TreeMap` |
      | Thread-safe map | `ConcurrentHashMap` |
      | Read-heavy list | `CopyOnWriteArrayList` |
- MUST USE Lazy Supplier or Guava Suppliers.memoize
- MUST USE Lazy Field Initialization
- DO NOT USE String.format, instead use MessageFormat, StringBuilder or concatenation for simple cases
- MUST USE Hot Path Optimization: try use heavy operations once by initialization
- MUST Avoid Virtual Calls in Hot Paths:
  ```java
    // Slow — virtual dispatch each iteration
    public void processAll(List<UpdateMiddleware> middlewares, UpdateContext ctx) {
        for (UpdateMiddleware m : middlewares) {
            ctx = m.process(ctx);  // virtual call
        }
    }

    // Faster — inline simple operations
    public void processAll(UpdateContext ctx) {
        // Manually inlined for hot path
        ctx = loggingMiddleware.process(ctx);
        ctx = rateLimitMiddleware.process(ctx);
        ctx = authMiddleware.process(ctx);
    }
  ```
- MUST Avoid Exceptions in Hot Paths
- MUST Minimize Object Creation
- SHOULD USE Flyweight Pattern
- MUST USE Compact Data Structures

---

## Memory Efficiency

### Object Size Awareness

| Type | Size (64-bit JVM) |
|------|-------------------|
| Object header | 16 bytes |
| boolean, byte | 1 byte |
| char, short | 2 bytes |
| int, float | 4 bytes |
| long, double | 8 bytes |
| Reference | 8 bytes (compressed: 4) |

### Memory Optimization Techniques

- MUST BE aware of object padding (8-byte alignment)
- SHOULD USE primitive arrays instead of wrapper arrays
- MUST AVOID creating unnecessary intermediate objects
- SHOULD USE object pooling for frequently allocated objects

---

## Profiling Guidelines

### When to Optimize

1. **Measure first** — don't guess bottlenecks
2. **Optimize hot paths** — focus on frequently executed code
3. **Profile under load** — test with realistic data volumes
4. **Benchmark changes** — verify improvement

### Tools

| Tool | Purpose |
|------|---------|
| JMH | Microbenchmarks |
| async-profiler | CPU & allocation profiling |
| JFR (Flight Recorder) | Production profiling |
| VisualVM | Memory & thread analysis |

### Profiling Workflow

```
1. Identify suspected bottleneck
2. Create reproducible test scenario
3. Profile with async-profiler or JFR
4. Identify actual hot spots
5. Optimize and benchmark with JMH
6. Verify in production with JFR
```

---

## Related

- [Performance Checklist](../checklist/performance.md)
- [Concurrency Rules](./concurrency.md) — thread-safe collections
