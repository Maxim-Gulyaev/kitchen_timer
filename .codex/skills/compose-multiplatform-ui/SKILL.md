---
name: compose-multiplatform-ui
description: Design, implement, or review KitchenTimer Compose Multiplatform UI. Use for shared Compose screens, components, Material 3 styling, timer readability, state hoisting, recomposition risks, accessibility labels, small-screen layout, and idle/running/paused/finished visual states.
---

# Compose Multiplatform UI

Use this skill for shared UI work under `shared/src/commonMain`.

## Workflow

1. Inspect existing composables and theme before editing.
2. Keep state ownership above reusable components.
3. Model user actions as explicit callbacks or intents.
4. Keep timer value and primary controls stable in size across countdown changes.
5. Verify UI-only work with `./gradlew :androidApp:assembleDebug`.

## UI Rules

- Prefer Material 3 components already available in the project.
- Use icon buttons for compact controls and provide content descriptions.
- Keep copy short; do not explain obvious controls in visible UI text.
- Avoid nested cards and layout jitter.
- Check states: idle, running, paused, finished, disabled, and zero-duration.

## Reference

Read `references/timer-ui.md` before building or reviewing the main timer screen.
