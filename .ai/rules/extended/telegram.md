# Telegram-Specific Rules

> Правила специфичные для Telegram Bot API

## Webhook Security

### Secret Token

- MUST verify `X-Telegram-Bot-Api-Secret-Token` header
- MUST generate cryptographically secure token (min 32 chars)
- MUST store token securely (environment variable)

```java
public boolean verifyWebhookRequest(HttpRequest request) {
    String expectedToken = System.getenv("TELEGRAM_WEBHOOK_SECRET");
    String actualToken = request.getHeader("X-Telegram-Bot-Api-Secret-Token");

    return MessageDigest.isEqual(
        expectedToken.getBytes(UTF_8),
        actualToken.getBytes(UTF_8)
    );
}
```

### HTTPS

- MUST USE HTTPS only (no HTTP fallback)
- MUST NOT disable SSL certificate validation
- SHOULD USE valid certificate (Let's Encrypt or paid CA)

### IP Whitelist

- SHOULD USE IP whitelist for Telegram servers
- Telegram IPs: `149.154.160.0/20`, `91.108.4.0/22`

```java
private static final Set<String> TELEGRAM_SUBNETS = Set.of(
    "149.154.160.0/20",
    "91.108.4.0/22"
);

public boolean isFromTelegram(String ip) {
    return TELEGRAM_SUBNETS.stream()
        .anyMatch(subnet -> isInSubnet(ip, subnet));
}
```

---

## Rate Limiting

### Telegram Limits

| Limit Type | Value |
|------------|-------|
| Messages to same chat | 30/second |
| Messages to same group | 20/minute |
| Bulk messages (different chats) | 30/second |
| Inline query results | 50 results max |
| Callback answer | 200 chars max |

### Implementation

- MUST implement rate limiting for outgoing requests
- MUST handle 429 (Too Many Requests) with `retry_after` header
- MUST USE exponential backoff for retries

```java
public class RateLimiter {
    private final RateLimiter limiter = RateLimiter.create(30.0); // 30/sec

    public void acquire() {
        limiter.acquire();
    }
}
```

### 429 Handling

```java
public <T> T executeWithRetry(Supplier<T> request) {
    int attempts = 0;
    while (attempts < MAX_RETRIES) {
        try {
            return request.get();
        } catch (TelegramApiException e) {
            if (e.getErrorCode() == 429) {
                Duration retryAfter = e.getRetryAfter();
                log.warn("Rate limited, waiting {}", retryAfter);
                Thread.sleep(retryAfter.toMillis());
                attempts++;
            } else {
                throw e;
            }
        }
    }
    throw new TelegramException("Max retries exceeded");
}
```

---

## Bot Token Security

### Storage

- MUST store token in environment variable, not code
- MUST NOT log bot token (even partially)
- MUST NOT include token in error messages
- SHOULD rotate token if compromised

```java
// Good
String token = System.getenv("TELEGRAM_BOT_TOKEN");

// Bad
String token = "123456:ABC-DEF...";  // Hardcoded!
log.info("Using token: {}", token);  // Logged!
```

### Masking

```java
public static String maskToken(String token) {
    if (token == null || token.length() < 10) {
        return "***";
    }
    return token.substring(0, 5) + "..." + token.substring(token.length() - 3);
}
// Result: "12345...ABC"
```

---

## API Versioning Strategy

### Backward Compatibility

- MUST support backward compatibility within MAJOR version
- MUST provide migration guide for breaking changes
- MUST deprecate before removing (2 minor versions)

### Deprecation Workflow

```
1. Add @Deprecated annotation with explanation
2. Add @since with deprecation version
3. Log warning on first use
4. Remove after 2 minor versions
```

```java
/**
 * @deprecated Use {@link #sendMessage(SendMessageRequest)} instead.
 *             Will be removed in 2.0.0.
 * @since 1.5.0 (deprecated)
 */
@Deprecated(since = "1.5.0", forRemoval = true)
public Message sendMessage(long chatId, String text) {
    log.warn("sendMessage(long, String) is deprecated, use SendMessageRequest");
    return sendMessage(SendMessageRequest.builder()
        .chatId(chatId)
        .text(text)
        .build());
}
```

### Tracking Telegram API Version

```java
public class TelegramApiVersion {
    public static final String BOT_API_VERSION = "7.1";
    public static final LocalDate BOT_API_DATE = LocalDate.of(2024, 2, 16);
}
```

---

## Update Handling

### Update ID Validation

- MUST validate `update_id` sequence (detect duplicates)
- MUST handle unknown update types gracefully
- MUST USE polling OR webhook, not both

```java
private final AtomicLong lastUpdateId = new AtomicLong(-1);

public boolean shouldProcess(Update update) {
    long updateId = update.getUpdateId();
    long last = lastUpdateId.get();

    if (updateId <= last) {
        log.debug("Skipping duplicate update: {}", updateId);
        return false;
    }

    lastUpdateId.set(updateId);
    return true;
}
```

### Unknown Update Types

```java
public BotResponse handle(Update update) {
    return switch (update.getType()) {
        case MESSAGE -> handleMessage(update.getMessage());
        case CALLBACK_QUERY -> handleCallback(update.getCallbackQuery());
        // ... known types

        default -> {
            log.info("Unknown update type: {}", update.getType());
            yield BotResponse.empty();  // Don't fail!
        }
    };
}
```

### Graceful Shutdown

- SHOULD finish processing current updates before shutdown
- SHOULD acknowledge all received updates

---

## Configuration

### Properties

| Property | Required | Default | Description |
|----------|----------|---------|-------------|
| `telegram.bot.token` | Yes | - | Bot token from @BotFather |
| `telegram.bot.username` | No | - | Bot username (for deep links) |
| `telegram.webhook.url` | Webhook | - | Webhook URL |
| `telegram.webhook.secret` | Webhook | - | Webhook secret token |
| `telegram.polling.timeout` | Polling | 30s | Long polling timeout |
| `telegram.request.timeout` | No | 60s | HTTP request timeout |

### Typed Configuration

- MUST USE typed configuration (not raw Properties)
- MUST validate configuration at startup (fail-fast)
- SHOULD support environment variable override

```java
@Value
@Builder
public class BotConfig {
    @NonNull String token;
    @Nullable String username;
    @Builder.Default Duration requestTimeout = Duration.ofSeconds(60);
    @Builder.Default Duration pollingTimeout = Duration.ofSeconds(30);

    public static BotConfig fromEnvironment() {
        String token = System.getenv("TELEGRAM_BOT_TOKEN");
        Objects.requireNonNull(token, "TELEGRAM_BOT_TOKEN not set");

        return BotConfig.builder()
            .token(token)
            .username(System.getenv("TELEGRAM_BOT_USERNAME"))
            .build();
    }
}
```

---

## Logging Conventions

### PII Handling

| Field | Action | Reason |
|-------|--------|--------|
| `user_id` | Log | Needed for debugging |
| `chat_id` | Log | Needed for debugging |
| `message_id` | Log | Needed for tracking |
| `first_name` | Log | Usually not sensitive |
| `last_name` | Mask or omit | May be sensitive |
| `phone_number` | Mask | Sensitive PII |
| `username` | Log | Public data |

### Masking

```java
public static String maskPhone(String phone) {
    if (phone == null || phone.length() < 4) return "***";
    return "***" + phone.substring(phone.length() - 4);
}
// Result: "***1234"
```

### Log Levels for Telegram

| Level | What to Log |
|-------|-------------|
| TRACE | Raw Update JSON (dev only) |
| DEBUG | Handler routing, decision branches |
| INFO | Successful operations (message sent, callback answered) |
| WARN | Rate limits, retries, recoverable errors |
| ERROR | API errors, network failures, unhandled exceptions |

### Structured Logging

```java
// Good: structured, with context
log.info("Message sent. chatId={}, messageId={}, type={}",
    message.getChatId(),
    message.getMessageId(),
    message.getType());

// Bad: unstructured
log.info("Sent message to " + chatId);
```

---

## Related

- [Security Checklist](../checklist/security.md) — input validation
- [Core Rules](../rules_core.md) — error handling, security
