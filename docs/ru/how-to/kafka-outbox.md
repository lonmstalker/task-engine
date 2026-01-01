# Публикация событий цепочек в Kafka

## Предварительные условия

- Убедитесь, что таблицы outbox созданы (они входят в Postgres схему).
- Включите Kafka publisher и задайте `topic` и `bootstrap-servers`.

```yaml
task:
  kafka:
    enabled: true
    topic: "task-events"
    bootstrap-servers:
      - "localhost:9092"
```

## Дополнительные настройки

- Переопределите `TaskEventMessageCodec` (по умолчанию Jackson).
- Переопределите `TaskEventKeyProvider` (ключ по умолчанию = task id).
- Задайте свой `TaskEventOutboxStore`, если Postgres не используется.
