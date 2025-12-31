# Gradle Error Patterns

> Справочник паттернов ошибок Gradle/javac для команды `/fix`

## Compilation Errors

### Missing Symbol (Import)

**Pattern:**
```
error: cannot find symbol
  symbol:   class {ClassName}
  location: class {LocationClass}
```

**Regex:**
```regex
error: cannot find symbol\s+symbol:\s+class (\w+)\s+location: class (\S+)
```

**Extracted:**
- `symbol` — имя класса
- `location` — где используется

**Priority:** 100 (highest — fixes cascade errors)

---

### Missing Symbol (Method)

**Pattern:**
```
error: cannot find symbol
  symbol:   method {methodName}({params})
  location: class {LocationClass}
```

**Regex:**
```regex
error: cannot find symbol\s+symbol:\s+method (\w+)\(([^)]*)\)\s+location: class (\S+)
```

**Priority:** 95

---

### Type Mismatch

**Pattern:**
```
error: incompatible types: {ActualType} cannot be converted to {ExpectedType}
    {code}
    ^
```

**Regex:**
```regex
error: incompatible types: (\S+) cannot be converted to (\S+)
```

**Extracted:**
- `actual` — фактический тип
- `expected` — ожидаемый тип

**Priority:** 80

---

### Nullability (Checker Framework)

**Patterns:**
```
error: [argument] incompatible argument for parameter X
error: [return] incompatible return type
error: [assignment] incompatible types in assignment
```

**Regex:**
```regex
error: \[(argument|return|assignment)\].*incompatible
```

**Priority:** 90

---

### Syntax Errors

| Error | Pattern | Regex |
|-------|---------|-------|
| Missing semicolon | `error: ';' expected` | `error: ';' expected` |
| Missing paren | `error: ')' expected` | `error: '\)' expected` |
| Missing brace | `error: '{' expected` | `error: '\{' expected` |
| Unclosed string | `error: unclosed string literal` | `error: unclosed string literal` |

**Priority:** 60

---

### Access Errors

**Pattern:**
```
error: {member} has private access in {Class}
```

**Regex:**
```regex
error: (\w+) has private access in (\S+)
```

**Priority:** 70

---

### Duplicate Definition

**Pattern:**
```
error: variable {name} is already defined in method {method}
```

**Regex:**
```regex
error: variable (\w+) is already defined
```

**Priority:** 65

---

## Test Failures

### JUnit 5 Assertion

**Pattern:**
```
org.opentest4j.AssertionFailedError: expected: <{expected}> but was: <{actual}>
```

**Regex:**
```regex
AssertionFailedError:.*expected:\s*<([^>]*)>\s*but was:\s*<([^>]*)>
```

**Priority:** 85

---

### AssertJ Assertion

**Pattern:**
```
org.assertj.core.api.AssertionError:
Expecting:
  <{actual}>
to be equal to:
  <{expected}>
but was not.
```

**Regex (multiline):**
```regex
Expecting:\s+<([^>]+)>\s+to be equal to:\s+<([^>]+)>
```

**Priority:** 85

---

### NullPointerException

**Pattern:**
```
java.lang.NullPointerException: Cannot invoke "{method}" because "{reference}" is null
    at {Class}.{method}({File}.java:{line})
```

**Regex:**
```regex
NullPointerException.*at (\S+)\.(\w+)\((\w+\.java):(\d+)\)
```

**Priority:** 95

---

### Mockito Errors

| Error | Pattern |
|-------|---------|
| Missing stub | `org.mockito.exceptions.misusing.UnnecessaryStubbingException` |
| Wrong invocation | `org.mockito.exceptions.misusing.MissingMethodInvocationException` |
| Verification failed | `org.mockito.exceptions.verification.WantedButNotInvoked` |

**Priority:** 75

---

### Timeout

**Pattern:**
```
org.junit.jupiter.api.extension.TimeoutException: {test} timed out after {duration}
```

**Regex:**
```regex
TimeoutException:.*timed out after (\d+)
```

**Priority:** 50

---

## File Location Extraction

**Pattern:**
```
{FilePath}.java:{line}: error: {message}
```

**Regex:**
```regex
([\w/]+\.java):(\d+): error: (.+)
```

**Extracted:**
- `file` — путь к файлу
- `line` — номер строки
- `message` — сообщение об ошибке

---

## Cascade Error Detection

**Признаки cascade errors:**
1. Несколько ошибок в одном файле
2. Ошибка "cannot find symbol" для метода класса, который не импортирован
3. Ошибки на последовательных строках

**Правило:** Исправляй ошибки с высшим приоритетом первыми — они часто являются root cause.

---

## Common Import Mappings

### Java Standard Library

| Class | Package |
|-------|---------|
| `List`, `Set`, `Map`, `ArrayList`, `HashMap` | `java.util` |
| `Optional`, `Stream`, `Collectors` | `java.util` / `java.util.stream` |
| `File`, `InputStream`, `OutputStream` | `java.io` |
| `Path`, `Files` | `java.nio.file` |
| `LocalDate`, `LocalDateTime`, `Instant`, `Duration` | `java.time` |
| `Pattern`, `Matcher` | `java.util.regex` |
| `Supplier`, `Function`, `Consumer`, `Predicate` | `java.util.function` |

### Lombok

| Annotation | Package |
|------------|---------|
| `@Slf4j`, `@Log4j2` | `lombok.extern.slf4j` / `lombok.extern.log4j` |
| `@Value`, `@Data`, `@Builder` | `lombok` |
| `@Getter`, `@Setter`, `@RequiredArgsConstructor` | `lombok` |
| `@NonNull` | `lombok` |

### Jackson

| Class | Package |
|-------|---------|
| `ObjectMapper` | `com.fasterxml.jackson.databind` |
| `@JsonProperty`, `@JsonIgnore` | `com.fasterxml.jackson.annotation` |

### Testing

| Class | Package |
|-------|---------|
| `@Test`, `@BeforeEach`, `@Nested` | `org.junit.jupiter.api` |
| `assertThat` | `org.assertj.core.api.Assertions` |
| `@Mock`, `@InjectMocks`, `when()` | `org.mockito` / `org.mockito.Mockito` |

### Project-Specific (JTGbots)

| Class | Package |
|-------|---------|
| `@NonNull`, `@Nullable` | `org.checkerframework.checker.nullness.qual` |
| `ApiResult` | `io.github.jtgbots.api` |
| `TelegramException` | `io.github.jtgbots.exception` |