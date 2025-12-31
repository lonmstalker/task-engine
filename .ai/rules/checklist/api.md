### Before Adding New API

- [ ] Is this the simplest possible interface?
- [ ] Is this CORRESPOND [BASE AND SPECIFIC RULES](../)?
- [ ] Does it follow existing naming patterns?
- [ ] Can it be implemented without breaking existing code?
- [ ] Is it documented with examples?
- [ ] Are edge cases handled (null, empty, boundaries)?
- [ ] Is thread safety documented?
- [ ] Should it be marked `@Beta` initially?

### Before Removing/Changing API

- [ ] Is there an alternative for users?
- [ ] Has deprecation period elapsed (2+ minor versions)?
- [ ] Is migration guide available?
- [ ] Is breaking change documented in CHANGELOG?
- [ ] Does MAJOR version need to be bumped?

### Code Review Checks

- [ ] All public methods have Javadoc
- [ ] All parameters documented with `@param`
- [ ] Return value documented with `@return`
- [ ] `@since` tag present for new API
- [ ] `@throws` for documented exceptions
- [ ] `@see` links to related APIs