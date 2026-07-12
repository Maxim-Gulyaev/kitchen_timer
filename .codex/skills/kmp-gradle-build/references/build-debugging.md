# Build Debugging

## Files

- `gradle/libs.versions.toml`: dependency and plugin versions.
- `settings.gradle.kts`: plugin repositories and module inclusion.
- root `build.gradle.kts`: shared plugin declarations.
- `shared/build.gradle.kts`: KMP targets, source sets, Compose setup.
- `androidApp/build.gradle.kts`: Android app entry point.

## Dependency Placement

- Common Kotlin/Compose dependencies belong in `commonMain`.
- Android-only APIs belong in `androidMain` or `androidApp`.
- iOS-specific behavior belongs in `iosMain` or Swift entry code.

## Failure Report Shape

Return the first meaningful error, suspected file, smallest fix, and verification command.
