# KitchenTimer Project Map

## Target Shape

KitchenTimer is a small KMP app with shared domain logic and shared Compose Multiplatform UI.

Grow the app in this order:

1. Common timer state and transitions.
2. Shared Compose screen and components.
3. Common tests for timer rules.
4. Platform notification, sound, and haptic adapters.
5. Persistence or background behavior after the foreground MVP works.

## Guardrails

- Keep `androidApp` and `iosApp` thin.
- Avoid adding dependencies until the current Kotlin, Compose, lifecycle, and coroutines stack is insufficient.
- Keep display formatting deterministic and testable.
- Treat completion as a state transition plus one-shot side effect, not repeated UI recomposition behavior.

## Verification

- Domain-only changes: `./gradlew :shared:allTests`
- Shared UI/resource changes: `./gradlew :androidApp:assembleDebug`
- Gradle/source-set changes: `./gradlew :shared:assemble` and `./gradlew :androidApp:assembleDebug`
