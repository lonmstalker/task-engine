# Fix Strategies

> Стратегии исправления ошибок для команды `/fix`

## Strategy Overview

| Strategy | Priority | Safe | Auto-apply |
|----------|----------|------|------------|
| ImportFixer | 100 | ✅ yes | ✅ yes |
| NullabilityFixer | 90 | ⚠️ depends | ⚠️ ask if logic |
| TypeMismatchFixer | 80 | ⚠️ depends | ⚠️ ask if semantic |
| TestFixer | 70 | ❌ no | ❌ always ask |
| SyntaxFixer | 60 | ✅ yes | ✅ yes |
| AccessFixer | 55 | ⚠️ depends | ⚠️ ask |
| FallbackExplainer | 0 | N/A | N/A |

---

## ImportFixer

### Trigger
```
error: cannot find symbol
  symbol:   class {ClassName}
```

### Algorithm

```
1. Check project classes:
   grep -r "class {ClassName}" --include="*.java"

2. Check Java standard library:
   - java.util.* (List, Map, Set, Optional, Stream)
   - java.io.* (File, InputStream)
   - java.time.* (LocalDate, Instant)
   - java.nio.* (Path, Files)

3. Check dependencies:
   - Lombok (@Slf4j, @Value, @Builder)
   - Jackson (ObjectMapper, @JsonProperty)
   - JUnit/AssertJ (@Test, assertThat)
   - Checker Framework (@NonNull, @Nullable)

4. If multiple matches → ask user

5. Apply:
   - Find package statement
   - Find first import or class declaration
   - Insert import between them
```

### Fix Template

```java
// Before
package com.example;

public class MyClass {
    private List<String> items;  // ERROR: cannot find symbol
}

// After
package com.example;

import java.util.List;

public class MyClass {
    private List<String> items;  // OK
}
```

### Explanation Template

```
✅ Fixed: Added import for {ClassName}
   Package: {full.package.name}
   Reason: {Standard Java collection | Lombok annotation | ...}
   Reference: Common Java import
```

---

## NullabilityFixer

### Trigger
- `@Nullable`/`@NonNull` mismatch
- Potential NPE detected by Checker Framework
- NPE in test execution

### Options

| Situation | Option A | Option B | Option C |
|-----------|----------|----------|----------|
| Method returns nullable | Add `@Nullable` | Return `Optional<T>` | Add null-check |
| Parameter may be null | Add `@Nullable` | Add `Objects.requireNonNull()` | Default value |
| Using nullable value | Add null-check | Use `Objects.requireNonNullElse()` | Change contract |

### Fix Templates

**Option A: Add annotation**
```java
// Before
public String getValue() { return this.value; }

// After
public @Nullable String getValue() { return this.value; }
```

**Option B: Add null-check**
```java
// Before
public void process(String value) {
    value.length();  // NPE risk
}

// After
public void process(String value) {
    Objects.requireNonNull(value, "value must not be null");
    value.length();
}
```

**Option C: Safe access**
```java
// Before
return user.getName().toUpperCase();

// After
return user.getName() != null ? user.getName().toUpperCase() : null;
```

### Explanation Template

```
⚠️ Fixed: Added @Nullable annotation
   Rule: Per rules_core.md — "All public methods must have @NonNull/@Nullable"
   Why: Method may return null when {condition}
   Alternative: Could return Optional<T> for clearer contract
```

### Safety Classification

| Fix Type | Safe? |
|----------|-------|
| Add `@Nullable` annotation | ✅ Yes |
| Add `@NonNull` annotation | ✅ Yes |
| Add `Objects.requireNonNull()` | ⚠️ Ask (changes behavior) |
| Add null-check with return | ⚠️ Ask (changes logic) |
| Change return type to Optional | ⚠️ Ask (API change) |

---

## TypeMismatchFixer

### Trigger
```
error: incompatible types: {Actual} cannot be converted to {Expected}
```

### Conversion Table

| From | To | Fix |
|------|-----|-----|
| `int` | `String` | `String.valueOf(value)` |
| `Integer` | `String` | `String.valueOf(value)` or `value.toString()` |
| `String` | `int` | `Integer.parseInt(value)` |
| `String` | `Integer` | `Integer.valueOf(value)` |
| `long` | `int` | `(int) value` or `Math.toIntExact(value)` |
| `double` | `int` | `(int) value` or `Math.round(value)` |
| `Object` | `SpecificType` | `(SpecificType) object` |
| `List<A>` | `List<B>` | `.stream().map(...).toList()` |
| `Optional<T>` | `T` | `.orElse(default)` or `.orElseThrow()` |

### Fix Templates

**Primitive to String:**
```java
// Before
String id = userId;  // userId is int

// After
String id = String.valueOf(userId);
```

**String to Primitive:**
```java
// Before
int count = countStr;  // countStr is String

// After
int count = Integer.parseInt(countStr);
```

**Type cast:**
```java
// Before
MyClass obj = genericObject;

// After
MyClass obj = (MyClass) genericObject;
// Or with check:
if (genericObject instanceof MyClass myObj) {
    // use myObj
}
```

