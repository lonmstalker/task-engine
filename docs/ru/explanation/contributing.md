# Вклад в проект

## Локальная настройка

- Требуется Java 21 (Gradle toolchain настроен).
- Сборка и тесты: `./gradlew check`

## Документация

- Документы организованы по Diataxis в `docs/`.
- Добавляйте новые уроки и how-to как отдельные файлы.

## Релиз

### Подготовка

- Создайте Central Portal user token: `https://central.sonatype.com/usertoken`.
- Используйте username/password токена, а не пароль от портала.
- Настройте подпись: `signingKey` (или `signingKeyRingFile`) и `signingPassword`.
- Публикация идет через OSSRH staging API совместимости.

### Gradle properties

Добавьте параметры публикации в `~/.gradle/gradle.properties`:

```properties
publishUrl=https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/
publishUsername=<portal-token-username>
publishPassword=<portal-token-password>
```

### Публикация

1. Обновите `gradle.properties` и `CHANGELOG.md`.
2. Опубликуйте артефакты: `./gradlew -Prelease publish` (или `-PreleaseVersion=1.0.0`).
3. Передайте staging в Central Portal (должен быть тот же IP, что и при публикации):

```bash
TOKEN_USER=...
TOKEN_PASS=...
NAMESPACE=io.github.lonmstalker

auth=$(printf '%s:%s' "$TOKEN_USER" "$TOKEN_PASS" | base64 | tr -d '\n')

curl -X POST -H "Authorization: Bearer $auth" \
  "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/$NAMESPACE"
```

### Если IP другой

1. Найдите ключ репозитория:

```bash
curl -H "Authorization: Bearer $auth" \
  "https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?ip=any&profile_id=$NAMESPACE"
```

2. Передайте staging по ключу:

```bash
curl -X POST -H "Authorization: Bearer $auth" \
  "https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/<repositoryKey>"
```

### Очистка

Если деплой упал, удалите staging и повторите публикацию:

```bash
curl -X DELETE -H "Authorization: Bearer $auth" \
  "https://ossrh-staging-api.central.sonatype.com/manual/drop/repository/<repositoryKey>"
```

### Проверка

Статус деплоя: `https://central.sonatype.com/publishing/deployments`.
