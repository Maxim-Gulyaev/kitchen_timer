@RTK.md

# KitchenTimer Agent Guide

## Project Overview

KitchenTimer is a Kotlin Multiplatform app for a kitchen timer. The current app targets Android and iOS and shares UI through Compose Multiplatform in the `shared` module.

Communicate with the user in Russian. Use normal engineering language and IT terms when they make the explanation shorter or more precise.

## Repository Layout

- `shared/` contains cross-platform Kotlin and Compose Multiplatform UI.
- `shared/src/commonMain/` is the default home for timer domain logic, state, ViewModels, shared UI, themes, and resources.
- `shared/src/androidMain/` is for Android-specific adapters such as sound, vibration, notifications, or platform permissions.
- `shared/src/iosMain/` is for iOS-specific adapters and `MainViewController`.
- `androidApp/` is the Android entry point and should stay thin.
- `iosApp/` is the SwiftUI/Xcode entry point and should stay thin.
- `gradle/libs.versions.toml` owns dependency and plugin versions.
- `.codex/subagents.toml` contains the local Codex runtime subagent registry with prompt/model guidance.
- `.codex/subagents/` contains one TOML preset per subagent.
- `.codex/skills/kitchen-timer-kmp/` contains the umbrella local project skill.
- `.codex/skills/` also contains focused skills for KMP architecture, Compose UI, timer domain logic, Gradle/build work, platform adapters, KMP testing, and compact RTK-style inspection.

## Common Commands

- Build Android debug APK: `./gradlew :androidApp:assembleDebug`
- Build shared module: `./gradlew :shared:assemble`
- Run shared tests: `./gradlew :shared:allTests`
- Run Android unit tests when added: `./gradlew :androidApp:testDebugUnitTest`
- Inspect available Gradle tasks: `./gradlew tasks`

Use the Gradle wrapper. Do not assume a system Gradle install.

## Architecture Conventions

- Keep timer state and business rules in `shared/src/commonMain/kotlin/com/maxim/kitchentimer/timer`.
- Keep Compose screens in `shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui`.
- Keep reusable Compose pieces in `shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/components`.
- Keep theme tokens in `shared/src/commonMain/kotlin/com/maxim/kitchentimer/ui/theme`.
- Use `expect`/`actual` only for platform behavior that cannot live in common code, such as sound, haptics, notifications, or foreground/background lifecycle handling.
- Keep `androidApp` and `iosApp` as composition roots. Avoid putting business logic there.
- Prefer immutable UI state data classes and explicit user intents over ad hoc mutable flags scattered through composables.
- Use Compose Multiplatform Material 3 and existing version-catalog aliases before adding new dependencies.

## Development Rules

- Make small, scoped changes that fit the existing KMP skeleton.
- Before adding a dependency, check whether Compose, Kotlin stdlib, lifecycle, or coroutines already cover the need.
- Do not edit generated files, Gradle caches, `.idea/workspace.xml`, or build output.
- Do not commit or expose secrets from `local.properties`.
- Preserve user changes in the working tree. Never reset or revert unrelated edits.
- For file discovery, prefer `rg` and `rg --files`.

## Testing Guidance

- Timer countdown logic should have common tests where possible.
- UI-only changes should at least compile through `./gradlew :androidApp:assembleDebug`.
- Platform-specific behavior should be behind small contracts so common tests can cover state transitions without needing Android or iOS runtime.
- For time-based logic, prefer injectable clock/ticker abstractions over real delays in tests.

## Agent Workflow

- For subagent delegation, use Codex runtime agents (`explorer`, `worker`, or `default`) and the presets in `.codex/subagents.toml`.
- For architecture questions, use `.codex/subagents/kmp-architect.toml`.
- For Compose UI polish/review, use `.codex/subagents/compose-ui-reviewer.toml`.
- For Gradle/build failures, use `.codex/subagents/gradle-build-doctor.toml`.
- For timer behavior and edge cases, use `.codex/subagents/timer-domain-reviewer.toml`.
- For platform behavior and adapters, use `.codex/subagents/platform-integration-agent.toml`.
- For test planning and regression coverage, use `.codex/subagents/test-strategy-agent.toml`.
- For scoped implementation work, use `.codex/subagents/feature-worker.toml`.
- For repository discovery, use `.codex/subagents/repo-explorer.toml`.
- For feature implementation in this app, load `$kitchen-timer-kmp` from `.codex/skills/kitchen-timer-kmp`.
- For architecture/source-set decisions, load `$kmp-architecture`.
- For Compose Multiplatform UI work, load `$compose-multiplatform-ui`.
- For timer countdown semantics and state transitions, load `$timer-domain`.
- For Gradle/KMP build issues, load `$kmp-gradle-build`.
- For Android/iOS side effects and service contracts, load `$platform-adapters`.
- For deterministic common tests and fake time, load `$kmp-testing`.
- For compact repository inspection, load `$project-rtk-workflow` or the global `$rtk-token-saver`.
