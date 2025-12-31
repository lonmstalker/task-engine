# Concurrency Rules

> Правила многопоточности и асинхронного программирования

## Thread Safety

### Documentation

- MUST document thread safety in Javadoc
- Use annotations: `@ThreadSafe`, `@NotThreadSafe`, `@Immutable`

```java
/**
 * Thread-safe message sender.
 * All methods are safe to call from multiple threads.
 */
@ThreadSafe
public class TelegramSender { ... }
```

### Immutability

- MUST USE immutable objects where possible
- MUST USE `final` fields for shared state
- MUST USE defensive copies for mutable inputs

```java
// Good: immutable
public record BotConfig(
    String token,
    Duration timeout,
    List<String> allowedUpdates
) {
    public BotConfig {
        allowedUpdates = List.copyOf(allowedUpdates);
    }
}

// Bad: mutable shared state
public class BotConfig {
    private List<String> allowedUpdates; // Mutable!
}
```

### Thread-Safe Collections

| Use Case | Collection |
|----------|------------|
| Thread-safe map | `ConcurrentHashMap` |
| Read-heavy list | `CopyOnWriteArrayList` |
| Blocking queue | `LinkedBlockingQueue` |
| Atomic reference | `AtomicReference<T>` |
| Atomic counter | `AtomicLong`, `LongAdder` |

---

## CompletableFuture Guidelines

### Async API Design

- MUST USE `CompletableFuture<T>` for async operations
- MUST provide both sync and async API variants
- SHOULD USE method naming: `send()` / `sendAsync()`

```java
public interface TelegramClient {
    // Sync (blocking)
    Message send(SendMessage request);

    // Async (non-blocking)
    CompletableFuture<Message> sendAsync(SendMessage request);
}
```

### Composition

```java
// Good: thenCompose for nested futures
CompletableFuture<Message> sendAndPin(SendMessage request) {
    return sendAsync(request)
        .thenCompose(message -> pinMessageAsync(message.getChatId(), message.getMessageId()));
}

// Bad: thenApply creates CompletableFuture<CompletableFuture<Message>>
```

### Error Handling

```java
sendAsync(request)
    .thenApply(this::processMessage)
    .exceptionally(ex -> {
        log.error("Failed to send message", ex);
        return fallbackMessage();
    })
    .thenAccept(this::logResult);
```

### Timeouts

```java
// MUST USE timeouts for async operations
sendAsync(request)
    .orTimeout(30, TimeUnit.SECONDS)
    .exceptionally(ex -> {
        if (ex instanceof TimeoutException) {
            log.warn("Request timed out");
        }
        return null;
    });
```

---

## Executor Management

### Thread Factory

- MUST USE named thread factories

```java
ThreadFactory factory = new ThreadFactoryBuilder()
    .setNameFormat("telegram-sender-%d")
    .setDaemon(true)
    .setUncaughtExceptionHandler((t, e) -> log.error("Uncaught in {}", t.getName(), e))
    .build();
```

### Thread Pool Configuration

| Pool Type | Use Case | Configuration |
|-----------|----------|---------------|
| Fixed | Known load | `Executors.newFixedThreadPool(n)` |
| Virtual (Java 21+) | I/O-bound | `Executors.newVirtualThreadPerTaskExecutor()` |
| Scheduled | Periodic tasks | `Executors.newScheduledThreadPool(n)` |

```java
// For HTTP requests (I/O-bound) — virtual threads preferred
ExecutorService httpExecutor = Executors.newVirtualThreadPerTaskExecutor();

// For CPU-bound work — bounded pool
ExecutorService cpuExecutor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);
```

### Graceful Shutdown

```java
public void close() {
    executor.shutdown();

    try {
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            log.warn("Executor did not terminate in time, forcing shutdown");
            executor.shutdownNow();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        executor.shutdownNow();
    }
}
```

---

## Async Naming Conventions

| Sync Method | Async Variant |
|-------------|---------------|
| `send()` | `sendAsync()` |
| `execute()` | `executeAsync()` |
| `get()` | `getAsync()` |
| `find()` | `findAsync()` |
| `process()` | `processAsync()` |

---

## Blocking Operations

### Annotation

- MUST mark blocking methods with `@Blocking` annotation
- MUST provide timeout for all blocking operations
- MUST USE `Duration`, not raw milliseconds

```java
/**
 * Sends message and waits for response.
 * @param timeout maximum wait time
 * @throws TimeoutException if no response within timeout
 */
@Blocking
public Message send(SendMessage request, Duration timeout) {
    return sendAsync(request)
        .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .join();
}
```

---

## Testing Concurrency

### Rules

- MUST USE `CountDownLatch`, not `Thread.sleep()`
- MUST verify thread safety with concurrent tests
- SHOULD USE `jcstress` for lock-free algorithms

```java
@Test
void shouldBeThreadSafe() throws InterruptedException {
    var latch = new CountDownLatch(10);
    var errors = new AtomicInteger();

    for (int i = 0; i < 10; i++) {
        executor.submit(() -> {
            try {
                sender.send(message);
            } catch (Exception e) {
                errors.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }

    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(errors.get()).isZero();
}
```

---

## Related

- [Performance Rules](./performance.md) — thread-safe collections
- [Test Rules](./tests.md) — concurrency testing
- [Core Rules](../rules_core.md) — error handling patterns
