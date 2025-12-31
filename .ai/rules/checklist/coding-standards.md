### Visual Formatting
- [ ] Logical blocks separated by empty lines
- [ ] Empty line before `return` at method end
- [ ] Builders formatted with each method on new line
- [ ] Method parameters (3+) on separate lines

### Class Structure
- [ ] Fields grouped (Configuration → State)
- [ ] Methods grouped by functionality
- [ ] Sections separated by comments (`// =====` or `// ─────`)

### Documentation
- [ ] Public classes have Javadoc with description
- [ ] Public methods have `@param` and `@return`
- [ ] Comments above code, not to the right

### Naming
- [ ] Variables have descriptive names
- [ ] No `temp`, `data`, `obj`, `x` (except obvious cases)
- [ ] Methods start with verb

### Lombok & Nullability
- [ ] All public methods have `@NonNull`/`@Nullable` annotations
- [ ] `Objects.requireNonNull()` in constructors for non-null params
- [ ] Collections return empty, never null
- [ ] No `@Data`, only `@Value` for immutable objects
- [ ] `@RequiredArgsConstructor` instead of manual constructors (if no validation)
- [ ] `@Slf4j` instead of manual logger declaration