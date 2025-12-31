# Documentation Checklist

## Javadoc (Before Commit)

### Public API

- [ ] All public classes have Javadoc with description
- [ ] All public methods have `@param` for every parameter
- [ ] All public methods have `@return` (if non-void)
- [ ] All public methods have `@throws` for exceptions
- [ ] `@see` links to related classes/methods
- [ ] `@since` tag for new API (if version > 1.0.0)

### Quality

- [ ] First sentence is meaningful summary
- [ ] No empty Javadoc (`/** */`)
- [ ] No copy-paste from method signature
- [ ] Documentation matches actual behavior
- [ ] No spelling errors

### Style

- [ ] Using `{@code}` for inline code
- [ ] Using `{@link}` for cross-references
- [ ] No `@author` tags
- [ ] No unnecessary HTML

---

## docs/ (Before Commit)

### Structure

- [ ] Document in correct folder (tutorials/how-to/reference/explanation)
- [ ] File naming follows convention
- [ ] Has one-line description after title
- [ ] Has Prerequisites section (tutorials/how-to)
- [ ] Has Next Steps section (tutorials)

### Content

- [ ] Code examples are complete and runnable
- [ ] Code examples use realistic values
- [ ] Active voice, present tense
- [ ] Sentences under 25 words
- [ ] Every concept has code example

### Links

- [ ] All internal links are valid
- [ ] Cross-references to related docs
- [ ] External links work

---

## Before Release

### Documentation Completeness

- [ ] README.md is up to date
- [ ] `docs/tutorials/01-quick-start.md` works with current API
- [ ] `docs/reference/configuration.md` reflects all options
- [ ] All public API has Javadoc
- [ ] Breaking changes documented in CHANGELOG.md

### Quality Assurance

- [ ] Javadoc generates without warnings
- [ ] All code examples compile and run
- [ ] No broken links in docs/
- [ ] Version numbers are correct

---

## Anti-patterns (Must Be Absent)

- [ ] No undocumented public API
- [ ] No outdated examples
- [ ] No TODO comments in documentation
- [ ] No placeholder text (`foo`, `bar`, `TBD`)
- [ ] No walls of text without code examples