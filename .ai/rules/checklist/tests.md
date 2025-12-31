### Structure

- [ ] `@DisplayName` on class (tested class name)
- [ ] `@DisplayName` on all test methods (in Russian)
- [ ] `@Nested` for grouping related tests
- [ ] `@BeforeEach` for common setup
- [ ] Helper methods at end of class
- [ ] Test fixtures as `static` inner classes

### Naming

- [ ] File: `*Test` for unit, `*IT` for integration
- [ ] Methods: `shouldXxxWhenYyy` or `xxxShouldYyy`
- [ ] Variable `sut` for tested object
- [ ] Prefix `mock` for mock dependencies

### Assertions

- [ ] Only AssertJ (`assertThat()`)
- [ ] `assertThatThrownBy()` for exceptions
- [ ] `assertThatCode(...).doesNotThrowAnyException()` for no exceptions
- [ ] Custom assertions for domain objects (keyboards, responses)

### Test Cases

- [ ] Happy path covered
- [ ] Edge cases (null, empty, boundaries)
- [ ] Error scenarios (exceptions)
- [ ] State transitions (if FSM/lifecycle)

### Integration Tests

- [ ] `@AfterEach` with cleanup
- [ ] Timeouts on async operations
- [ ] `CountDownLatch` instead of `Thread.sleep()`
- [ ] State verification (CREATED → RUNNING → STOPPED)

### Stub DSL

- [ ] `try-with-resources` for auto-verify
- [ ] Explicit `Strictness` if not STRICT
- [ ] Explicit `MatchingStrategy` if not ORDERED
- [ ] `required()` / `optional()` for stubs
- [ ] `tg.calls()` verification if needed

### Anti-patterns (Must Be Absent)

- [ ] No `Thread.sleep()` without timeouts
- [ ] No tests without assertions
- [ ] No dependencies between tests
- [ ] No hardcode (use constants or builders)
- [ ] No JUnit assertions (`assertEquals`, `assertTrue`)
- [ ] No ignoring exceptions in tests