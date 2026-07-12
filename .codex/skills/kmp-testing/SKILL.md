---
name: kmp-testing
description: Plan, implement, or review deterministic tests for KitchenTimer. Use for common tests, timer edge cases, fake clocks/tickers, ViewModel/state-holder tests, platform contract seams, regression coverage, and choosing focused Gradle verification commands for Kotlin Multiplatform changes.
---

# KMP Testing

Use this skill when behavior needs verification.

## Workflow

1. Identify the invariant or regression risk.
2. Prefer common tests for timer state transitions.
3. Use fake time sources instead of real delays.
4. Keep tests focused and deterministic.
5. Run the narrowest relevant Gradle task.

## Verification Commands

- Timer/domain tests: `./gradlew :shared:allTests`
- Shared compile check: `./gradlew :shared:assemble`
- Android unit tests when present: `./gradlew :androidApp:testDebugUnitTest`
- Shared UI compile check: `./gradlew :androidApp:assembleDebug`

## Reference

Read `references/test-patterns.md` before adding time-based tests.
