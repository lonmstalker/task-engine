# Documentation Rules

> Rules for Javadoc and docs/ documentation. Load when writing public API or documentation.

---

## Javadoc Rules

### When Required

| Element | Javadoc Required |
|---------|------------------|
| Public class/interface | Always |
| Public method | Always |
| Public field/constant | Always |
| Protected method | If part of extension API |
| Package-private | Only for complex logic |
| Private | No (use inline comments if needed) |

### Required Tags

| Tag | When | Example |
|-----|------|---------|
| `@param` | Every parameter | `@param chatId the target chat ID` |
| `@return` | Non-void methods | `@return result containing Message on success` |
| `@throws` | Checked + important unchecked | `@throws TelegramApiException if API call fails` |
| `@see` | Related classes/methods | `@see SendMessageRequest` |
| `@since` | New API in version > 1.0.0 | `@since 1.2.0` |

### Format Template

```java
/**
 * Brief one-line description ending with period.
 *
 * <p>Optional detailed description. Can span multiple lines.
 * Explain behavior, edge cases, thread-safety if relevant.
 *
 * @param paramName description starting lowercase, no period
 * @return description starting lowercase, no period
 * @throws ExceptionType description starting lowercase
 * @see RelatedClass
 * @see #relatedMethod()
 * @since X.Y.Z
 */
```

### Example

```java
/**
 * Sends a message to the specified chat.
 *
 * <p>The message is sent asynchronously. Use the returned result
 * to handle success or failure cases.
 *
 * @param chatId the target chat identifier
 * @param text the message text, supports Markdown formatting
 * @return result containing {@link Message} on success,
 *         or failure with error details
 * @throws TelegramApiException if the API call fails
 * @see SendMessageRequest
 * @see ParseMode
 * @since 1.0.0
 */
public @NonNull ApiResult<Message> sendMessage(
    @NonNull ChatId chatId,
    @NonNull String text
);
```

### Style Rules

| Rule | Description |
|------|-------------|
| First sentence | Summary shown in IDE tooltips, end with period |
| `{@code value}` | Use for inline code references |
| `{@link ClassName}` | Use for cross-references to classes/methods |
| NO @author | Use git history instead |
| Minimal HTML | Only `<p>`, `<pre>`, `{@code}` allowed |

### Anti-patterns

- [ ] Empty Javadoc (`/** */`)
- [ ] Only `@param` without descriptions
- [ ] Copy-paste from method signature
- [ ] Outdated documentation not matching code
- [ ] `@author` tags

---

## Documentation Structure (docs/)

### Diátaxis Framework

```
docs/
├── tutorials/              # Learning-oriented: step-by-step learning
│   ├── 01-quick-start.md
│   ├── 02-handling-updates.md
│   └── 03-keyboards.md
│
├── how-to/                 # Task-oriented: solving specific tasks
│   ├── send-messages.md
│   ├── handle-callbacks.md
│   ├── use-webhooks.md
│   └── error-handling.md
│
├── reference/              # Information-oriented: factual information
│   ├── api/                # Generated Javadoc or link
│   ├── configuration.md
│   └── exceptions.md
│
├── explanation/            # Understanding-oriented: concepts
│   ├── architecture.md
│   ├── design-decisions.md
│   └── contributing.md
│
├── plans/                  # Design documents
│
└── ru/                     # Optional Russian translation
    └── tutorials/
```

### When to Use Each Type

| Type | Purpose | Audience | Example |
|------|---------|----------|---------|
| Tutorial | Teach by doing | New users | "Build your first bot" |
| How-to | Solve a problem | Users with goal | "How to handle callbacks" |
| Reference | Provide facts | Users looking up info | "Configuration options" |
| Explanation | Build understanding | Users wanting depth | "Why we use ApiResult" |

---

## File Naming

| Type | Pattern | Example |
|------|---------|---------|
| Tutorial | `NN-kebab-case.md` | `01-quick-start.md` |
| How-to | `verb-noun.md` | `send-messages.md` |
| Reference | `noun.md` | `configuration.md` |
| Explanation | `noun.md` | `architecture.md` |

---

## Document Structure

Every document MUST have:

```markdown
# Title

> One-line description of what this document covers.

## Prerequisites (for tutorials/how-to)

- Requirement 1
- Requirement 2

## Content sections...

## Next Steps (for tutorials)

- [Next Tutorial](./02-next.md)
- [Related How-to](../how-to/related.md)
```

---

## Writing Style

| Rule | Good | Bad |
|------|------|-----|
| Active voice | "The bot sends a message" | "A message is sent by the bot" |
| Present tense | "This method returns" | "This method will return" |
| Second person | "You can configure..." | "One can configure..." |
| Short sentences | Max 25 words | Long complex sentences |
| Code examples | Every concept | Walls of text |

---

## Code Examples in docs/

### Requirements

- MUST be complete and runnable
- MUST include imports if non-obvious
- MUST use realistic values (not `foo`, `bar`)
- SHOULD show both success and error cases

### Good Example

```java
import io.github.jtgbots.TelegramBot;
import io.github.jtgbots.model.Message;

var bot = TelegramBot.create("123456:ABC-DEF...")
    .onCommand("/start", ctx -> {
        ctx.reply("Welcome to the bot!");
    })
    .onMessage(ctx -> {
        ctx.reply("You said: " + ctx.getText());
    })
    .start();
```

### Bad Example

```java
var x = new Bot(token);
x.handle(cmd, (c) -> c.send(msg));
```

---

## Cross-linking

| Type | Format |
|------|--------|
| Internal docs | `[Configuration](../reference/configuration.md)` |
| Javadoc class | `{@link ClassName}` or link to generated docs |
| External | `[Telegram Bot API](https://core.telegram.org/bots/api)` |

---

## Language

| Content | Language |
|---------|----------|
| Javadoc | English |
| docs/ (main) | English |
| docs/ru/ | Russian (optional translation) |
| Code comments | English |

---

## See Also

- [Documentation Checklist](../checklist/documentation.md)
- [Diátaxis Framework](https://diataxis.fr/)