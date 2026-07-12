---
name: kmp-gradle-build
description: Diagnose or modify KitchenTimer Gradle/Kotlin Multiplatform build configuration. Use for Gradle task failures, source-set dependency placement, version catalog changes, Kotlin/AGP/Compose Multiplatform compatibility, Android build issues, shared tests setup, and deciding the narrowest useful Gradle verification command.
---

# KMP Gradle Build

Use this skill for build, dependency, and Gradle source-set work.

## Workflow

1. Inspect `settings.gradle.kts`, root `build.gradle.kts`, module build files, and `gradle/libs.versions.toml`.
2. Prefer targeted tasks over broad builds.
3. When a build fails, identify the first meaningful error and the likely config file.
4. Keep cache-clearing and destructive steps as last resorts.
5. Use the Gradle wrapper only.

## Preferred Commands

- `./gradlew :shared:assemble`
- `./gradlew :androidApp:assembleDebug`
- `./gradlew :shared:allTests`
- `./gradlew tasks`

## Reference

Read `references/build-debugging.md` for dependency placement and failure triage.
