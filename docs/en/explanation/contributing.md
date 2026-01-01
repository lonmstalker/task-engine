# Contributing

## Local setup

- Java 21 is required (Gradle toolchain is configured).
- Build and tests: `./gradlew check`

## Documentation

- Keep docs in the Diataxis structure under `docs/`.
- Add new tutorials and how-to guides as separate files.

## Release

### Prerequisites

- Create a Central Portal user token: `https://central.sonatype.com/usertoken`.
- Use the token username/password (not your portal login).
- Configure signing with `signingKey` (or `signingKeyRingFile`) and `signingPassword`.
- Publishing uses the OSSRH staging API compatibility endpoint.

### Gradle properties

Add the publish credentials and endpoint to `~/.gradle/gradle.properties`:

```properties
publishUrl=https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/
publishUsername=<portal-token-username>
publishPassword=<portal-token-password>
```

### Publish

1. Update `gradle.properties` and `CHANGELOG.md`.
2. Publish artifacts: `./gradlew -Prelease publish` (or `-PreleaseVersion=1.0.0`).
3. Transfer the staging repository to the Central Portal (must be the same IP as the publish step):

```bash
TOKEN_USER=...
TOKEN_PASS=...
NAMESPACE=io.github.lonmstalker

auth=$(printf '%s:%s' "$TOKEN_USER" "$TOKEN_PASS" | base64 | tr -d '\n')

curl -X POST -H "Authorization: Bearer $auth" \
  "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/$NAMESPACE"
```

### If the IP is different

1. Find the repository key:

```bash
curl -H "Authorization: Bearer $auth" \
  "https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?ip=any&profile_id=$NAMESPACE"
```

2. Upload by repository key:

```bash
curl -X POST -H "Authorization: Bearer $auth" \
  "https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/<repositoryKey>"
```

### Cleanup

If a deployment fails, drop the broken repository and re-publish:

```bash
curl -X DELETE -H "Authorization: Bearer $auth" \
  "https://ossrh-staging-api.central.sonatype.com/manual/drop/repository/<repositoryKey>"
```

### Verify

Check deployment status in `https://central.sonatype.com/publishing/deployments`.
