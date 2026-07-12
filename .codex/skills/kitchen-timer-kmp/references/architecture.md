# KitchenTimer Architecture Reference

## Target Shape

KitchenTimer should stay a compact KMP app with shared domain logic and shared Compose Multiplatform UI.

Build features in this order:

1. Common timer state and transitions.
2. Shared Compose screen and components.
3. Common tests for timer rules.
4. Platform notification, sound, and haptic adapters.
5. Persistence or background behavior after the foreground MVP works.

## Suggested Domain Types

- `TimerPreset`: stable preset id, display label, duration.
- `TimerStatus`: `Idle`, `Running`, `Paused`, `Finished`.
- `TimerState`: selected preset, total duration, remaining duration, status, progress.
- `TimerIntent`: start, pause, resume, reset, select preset, add time.

## Rules

- A timer must never expose negative remaining time.
- Completion is a state transition, not a repeated UI event.
- Reset should restore the selected duration and return to idle.
- Pause/resume should preserve remaining time.
- Selecting a preset while running must be disabled or explicitly implemented as reset-and-select.
- Notification, sound, and haptic feedback should not be embedded in pure reducer logic.
