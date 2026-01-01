# Явная обработка дубликатов

- `TaskKey` - ключ идемпотентности. Повторная отправка возвращает `TaskSubmissionStatus`
  `CREATED`, `UPDATED` или `DUPLICATE`.
- `TaskContextMerger` объединяет контекст только пока задача в `PENDING` или `WAITING_RETRY`.
- `TaskStateMachine.resolveExternalState` решает, применять, игнорировать или отклонять
  входящее состояние.
