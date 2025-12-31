# Git Workflow Rules

> Правила работы с Git: коммиты, ветки, PR

## Conventional Commits

MUST USE Conventional Commits format for all commit messages.

### Types

| Type | When to Use | Example |
|------|-------------|---------|
| `feat` | New feature | `feat(bot): add inline query support` |
| `fix` | Bug fix | `fix(sender): handle rate limit correctly` |
| `docs` | Documentation only | `docs: update API reference` |
| `style` | Formatting, no code change | `style: fix indentation in Handler` |
| `refactor` | Code change without fix/feature | `refactor(dispatcher): extract routing logic` |
| `perf` | Performance improvement | `perf(parser): cache compiled patterns` |
| `test` | Adding/updating tests | `test(sender): add rate limit tests` |
| `chore` | Maintenance | `chore: update dependencies` |
| `build` | Build system changes | `build(gradle): add codegen task` |
| `ci` | CI configuration | `ci: add GitHub Actions workflow` |

---

## Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Rules

- **Subject**: Imperative mood, lowercase, no period, max 50 chars
- **Body**: Optional, wrap at 72 chars, explain "why" not "what"
- **Footer**: Optional, references to issues

### Examples

```
feat(bot): add callback query handler

Implement CallbackQueryHandler for processing button clicks.
Support both inline keyboards and reply keyboards.

Closes #42
```

```
fix(sender): retry on rate limit

Previously the sender would fail immediately on 429.
Now it respects Retry-After header and retries automatically.

Fixes #123
```

```
refactor(codegen)!: change IR structure

BREAKING CHANGE: TypeDefinition now uses sealed interfaces
instead of enum for polymorphic types.
```

---

## Branch Naming

| Pattern | Use Case | Example |
|---------|----------|---------|
| `feature/<issue>-<description>` | New feature | `feature/42-inline-queries` |
| `fix/<issue>-<description>` | Bug fix | `fix/123-rate-limit-retry` |
| `docs/<description>` | Documentation | `docs/api-reference` |
| `refactor/<description>` | Refactoring | `refactor/dispatcher-routing` |
| `release/<version>` | Release preparation | `release/1.2.0` |
| `hotfix/<issue>-<description>` | Production fix | `hotfix/456-critical-bug` |

### Rules

- MUST USE lowercase with hyphens
- MUST include issue number for features and fixes
- SHOULD keep description short (2-4 words)

---

## Pull Request Rules

### Title

- MUST follow commit message format
- MUST be descriptive (same as main commit)

### Description Template

```markdown
## Summary
Brief description of changes.

## Changes
- Added X
- Updated Y
- Fixed Z

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing done

## Related Issues
Closes #42
```

### Requirements

- MUST reference issue in description
- MUST pass all CI checks before merge
- SHOULD have at least one approval
- MUST squash commits on merge (one clean commit)

---

## Protected Branches

| Branch | Protection | Purpose |
|--------|------------|---------|
| `main` | Required reviews, CI pass | Production-ready code |
| `develop` | CI pass | Integration (if used) |
| `release/*` | Required reviews | Release preparation |

---

## Git Hygiene

### DO

- Write meaningful commit messages
- Keep commits atomic (one logical change)
- Rebase feature branches on main before merge
- Use `git pull --rebase` to avoid merge commits

### DON'T

- Force push to shared branches
- Commit generated files (except when intentional)
- Commit secrets or credentials
- Create merge commits in feature branches

---

## Related

- [Releases Checklist](../checklist/releases.md) — release workflow
- [Telegram Rules](./telegram.md) — bot token security