### Explanation Template

```
⚠️ Fixed: Type conversion from {Actual} to {Expected}
   Method: {conversion method}
   Note: {semantic meaning preserved | potential data loss | ...}
```

### Safety Classification

| Conversion | Safe? |
|------------|-------|
| Same value, different representation | ✅ Yes |
| Narrowing (long→int, double→int) | ⚠️ Ask (data loss) |
| Unchecked cast | ⚠️ Ask (ClassCastException risk) |
| Collection transformation | ⚠️ Ask (semantic change) |

---

## TestFixer

### Trigger
- Assertion failure
- NullPointerException in test
- Missing mock/stub

### Sub-strategies

#### AssertionFixer

```
Expected: <5>
Actual: <3>

Analysis:
- Method: calculateSum(2, 3)
- Expected: 5
- Actual: 3

Decision required:
A) Fix test → change expected to 3 (if implementation is correct)
B) Fix code → investigate calculateSum (if test is correct)
```

**NEVER auto-apply** — always ask user!

#### MockFixer

**Missing @Mock:**
```java
// Before
private UserRepository userRepository;

// After
@Mock
private UserRepository userRepository;
```

**Missing stub:**
```java
// Before
// (no setup)

// After
when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));
```

**Missing @ExtendWith:**
```java
// Before
class MyTest {

// After
@ExtendWith(MockitoExtension.class)
class MyTest {
```

#### SetupFixer

```java
// Before
private MyService sut;

@Test
void test() {
    sut.doSomething();  // NPE: sut is null
}

// After
private MyService sut;

@BeforeEach
void setUp() {
    sut = new MyService(mockDependency);
}

@Test
void test() {
    sut.doSomething();
}
```

### Explanation Template

```
❓ Test failure requires decision:

Expected: {expected}
Actual: {actual}
Location: {file}:{line}

Options:
A) Fix TEST — the implementation is correct, test expectation was wrong
B) Fix CODE — the test is correct, implementation has a bug

Which should I fix?
```

---

## SyntaxFixer

### Trigger
- Missing semicolon
- Missing brackets
- Unclosed strings

### Fix Table

| Error | Pattern | Fix |
|-------|---------|-----|
| `';' expected` | Statement without `;` | Add `;` at end |
| `')' expected` | Unclosed `(` | Find matching, add `)` |
| `'}' expected` | Unclosed `{` | Find matching, add `}` |
| `unclosed string literal` | `"text` | Add closing `"` |

### Algorithm

```
1. Parse error location (file:line)
2. Read line content
3. Analyze context (method, block, statement)
4. Determine missing character
5. Insert at correct position
```

### Explanation Template

```
✅ Fixed: Syntax error at {file}:{line}
   Issue: Missing semicolon
   Rule: Java statements must end with semicolon
```

---

## AccessFixer

### Trigger
```
error: {member} has private access in {Class}
```

### Options

| Solution | When to Use |
|----------|-------------|
| Make method public | If it should be API |
| Use getter | If encapsulation needed |
| Make package-private | If same package |
| Create accessor method | If controlled access |

### Explanation Template

```
⚠️ Access issue: {member} is private in {Class}

Options:
A) Change to public (breaks encapsulation)
B) Use getter method: get{Member}()
C) Make package-private (if same package)

Recommendation: B — preserves encapsulation
```

---

## FallbackExplainer

### Trigger
- Unknown error pattern
- Complex error requiring refactoring
- Multiple interrelated errors

### Output Template

```
❓ Cannot auto-fix: {error category}

**Error:**
{full error message}

**Location:** {file}:{line}

**Analysis:**
{explanation of what the error means}

**Possible causes:**
1. {cause 1}
2. {cause 2}

**Suggested actions:**
- [ ] Check {related code}
- [ ] Review {documentation}
- [ ] Consider {architectural change}

**Action taken:**
Created task in backlog.md for manual resolution
```

---

## Decision Flowchart

```
Error detected
     │
     ▼
┌─────────────────┐
│ Classify error  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────┐
│ Import error?   │────▶│ ImportFixer     │──▶ Auto-apply
└────────┬────────┘     └─────────────────┘
         │ No
         ▼
┌─────────────────┐     ┌─────────────────┐
│ Syntax error?   │────▶│ SyntaxFixer     │──▶ Auto-apply
└────────┬────────┘     └─────────────────┘
         │ No
         ▼
┌─────────────────┐     ┌─────────────────┐
│ Nullability?    │────▶│ NullabilityFixer│──▶ Ask if logic change
└────────┬────────┘     └─────────────────┘
         │ No
         ▼
┌─────────────────┐     ┌─────────────────┐
│ Type mismatch?  │────▶│ TypeMismatchFixer│──▶ Ask if semantic
└────────┬────────┘     └─────────────────┘
         │ No
         ▼
┌─────────────────┐     ┌─────────────────┐
│ Test failure?   │────▶│ TestFixer       │──▶ Always ask
└────────┬────────┘     └─────────────────┘
         │ No
         ▼
┌─────────────────┐
│ FallbackExplainer│──▶ Explain + backlog
└─────────────────┘
```