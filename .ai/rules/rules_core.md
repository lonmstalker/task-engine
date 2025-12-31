# Core Rules

> Основные правила проекта. **Этот файл всегда должен быть в памяти.**

## Quick Reference (Extended Rules)

| When | Load |
|------|------|
| Writing tests | [extended/tests.md](./extended/tests.md) |
| Writing docs/Javadoc | [extended/documentation.md](./extended/documentation.md) |
| Async/threading | [extended/concurrency.md](./extended/concurrency.md) |
| Performance optimization | [extended/performance.md](./extended/performance.md) |
| Telegram API/webhooks | [extended/telegram.md](./extended/telegram.md) |
| Git commit/branch/PR | [extended/git.md](./extended/git.md) |
| Code generation | [codegen/00-overview.md](./codegen/00-overview.md) |
| JSON serialization | [ADR-003](../decisions/ADR-003-json-serialization-strategy.md) |
| Errors/stuck | [extended/troubleshooting.md](./extended/troubleshooting.md) |

---

## Code

- MUST BE All public methods have `@NonNull`/`@Nullable` annotations:
    - public @Nullable Integer XXX(@NonNull Object obj
    - public void XXX(Object.@NonNull Inner obj))
    - public List<@Nullable String> XXX(Object.@NonNull Inner obj))
- MUST BE use `@NonNull`/`@Nullable` annotations for object fields
- MUST BE validate constructor and method arguments by use `Objects.requireNonNull()` for non-null params and Guava
  Preconditions
- MUST BE Lombok `@Slf4j` instead of manual logger
- MUST BE Collections return empty, never null
- MUST BE Use Google Java Style
- MUST BE Documented API contracts in Javadoc
- DO NOT USE comments for code changelog, only describe hard logic code
- MUST USE custom Runtime exceptions in ApiResult for business errors
- MUST USE import when you need use class, don't use absolute class path, DO NOT USE wildcard import
- SHOULD USE Builder/Configuration instead of overloading
- MUST write code by TDD pattern: write not working tests -> write code -> check green tests

---

## Lombok

| Use                        | Avoid                         |
|----------------------------|-------------------------------|
| `@Value`, record           | `@Data`                       |
| `@Builder`                 | `@Setter`                     |
| `@RequiredArgsConstructor` | `@AllArgsConstructor` (alone) |
| `@Slf4j`                   | Manual logger declaration     |
| `@Getter` (when needed)    | `@ToString` (unless explicit) |

---

## Gradle

- Gradle dependency version must be use from ./gradle/libs.version.toml

---

## Architecture

- MUST use SOLID (IMPORTANT: MUST USE factory pattern and DI for dependencies in services)
- MUST use Value object pattern for domain objects with different types (example: ApiResult with error or value)
- MUST-use Facade for other libraries (exclude: java, guava, apache commons and other utility libraries)
- MUST BE split api and implementation (public api - in api module)
- SHOULD USE Middleware, Plugins, Chains patterns for extensions
- Users MUST CANT tune Library logic (NOT CRITICAL!) by configuration classes
- DO NOT USE god object
- MUST BE prefer Specific Types. BAD: Collection, Object. GOOD: Collection<Route>, String
- MUST BE inject required parameters (most important first) and SHOULD USE step builder pattern
- MUST REMEMBER principles: High Cohesion, Low Coupling, KISS, DRY, YAGNI, Composition > Inheritance
- MUST USE Rich Model and DDD
- TRY TO USE Fallback Pattern, Circuit Breaker

---

## Action Verbs

| Verb           | Use Case                         | Example                                 |
|----------------|----------------------------------|-----------------------------------------|
| `get*`         | Accessor, simple retrieval       | `getUserId()`, `getText()`              |
| `find*`        | Query that may return null/empty | `findById(id)`, `findMatching(pattern)` |
| `create*`      | Factory method                   | `createBuilder()`, `createDefault()`    |
| `with*`        | Return copy with modified value  | `withPriority(HIGH)`, `withTimeout(5s)` |
| `add*`         | Add to collection (mutating)     | `addMiddleware(m)`, `addController(c)`  |
| `is*` / `has*` | Boolean accessor                 | `isEmpty()`, `hasErrors()`              |
| `to*`          | Conversion                       | `toString()`, `toBuilder()`, `toList()` |
| `on*`          | Event handler / callback setup   | `onMessage()`, `onError()`              |
| `execute*`     | Perform action                   | `execute()`, `executeAsync()`           |
| `build*`       | Finalize builder                 | `build()`, `buildAndStart()`            |

---

## Naming Conventions

