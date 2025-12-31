## BASE
- MUST USE Tests use AssertJ, not JUnit assertions
- MUST USE Naming (Pattern: shouldXxx_WhenYyy):
  - ```java
    @Test
    void shouldRouteStartCommand() { ... }
    ```
- MUST USE @DisplayName: Used on **all** test classes and methods. Language — **English** for consistency:
- MUST USE `@Nested` for logical test grouping
- MUST USE Given-When-Then: Comments **mandatory** for complex tests

## Testing Pyramid

```
        /\
       /  \      E2E / Manual (minimum)
      /────\
     /      \    Integration Tests (*IT)
    /────────\
   /          \  Unit Tests (*Test) — foundation
  ──────────────
```

| Type | Quantity | Speed | Isolation |
|------|----------|-------|-----------|
| Unit | ~70% | Fast (<100ms) | Full (mocks) |
| Integration | ~25% | Medium (<5s) | Partial |
| E2E | ~5% | Slow | None |

## FIRST Principles

| Principle | Description | Example |
|-----------|-------------|---------|
| **F**ast | Quick, don't slow CI | Unit tests <100ms |
| **I**ndependent | Don't depend on each other | Each test isolated |
| **R**epeatable | Deterministic results | No time/network dependency |
| **S**elf-validating | Automatic pass/fail | AssertJ assertions |
| **T**imely | Written in time (before code) | TDD |

---

## Test Case Categories

### Mandatory Categories

| Category | When to Write | Examples |
|----------|--------------|----------|
| **Happy Path** | Always | Successful scenario, main use case |
| **Edge Cases** | Always | `null`, empty values, boundaries |
| **Error Scenarios** | Always | Exceptions, incorrect input |

### As Needed

| Category | When to Write | Examples |
|----------|--------------|----------|
| **State Transitions** | FSM, lifecycle | CREATED → RUNNING → STOPPED |
| **Concurrency** | Multithreading | Race conditions, deadlocks |
| **Performance** | Critical code | Load tests |