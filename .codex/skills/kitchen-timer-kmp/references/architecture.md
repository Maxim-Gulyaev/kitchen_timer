# KitchenTimer Architecture Reference

## Target Shape

KitchenTimer should be a small KMP app with shared domain logic and shared Compose Multiplatform UI.

The app should grow in this order:
1. common timer state and transitions;
2. shared Compose screen and components;
3. common tests for timer rules;
4. platform notification/haptic adapters;
5. persistence or background behavior only after the foreground MVP works.

## Suggested Domain Types

- `TimerPreset`: stable preset id, display label, duration.
- `TimerStatus`: `Idle`, `Running`, `Paused`, `Finished`.
- `TimerState`: selected preset, total duration, remaining duration, status, progress.
- `TimerIntent`: start, pause, resume, reset, select preset, add time.
- `TimerViewModel`: state holder that coordinates ticker and side effects.

## Timer Rules

- A timer must never expose negative remaining time.
- Completion is a state transition, not a repeated UI event.
- Reset should restore the selected duration and return to idle.
- Pause/resume should preserve remaining time.
- Selecting a preset while running must be handled deliberately: either disabled in UI or implemented as a reset-and-select action.
- Notification, sound, and haptic feedback should not be embedded in pure reducer logic.

## Compose Rules

- Hoist state to the screen level.
- Keep display formatting deterministic and testable.
- Use fixed or constrained dimensions for countdown display and primary controls to avoid layout jitter.
- Add content descriptions for icon-only controls.
- Keep screen copy short; the timer value and controls should carry the experience.

## Verification

- For domain-only changes, run `./gradlew :shared:allTests`.
- For shared UI or resource changes, run `./gradlew :androidApp:assembleDebug`.
- For Gradle/source-set changes, run both `./gradlew :shared:assemble` and `./gradlew :androidApp:assembleDebug`.