| Name          | Purpose               | Example                           |
|---------------|-----------------------|-----------------------------------|
| `of(...)`     | Create from values    | `List.of(a, b)`, `Optional.of(x)` |
| `from(...)`   | Convert/parse         | `Duration.from(temporal)`         |
| `create(...)` | Factory with config   | `BotBuilder.create()`             |
| `empty()`     | Empty instance        | `BotResponse.empty()`             |
| `builder()`   | Get builder           | `BotResponse.builder()`           |
| `default*()`  | Default configuration | `defaultConfig()`                 |

---

## Create Class

| Construct                      | When to Use                         | Example                                 |
|--------------------------------|-------------------------------------|-----------------------------------------|
| **Java Record**                | Simple DTOs, operation results      | `SendResult<R>`, `StateKey`             |
| **@Value + @Builder**          | Domain objects with builder pattern | `BotUser`, `BotResponse`, `StateRecord` |
| **final class + final fields** | Services with logic                 | `DefaultDispatcher`, `HandlerMethod`    |

### Decision Tree

```
Is it immutable DTO without logic?
├── Yes → Java Record
└── No → Has builder pattern needed?
    ├── Yes → @Value + @Builder (Lombok)
    └── No → final class + final fields
```

---

## Class Element Order

Elements in a class arranged in specific order:

```
1. Static constants (public static final)
2. Static fields (private static)
3. Instance fields
   - Configuration (final, injected)
   - State (mutable, runtime)
4. Constructors
5. Static factory methods (of(), create(), builder())
6. Public methods
   - Grouped by functionality
   - With section headers
7. Protected/package methods
8. Private methods
9. Inner classes / enums
10. Builder class (if manual)
```

### Example

```java
public class BotDispatcher {

    // ───────────────────────────────────────────────────
    // Constants
    // ───────────────────────────────────────────────────

    private static final Logger log = LoggerFactory.getLogger(BotDispatcher.class);
    private static final int DEFAULT_TIMEOUT = 30;

    // ───────────────────────────────────────────────────
    // Fields
    // ───────────────────────────────────────────────────

    private final BotConfig config;           // Configuration
    private final List<Handler> handlers;     // Configuration
    private volatile boolean running;         // State

    // ===== Constructors =====

    public BotDispatcher(BotConfig config) { ... }

    // ===== Factory Methods =====

    public static BotDispatcher create(BotConfig config) { ... }

    // ===== Public API =====

    public void start() { ... }
    public void stop() { ... }

    // --- Private Helpers ---

    private void processUpdate(Update update) { ... }
}
```

---

## Logging Levels

```java
log.trace("Entering createOrder with {}", order);           // detailed debug
log.debug("Creating order for user {}", order.getUserId()); // debug
log.info("Order {} created successfully", order.getId());   // important events
log.warn("Stock running low for product {}", productId);    // warnings
log.error("Failed to create order", exception);             // errors
```

### When to Use Each Level

| Level | When to Use | Example |
|-------|-------------|---------|
| TRACE | Method entry/exit, loop iterations | `Entering processUpdate with id={}` |
| DEBUG | Variable values, decision branches | `Using handler {} for update type {}` |
| INFO | Business events, state changes | `Bot started on port {}` |
| WARN | Recoverable issues, degraded state | `Rate limit approaching, {} requests remaining` |
| ERROR | Failures requiring attention | `Failed to send message to chat {}` |

---

## Code Breathing

Code SHOULD "breathe". Logical blocks separated by empty lines:

| Location | Empty Line? |
|----------|-------------|
| After variable declarations | YES |
| Before `return` at method end | YES |
| Between logical blocks | YES |
| After `if`/`for`/`while` block | YES |
| Between related single-line operations | NO |

### Example

```java
public Optional<Handler> findHandler(Update update) {
    // Variable declarations
    var updateType = update.getType();
    var handlers = this.handlers;

    // Filtering logic
    for (Handler handler : handlers) {
        if (handler.supports(updateType)) {
            log.debug("Found handler: {}", handler.getClass().getSimpleName());

            return Optional.of(handler);
        }
    }

    // Fallback
    log.warn("No handler found for update type: {}", updateType);

    return Optional.empty();
}
```

---

## Section Dividers

For separating large logical blocks in classes, use ASCII dividers:

| Level | Format | When to Use |
|-------|--------|-------------|
| **Major section** | `// ─────────────────────────────────────────────────` | Between groups (fields, methods) |
| **Section** | `// ===== Section Name =====` | Method groups by functionality |
| **Subsection** | `// --- Subsection ---` | Within a section |

---

## Fluent API / Builder Formatting

Builder chains and fluent API formatted with each method on new line:

```java
// Good
var request = SendMessage.builder()
    .chatId(chatId)
    .text("Hello!")
    .parseMode(ParseMode.HTML)
    .replyMarkup(keyboard)
    .build();

// Bad
var request = SendMessage.builder().chatId(chatId).text("Hello!").parseMode(ParseMode.HTML).build();
```

---

## Method Parameters

Methods with multiple parameters formatted with each parameter on separate line.
**Closing parenthesis `)` on separate line before `{`**:

