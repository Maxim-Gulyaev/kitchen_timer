---
name: kitchen-timer-kmp
description: Build and maintain the KitchenTimer Kotlin Multiplatform app. Use when implementing timer features, Compose Multiplatform UI, KMP source-set architecture, timer state logic, presets, platform notification adapters, or tests in this repository.
---

# KitchenTimer KMP

Use this skill when working on the KitchenTimer app in this repository.

## Workflow

1. Read `AGENTS.md` and `RTK.md`.
2. Inspect the existing KMP/Compose structure before editing.
3. Keep feature work in `shared` by default.
4. Put pure timer behavior in common code and platform effects behind small contracts.
5. Verify with the narrowest meaningful Gradle task, then broaden if the change touches shared contracts.

## Default File Placement

- Domain model and state: `shared/src/commonMain/kotlin/com/maxim/kitchentimer/timer/`
- Screens: `shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/`
- Components: `shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/components/`
- Theme: `shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/theme/`
- Platform contracts: `shared/src/commonMain/kotlin/com/maxim/kitchentimer/platform/`
- Android actuals: `shared/src/androidMain/kotlin/com/maxim/kitchentimer/platform/`
- iOS actuals: `shared/src/iosMain/kotlin/com/maxim/kitchentimer/platform/`
- Common tests: `shared/src/commonTest/kotlin/com/maxim/kitchentimer/`

## Implementation Preferences

- Model UI state with immutable data classes.
- Route user actions through explicit intents or callbacks.
- Avoid real delays in tests; inject a ticker, clock, or countdown driver.
- Keep `App()` as the shared composition root.
- Keep Android `MainActivity` and iOS `MainViewController` thin.
- Use Material 3 components already available in the project.
- Avoid adding dependencies until the current stack is insufficient.

## References

Read `references/architecture.md` before making broad architecture, state-management, platform-notification, or test-structure changes.
