### Allocations

- [ ] Avoid boxing in hot paths
- [ ] Reuse objects in loops (StringBuilder, etc.)
- [ ] Use primitive arrays when possible
- [ ] Cache compiled Patterns

### Collections

- [ ] Set initial capacity for known sizes
- [ ] Choose appropriate collection type
- [ ] Use specialized primitive collections if needed

### Caching

- [ ] Cache expensive computations
- [ ] Use appropriate TTL strategy
- [ ] Set maximum size to bound memory
- [ ] Monitor cache hit rates

### Hot Paths

- [ ] Identify and profile hot paths
- [ ] Minimize object creation
- [ ] Avoid exceptions for control flow
- [ ] Consider inlining critical operations
- [ ] Use primitives over wrappers

### Memory

- [ ] Use flyweight for frequent values
- [ ] Prefer primitives over wrappers
- [ ] Use compact data structures
- [ ] Avoid unnecessary object wrapping