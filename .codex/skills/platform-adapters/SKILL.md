---
name: platform-adapters
description: Build or review KitchenTimer Android and iOS platform adapters. Use for sound, haptics, notifications, permissions, foreground/background lifecycle, app entry-point wiring, expect/actual service contracts, and keeping platform side effects separate from common timer state logic.
---

# Platform Adapters

Use this skill for Android/iOS APIs and side effects.

## Workflow

1. Define the common contract first.
2. Keep the contract small and domain-neutral.
3. Implement Android actuals in `shared/src/androidMain`.
4. Implement iOS actuals in `shared/src/iosMain`.
5. Wire dependencies from `androidApp` or `iosApp` only when platform entry points are the right composition root.
6. Verify with `./gradlew :shared:assemble` and Android build when Android files changed.

## Rules

- Do not duplicate timer transition logic in platform code.
- Treat permission failures and unavailable APIs as explicit no-op or error states.
- Keep notification, sound, and haptic triggers one-shot.
- Prefer common tests for state transitions and platform tests only for adapter behavior.

## Reference

Read `references/platform-services.md` before adding a platform service.
