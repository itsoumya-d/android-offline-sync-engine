# Android Offline Sync Engine

Modular, offline-first synchronization engine for modern Android applications built with Kotlin Coroutines, StateFlow, and exponential backoff retry policies.

## Features
- **Offline-First Synchronization**: Queues operations locally when offline and drains sequentially upon reconnection.
- **Exponential Backoff with Jitter**: Robust retry policy preventing thundering herd problems on mobile networks.
- **Reactive State Flow**: Observable `SyncState` (`Idle`, `Syncing`, `Offline`, `Error`) exposed via Kotlin `StateFlow`.
- **Pluggable Conflict Resolution**: Supports Client-Wins, Server-Wins, and Timestamp-based resolution strategies.
- **Zero Android SDK Coupling in Core**: Pure Kotlin JVM / KMP compatible design for instant test execution in CI/CD without an emulator.
- **100% Test Coverage**: Comprehensive unit test suite covering network transitions, retries, and race conditions.

## Architecture
```
+-------------------------------------------------------------+
|                        UI / ViewModels                      |
+-------------------------------------------------------------+
                               |
                        observes StateFlow
                               v
+-------------------------------------------------------------+
|                         SyncManager                         |
|  - Manages sync lifecycle and network status triggers       |
|  - Dispatches operations through ConflictResolver           |
+-------------------------------------------------------------+
          |                                      |
          v                                      v
+-----------------------+              +----------------------+
|       SyncQueue       |              |      RetryPolicy     |
| (Local Mutation Log)  |              | (Exponential Jitter) |
+-----------------------+              +----------------------+
```

## Build & Test
```bash
./gradlew test
./gradlew check
```