| Parameters | Formatting |
|------------|------------|
| 1-2 parameters | Single line acceptable |
| 3+ parameters | Each on new line |
| Long types/annotations | Always on new line |

### Examples

```java
// 1-2 parameters — single line OK
public void send(ChatId chatId, String text) {
    ...
}

// 3+ parameters — each on new line
public void sendMessage(
    @NonNull ChatId chatId,
    @NonNull String text,
    @Nullable ParseMode parseMode,
    @Nullable ReplyMarkup replyMarkup
) {
    ...
}
```

---

## Switch Expressions

Java 21+ switch expressions formatted with empty lines between complex cases:

```java
return switch (update.getType()) {
    case MESSAGE -> handleMessage(update.getMessage());
    case CALLBACK_QUERY -> handleCallback(update.getCallbackQuery());

    case INLINE_QUERY -> {
        var query = update.getInlineQuery();
        log.debug("Processing inline query: {}", query.getQuery());
        yield handleInlineQuery(query);
    }

    default -> {
        log.warn("Unknown update type: {}", update.getType());
        yield BotResponse.empty();
    }
};
```

---

## Error Handling Strategy

### When to Throw vs Return

| Situation | Action | Example |
|-----------|--------|---------|
| User input validation | Return error | `ApiResult.failure(ValidationError)` |
| Configuration error | Throw | `IllegalArgumentException` |
| Programming error | Throw | `IllegalStateException`, `NullPointerException` |
| API error (business) | Return error | `ApiResult.failure(TelegramApiException)` |
| Network error (transient) | Retry or return error | Configurable policy |

### ApiResult Pattern

```java
public sealed interface ApiResult<T> permits Success, Failure {

    record Success<T>(T value) implements ApiResult<T> {}
    record Failure<T>(TelegramException error) implements ApiResult<T> {}

    static <T> ApiResult<T> success(T value) {
        return new Success<>(value);
    }

    static <T> ApiResult<T> failure(TelegramException error) {
        return new Failure<>(error);
    }

    default T getOrThrow() {
        return switch (this) {
            case Success<T> s -> s.value();
            case Failure<T> f -> throw f.error();
        };
    }

    default <U> ApiResult<U> map(Function<T, U> mapper) {
        return switch (this) {
            case Success<T> s -> success(mapper.apply(s.value()));
            case Failure<T> f -> failure(f.error());
        };
    }
}
```

---

## Exception Hierarchy

```
TelegramException (base, abstract)
├── TelegramApiException          // API returned error
│   ├── error_code: int
│   ├── description: String
│   └── retryAfter: Duration?
├── TelegramNetworkException      // Connection, timeout
├── TelegramValidationException   // Invalid request data
└── TelegramConfigException       // Invalid configuration
```

---

## Recovery Patterns

### Retry Pattern

```java
public interface RetryPolicy {
    int maxAttempts();
    Duration delayBetweenAttempts(int attempt);
    boolean shouldRetry(TelegramException e);
}
```

### Fallback Pattern

```java
public <T> T executeWithFallback(
    Supplier<T> primary,
    Supplier<T> fallback
) {
    try {
        return primary.get();
    } catch (TelegramException e) {
        log.warn("Primary operation failed, using fallback", e);
        return fallback.get();
    }
}
```

---

## Error Response Formatting

- MUST return structured error (not raw exception message)
- MUST mask sensitive data in error messages
- SHOULD include correlation ID for debugging
- MUST NOT expose internal paths or stack traces

---

## API Markers

```java
@Internal           // Do not use outside the library
@Beta               // May change in next minor version
@Stable             // Stable API, follows deprecation policy
@Deprecated         // Will be removed after 2 minor versions
```

---

## Security

- MUST prefer whitelist: explicitly allow known good values
- MUST USE Input Sanitization
- MUST REMEMBER about Numeric Bounds
- NEVER Log Secrets
- MUST Mask Sensitive Data
- MUST USE Injection Prevention
- MUST REMEMBER Authentication & Authorization:
  - Verify all Tokens
  - remember Role-Based Access

See also: [Telegram Security](./extended/telegram.md#webhook-security)

---

## Releases

```
MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]

Examples:
  1.0.0         — first stable release
  1.2.3         — stable release
  2.0.0-alpha.1 — alpha pre-release
  2.0.0-beta.2  — beta pre-release
  2.0.0-rc.1    — release candidate
  1.2.3+build.456 — with build metadata
```

---

## FAQ

- User input validation failure: MUST BE Return error response, don't throw
- Configuration error (fail fast): MUST BE Throw IllegalArgumentException / IllegalStateException
- Internal programming error: Throw AssertionError (tests) or IllegalStateException

---

## Checklist

- After create plan or write code MUST check checklist by base and specific checklists
  in [BASE AND SPECIFIC CHECKLIST](./checklist)