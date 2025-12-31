# Task Lib Spring Boot Starter

Spring Boot starter that auto-configures `TaskEngine` and related infrastructure.

## Auto-configuration

Auto-configuration is enabled by default when:
- `TaskEngine` is on the classpath
- a `TaskStore` bean is available
- `task.engine.enabled` is `true` (default)

It registers:
- `TaskDispatcherFactory` (default strategy)
- `TaskDispatcher`
- `TaskEngine`
- `TaskEngineLifecycle`
- `TaskDispatcherReporter` (logs configuration + registers metrics when Micrometer is present)

## Properties

Prefix: `task.engine`

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Enable/disable auto-configuration. |
| `auto-start` | `true` | Start engine automatically via `SmartLifecycle`. |
| `poll-interval` | `PT1S` | Polling interval for claiming tasks. |
| `lease-duration` | `PT30S` | Lease duration for claimed tasks. |
| `recovery-interval` | `PT10S` | Interval for lease recovery. |
| `claim-batch-size` | `100` | Max tasks claimed per poll. |
| `engine-id` | none | Optional engine identifier. |
| `dispatcher.virtual-threads` | `true` | Use virtual threads for dispatching tasks. |
| `dispatcher.parallelism` | CPU count | Dispatcher parallelism hint. |
| `dispatcher.thread-name-format` | `task-worker-%d` | Thread name format for dispatcher threads. |

## Metrics

When a `MeterRegistry` bean is present, the starter registers:
- `task.engine.dispatcher.virtual_threads` (gauge, 1 = true, 0 = false)
- `task.engine.dispatcher.parallelism` (gauge)

It also logs a summary at startup:
```
Task dispatcher configured: type=..., virtualThreads=..., parallelism=..., threadNameFormat=...
```

## Customization

### Custom `TaskDispatcherFactory`

Provide your own factory to replace the default strategy:

```java
@Bean
TaskDispatcherFactory taskDispatcherFactory() {
    return properties -> new CustomDispatcher();
}
```

If your dispatcher implements `TaskDispatcherDescriptor`, the reporter will publish metrics
and include values in the startup log.

### Custom `TaskDispatcher`

Define a `TaskDispatcher` bean to fully override the default:

```java
@Bean
TaskDispatcher taskDispatcher() {
    return new CustomDispatcher();
}
```

## Example

```yaml
task:
  engine:
    dispatcher:
      virtual-threads: true
      parallelism: 64
      thread-name-format: "task-vt-%d"
```
