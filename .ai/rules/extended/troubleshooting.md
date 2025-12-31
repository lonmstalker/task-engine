# Troubleshooting Guide

> Инструкции по восстановлению при ошибках. Загружать при проблемах.

---

## Build Failures

### Compilation Error

```
SITUATION: ./gradlew build fails with compilation error
```

**Steps:**
1. Read the error message carefully — identify file and line
2. Check if error is in generated code → re-run codegen
3. Check for missing imports → add import statement
4. Check for incompatible types → verify @Nullable/@NonNull
5. If still stuck → search codebase for similar patterns

**DO NOT:**
- Blindly add casts or suppressions
- Comment out failing code
- Skip the failing module

---

### Test Failure

```
SITUATION: Tests fail during build
```

**Steps:**
1. Run single failing test: `./gradlew test --tests "ClassName.methodName"`
2. Read assertion error — what was expected vs actual?
3. Check if test is correct or implementation is wrong
4. If flaky test → check for time/order dependencies
5. Fix implementation OR fix test (document why)

**Common Causes:**

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| NPE in test | Missing mock setup | Add `when(...).thenReturn(...)` |
| Assertion mismatch | Logic error | Debug implementation |
| Timeout | Async issue | Use `CountDownLatch`, not `Thread.sleep` |
| Order-dependent | Shared state | Isolate test state in `@BeforeEach` |

---

### Dependency Issues

```
SITUATION: Gradle cannot resolve dependency
```

**Steps:**
1. Check `gradle/libs.versions.toml` for correct version
2. Run `./gradlew dependencies` to see dependency tree
3. Check for version conflicts
4. Try `./gradlew build --refresh-dependencies`

---

## Stuck Situations

### "I don't know where to start"

**Steps:**
1. Read [project-overview.md](../context/project-overview.md)
2. Check "Current Focus" section
3. Read [current_task.md](../tasks/current_task.md)
4. If empty → ask user for direction

---

### "I made changes but broke something"

**Steps:**
1. Run `git diff` — see what changed
2. Run `git stash` — temporarily save changes
3. Run `./gradlew build` — verify clean state works
4. Run `git stash pop` — restore changes
5. Apply changes incrementally, testing after each

---

### "Tests pass locally but I'm not sure it's correct"

**Steps:**
1. Re-read acceptance criteria in SPEC
2. Check edge cases in testcases.md
3. Verify all TDD checklist items in task.md
4. Run code review checklist
5. If still unsure → ask user for review

---

### "I don't understand existing code"

**Steps:**
1. Check [glossary.md](../context/glossary.md) for terminology
2. Look for related tests — they explain expected behavior
3. Check Javadoc on public methods
4. Search for usages with `grep` or IDE
5. Check [decisions/](../decisions/) for architectural context

---

### "Requirements are unclear"

**Steps:**
1. Re-read SPEC document, especially Acceptance Criteria
2. Check Open Questions section in SPEC
3. Look for similar features in codebase
4. If still unclear → ask user for clarification

---

## Recovery Commands

| Situation | Command |
|-----------|---------|
| Undo last commit (keep changes) | `git reset --soft HEAD~1` |
| Discard all local changes | `git checkout -- .` |
| Discard specific file changes | `git checkout -- path/to/file` |
| See what changed | `git diff` |
| See staged changes | `git diff --staged` |
| Clean build artifacts | `./gradlew clean` |
| Full rebuild | `./gradlew clean build` |
| Run specific test | `./gradlew test --tests "TestClass"` |
| Run tests with output | `./gradlew test --info` |
| Check dependency tree | `./gradlew dependencies` |
| Refresh dependencies | `./gradlew build --refresh-dependencies` |

---

## When to Ask User

### MUST ask user if:

- [ ] Build fails with unclear error after 3 attempts
- [ ] Architectural decision needed (not covered by ADR)
- [ ] Requirements are ambiguous after checking SPEC
- [ ] Test reveals spec inconsistency
- [ ] Blocked by external dependency
- [ ] Security-related decision
- [ ] Breaking change to public API

### DO NOT ask if:

- Error message is clear and fixable
- Similar pattern exists in codebase
- Solution documented in rules
- Standard refactoring (rename, extract method)
- Adding tests for existing code

---

## Error Message Patterns

| Error Pattern | Likely Issue | Quick Fix |
|---------------|--------------|-----------|
| `NullPointerException` | Missing null check | Add `Objects.requireNonNull()` or `@Nullable` |
| `ClassNotFoundException` | Missing dependency | Check `build.gradle.kts` |
| `NoSuchMethodError` | Version mismatch | Align versions in `libs.versions.toml` |
| `AssertionError` in test | Logic bug or wrong test | Debug step by step |
| `JsonProcessingException` | Serialization issue | Check `@JsonProperty` annotations |
| `IllegalStateException` | Wrong object state | Check initialization order |

---

## See Also

- [tests.md](./tests.md) — Testing rules and patterns
- [git.md](./git.md) — Git recovery commands
- [project-overview.md](../context/project-overview.md) — Project context
