### Input Validation

- [ ] Validate at system boundaries
- [ ] Use whitelist, not blacklist
- [ ] Check length limits
- [ ] Validate numeric bounds
- [ ] Sanitize for logging

### Secrets

- [ ] Never log tokens/passwords
- [ ] Use environment variables for secrets
- [ ] Mask sensitive data in errors
- [ ] Zero out char[] passwords after use
- [ ] No hardcoded secrets in code

### Injection Prevention

- [ ] Use parameterized queries
- [ ] Avoid shell command execution
- [ ] Validate callback data format
- [ ] Sanitize log inputs
- [ ] Use ProcessBuilder with arrays

### Defensive Coding

- [ ] Defensive copy mutable inputs
- [ ] Return immutable collections
- [ ] Copy arrays on get/set
- [ ] Don't expose internal state

### Error Safety

- [ ] No internal paths in messages
- [ ] No stack traces to users
- [ ] Generic error messages
- [ ] Log details separately
- [ ] Don't reveal system info

### Authentication

- [ ] Validate token format
- [ ] Use role-based access control
- [ ] Verify webhook secret
- [ ] HTTPS only
- [ ] Don't disable certificate validation