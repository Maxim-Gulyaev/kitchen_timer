---
name: kitchen-timer-kmp
description: Build and maintain the KitchenTimer Kotlin Multiplatform app. Use when implementing timer features, Compose Multiplatform UI, KMP source-set architecture, timer state logic, presets, platform notification/sound/haptic adapters, Gradle changes, or tests in this repository.
---

# KitchenTimer KMP

Use this as the umbrella project skill for KitchenTimer feature work.

## Workflow

1. Read `AGENTS.md` and `RTK.md`.
2. Inspect the smallest relevant slice of the repo with `rg`/`rg --files`.
3. Keep cross-platform behavior in `shared/src/commonMain` by default.
4. Put pure timer rules in common code and platform effects behind small contracts.
5. Preserve user changes in the worktree; do not reset unrelated edits.
6. Verify with the narrowest meaningful Gradle task.

## Default Placement

- Domain model and state: `shared/src/commonMain/kotlin/com/maxim/kitchentimer/timer/`
- Screens: `shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/`
- Components: `shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/components/`
- Theme: `shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/theme/`
- Platform contracts: `shared/src/commonMain/kotlin/com/maxim/kitchentimer/platform/`
- Android actuals: `shared/src/androidMain/kotlin/com/maxim/kitchentimer/platform/`
- iOS actuals: `shared/src/iosMain/kotlin/com/maxim/kitchentimer/platform/`
- Common tests: `shared/src/commonTest/kotlin/com/maxim/kitchentimer/`

## References

- Read `references/project-map.md` before broad feature work or when file placement is unclear.
- Read `references/architecture.md` before broad architecture, state-management, platform-notification, or test-structure changes.
- Use `$timer-domain` for countdown semantics.
- Use `$compose-multiplatform-ui` for UI polish.
- Use `$kmp-gradle-build` for build and dependency changes.
- Use `$platform-adapters` for Android/iOS side effects.
- Use `$kmp-testing` for deterministic test strategy.
